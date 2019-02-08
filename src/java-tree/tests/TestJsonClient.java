
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

import java.io.Console;
import java.net.InetSocketAddress;

import javax.json.Json;
import javax.json.JsonObject;

import uk.ac.lancs.networks.jsoncmd.JsonChannel;
import uk.ac.lancs.networks.jsoncmd.JsonChannelManager;
import uk.ac.lancs.networks.jsoncmd.MultiplexingJsonClient;

/**
 * 
 * 
 * @author simpsons
 */
public class TestJsonClient {

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        InetSocketAddress peerAddr = new InetSocketAddress(args[0], 6666);
        JsonChannelManager rootMgr = new SocketJsonChannelManager(peerAddr);
        next_conn:
        do {
            try (JsonChannel channel = rootMgr.getChannel()) {
                if (channel == null) {
                    System.err.printf("no more outer channels%n");
                    break;
                }
                JsonChannelManager mgr =
                    new MultiplexingJsonClient(channel, false);
                next_session:
                do {
                    System.out.printf("New session%n");
                    try (JsonChannel inner = mgr.getChannel()) {
                        if (inner == null) {
                            System.err.printf("no more inner channels%n");
                            break;
                        }
                        Console cons = System.console();
                        String line;
                        while ((line = cons.readLine("> ")) != null) {
                            if ("!!".equals(line)) continue next_session;
                            JsonObject msg = Json.createObjectBuilder()
                                .add("msg", line).build();
                            inner.write(msg);
                        }
                        break next_conn;
                    }
                } while (true);
            }
        } while (true);
    }
}
