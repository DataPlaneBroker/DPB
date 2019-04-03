/*
 * Copyright 2017, Regents of the University of Lancaster
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the
 *    distribution.
 * 
 *  * Neither the name of the University of Lancaster nor the names of
 *    its contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *
 * Author: Steven Simpson <s.simpson@lancaster.ac.uk>
 */

package uk.ac.lancs.networks.jsoncmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/**
 * Multiplexes session channels on to one base JSON channel. Each
 * message sent on the base is wrapped in another object as its
 * <samp>{@value #CONTENT}</samp> field, alongside a
 * <samp>{@value #DISCRIMINATOR}</samp> field that distinguishes the
 * session. Each message received from the base contains a matching
 * field to direct it to the appropriate caller, and the
 * <samp>{@value #CONTENT}</samp> field is extracted and delivered. A
 * special message sent in either direction consisting only of the
 * <samp>{@value #DISCRIMINATOR}</samp> field indicates the end of the
 * session channel, but not the end of the base.
 * 
 * @author simpsons
 */
public abstract class MultiplexingJsonChannelManager {
    /**
     * The name of the field of the root object that identifies the
     * session, namely <samp>{@value}</samp>
     */
    public static final String DISCRIMINATOR = "session";

    /**
     * The name of the field of the root object that holds the
     * encapsulated message, namely <samp>{@value}</samp>
     */
    public static final String CONTENT = "content";

    final JsonChannel base;

    MultiplexingJsonChannelManager(JsonChannel base) {
        if (base == null) throw new NullPointerException("base");
        this.base = base;
    }

    /**
     * Start the thread to read messages.
     */
    public void initiate() {
        thread.start();
    }

    /**
     * Close the base channel, causing the thread to stop.
     */
    public synchronized void terminate() {
        if (terminated) return;
        terminated = true;
        base.close();
        notifyAll();
    }

    private final Thread thread = new Thread(this::watch);

    {
        thread.setDaemon(true);
    }

    private void watch() {
        try {
            while (!terminated) {
                JsonObject msg = base.read();

                /* Stop if the base channel has stopped. */
                if (msg == null) break;

                /* Ignore messages without a session id. Otherwise, get
                 * it. */
                if (!msg.containsKey(DISCRIMINATOR)) continue;
                int sessId = msg.getInt(DISCRIMINATOR);

                synchronized (this) {
                    SessionChannel sess;
                    sess = sessions.get(sessId);
                    if (sess == null) {
                        sess = open(sessId);
                        if (sess == null) continue;
                    }
                    if (msg.containsKey(CONTENT)) {
                        sess.deliver(msg.getJsonObject(CONTENT));
                    } else {
                        sess.terminate();
                    }
                    notifyAll();
                }
            }
        } catch (JsonException ex) {
            // Stop here.
        }

        /* Terminate all remaining sessions. */
        synchronized (this) {
            terminated = true;
            for (SessionChannel sess : sessions.values())
                sess.terminate();
            sessions.clear();
        }
    }

    /**
     * Indexes session channels by session ids. Entries are only removed
     * when the local peer closes the session.
     */
    final Map<Integer, SessionChannel> sessions = new HashMap<>();

    /**
     * If {@code true}, the base channel has returned {@code null},
     * indicating that it has closed, and so no more sessions can be
     * started, and no more messages can be read or written.
     */
    volatile boolean terminated;

    class SessionChannel implements JsonChannel {
        SessionChannel(int id) {
            this.id = id;
        }

        final int id;
        final List<JsonObject> incoming = new ArrayList<>();
        boolean term;

        void deliver(JsonObject next) {
            incoming.add(next);
        }

        void terminate() {
            term = true;
        }

        @Override
        public void write(JsonObject msg) {
            if (msg == null) {
                close();
                return;
            }
            JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add(CONTENT, msg);
            builder.add(DISCRIMINATOR, id);
            msg = builder.build();
            synchronized (MultiplexingJsonChannelManager.this) {
                base.write(msg);
            }
        }

        @Override
        public JsonObject read() {
            synchronized (MultiplexingJsonChannelManager.this) {
                while (incoming.isEmpty() && !term) {
                    try {
                        MultiplexingJsonChannelManager.this.wait();
                    } catch (InterruptedException e) {
                        // Do nothing.
                    }
                }

                /* If a response was queued by another thread, return
                 * that. */
                if (!incoming.isEmpty()) return incoming.remove(0);

                return null;
            }
        }

        @Override
        public void close() {
            JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add(DISCRIMINATOR, id);
            JsonObject msg = builder.build();
            synchronized (MultiplexingJsonChannelManager.this) {
                term = true;
                base.write(msg);
                sessions.remove(id);
                if (sessions.isEmpty() && shouldCloseOnEmpty()) {
                    terminate();
                }
                MultiplexingJsonChannelManager.this.notifyAll();
            }
        }
    }

    /**
     * Determine whether a new absence of sessions should result in a
     * closing of the base. Clients should usually say yes, while
     * servers should usually say no.
     * 
     * @return {@code true} if the base should be closed after all
     * sessions have terminated
     */
    abstract boolean shouldCloseOnEmpty();

    /**
     * Create a new sharing channel with the specific id if appropriate.
     * 
     * @param id the caller id of the new channel
     * 
     * @return the new channel, or {@code null} if creating such a
     * channel is not appropriate
     */
    abstract SessionChannel open(int id);

    abstract boolean shouldRespondToClose();
}
