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
        this.base = base;
    }

    /**
     * Indexes session channels by session ids. Entries are only removed
     * when the local peer closes the session.
     */
    final Map<Integer, SessionChannel> sessions = new HashMap<>();

    /**
     * If {@code true}, a thread is reading the base channel of behalf
     * of some session, and will act on behalf of other sessions until
     * it gets a message pertaining it its session.
     */
    boolean inUse;

    /**
     * If {@code true}, the base channel has returned {@code null},
     * indicating that it has closed, and so no more sessions can be
     * started, and no more messages can be read or written.
     */
    boolean terminated;

    class SessionChannel implements JsonChannel {
        SessionChannel(int id) {
            this.id = id;
        }

        final int id;
        final List<JsonObject> incoming = new ArrayList<>();
        boolean term;

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
            synchronized (base) {
                base.write(msg);
            }
        }

        @Override
        public JsonObject read() {
            synchronized (MultiplexingJsonChannelManager.this) {
                while (incoming.isEmpty() && inUse && !terminated && !term) {
                    try {
                        MultiplexingJsonChannelManager.this.wait();
                    } catch (InterruptedException e) {
                        // Do nothing.
                    }
                }

                /* If a response was queued by another thread, return
                 * that. */
                if (!incoming.isEmpty()) return incoming.remove(0);

                if (terminated || term) return null;

                /* We have to get a response ourselves. */
                inUse = true;
            }
            return process(this);
        }

        @Override
        public void close() {
            JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add(DISCRIMINATOR, id);
            JsonObject msg = builder.build();
            synchronized (base) {
                base.write(msg);
            }
            synchronized (MultiplexingJsonChannelManager.this) {
                sessions.remove(id);
                if (sessions.isEmpty() && shouldCloseOnEmpty()) {
                    base.close();
                    terminated = true;
                    MultiplexingJsonChannelManager.this.notifyAll();
                }
            }
        }
    }

    /**
     * Read messages until we get one for a specific session channel, or
     * a new session channel is created.
     * 
     * @param ch the session channel a message for which we are waiting
     * for, or {@code null} if waiting for a channel to be created
     * 
     * @return the message for the specific channel, or {@code null} if
     * no channel was specified
     */
    JsonObject process(SessionChannel ch) {
        assert inUse;
        try {
            do {
                /* Get the next message. */
                JsonObject rsp = base.read();

                /* If it is null, the peer has terminated the base
                 * session that we're multiplexing on, so all sessions'
                 * threads have to be notified. */
                if (rsp == null) {
                    synchronized (this) {
                        terminated = true;
                        notifyAll();
                    }
                    return null;
                }

                /* Discard messages that don't identify the session. */
                if (!rsp.containsKey(DISCRIMINATOR)) continue;

                /* Extract the session id. */
                int caller = rsp.getInt(DISCRIMINATOR);

                /* Yield this message if it's for us. */
                if (ch != null && caller == ch.id) {
                    if (!rsp.containsKey(CONTENT)) {
                        synchronized (this) {
                            ch.term = true;
                            /* (We don't have to notify ourselves.) */
                        }
                        return null;
                    }
                    assert ch.incoming.isEmpty();
                    return rsp.getJsonObject(CONTENT);
                }

                /* It doesn't belong to our session. Find out which one
                 * it does belong to. */
                SessionChannel other = sessions.get(caller);
                boolean created = false;
                if (other == null) {
                    /* This is an unknown session. As a client, we
                     * should ignore it, but as a server, we should
                     * create a new session channel for it. */
                    other = open(caller);
                    if (other == null) continue;
                    created = true;
                }

                /* Deliver the message to the other session. */
                if (!rsp.containsKey(CONTENT)) {
                    /* The other session to ours is closing. */
                    synchronized (this) {
                        other.term = true;
                        notifyAll();
                    }
                } else if (!other.term) {
                    synchronized (this) {
                        /* The other session receives a message, so
                         * queue its content. */
                        other.incoming.add(rsp.getJsonObject(CONTENT));

                        /* If this session has just started, that is the
                         * first message, and no-one will be listening
                         * to the session, but our caller might be the
                         * thread that is waiting to receive the session
                         * channel. Just return back to that caller, who
                         * will then detect that the queue of channels
                         * is no longer empty. */
                        if (created && ch == null) return null;

                        /* Otherwise, we'll have to notify threads that
                         * a message has arrived, and possibly that new
                         * session has started. */
                        notifyAll();
                    }
                }
            } while (true);
        } finally {
            synchronized (this) {
                inUse = false;
                notifyAll();
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
