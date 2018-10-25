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
package uk.ac.lancs.networks.apps;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;

import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.networks.InvalidServiceException;
import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.Segment;
import uk.ac.lancs.networks.Service;
import uk.ac.lancs.networks.ServiceListener;
import uk.ac.lancs.networks.ServiceStatus;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.networks.mgmt.NetworkManagementException;
import uk.ac.lancs.networks.mgmt.NetworkResourceException;
import uk.ac.lancs.routing.span.Edge;

/**
 * 
 * 
 * @author simpsons
 */
class SSHNetwork implements Network {
    /* TODO: Totally re-work this class. */

    private final String externalName;
    private final String internalName;
    private final String hostname;
    private final String username;
    private final int port;
    private final Path keyPath;
    private final String command = "invoke-dataplane-broker";

    public SSHNetwork(Configuration conf) {
        hostname = conf.get("host");
        username = conf.get("user");
        port = Integer.parseInt(conf.get("port", "22"));
        keyPath = conf.getPath("key-file");
        internalName = conf.get("network");
        externalName = conf.get("name", internalName);
    }

    protected final class Channel implements AutoCloseable {
        private final Process proc;
        private boolean inUse = true;
        private final JsonWriter out;
        private final JsonReader in;

        public void write(JsonObject msg) {
            if (!inUse)
                throw new IllegalStateException("channel is out of use");
            out.writeObject(msg);
        }

        public JsonObject read() {
            if (!inUse)
                throw new IllegalStateException("channel is out of use");
            return in.readObject();
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
            command.add(SSHNetwork.this.command);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectError(Redirect.INHERIT);

            /* Start the command. */
            try {
                proc = builder.start();
            } catch (IOException e) {
                throw new NetworkResourceException(SSHNetwork.this, e);
            }
            out = writerFactory.createWriter(proc.getOutputStream(),
                                             StandardCharsets.UTF_8);
            in = readerFactory.createReader(proc.getInputStream(),
                                            StandardCharsets.UTF_8);

            /* Select the remote network. */
            JsonObject selNet =
                Json.createObjectBuilder().add("type", "network")
                    .add("network-name", internalName).build();
            write(selNet);
            JsonObject selNetRsp = read();
            String error = selNetRsp.getString("error");
            switch (error) {
            case "unauthorized":
                throw new IllegalArgumentException("unauthorized to access "
                    + internalName);

            case "no-network":
                throw new IllegalArgumentException("no network "
                    + internalName);
            }
        }

        @Override
        public void close() {
            saveChannel(this);
        }
    }

    private final List<Channel> channels = new ArrayList<>();

    protected final synchronized Channel getChannel() {
        if (channels.isEmpty()) return new Channel();
        Channel result = channels.remove(0);
        result.inUse = true;
        return result;
    }

    private final synchronized void saveChannel(Channel channel) {
        channel.inUse = false;
        channels.add(channel);
    }

    @Override
    public void removeTerminal(String name)
        throws NetworkManagementException {
        try (Channel ch = getChannel()) {
            JsonObject req =
                Json.createObjectBuilder().add("type", "remove-terminal")
                    .add("terminal-name", name).build();
            ch.write(req);
            @SuppressWarnings("unused")
            JsonObject rsp = ch.read();
        }
    }

    @Override
    public void dumpStatus(PrintWriter out) {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    @Override
    public NetworkControl getControl() {
        return control;
    }

    private class RemoteService implements Service {
        private final int id;
        private Segment request;

        private RemoteService(int id) {
            this.id = id;
        }

        @Override
        public NetworkControl getNetwork() {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public Segment getRequest() {
            return request;
        }

        @Override
        public void define(Segment request) throws InvalidServiceException {
            try (Channel ch = getChannel()) {
                {
                    JsonObject req =
                        Json.createObjectBuilder().add("type", "service")
                            .add("service-name", id).build();
                    ch.write(req);
                    JsonObject rsp = ch.read();
                    if (rsp.getString("error") != null) return;
                }
                /* TODO: Define the end-points. */
                /* TODO: Send the define command. */
            }
            this.request = request;
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public void addListener(ServiceListener events) {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public void removeListener(ServiceListener events) {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public void activate() {
            try (Channel ch = getChannel()) {
                {
                    JsonObject req =
                        Json.createObjectBuilder().add("type", "service")
                            .add("service-name", id).build();
                    ch.write(req);
                    JsonObject rsp = ch.read();
                    if (rsp.getString("error") != null) return;
                }
                {
                    JsonObject req = Json.createObjectBuilder()
                        .add("type", "activate").build();
                    ch.write(req);
                    ch.read();
                }
            }
        }

        @Override
        public void deactivate() {
            try (Channel ch = getChannel()) {
                {
                    JsonObject req =
                        Json.createObjectBuilder().add("type", "service")
                            .add("service-name", id).build();
                    ch.write(req);
                    JsonObject rsp = ch.read();
                    if (rsp.getString("error") != null) return;
                }
                {
                    JsonObject req = Json.createObjectBuilder()
                        .add("type", "deactivate").build();
                    ch.write(req);
                    ch.read();
                }
            }
        }

        @Override
        public ServiceStatus status() {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public Collection<Throwable> errors() {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public void release() {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public int id() {
            return id;
        }
    }

    private final Map<Integer, RemoteService> services = new HashMap<>();

    private final NetworkControl control = new NetworkControl() {
        @Override
        public Service newService() {
            try (Channel ch = getChannel()) {
                JsonObject req = Json.createObjectBuilder()
                    .add("type", "new-service").build();
                ch.write(req);
                JsonObject rsp = ch.read();
                switch (rsp.getString("error")) {
                case "no-control":
                    /* Should never happen. */
                    break;
                }
                int sid = rsp.getInt("service-name");
                RemoteService srv = new RemoteService(sid);
                synchronized (SSHNetwork.this) {
                    services.put(sid, srv);
                }
                return srv;
            }
        }

        @Override
        public String name() {
            return externalName;
        }

        @Override
        public Collection<String> getTerminals() {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public Terminal getTerminal(String id) {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public Collection<Integer> getServiceIds() {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public Service getService(int id) {
            synchronized (SSHNetwork.this) {
                RemoteService srv = services.get(id);
                if (srv != null) return srv;
            }
            try (Channel ch = getChannel()) {
                JsonObject req = Json.createObjectBuilder()
                    .add("type", "service").add("service-name", id).build();
                ch.write(req);
                JsonObject rsp = ch.read();
                if (rsp.getString("error") != null) return null;
            }
            synchronized (SSHNetwork.this) {
                RemoteService srv = new RemoteService(id);
                services.put(id, srv);
                return srv;
            }
        }

        @Override
        public Map<Edge<Terminal>, Double> getModel(double minimumBandwidth) {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }
    };

    private static final JsonReaderFactory readerFactory =
        Json.createReaderFactory(Collections.emptyMap());
    private static final JsonWriterFactory writerFactory =
        Json.createWriterFactory(Collections.emptyMap());
}
