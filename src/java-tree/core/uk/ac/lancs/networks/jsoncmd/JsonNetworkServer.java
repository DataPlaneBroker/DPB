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
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator.OfInt;
import java.util.concurrent.Executor;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;

import uk.ac.lancs.networks.ChordMetrics;
import uk.ac.lancs.networks.Circuit;
import uk.ac.lancs.networks.CircuitLogicException;
import uk.ac.lancs.networks.ExpiredServiceException;
import uk.ac.lancs.networks.InvalidServiceException;
import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.NetworkLogicException;
import uk.ac.lancs.networks.Segment;
import uk.ac.lancs.networks.Service;
import uk.ac.lancs.networks.ServiceListener;
import uk.ac.lancs.networks.ServiceLogicException;
import uk.ac.lancs.networks.ServiceStatus;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.TerminalLogicException;
import uk.ac.lancs.networks.TrafficFlow;
import uk.ac.lancs.networks.mgmt.BandwidthUnavailableException;
import uk.ac.lancs.networks.mgmt.ExpiredTrunkException;
import uk.ac.lancs.networks.mgmt.LabelManagementException;
import uk.ac.lancs.networks.mgmt.LabelsInUseException;
import uk.ac.lancs.networks.mgmt.LabelsUnavailableException;
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
import uk.ac.lancs.networks.mgmt.TrunkManagementException;
import uk.ac.lancs.networks.mgmt.UnknownSubnetworkException;
import uk.ac.lancs.networks.mgmt.UnknownSubterminalException;
import uk.ac.lancs.networks.mgmt.UnknownTerminalException;
import uk.ac.lancs.networks.mgmt.UnknownTrunkException;
import uk.ac.lancs.routing.span.Edge;

/**
 * Translates JSON-formatted requests into invocations on a
 * {@link Network}, and results of those invocations back to JSON.
 * 
 * @author simpsons
 */
public class JsonNetworkServer {
    private final Network network;
    private final boolean allowMgmt;

    /**
     * Create a JSON adaptor around a network.
     * 
     * @param network the network to be invoked
     * 
     * @param allowMgmt whether management calls will be allowed, or
     * only calls on {@link Network#getControl()}
     */
    public JsonNetworkServer(Network network, boolean allowMgmt) {
        this.network = network;
        this.allowMgmt = allowMgmt;
    }

    private final void checkManagement() throws NetworkResourceException {
        if (!allowMgmt)
            throw new NetworkResourceException(network.getControl().name(),
                                               "management calls forbidden");
    }

    private Service confirmService(JsonObject req) {
        int id = req.getInt("service-id");
        Service srv = network.getControl().getService(id);
        if (srv == null)
            throw new ExpiredServiceException(network.getControl().name(),
                                              id);
        return srv;
    }

    /**
     * Process a JSON-formatted request. Subclasses should override this
     * method, and then invoke it on {@code super}. If {@code null} is
     * returned, the request should be handled by the subclass.
     * 
     * @param req the request to be processed
     * 
     * @param onClose Actions submitted to this executor will be invoked
     * when the connection supplying request objects has been closed.
     * 
     * @return a series of JSON responses (usually just one), or
     * {@code null} if the request was not recognized
     * 
     * @default This implementation recognizes the following commands:
     * 
     * <dl>
     * 
     * <dt><samp>remove-terminal <var>terminal-name</var></samp>
     * 
     * <dd>Invoke {@link Network#removeTerminal(String)} with the
     * specified name.
     * 
     * <dt><samp>dump-status</samp>
     * 
     * <dd>Invoke {@link Network#dumpStatus(PrintWriter)}, providing the
     * output as a single field <samp>output</samp> of the result.
     * 
     * <dt><samp>check-terminal <var>terminal-name</var></samp>
     * 
     * <dd>Invoke {@link NetworkControl#getTerminal(String)} with the
     * specified name. Return an object with a boolean field
     * <samp>exists</samp>, with the value {@code true} if the result
     * was not {@code null}.
     * 
     * <dt><samp>get-terminals</samp>
     * 
     * <dd>Invoke {@link NetworkControl#getTerminals()}, and return an
     * object with a single field <samp>terminal-names</samp> containing
     * an array of the result.
     * 
     * <dt><samp>check-service <var>service-id</var></samp>
     * 
     * <dd>Invoke {@link NetworkControl#getService(int)} with the
     * specified id. Return an object with a boolean field
     * <samp>exists</samp>, with the value {@code true} if the result
     * was not {@code null}.
     * 
     * <dt><samp>get-services</samp>
     * 
     * <dd>Invoke {@link NetworkControl#getServiceIds()}, and return an
     * object with a single field <samp>service-ids</samp> containing an
     * array of the result.
     * 
     * <dt><samp>get-model <var>min-bw</var></samp>
     * 
     * <dd>Invoke {@link NetworkControl#getModel(double)} with the
     * provided minimum bandwidth. An object is returned with a single
     * field <samp>edges</samp>, which is an array of objects, each
     * corresponding to an entry in the returned map. Each object
     * consists of the name of the entry's edge's first terminal
     * <samp>from</samp>, the name of the second terminal
     * <samp>to</samp>, and the weight of the edge <samp>weight</samp>.
     * 
     * <dt><samp>new-service</samp>
     * 
     * <dd>Invoke {@link NetworkControl#newService()}, returning an
     * object with the single field <samp>service-id</samp> containing
     * the new service's id.
     * 
     * <dt><samp>define-service <var>service-id</var>
     * <var>segment</var></samp>
     * 
     * <dd>Invoke {@link Service#define(uk.ac.lancs.networks.Segment)}
     * on the identified service with the provided segment descriptor,
     * which is an array of objects. Each object identifies the terminal
     * by name <samp>terminal-name</samp>, the label <samp>label</samp>,
     * the ingress bandwidth <samp>ingress</samp>, and the egress
     * bandwidth <samp>egress</samp>. An empty object is returned.
     * 
     * <dt><samp>activate-service <var>service-id</var></samp>
     * 
     * <dd>Invoke {@link Service#activate()} on the identified service
     * with the provided segment descriptor. An empty object is
     * returned.
     * 
     * <dt><samp>deactivate-service <var>service-id</var></samp>
     * 
     * <dd>Invoke {@link Service#deactivate()} on the identified service
     * with the provided segment descriptor. An empty object is
     * returned.
     * 
     * <dt><samp>release-service <var>service-id</var></samp>
     * 
     * <dd>Invoke {@link Service#release()} on the identified service
     * with the provided segment descriptor. An empty object is
     * returned.
     * 
     * <dt><samp>watch-service <var>service-id</var></samp>
     * 
     * <dd>Invoke {@link Service#addListener(ServiceListener)} on the
     * identified service with an internal listener. Each event on the
     * listener is reported as an object with a single field
     * <samp>status</samp>, until <samp>RELEASED</samp> is reported, or
     * until no more request objects are submitted.
     * 
     * <dt><samp>await-service-status <var>service-id</var>
     * <var>acceptable</var> <var>timeout-millis</var></samp>
     * 
     * <dd>Invoke
     * {@link Service#awaitStatus(java.util.Collection, long)} on the
     * identified service with the given timeout and set of acceptable
     * terminating statuses.
     * 
     * </dl>
     */
    public Iterable<JsonObject> interact(JsonObject req, Executor onClose) {
        try {
            switch (req.getString("type")) {
            case "remove-terminal": {
                checkManagement();
                String name = req.getString("terminal-name");
                network.removeTerminal(name);
                return empty();
            }

            case "dump-status": {
                checkManagement();
                StringWriter result = new StringWriter();
                try (PrintWriter out = new PrintWriter(result)) {
                    network.dumpStatus(out);
                }
                return one(Json.createObjectBuilder()
                    .add("output", result.toString()).build());
            }

            case "check-terminal": {
                String name = req.getString("terminal-name");
                Terminal term = network.getControl().getTerminal(name);
                return one(Json.createObjectBuilder()
                    .add("exists", term != null).build());
            }

            case "await-service-status": {
                int id = req.getInt("service-id");
                Service srv = network.getControl().getService(id);
                EnumSet<ServiceStatus> accepted =
                    EnumSet.noneOf(ServiceStatus.class);
                for (JsonString txt : req.getJsonArray("acceptable")
                    .getValuesAs(JsonString.class)) {
                    ServiceStatus v = ServiceStatus.valueOf(txt.getString());
                    accepted.add(v);
                }
                long timeoutMillis =
                    req.getJsonNumber("timeout-millis").longValue();
                ServiceStatus result =
                    srv.awaitStatus(accepted, timeoutMillis);
                return one(Json.createObjectBuilder()
                    .add("status", result.toString()).build());
            }

            case "check-service": {
                int id = req.getInt("service-id");
                Service srv = network.getControl().getService(id);
                return one(Json.createObjectBuilder()
                    .add("exists", srv != null).build());
            }

            case "find-service": {
                String handle = req.getString("handle", null);
                Service srv = network.getControl().getService(handle);
                if (srv == null) {
                    return empty();
                } else {
                    return one(Json.createObjectBuilder()
                        .add("service-id", srv.id()).build());
                }
            }

            case "new-service": {
                String handle = req.getString("handle", null);
                Service srv = network.getControl().newService(handle);
                if (srv == null) return empty();
                return one(Json.createObjectBuilder()
                    .add("service-id", srv.id()).build());
            }

            case "get-terminals": {
                JsonArrayBuilder builder = Json.createArrayBuilder();
                for (String name : network.getControl().getTerminals())
                    builder.add(name);
                return one(Json.createObjectBuilder()
                    .add("terminal-names", builder).build());
            }

            case "get-services": {
                JsonArrayBuilder builder = Json.createArrayBuilder();
                for (int name : network.getControl().getServiceIds())
                    builder.add(name);
                return one(Json.createObjectBuilder()
                    .add("service-ids", builder).build());
            }

            case "get-model": {
                double minBw = req.getJsonNumber("min-bw").doubleValue();
                Map<Edge<Terminal>, ChordMetrics> model =
                    network.getControl().getModel(minBw);
                JsonArrayBuilder result = Json.createArrayBuilder();
                for (Map.Entry<Edge<Terminal>, ChordMetrics> entry : model
                    .entrySet()) {
                    Edge<Terminal> edge = entry.getKey();
                    ChordMetrics metrics = entry.getValue();
                    JsonObjectBuilder ob = Json.createObjectBuilder()
                        .add("from", edge.get(0).name())
                        .add("to", edge.get(1).name());
                    metrics.copyDelay(v -> ob.add("weight", v));
                    metrics.copyUpstream(v -> ob.add("upstream", v));
                    metrics.copyDownstream(v -> ob.add("downstream", v));
                    result.add(ob);
                }
                return one(Json.createObjectBuilder().add("edges", result)
                    .build());
            }

            case "define-service": {
                Service srv = confirmService(req);
                JsonArray segmentDesc = req.getJsonArray("segment");
                Map<Circuit, TrafficFlow> parts = new HashMap<>();
                for (JsonObject endPoint : segmentDesc
                    .getValuesAs(JsonObject.class)) {
                    Terminal term = network.getControl()
                        .getTerminal(endPoint.getString("terminal-name"));
                    int label = endPoint.getInt("label");
                    Circuit circuit = term.circuit(label);
                    double ingress =
                        endPoint.getJsonNumber("ingress-bw").doubleValue();
                    double egress =
                        endPoint.getJsonNumber("egress-bw").doubleValue();
                    TrafficFlow flow = TrafficFlow.of(ingress, egress);
                    parts.put(circuit, flow);
                }
                Segment segment = Segment.create(parts);
                srv.define(segment);
                return empty();
            }

            case "activate-service": {
                Service srv = confirmService(req);
                srv.activate();
                return empty();
            }

            case "deactivate-service": {
                Service srv = confirmService(req);
                srv.deactivate();
                return empty();
            }

            case "release-service": {
                Service srv = confirmService(req);
                srv.release();
                return empty();
            }

            case "watch-service": {
                Service srv = confirmService(req);
                class MyListener
                    implements Iterable<JsonObject>, Iterator<JsonObject>,
                    ServiceListener {

                    private final List<JsonObject> rsps = new ArrayList<>();
                    private boolean more = true;

                    synchronized void stop() {
                        srv.removeListener(this);
                        more = false;
                        notify();
                    }

                    @Override
                    public synchronized void
                        newStatus(ServiceStatus newStatus) {
                        rsps.add(Json.createObjectBuilder()
                            .add("status", newStatus.toString()).build());
                        if (newStatus == ServiceStatus.RELEASED)
                            stop();
                        else
                            notify();
                    }

                    @Override
                    public Iterator<JsonObject> iterator() {
                        return this;
                    }

                    @Override
                    public synchronized boolean hasNext() {
                        while (more && rsps.isEmpty())
                            try {
                                wait();
                            } catch (InterruptedException e) {
                                // Ignore?
                            }
                        return !rsps.isEmpty();
                    }

                    @Override
                    public synchronized JsonObject next() {
                        if (rsps.isEmpty())
                            throw new NoSuchElementException();
                        return rsps.remove(0);
                    }
                }
                MyListener listener = new MyListener();
                srv.addListener(listener);
                onClose.execute(listener::stop);
                return listener;
            }

            default:
                return null;
            }
        } catch (NetworkManagementException | InvalidServiceException e) {
            return handle(e);
        }
    }

    /**
     * Translates an exception into an error response.
     * 
     * @param t the exception
     * 
     * @return a single JSON response
     */
    protected Iterable<JsonObject> handle(Throwable t) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("msg", t.getMessage());

        try {
            throw t;
        } catch (NetworkManagementException e) {
            builder.add("network-name", e.getNetworkName());
            try {
                throw e;
            } catch (TrunkManagementException e2) {
                TerminalId start = e2.getStartTerminal();
                TerminalId end = e2.getEndTerminal();
                builder.add("start-terminal-name", start.network)
                    .add("start-network-name", start.terminal)
                    .add("start-terminal-name", end.network)
                    .add("start-network-name", end.terminal);
                try {
                    throw e2;
                } catch (LabelManagementException e3) {
                    JsonArrayBuilder arr = Json.createArrayBuilder();
                    for (OfInt iter = e3.getLabels().stream().iterator(); iter
                        .hasNext();)
                        arr.add(iter.nextInt());
                    builder.add("labels", arr.build());
                    try {
                        throw e3;
                    } catch (LabelsInUseException e4) {
                        builder.add("error", "labels-in-use");
                    } catch (LabelsUnavailableException e4) {
                        builder.add("error", "labels-unavailable");
                    } catch (LabelManagementException e4) {
                        builder.add("error", "label-mgmt");
                    }
                } catch (BandwidthUnavailableException e3) {
                    builder
                        .add("direction",
                             e3.isUpstream() ? "upstream" : "downstream")
                        .add("amount", e3.getAvailable());
                    builder.add("error", "bw-unavailable");
                } catch (TrunkManagementException e3) {
                    builder.add("error", "trunk-mgmt");
                }
            } catch (TerminalNameException e2) {
                builder.add("terminal-name", e2.getName());
                try {
                    throw e2;
                } catch (UnknownTerminalException e3) {
                    builder.add("error", "terminal-unknown");
                } catch (TerminalExistsException e3) {
                    builder.add("error", "terminal-exists");
                } catch (TerminalNameException e3) {
                    builder.add("error", "terminal-name");
                }
            } catch (TerminalConfigurationException e2) {
                builder.add("config", e2.getConfiguration());
                builder.add("error", "terminal-config");
            } catch (SubterminalManagementException e2) {
                builder.add("subterminal-name", e2.getTerminal().terminal)
                    .add("subnetwork-name", e2.getTerminal().network);
                try {
                    throw e2;
                } catch (SubterminalBusyException e3) {
                    builder.add("error", "subterminal-busy");
                } catch (UnknownSubterminalException e3) {
                    builder.add("error", "subterminal-unknown");
                } catch (UnknownTrunkException e3) {
                    builder.add("error", "trunk-unknown");
                } catch (SubterminalManagementException e3) {
                    builder.add("error", "subterminal-mgmt");
                }
            } catch (TerminalManagementException e2) {
                builder.add("terminal-name", e2.getTerminalName());
                try {
                    throw e2;
                } catch (TerminalBusyException e3) {
                    builder.add("error", "terminal-busy");
                } catch (OwnTerminalException e3) {
                    builder.add("error", "own-terminal");
                } catch (TerminalManagementException e3) {
                    builder.add("error", "terminal-mgmt");
                }
            } catch (UnknownSubnetworkException e2) {
                builder.add("subnetwork-name", e2.getInferiorNetworkName());
                builder.add("error", "subnetwork-unknown");
            } catch (NetworkManagementException e2) {
                builder.add("error", "network-mgmt");
            }
        } catch (NetworkLogicException e) {
            builder.add("network-name", e.getNetworkName());
            try {
                throw e;
            } catch (ServiceLogicException e2) {
                builder.add("service-id", e2.getServiceId());
                try {
                    throw e2;
                } catch (InvalidServiceException e3) {
                    builder.add("error", "segment-invalid");
                } catch (ServiceLogicException e1) {
                    builder.add("error", "service-logic");
                }
            } catch (TerminalLogicException e2) {
                builder.add("terminal-name", e2.getTerminalName());
                try {
                    throw e2;
                } catch (CircuitLogicException e3) {
                    builder.add("label", e3.getLabel()).add("error",
                                                            "circuit-logic");
                } catch (TerminalLogicException e1) {
                    builder.add("error", "terminal-logic");
                }
            } catch (NetworkLogicException e1) {
                builder.add("error", "network-logic");
            }
        } catch (NetworkResourceException e) {
            builder.add("network-name", e.getNetworkName());
            try {
                throw e;
            } catch (ExpiredTrunkException e2) {
                TerminalId start = e2.getStartTerminal();
                TerminalId end = e2.getEndTerminal();
                builder.add("start-terminal-name", start.network)
                    .add("start-network-name", start.terminal)
                    .add("start-terminal-name", end.network)
                    .add("start-network-name", end.terminal);
                builder.add("error", "trunk-expired");
            } catch (NetworkResourceException e2) {
                builder.add("error", "network-resource");
            }
        } catch (IllegalArgumentException ex) {
            builder.add("error", "bad-argument");
        } catch (Throwable ex) {
            builder.add("error", "unknown").add("type",
                                                ex.getClass().getName());
        }
        return one(builder.build());
    }

    /**
     * Create a list of one item.
     * 
     * @param elem the sole item
     * 
     * @return an unmodifiable list of the sole item
     */
    public static <E> List<E> one(E elem) {
        return Collections.singletonList(elem);
    }

    /**
     * Create a list of one empty JSON object.
     * 
     * @return an unmodifiable list of a single empty JSON object
     */
    public static List<JsonObject> empty() {
        return one(Json.createObjectBuilder().build());
    }
}
