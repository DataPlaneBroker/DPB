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

import uk.ac.lancs.networks.Circuit;
import uk.ac.lancs.networks.InvalidServiceException;
import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.NetworkControlException;
import uk.ac.lancs.networks.Segment;
import uk.ac.lancs.networks.Service;
import uk.ac.lancs.networks.ServiceListener;
import uk.ac.lancs.networks.ServiceStatus;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.TrafficFlow;
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.networks.mgmt.NetworkManagementException;
import uk.ac.lancs.networks.mgmt.NetworkResourceException;
import uk.ac.lancs.networks.mgmt.TerminalBusyException;
import uk.ac.lancs.networks.mgmt.TerminalManagementException;
import uk.ac.lancs.networks.mgmt.UnknownTerminalException;
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
     * @throws NetworkControlException if a network control exception
     * occurred
     * 
     * @throws InvalidServiceException if a service was badly specified
     */
    protected void checkErrors(JsonObject rsp)
        throws NetworkManagementException,
            NetworkControlException,
            InvalidServiceException {
        String type = rsp.getString("error");
        if (type == null) return;
        switch (type) {
        case "invalid-segment":
            throw new InvalidServiceException(rsp.getString("msg"));
        case "bad-argument":
            throw new IllegalArgumentException(rsp.getString("msg"));
        case "network-rsrc":
            throw new NetworkResourceException(this, rsp.getString("msg"));
        case "network-ctrl":
            throw new NetworkControlException(control, rsp.getString("msg"));
        case "terminal-mgmt":
            throw new TerminalManagementException(this, getKnownTerminal(rsp
                .getString("terminal-name")), rsp.getString("msg"));
        default:
            throw new NetworkManagementException(this, rsp.getString("msg"));
        }
    }

    @Override
    public void dumpStatus(PrintWriter out) {
        JsonObject req = startRequest("dump-status").build();
        JsonObject rsp = interact(req);
        out.print(rsp.getString("output"));
    }

    @Override
    public NetworkControl getControl() {
        return control;
    }

    private final NetworkControl control = new NetworkControl() {
        @Override
        public Service newService() {
            JsonObject req = startRequest("new-service").build();
            JsonObject rsp = interact(req);
            int id = Integer.parseInt(rsp.getString("service-id"));
            return serviceWatcher.get(id);
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
            JsonObject req =
                startRequest("get-terminal").add("terminal-name", id).build();
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
        public Service getService(int id) {
            JsonObject req =
                startRequest("check-service").add("service-id", id).build();
            JsonObject rsp = interact(req);
            if (rsp.getBoolean("exists")) return serviceWatcher.get(id);
            return null;
        }

        @Override
        public Map<Edge<Terminal>, Double> getModel(double minimumBandwidth) {
            JsonObject req = startRequest("get-model")
                .add("min-bw", minimumBandwidth).build();
            JsonObject rsp = interact(req);
            Map<Edge<Terminal>, Double> model = new HashMap<>();
            for (JsonObject elem : rsp.getJsonArray("edges")
                .getValuesAs(JsonObject.class)) {
                Terminal from = getKnownTerminal(elem.getString("from"));
                Terminal to = getKnownTerminal(elem.getString("to"));
                double weight = elem.getJsonNumber("weight").doubleValue();
                Edge<Terminal> edge = Edge.of(from, to);
                model.put(edge, weight);
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

            executor.execute(this::awaitEvents);
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
            JsonObjectBuilder req = startRequest("define-service");
            JsonArrayBuilder segmentDesc = Json.createArrayBuilder();
            for (Map.Entry<? extends Circuit, ? extends TrafficFlow> entry : request
                .circuitFlows().entrySet()) {
                Circuit c = entry.getKey();
                Terminal t = c.getTerminal();
                if (own(t) == null)
                    throw new InvalidServiceException("not my terminal");
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
            try {
                while (valid)
                    awaitEvent();
            } catch (InterruptedException e) {
                /* Just stop. */
            }
        }

        private void awaitEvent() throws InterruptedException {
            synchronized (this) {
                while (channel == null && valid)
                    wait();
                if (!valid) return;
                JsonObject rsp = channel.read();
                if (rsp == null) {
                    channel = null;
                    return;
                }
                lastStatus = Enum.valueOf(ServiceStatus.class,
                                          rsp.getString("status"));
                listeners.forEach(l -> l.newStatus(lastStatus));
            }
        }

        private JsonChannel channel;

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
            notify();
        }

        @Override
        public synchronized void removeListener(ServiceListener events) {
            if (!valid) return;
            if (!listeners.remove(events) || !listeners.isEmpty()) return;

            /* Stop talking to the remote server. */
            channel.write(null);
            notify();
        }

        @Override
        public void activate() {
            JsonObject req = startRequest("activate-service").build();
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
            JsonObject req = startRequest("deactivate-service").build();
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
                .add("type", "release-service").build();
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
            return channel.read();
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
