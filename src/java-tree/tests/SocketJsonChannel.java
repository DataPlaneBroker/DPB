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

import java.io.IOException;
import java.net.Socket;

import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonWriter;

import uk.ac.lancs.networks.jsoncmd.ContinuousJsonReader;
import uk.ac.lancs.networks.jsoncmd.ContinuousJsonWriter;
import uk.ac.lancs.networks.jsoncmd.JsonChannel;

class SocketJsonChannel implements JsonChannel {
    private final Socket socket;
    private final JsonReader in;
    private final JsonWriter out;

    public SocketJsonChannel(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new ContinuousJsonReader(this.socket.getInputStream());
        this.out = new ContinuousJsonWriter(this.socket.getOutputStream());
        System.err.printf("new channel on %s%n",
                          socket.getRemoteSocketAddress());
    }

    @Override
    public void write(JsonObject msg) {
        if (msg == null) {
            close();
            return;
        }
        out.writeObject(msg);
    }

    @Override
    public JsonObject read() {
        return in.readObject();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            throw new JsonException("closing", e);
        }
    }
}
