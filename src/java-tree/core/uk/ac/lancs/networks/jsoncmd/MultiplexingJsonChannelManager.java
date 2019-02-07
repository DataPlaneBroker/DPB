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
        this.base = base;
    }

    final Map<Integer, SessionChannel> sessions = new HashMap<>();

    boolean inUse;
    boolean terminated;

    class SessionChannel implements JsonChannel {
        SessionChannel(int id) {
            this.id = id;
        }

        final int id;
        final List<JsonObject> responses = new ArrayList<>();
        boolean term;

        @Override
        public void write(JsonObject msg) {
            JsonObjectBuilder builder = Json.createObjectBuilder();
            if (msg != null) builder.add(CONTENT, msg);
            builder.add(DISCRIMINATOR, id);
            msg = builder.build();
            synchronized (MultiplexingJsonChannelManager.this) {
                base.write(msg);
            }
        }

        @Override
        public JsonObject read() {
            synchronized (MultiplexingJsonChannelManager.this) {
                while (responses.isEmpty() && inUse && !terminated && !term) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        throw new JsonException("interrupted", e);
                    }
                }

                /* If a response was queued by another thread, return
                 * that. */
                if (!responses.isEmpty()) {
                    JsonObject rsp = responses.remove(0);
                    if (rsp.isEmpty()) return null;
                    return rsp;
                }

                if (terminated || term) return null;

                /* We have to get a response ourselves. */
                return process(this);
            }
        }

        @Override
        public void close() {
            synchronized (MultiplexingJsonChannelManager.this) {
                sessions.remove(id);
                if (sessions.isEmpty() && shouldCloseOnEmpty()) base.close();
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
    protected JsonObject process(SessionChannel ch) {
        inUse = true;
        try {
            do {
                /* Get the next message. */
                JsonObject rsp = base.read();

                /* If it is null, the peer has terminated the base
                 * session that we're multiplexing on, so all callers
                 * have to be notified. */
                if (rsp == null) {
                    terminated = true;
                    notifyAll();
                    return null;
                }

                /* Discard messages that don't identify the caller. */
                if (!rsp.containsKey(DISCRIMINATOR)) continue;

                /* Extract and remove the caller id. */
                int caller = rsp.getInt(DISCRIMINATOR);

                /* Yield this message if it's for us. */
                if (ch != null && caller == ch.id) {
                    if (!rsp.containsKey(CONTENT)) {
                        ch.term = true;
                        return null;
                    }
                    return rsp;
                }

                /* Otherwise, queue it with the appropriate caller,
                 * notify everyone, and read the next message. */
                SessionChannel other = sessions.get(caller);
                boolean created = false;
                if (other == null) {
                    other = open(caller);
                    created = true;
                }
                if (other == null) continue;
                if (!rsp.containsKey(CONTENT)) {
                    other.term = true;
                    notifyAll();
                } else if (!other.term) {
                    other.responses.add(rsp);
                    if (created && ch == null) return null;
                    notifyAll();
                }
            } while (true);
        } finally {
            inUse = false;
        }
    }

    /**
     * Determine whether a new absence of callers should result in a
     * closing of the base.
     * 
     * @return {@code true} if the base should be closed after all
     * callers have gone
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
}
