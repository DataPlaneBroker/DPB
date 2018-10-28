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
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator.OfInt;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

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
import uk.ac.lancs.networks.mgmt.LabelManagementException;
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.networks.mgmt.NetworkManagementException;
import uk.ac.lancs.networks.mgmt.NetworkResourceException;
import uk.ac.lancs.networks.mgmt.TerminalId;
import uk.ac.lancs.networks.mgmt.TerminalManagementException;
import uk.ac.lancs.networks.mgmt.Trunk;
import uk.ac.lancs.networks.mgmt.TrunkManagementException;
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
            throw new NetworkResourceException(network,
                                               "management calls forbidden");
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

            case "check-service": {
                int id = req.getInt("service-id");
                Service srv = network.getControl().getService(id);
                return one(Json.createObjectBuilder()
                    .add("exists", srv != null).build());
            }

            case "new-service": {
                Service srv = network.getControl().newService();
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
                Map<Edge<Terminal>, Double> model =
                    network.getControl().getModel(minBw);
                JsonArrayBuilder result = Json.createArrayBuilder();
                for (Map.Entry<Edge<Terminal>, Double> entry : model
                    .entrySet()) {
                    Edge<Terminal> edge = entry.getKey();
                    double value = entry.getValue();
                    result.add(Json.createObjectBuilder()
                        .add("from", edge.get(0).name())
                        .add("to", edge.get(1).name()).add("weight", value));
                }
                return one(Json.createObjectBuilder().add("edges", result)
                    .build());
            }

            case "define-service": {
                int id = req.getInt("service-id");
                Service srv = network.getControl().requireService(id);
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
                int id = req.getInt("service-id");
                Service srv = network.getControl().requireService(id);
                srv.activate();
                return empty();
            }

            case "deactivate-service": {
                int id = req.getInt("service-id");
                Service srv = network.getControl().requireService(id);
                srv.deactivate();
                return empty();
            }

            case "release-service": {
                int id = req.getInt("service-id");
                Service srv = network.getControl().requireService(id);
                srv.release();
                return empty();
            }

            case "watch-service": {
                int id = req.getInt("service-id");
                Service srv = network.getControl().requireService(id);
                Queue<JsonObject> results = new ConcurrentLinkedQueue<>();
                ServiceListener listener = new ServiceListener() {
                    @Override
                    public void newStatus(ServiceStatus newStatus) {
                        results.add(Json.createObjectBuilder()
                            .add("status", newStatus.toString()).build());

                        if (newStatus == ServiceStatus.RELEASED)
                            srv.removeListener(this);
                    }
                };
                srv.addListener(listener);
                onClose.execute(() -> {
                    srv.removeListener(listener);
                });
                return results;
            }

            default:
                return null;
            }
        } catch (NetworkManagementException | InvalidServiceException
            | NetworkControlException e) {
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

        try {
            throw t;
        } catch (LabelManagementException e) {
            JsonArrayBuilder arr = Json.createArrayBuilder();
            for (OfInt iter = e.getLabels().stream().iterator(); iter
                .hasNext();)
                arr.add(iter.nextInt());
            Trunk trunk = e.getTrunk();
            TerminalId start = trunk.getStartTerminal();
            TerminalId end = trunk.getEndTerminal();
            builder.add("error", "label-mgmt").add("msg", e.getMessage())
                .add("network-name", e.getNetwork().getControl().name())
                .add("labels", arr.build())
                .add("start-terminal-name", start.network)
                .add("start-network-name", start.terminal)
                .add("start-terminal-name", end.network)
                .add("start-network-name", end.terminal);
        } catch (TrunkManagementException e) {
            Trunk trunk = e.getTrunk();
            TerminalId start = trunk.getStartTerminal();
            TerminalId end = trunk.getEndTerminal();
            builder.add("error", "trunk-mgmt").add("msg", e.getMessage())
                .add("network-name", e.getNetwork().getControl().name())
                .add("start-terminal-name", start.network)
                .add("start-network-name", start.terminal)
                .add("start-terminal-name", end.network)
                .add("start-network-name", end.terminal);
        } catch (TerminalManagementException e) {
            builder.add("error", "terminal-mgmt").add("msg", e.getMessage())
                .add("terminal-name", e.getTerminal().name())
                .add("network-name", e.getNetwork().getControl().name());
        } catch (NetworkManagementException e) {
            builder.add("error", "network-mgmt")
                .add("network-name", e.getNetwork().getControl().name())
                .add("msg", e.getMessage());
        } catch (NetworkControlException e) {
            builder.add("error", "network-ctrl")
                .add("network-name", e.getControl().name())
                .add("msg", e.getMessage());
        } catch (NetworkResourceException e) {
            builder.add("error", "network-rsrc")
                .add("network-name", e.getNetwork().getControl().name())
                .add("msg", e.getMessage());
        } catch (IllegalArgumentException ex) {
            builder.add("error", "bad-argument").add("msg", ex.getMessage());
        } catch (Throwable ex) {
            builder.add("error", "unknown").add("msg", ex.getMessage())
                .add("type", ex.getClass().getName());
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
