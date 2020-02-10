/*
 * Copyright 2018,2019, Regents of the University of Lancaster
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
import java.io.UncheckedIOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonWriter;

import uk.ac.lancs.config.Configuration;

/**
 * Creates JSON channels to a remote entity over SSH connections.
 * 
 * @author simpsons
 */
public final class SSHJsonChannelManager implements JsonChannelManager {
    private final String hostname;
    private final String username;
    private final Path configPath;
    private final int port;
    private final Path keyPath;
    private final String networkName;

    /**
     * Create a new manager.
     * 
     * @param network the name of the network to select
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
     * <dt><samp>config-file</samp> (optional)</dt>
     * 
     * <dd>Specifies the SSH configuration file to load. Its main use is
     * to prevent loading of <samp>~/.ssh/config</samp> by default, in
     * case there are permission issues.
     * 
     * </dl>
     * 
     * <p>
     * <samp>$&#123;&#125;</samp> structures are resolved first on
     * <samp>key-file</samp> and <samp>config-file</samp>, so
     * <samp>$&#123;user.home&#125;</samp> is valid, for example.
     */
    public SSHJsonChannelManager(String network, Configuration conf) {
        this.networkName = network;
        hostname = conf.get("host");
        username = conf.get("user");
        port = Integer.parseInt(conf.get("port", "22"));
        keyPath = conf.getExpandedPath("key-file");
        configPath = conf.getExpandedPath("config-file");
    }

    private final class Channel implements JsonChannel, AutoCloseable {
        private final Process proc;
        private boolean inUse = true;
        private final JsonWriter out;
        private final JsonReader in;

        public void write(JsonObject msg) {
            if (!inUse)
                throw new IllegalStateException("channel is out of use");
            if (msg == null) {
                out.close();
            } else {
                out.writeObject(msg);
                System.err.printf("sent %s%n", msg);
            }
        }

        public JsonObject read() {
            if (!inUse)
                throw new IllegalStateException("channel is out of use");
            JsonObject msg = in.readObject();
            System.err.printf("received %s%n", msg);
            return msg;
        }

        Channel() {
            /* Build the command line. */
            List<String> command = new ArrayList<>();
            command.add("ssh");
            if (configPath != null) {
                command.add("-F");
                command.add(keyPath.toAbsolutePath().toString());
            }
            command.add(hostname);
            if (username != null) {
                command.add("-l");
                command.add(username);
            }
            if (port != 22) {
                command.add("-p");
                command.add(Integer.toString(port));
            }
            if (keyPath != null) {
                command.add("-i");
                command.add(keyPath.toAbsolutePath().toString());

                /* IdentitiesOnly=yes should be enough to prevent the
                 * use of the agent, but it seems to be ineffective.
                 * IdentityAgent=none seems to work. */
                command.add("-o");
                command.add("IdentityAgent=none");
            }
            command.add("-o");
            command.add("ForwardX11=no");
            command.add("-o");
            command.add("BatchMode=yes");
            command.add("-o");
            command.add("PasswordAuthentication=no");
            command.add(networkName);
            System.err.println(command);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectError(Redirect.INHERIT);

            /* Start the command. */
            try {
                proc = builder.start();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            out = new ContinuousJsonWriter(proc.getOutputStream());
            in = new ContinuousJsonReader(proc.getInputStream());

            JsonObject status = in.readObject();
            String errorType = status.getString("error", null);
            if (errorType != null) {
                switch (errorType) {
                case "unauthorized":
                    throw new IllegalArgumentException("no access: "
                        + status.getString("network", networkName));
                case "no-network":
                    throw new IllegalArgumentException("no network: "
                        + status.getString("network-name", networkName));
                default:
                    throw new IllegalArgumentException("unknown network error: "
                        + errorType + " on " + networkName);

                }
            }
        }

        @Override
        public void close() {
            try {
                out.close();
            } finally {
                try {
                    in.close();
                } finally {
                    proc.destroy();
                    System.err.printf("Closing SSH to %s%n", networkName);
                }
            }
        }
    }

    public final JsonChannel getChannel() {
        return new Channel();
    }
}
