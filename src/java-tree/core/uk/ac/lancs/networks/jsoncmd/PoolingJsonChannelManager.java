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
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Timer;
import java.util.TimerTask;

import javax.json.JsonObject;

/**
 * Wraps a channel manager to provide pooling.
 * 
 * @author simpsons
 */
public final class PoolingJsonChannelManager implements JsonChannelManager {
    private final JsonChannelManager base;

    /**
     * Create a pooling channel manager.
     * 
     * @param base the base manager to provide the raw channels
     */
    public PoolingJsonChannelManager(JsonChannelManager base) {
        this.base = base;
        flusher.schedule(new TimerTask() {
            @Override
            public void run() {
                flush();
            }
        }, 30000, 30000);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                flush();
                flush();
            }
        });
    }

    private class PooledChannel implements JsonChannel {
        private final JsonChannel base;
        boolean inUse = true;

        PooledChannel(JsonChannel base) {
            this.base = base;
        }

        @Override
        public void write(JsonObject msg) {
            if (!inUse)
                throw new IllegalStateException("channel is out of use");
            base.write(msg);
        }

        @Override
        public JsonObject read() {
            if (!inUse)
                throw new IllegalStateException("channel is out of use");
            return base.read();
        }

        @Override
        public void close() {
            saveChannel(this);
        }
    }

    private final Collection<PooledChannel> channels = new LinkedHashSet<>();
    private final Collection<PooledChannel> oldChannels =
        new LinkedHashSet<>();

    private final Timer flusher =
        new Timer("JSON channel pool flusher", true);

    private void flush() {
        Collection<PooledChannel> toDelete;
        synchronized (this) {
            toDelete = new ArrayList<>(oldChannels);
            oldChannels.clear();
            oldChannels.addAll(channels);
            channels.clear();
        }
        for (PooledChannel ch : toDelete) {
            try {
                ch.base.close();
            } catch (Throwable t) {
                /* Ignore. We can't do anything about it. */
            }
        }
    }

    @Override
    public final JsonChannel getChannel() {
        final PooledChannel result;
        synchronized (this) {
            Iterator<PooledChannel> iter = channels.iterator();
            if (!iter.hasNext()) iter = oldChannels.iterator();
            if (!iter.hasNext()) return new PooledChannel(base.getChannel());
            result = iter.next();
            iter.remove();
        }
        result.inUse = true;
        return result;
    }

    private final void saveChannel(PooledChannel channel) {
        channel.inUse = false;
        synchronized (this) {
            channels.add(channel);
        }
    }
}
