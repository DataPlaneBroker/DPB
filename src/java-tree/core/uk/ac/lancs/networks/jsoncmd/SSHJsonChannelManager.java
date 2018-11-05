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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;

import uk.ac.lancs.config.Configuration;

/**
 * Creates JSON channels to a remote entity over SSH connections.
 * 
 * @author simpsons
 */
public final class SSHJsonChannelManager implements JsonChannelManager {
    private final String hostname;
    private final String username;
    private final int port;
    private final Path keyPath;
    private final String command = "invoke-dataplane-broker";

    /**
     * Create a new manager.
     * 
     * @param conf The following parameters are recognized:
     * 
     * <dl>
     * 
     * <dt><samp>host</samp></dt>
     * 
     * <dd>Specifies the remote host to connect to.
     * 
     * <dt><samp>user</samp></dt>
     * 
     * <dd>Specifies the remote username to access.
     * 
     * <dt><samp>port</samp> (default <samp>22</samp>)</dt>
     * 
     * <dd>Specifies the remote port to connect to.
     * 
     * <dt><samp>key-file</samp></dt>
     * 
     * <dd>Specifies the file containing the private key identifying the
     * caller.
     * 
     * </dl>
     */
    public SSHJsonChannelManager(Configuration conf) {
        hostname = conf.get("host");
        username = conf.get("user");
        port = Integer.parseInt(conf.get("port", "22"));
        keyPath = conf.getPath("key-file");
    }

    private final class Channel implements JsonChannel, AutoCloseable {
        private final Process proc;
        private boolean inUse = true;
        private final Writer out;
        private final Reader in;

        public void write(JsonObject msg) {
            if (!inUse)
                throw new IllegalStateException("channel is out of use");
            JsonWriter writer = writerFactory.createWriter(out);
            writer.writeObject(msg);
            System.err.printf("sent %s%n", msg);
            try {
                out.flush();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                throw new UnsupportedOperationException("unimplemented", e);
            }
        }

        public JsonObject read() {
            if (!inUse)
                throw new IllegalStateException("channel is out of use");
            JsonReader reader = readerFactory.createReader(in);
            JsonObject msg = reader.readObject();
            System.err.printf("received %s%n", msg);
            return msg;
        }

        Channel() {
            /* Build the command line. */
            List<String> command = new ArrayList<>();
            command.add("ssh");
            String loc = hostname;
            if (username != null) loc = username + "@" + loc;
            command.add(loc);
            if (port != 22) {
                command.add("-p");
                command.add(Integer.toString(port));
            }
            if (keyPath != null) {
                command.add("-i");
                command.add(keyPath.toAbsolutePath().toString());
            }
            command.add("-o");
            command.add("ForwardX11=no");
            command.add(SSHJsonChannelManager.this.command);
            System.err.println(command);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectError(Redirect.INHERIT);

            /* Start the command. */
            try {
                proc = builder.start();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            out = new OutputStreamWriter(proc.getOutputStream(),
                                         StandardCharsets.UTF_8);
            in = new InputStreamReader(proc.getInputStream(),
                                       StandardCharsets.UTF_8);
        }

        @Override
        public void close() {
            try {
                proc.getOutputStream().close();
            } catch (IOException e) {
                /* Not much we can do about this (hopefully) rare
                 * event. */
            }
        }
    }

    public final JsonChannel getChannel() {
        return new Channel();
    }

    private static final JsonReaderFactory readerFactory =
        Json.createReaderFactory(Collections.emptyMap());
    private static final JsonWriterFactory writerFactory =
        Json.createWriterFactory(Collections.emptyMap());
}
