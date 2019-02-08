
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

import java.net.ServerSocket;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.json.JsonObject;

import uk.ac.lancs.networks.jsoncmd.JsonChannel;
import uk.ac.lancs.networks.jsoncmd.JsonChannelManager;
import uk.ac.lancs.networks.jsoncmd.MultiplexingJsonServer;

/**
 * 
 * 
 * @author simpsons
 */
public class TestJsonServer {
    static final Executor executor = Executors.newCachedThreadPool();

    static class ChannelDisplay implements Runnable {
        private final JsonChannel channel;

        public ChannelDisplay(JsonChannel channel) {
            System.err.printf("New session%n");
            this.channel = channel;
        }

        @Override
        public void run() {
            System.err.printf("Waiting...%n");
            JsonObject msg;
            while ((msg = channel.read()) != null) {
                System.out.printf("Message: %s%n", msg);
            }
            System.out.printf("Terminated%n");
        }
    }

    static void processChannels(JsonChannelManager mgr) {
        System.err.printf("New connection%n");
        for (JsonChannel ch = null; (ch = mgr.getChannel()) != null;)
            executor.execute(new ChannelDisplay(ch));
    }

    public static void main(String[] args) throws Exception {
        ServerSocketJsonChannelManager server =
            new ServerSocketJsonChannelManager(new ServerSocket(6666));

        for (JsonChannel ch = null; (ch = server.getChannel()) != null;) {
            JsonChannelManager connMgr = new MultiplexingJsonServer(ch);
            executor.execute(() -> processChannels(connMgr));
        }
    }
}
