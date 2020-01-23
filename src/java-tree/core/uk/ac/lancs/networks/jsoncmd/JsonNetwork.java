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

import java.io.PrintWriter;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;

import uk.ac.lancs.networks.ChordMetrics;
import uk.ac.lancs.networks.Circuit;
import uk.ac.lancs.networks.InvalidServiceException;
import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.NetworkLogicException;
import uk.ac.lancs.networks.Segment;
import uk.ac.lancs.networks.Service;
import uk.ac.lancs.networks.ServiceListener;
import uk.ac.lancs.networks.ServiceStatus;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.TrafficFlow;
import uk.ac.lancs.networks.UnknownServiceException;
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.networks.mgmt.NetworkManagementException;
import uk.ac.lancs.networks.mgmt.NetworkResourceException;
import uk.ac.lancs.networks.mgmt.OwnTerminalException;
import uk.ac.lancs.networks.mgmt.SubterminalBusyException;
import uk.ac.lancs.networks.mgmt.SubterminalManagementException;
import uk.ac.lancs.networks.mgmt.TerminalBusyException;
import uk.ac.lancs.networks.mgmt.TerminalConfigurationException;
import uk.ac.lancs.networks.mgmt.TerminalExistsException;
import uk.ac.lancs.networks.mgmt.TerminalId;
import uk.ac.lancs.networks.mgmt.TerminalManagementException;
import uk.ac.lancs.networks.mgmt.TerminalNameException;
import uk.ac.lancs.networks.mgmt.UnknownSubnetworkException;
import uk.ac.lancs.networks.mgmt.UnknownSubterminalException;
import uk.ac.lancs.networks.mgmt.UnknownTerminalException;
import uk.ac.lancs.networks.mgmt.UnknownTrunkException;
import uk.ac.lancs.networks.util.ReferenceWatcher;
import uk.ac.lancs.routing.span.Edge;

/**
 * Accesses a remote network through a supply of JSON channels.
 * 
 * @author simpsons
 */
public class JsonNetwork implements Network {
    private final String name;
    private final JsonChannelManager channels;
    private final Executor executor;

    /**
     * Create a network accessible through a supply of JSON channels.
     * 
     * @param name the local network name
     * 
     * @param executor a resource for running background activities on
     * 
     * @param channels a supply of JSON channels
     */
    public JsonNetwork(String name, Executor executor,
                       JsonChannelManager channels) {
        this.name = name;
        this.executor = executor;
        this.channels = channels;

        serviceWatcher.start();
    }

    @Override
    public void removeTerminal(String name)
        throws UnknownTerminalException,
            TerminalBusyException {
        JsonObject req = startRequest("remove-terminal")
            .add("terminal-name", name).build();
        JsonObject rsp = interact(req);
        try {
            checkErrors(rsp);
        } catch (UnknownTerminalException | TerminalBusyException
            | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UndeclaredThrowableException(e);
        }
    }

    /**
     * Check for an error in a response, and throw a corresponding
     * exception. Subclasses should override this one by invoking it
     * first, and then performing their own checks.
     * 
     * @param rsp the response message to check
     * 
     * @throws NetworkManagementException if a network management
     * exception occurred
     * 
     * @throws NetworkLogicException if a functional network exception
     * occurred
     */
    protected void checkErrors(JsonObject rsp)
        throws NetworkManagementException,
            NetworkLogicException {
        String type = rsp.getString("error", null);
        if (type == null) return;
        switch (type) {
        case "network-mgmt":
            throw new NetworkManagementException(rsp
                .getString("network-name"), rsp.getString("msg"));

        case "subnetwork-unknown":
            throw new UnknownSubnetworkException(rsp
                .getString("network-name"), rsp.getString("subnetwork-name"));

        case "segment-invalid":
            throw new InvalidServiceException(rsp.getString("network-name"),
                                              rsp.getInt("service-id"),
                                              rsp.getString("msg"));
        case "bad-argument":
            throw new IllegalArgumentException(rsp.getString("msg"));

        case "network-resource":
            throw new NetworkResourceException(rsp.getString("network-name"),
                                               rsp.getString("msg"));

        case "service-unknown":
            throw new UnknownServiceException(rsp.getString("network-name"),
                                              rsp.getInt("service-id"));

        case "terminal-config":
            throw new TerminalConfigurationException(rsp
                .getString("network-name"), rsp.getString("config"),
                                                     rsp.getString("msg"));

        case "trunk-unknown": {
            TerminalId term =
                TerminalId.of(rsp.getString("subnetwork-name"),
                              rsp.getString("subterminal-name"));
            throw new UnknownTrunkException(rsp.getString("network-name"),
                                            term);
        }

        case "subterminal-unknown": {
            TerminalId term =
                TerminalId.of(rsp.getString("subnetwork-name"),
                              rsp.getString("subterminal-name"));
            throw new UnknownSubterminalException(rsp
                .getString("network-name"), term);
        }

        case "subterminal-busy": {
            TerminalId term =
                TerminalId.of(rsp.getString("subnetwork-name"),
                              rsp.getString("subterminal-name"));
            throw new SubterminalBusyException(rsp.getString("network-name"),
                                               term);
        }

        case "subterminal-mgmt": {
            TerminalId term =
                TerminalId.of(rsp.getString("subnetwork-name"),
                              rsp.getString("subterminal-name"));
            throw new SubterminalManagementException(rsp
                .getString("network-name"), term, rsp.getString("msg"));
        }

        case "own-terminal":
            throw new OwnTerminalException(rsp.getString("network-name"),
                                           rsp.getString("terminal-name"));

        case "terminal-busy":
            throw new TerminalBusyException(rsp.getString("network-name"),
                                            rsp.getString("terminal-name"));

        case "terminal-unknown":
            throw new UnknownTerminalException(rsp
                .getString("network-name"), rsp.getString("terminal-name"));

        case "terminal-exists":
            throw new TerminalExistsException(rsp.getString("network-name"),
                                              rsp.getString("terminal-name"));

        case "terminal-name":
            throw new TerminalNameException(rsp.getString("network-name"),
                                            rsp.getString("terminal-name"),
                                            rsp.getString("msg"));

        case "terminal-mgmt":
            throw new TerminalManagementException(rsp
                .getString("network-name"), rsp.getString("terminal-name"),
                                                  rsp.getString("msg"));

        default:
            throw new NetworkManagementException(rsp
                .getString("network-name"), rsp.getString("msg"));
        }
    }

    @Override
    public void dumpStatus(PrintWriter out) {
        JsonObject req = startRequest("dump-status").build();
        JsonObject rsp = interact(req);
        out.print(rsp.getString("output"));
        out.flush();
    }

    @Override
    public NetworkControl getControl() {
        return control;
    }

    private final NetworkControl control = new NetworkControl() {
        @Override
        public Service newService(String handle) {
            JsonObjectBuilder reqBuild = startRequest("new-service");
            if (handle != null) reqBuild = reqBuild.add("handle", handle);
            JsonObject req = reqBuild.build();
            JsonObject rsp = interact(req);
            if (rsp.containsKey("service-id")) {
                int id = rsp.getInt("service-id");
                return serviceWatcher.get(id);
            }
            return null;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Collection<String> getTerminals() {
            JsonObject req = startRequest("get-terminals").build();
            JsonObject rsp = interact(req);
            return rsp.getJsonArray("terminal-names")
                .getValuesAs(JsonString.class).stream()
                .map(JsonString::getString).collect(Collectors.toList());
        }

        @Override
        public Terminal getTerminal(String id) {
            JsonObject req = startRequest("check-terminal")
                .add("terminal-name", id).build();
            JsonObject rsp = interact(req);
            if (rsp.getBoolean("exists")) return getKnownTerminal(id);
            return null;
        }

        @Override
        public Collection<Integer> getServiceIds() {
            JsonObject req = startRequest("get-services").build();
            JsonObject rsp = interact(req);
            return rsp.getJsonArray("service-ids")
                .getValuesAs(JsonNumber.class).stream()
                .map(JsonNumber::intValue).collect(Collectors.toList());
        }

        @Override
        public Service getService(String handle) {
            JsonObject req =
                startRequest("find-service").add("handle", handle).build();
            JsonObject rsp = interact(req);
            if (!rsp.containsKey("service-id")) return null;
            int id = rsp.getInt("service-id");
            return serviceWatcher.get(id);
        }

        @Override
        public Service getService(int id) {
            JsonObject req =
                startRequest("check-service").add("service-id", id).build();
            JsonObject rsp = interact(req);
            if (rsp.getBoolean("exists")) return serviceWatcher.get(id);
            return null;
        }

        @Override
        public Map<Edge<Terminal>, ChordMetrics>
            getModel(double minimumBandwidth) {
            JsonObject req = startRequest("get-model")
                .add("min-bw", minimumBandwidth).build();
            JsonObject rsp = interact(req);
            Map<Edge<Terminal>, ChordMetrics> model = new HashMap<>();
            for (JsonObject elem : rsp.getJsonArray("edges")
                .getValuesAs(JsonObject.class)) {
                Terminal from = getKnownTerminal(elem.getString("from"));
                Terminal to = getKnownTerminal(elem.getString("to"));
                ChordMetrics.Builder cb = ChordMetrics.start();
                if (elem.containsKey("weight"))
                    cb.withDelay(elem.getJsonNumber("weight").doubleValue());
                if (elem.containsKey("upstream")) cb
                    .withDelay(elem.getJsonNumber("upstream").doubleValue());
                if (elem.containsKey("downstream")) cb.withDelay(elem
                    .getJsonNumber("downstream").doubleValue());
                Edge<Terminal> edge = Edge.of(from, to);
                if (edge.get(0) != from) cb.reverse();
                model.put(edge, cb.build());
            }
            return model;
        }
    };

    private RemoteService recoverService(Integer id) {
        return new RemoteService(id);
    }

    private final ReferenceWatcher<Service, RemoteService, Integer> serviceWatcher =
        ReferenceWatcher.on(Service.class, getClass().getClassLoader(),
                            this::recoverService, RemoteService::cleanUp);

    private class RemoteService implements Service {
        private final int id;
        private Segment request;
        private volatile boolean valid = true;

        synchronized void cleanUp() {
            valid = false;
            listeners.clear();
            notifyAll();
        }

        RemoteService(int id) {
            this.id = id;
        }

        private ServiceStatus lastStatus = ServiceStatus.DORMANT;

        @Override
        public NetworkControl getNetwork() {
            return control;
        }

        @Override
        public Segment getRequest() {
            return request;
        }

        @Override
        public void define(Segment request) throws InvalidServiceException {
            JsonObjectBuilder req =
                startRequest("define-service").add("service-id", id);
            JsonArrayBuilder segmentDesc = Json.createArrayBuilder();
            for (Map.Entry<? extends Circuit, ? extends TrafficFlow> entry : request
                .circuitFlows().entrySet()) {
                Circuit c = entry.getKey();
                Terminal t = c.getTerminal();
                if (own(t) == null)
                    throw new InvalidServiceException(control.name(), id,
                                                      "not my terminal");
                int label = c.getLabel();
                TrafficFlow f = entry.getValue();
                segmentDesc.add(Json.createObjectBuilder()
                    .add("terminal-name", t.name()).add("label", label)
                    .add("ingress-bw", f.ingress).add("egress-bw", f.egress));
            }
            req.add("segment", segmentDesc);
            JsonObject rsp = interact(req.build());
            try {
                checkErrors(rsp);
            } catch (InvalidServiceException | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new UndeclaredThrowableException(e);
            }

            synchronized (this) {
                this.request = request;
            }
        }

        private Collection<ServiceListener> listeners = new HashSet<>();

        private void awaitEvents() {
            while (awaitEvent())
                ;
        }

        private boolean awaitEvent() {
            synchronized (this) {
                if (!valid) return false;
            }
            JsonChannel ch = channel;
            if (ch == null) return false;
            JsonObject rsp = ch.read();
            synchronized (this) {
                if (rsp == null) {
                    channel = null;
                    return false;
                }
                lastStatus = Enum.valueOf(ServiceStatus.class,
                                          rsp.getString("status"));
                listeners.forEach(l -> l.newStatus(lastStatus));
                if (lastStatus == ServiceStatus.RELEASED) {
                    channel = null;
                    return false;
                }
                return true;
            }
        }

        private volatile JsonChannel channel;

        @Override
        public synchronized void addListener(ServiceListener events) {
            if (!valid) return;
            boolean emptyBefore = listeners.isEmpty();
            if (!listeners.add(events)) return;
            if (!emptyBefore) return;

            /* Start watching. */
            channel = channels.getChannel();
            JsonObject req =
                startRequest("watch-service").add("service-id", id).build();
            channel.write(req);

            executor.execute(this::awaitEvents);
            notify();
        }

        @Override
        public synchronized void removeListener(ServiceListener events) {
            if (!valid) return;
            if (!listeners.remove(events) || !listeners.isEmpty()) return;

            /* Stop talking to the remote server. */
            /* TODO: Shouldn't we just close the channel? */
            channel.write(null);
            channel = null;
            notify();
        }

        @Override
        public ServiceStatus
            awaitStatus(Collection<? extends ServiceStatus> accept,
                        long timeoutMillis) {
            /* TODO: Make this choice configurable. */
            if (true) {
                return Service.super.awaitStatus(accept, timeoutMillis);
            } else {
                JsonArrayBuilder sts = Json.createArrayBuilder();
                for (ServiceStatus st : accept)
                    sts.add(st.name());
                JsonObject req =
                    startRequest("await-service-status").add("service-id", id)
                        .add("timeout-millis", timeoutMillis)
                        .add("acceptable", sts).build();
                JsonObject rsp = interact(req);
                try {
                    checkErrors(rsp);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new UndeclaredThrowableException(e);
                }
                return ServiceStatus.valueOf(rsp.getString("status"));
            }
        }

        @Override
        public void activate() {
            JsonObject req = startRequest("activate-service")
                .add("service-id", id).build();
            JsonObject rsp = interact(req);
            try {
                checkErrors(rsp);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new UndeclaredThrowableException(e);
            }
        }

        @Override
        public void deactivate() {
            JsonObject req = startRequest("deactivate-service")
                .add("service-id", id).build();
            JsonObject rsp = interact(req);
            try {
                checkErrors(rsp);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new UndeclaredThrowableException(e);
            }
        }

        @Override
        public synchronized ServiceStatus status() {
            return lastStatus;
        }

        @Override
        public Collection<Throwable> errors() {
            /* TODO: Fetch remote errors. */
            return Collections.emptySet();
        }

        @Override
        public void release() {
            if (!valid) return;
            JsonObject req = Json.createObjectBuilder()
                .add("type", "release-service").add("service-id", id).build();
            JsonObject rsp = interact(req);
            try {
                checkErrors(rsp);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new UndeclaredThrowableException(e);
            }
        }

        @Override
        public int id() {
            return id;
        }
    }

    private class RemoteTerminal implements Terminal {
        private final String name;

        final JsonNetwork owner = JsonNetwork.this;

        public RemoteTerminal(String name) {
            this.name = name;
        }

        @Override
        public NetworkControl getNetwork() {
            return control;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            RemoteTerminal other = (RemoteTerminal) obj;
            if (!getOuterType().equals(other.getOuterType())) return false;
            if (name == null) {
                if (other.name != null) return false;
            } else if (!name.equals(other.name)) return false;
            return true;
        }

        private JsonNetwork getOuterType() {
            return JsonNetwork.this;
        }

        @Override
        public String toString() {
            return JsonNetwork.this.name + ":" + this.name;
        }
    }

    private RemoteTerminal own(Terminal t) {
        if (t instanceof RemoteTerminal) {
            RemoteTerminal result = (RemoteTerminal) t;
            if (result.owner != this) return null;
            return result;
        }
        return null;
    }

    /**
     * Issue a JSON request down a fresh channel, await a single JSON
     * response, and then close the channel.
     * 
     * @param req the request to issue
     * 
     * @return the single JSON response
     */
    protected final JsonObject interact(JsonObject req) {
        try (JsonChannel channel = channels.getChannel()) {
            channel.write(req);
            JsonObject rsp = channel.read();
            return rsp;
        }
    }

    /**
     * Get a terminal that has just been implied to exist the result of
     * a call.
     * 
     * @param id the local terminal name
     * 
     * @return the requested terminal
     */
    protected Terminal getKnownTerminal(String id) {
        return new RemoteTerminal(id);
    }

    /**
     * Start to build a request object. This is a JSON object with a
     * single key <samp>type</samp> whose value is specified by an
     * argument of this call. This is a convenience for code within this
     * class and subclasses that issues JSON requests.
     * 
     * @param type the value of the type field in the result
     * 
     * @return a JSON object builder with the field <samp>type</samp>
     * already set to the specified value
     */
    protected JsonObjectBuilder startRequest(String type) {
        return Json.createObjectBuilder().add("type", type);
    }
}
