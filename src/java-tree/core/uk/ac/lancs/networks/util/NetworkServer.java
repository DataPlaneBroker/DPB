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
package uk.ac.lancs.networks.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;

import uk.ac.lancs.agent.Agent;
import uk.ac.lancs.agent.AgentCreationException;
import uk.ac.lancs.agent.AgentFactory;
import uk.ac.lancs.agent.ServiceCreationException;
import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.config.ConfigurationContext;
import uk.ac.lancs.networks.Circuit;
import uk.ac.lancs.networks.InvalidServiceException;
import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.Segment;
import uk.ac.lancs.networks.Service;
import uk.ac.lancs.networks.ServiceListener;
import uk.ac.lancs.networks.ServiceStatus;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.TrafficFlow;
import uk.ac.lancs.networks.mgmt.Aggregator;
import uk.ac.lancs.networks.mgmt.LabelManagementException;
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.networks.mgmt.NetworkManagementException;
import uk.ac.lancs.networks.mgmt.NetworkResourceException;
import uk.ac.lancs.networks.mgmt.Switch;
import uk.ac.lancs.networks.mgmt.TerminalManagementException;
import uk.ac.lancs.networks.mgmt.Trunk;
import uk.ac.lancs.networks.mgmt.TrunkManagementException;
import uk.ac.lancs.scc.usmux.Session;
import uk.ac.lancs.scc.usmux.SessionException;
import uk.ac.lancs.scc.usmux.SessionServer;
import uk.ac.lancs.scc.usmux.SessionServerFactory;

/**
 * Instantiates network agents from configuration, and serves
 * JSON-formatted requests to them via Usmux.
 * 
 * @author simpsons
 */
public final class NetworkServer {
    private final Map<String, Network> networks = new HashMap<>();
    private final Executor executor;

    NetworkServer(Executor executor, SessionServer usmuxServer,
                  Configuration config)
        throws AgentCreationException,
            ServiceCreationException {
        this.executor = executor;

        /* Create a collection of named agents. */
        Map<String, Agent> agents = new LinkedHashMap<>();

        /* Provide an executor to other agents, and a way to find
         * networks by name. */
        agents.put("system", new Agent() {
            @Override
            public <T> T findService(Class<T> type, String key) {
                if (type == NetworkControl.class) {
                    Network nw = networks.get(key);
                    if (nw == null) return null;
                    return type.cast(nw.getControl());
                }
                if (type == Executor.class && key == null)
                    return type.cast(executor);
                return null;
            }

            @Override
            public Collection<String> getKeys(Class<?> type) {
                if (type == NetworkControl.class) return Collections
                    .unmodifiableCollection(networks.keySet());
                if (type == Executor.class)
                    return Collections.singleton(null);
                return Collections.emptySet();
            }
        });

        /* Instantiate all agents. */
        System.out.printf("Creating agents...%n");
        for (Configuration agentConf : config.references("agents")) {
            String name = agentConf.get("name");
            if (name == null)
                throw new IllegalArgumentException("agent config ["
                    + agentConf.prefix() + " has no name");
            for (AgentFactory factory : ServiceLoader
                .load(AgentFactory.class)) {
                if (!factory.recognize(agentConf)) continue;
                Agent agent = factory.makeAgent(agents::get, agentConf);
                agents.put(name, agent);
                System.out.printf("  Created agent %s%n", name);
            }
        }

        /* Obtain networks from agents. */
        System.out.printf("Obtaining networks...%n");
        for (Map.Entry<String, Agent> entry : agents.entrySet()) {
            String agentId = entry.getKey();
            Agent agent = entry.getValue();
            for (String serviceKey : agent.getKeys(Network.class)) {
                Network network =
                    agent.findService(Network.class, serviceKey);
                if (network == null) continue;
                String networkName = network.getControl().name();
                networks.put(networkName, network);
                System.out.printf("  Created network %s from agent %s%s%n",
                                  networkName, agentId,
                                  serviceKey != null ? ":" + serviceKey : "");
            }
        }
    }

    private class Interaction implements Runnable {
        private final Session sess;
        private Collection<String> managables = new HashSet<>();
        private Collection<String> controllables = new HashSet<>();
        private boolean privileged;

        NetworkControl control = null;
        Network network = null;
        Switch zwitch = null;
        Aggregator aggregator = null;
        TrafficFlow nextFlow = TrafficFlow.of(0.0, 0.0);
        Map<Circuit, TrafficFlow> circuits = new HashMap<>();
        Service service = null;

        public Interaction(Session sess) {
            this.sess = sess;
        }

        @Override
        public void run() {
            try (InputStream in = sess.getInputStream();
                OutputStream out = sess.getOutputStream()) {
                JsonObject req = readJson(in);
                for (JsonObject rsp : process(req))
                    writeJson(out, rsp, req.getString("txn"));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        private Iterable<JsonObject> process(JsonObject req) {
            try {
                String cmd = req.getString("type");
                switch (cmd) {
                case "authz-control": {
                    if (privileged) {
                        /* Allow a named network to be controlled. */
                        String key = req.getString("network");
                        controllables.add(key);
                        return empty();
                    } else {
                        return one(Json.createObjectBuilder()
                            .add("error", "unprivileged").build());
                    }
                }

                case "authz-mgmt": {
                    if (privileged) {
                        /* Allow a names network to be controlled and
                         * managed. */
                        String key = req.getString("network");
                        controllables.add(key);
                        managables.add(key);
                        return empty();
                    } else {
                        return one(Json.createObjectBuilder()
                            .add("error", "unprivileged").build());
                    }
                }

                case "drop-privs": {
                    if (privileged) {
                        /* Prevent any more changes to privileged
                         * resources. */
                        managables =
                            Collections.unmodifiableCollection(managables);
                        controllables =
                            Collections.unmodifiableCollection(controllables);
                        privileged = false;
                        return empty();
                    } else {
                        return one(Json.createObjectBuilder()
                            .add("error", "unprivileged").build());
                    }
                }

                case "network": {
                    String name = req.getString("network-name");
                    if (!controllables.contains(name)) return one(Json
                        .createObjectBuilder().add("error", "unauthorized")
                        .add("network", name).build());
                    network = networks.get(name);
                    if (network == null) { return one(Json
                        .createObjectBuilder().add("error", "no-network")
                        .add("network-name", name).build()); }
                    control = network.getControl();
                    if (managables.contains(name)) {
                        if (network instanceof Aggregator)
                            aggregator = (Aggregator) network;
                        if (network instanceof Switch)
                            zwitch = (Switch) network;
                    } else {
                        network = null;
                    }
                    JsonObjectBuilder builder =
                        Json.createObjectBuilder().add("network-name", name);
                    if (aggregator != null) builder.add("aggregator", true);
                    if (zwitch != null) builder.add("switch", true);
                    if (network != null) builder.add("network", true);
                    return one(builder.build());
                }

                case "decommission":
                case "recommission":
                case "commission": {
                    if (aggregator == null)
                        return one(Json.createObjectBuilder()
                            .add("error", "no-aggregator").build());
                    String name = req.getString("terminal-name");
                    Terminal term = network.getTerminal(name);
                    Trunk trunk = aggregator.getTrunk(term);
                    boolean add = cmd.charAt(0) != 'd';
                    if (add)
                        trunk.recommission();
                    else
                        trunk.decommission();
                    return empty();
                }

                case "set-delay": {
                    if (aggregator == null)
                        return one(Json.createObjectBuilder()
                            .add("error", "no-aggregator").build());

                    String name = req.getString("terminal-name");
                    Terminal term = network.getTerminal(name);
                    Trunk trunk = aggregator.getTrunk(term);

                    double delay = req.getJsonNumber("delay").doubleValue();
                    trunk.setDelay(delay);
                    return empty();
                }

                case "add-trunk": {
                    if (aggregator == null)
                        return one(Json.createObjectBuilder()
                            .add("error", "no-aggregator").build());

                    Terminal fromTerm;
                    {
                        JsonObject obj = req.getJsonObject("from");
                        String netName = req.getString("network-name");
                        Network subnet = networks.get(netName);
                        if (subnet == null) return one(Json
                            .createObjectBuilder().add("error", "no-network")
                            .add("network-name", netName).build());
                        String name = obj.getString("terminal-name");
                        fromTerm = subnet.getControl().getTerminal(name);
                        if (fromTerm == null) return one(Json
                            .createObjectBuilder().add("error", "no-terminal")
                            .add("network-name", netName)
                            .add("terminal-name", name).build());
                    }

                    Terminal toTerm;
                    {
                        JsonObject obj = req.getJsonObject("from");
                        String netName = req.getString("network-name");
                        Network subnet = networks.get(netName);
                        if (subnet == null) return one(Json
                            .createObjectBuilder().add("error", "no-network")
                            .add("network-name", netName).build());
                        String name = obj.getString("terminal-name");
                        toTerm = subnet.getControl().getTerminal(name);
                        if (toTerm == null) return one(Json
                            .createObjectBuilder().add("error", "no-terminal")
                            .add("network-name", netName)
                            .add("terminal-name", name).build());
                    }

                    aggregator.addTrunk(fromTerm, toTerm);
                    return empty();
                }

                case "remove-trunk": {
                    if (aggregator == null)
                        return one(Json.createObjectBuilder()
                            .add("error", "no-aggregator").build());

                    String name = req.getString("terminal-name");
                    Terminal term = network.getTerminal(name);

                    aggregator.removeTrunk(term);
                    return empty();
                }

                case "watch": {
                    if (service == null) return one(Json.createObjectBuilder()
                        .add("error", "no-service").build());
                    Queue<JsonObject> results = new ConcurrentLinkedQueue<>();
                    service.addListener(new ServiceListener() {
                        @Override
                        public void newStatus(ServiceStatus newStatus) {
                            results.add(Json.createObjectBuilder()
                                .add("status", newStatus.toString()).build());
                        }
                    });
                    return results;
                }
                case "remove-terminal": {
                    if (network == null) return one(Json.createObjectBuilder()
                        .add("error", "no-network").build());

                    String name = req.getString("terminal-name");

                    network.removeTerminal(name);
                    return empty();
                }

                case "open-labels":
                case "close-labels": {
                    if (aggregator == null)
                        return one(Json.createObjectBuilder()
                            .add("error", "no-aggregator").build());

                    String name = req.getString("terminal-name");
                    Terminal term = network.getTerminal(name);
                    Trunk trunk = aggregator.findTrunk(term);

                    int start = req.getJsonNumber("low").intValue();
                    JsonNumber highNum = req.getJsonNumber("high");
                    int amount = highNum == null ? 1
                        : (highNum.intValue() + 1 - start);
                    JsonNumber mapNum = req.getJsonNumber("map");
                    int map = mapNum == null ? start : mapNum.intValue();
                    boolean add = cmd.charAt(0) == 'o';
                    if (add) {
                        trunk.defineLabelRange(start, amount, map);
                    } else {
                        trunk.revokeStartLabelRange(start, amount);
                    }
                    return empty();
                }

                case "provide":
                case "withdraw": {
                    if (aggregator == null)
                        return one(Json.createObjectBuilder()
                            .add("error", "no-aggregator").build());

                    String name = req.getString("terminal-name");
                    Terminal term = network.getTerminal(name);
                    Trunk trunk = aggregator.findTrunk(term);

                    boolean add = cmd.charAt(0) == 'p';
                    JsonNumber rate = req.getJsonNumber("rate");
                    JsonNumber uprate = req.getJsonNumber("up");
                    JsonNumber downrate = req.getJsonNumber("down");
                    double up = uprate == null
                        ? rate == null ? 0.0 : rate.doubleValue()
                        : uprate.doubleValue();
                    double down = downrate == null
                        ? rate == null ? 0.0 : rate.doubleValue()
                        : downrate.doubleValue();
                    if (add) {
                        trunk.provideBandwidth(up, down);
                    } else {
                        trunk.withdrawBandwidth(up, down);
                    }
                    return empty();
                }

                case "new-service": {
                    if (control == null) return one(Json.createObjectBuilder()
                        .add("error", "no-control").build());
                    service = control.newService();
                    return one(Json.createObjectBuilder()
                        .add("service-name", "" + service.id()).build());
                }

                case "service": {
                    if (control == null) return one(Json.createObjectBuilder()
                        .add("error", "no-control").build());
                    String name = req.getString("service-name");
                    int number = Integer.parseInt(name);
                    service = control.getService(number);
                    if (service == null) return one(Json.createObjectBuilder()
                        .add("error", "no-service").add("service-name", name)
                        .build());
                    return empty();
                }

                case "release": {
                    if (service == null) return one(Json.createObjectBuilder()
                        .add("error", "no-service").build());
                    service.release();
                    return empty();
                }

                case "activate": {
                    if (service == null) return one(Json.createObjectBuilder()
                        .add("error", "no-service").build());
                    service.activate();
                    return empty();
                }

                case "deactivate": {
                    if (service == null) return one(Json.createObjectBuilder()
                        .add("error", "no-service").build());
                    service.deactivate();
                    return empty();
                }

                case "clear-circuits": {
                    circuits.clear();
                    return empty();
                }

                case "set-flow": {
                    JsonNumber inFlow = req.getJsonNumber("in");
                    JsonNumber outFlow = req.getJsonNumber("out");
                    if (inFlow != null)
                        nextFlow = nextFlow.withIngress(inFlow.doubleValue());
                    if (outFlow != null)
                        nextFlow = nextFlow.withEgress(outFlow.doubleValue());
                    return empty();
                }

                case "add-circuit": {
                    if (control == null) return one(Json.createObjectBuilder()
                        .add("error", "no-control").build());

                    String name = req.getString("terminal-name");
                    Terminal terminal = control.getTerminal(name);
                    if (terminal == null) return one(Json
                        .createObjectBuilder().add("error", "no-terminal")
                        .add("network-name", network.getControl().name())
                        .add("terminal-name", name).build());

                    int id = req.getJsonNumber("label").intValue();
                    Circuit c = terminal.circuit(id);
                    if (c == null) return one(Json.createObjectBuilder()
                        .add("error", "no-circuit").add("terminal-name", name)
                        .add("label", id).build());

                    circuits.put(c, nextFlow);
                    return empty();
                }

                case "initiate": {
                    if (service == null) return one(Json.createObjectBuilder()
                        .add("error", "no-service").build());
                    try {
                        service.define(Segment.create(circuits));
                        circuits.clear();
                        nextFlow = TrafficFlow.of(0.0, 0.0);
                        return empty();
                    } catch (InvalidServiceException e) {
                        return one(Json.createObjectBuilder()
                            .add("error", "invalid-segment")
                            .add("msg", e.getMessage()).build());
                    }
                }

                default:
                    return one(Json.createObjectBuilder()
                        .add("error", "unknown-command")
                        .add("type", req.getString("type")).build());
                }
            } catch (LabelManagementException ex) {
                return one(Json.createObjectBuilder()
                    .add("error", "terminal-mgmt")
                    .add("lower-label", ex.getLowerLabel())
                    .add("upper-label", ex.getUpperLabel())
                    .add("terminal", ex.getTerminal().name())
                    .add("msg", ex.getMessage()).build());
            } catch (TerminalManagementException ex) {
                return one(Json.createObjectBuilder()
                    .add("error", "terminal-mgmt")
                    .add("terminal", ex.getTerminal().name())
                    .add("msg", ex.getMessage()).build());
            } catch (TrunkManagementException ex) {
                return one(Json.createObjectBuilder()
                    .add("error", "trunk-mgmt").add("error", "network-mgmt")
                    .add("terminal-from", "TODO").add("msg", ex.getMessage())
                    .build());
            } catch (NetworkManagementException ex) {
                return one(Json.createObjectBuilder()
                    .add("error", "network-mgmt")
                    .add("network-name", network.getControl().name())
                    .add("msg", ex.getMessage()).build());
            } catch (NetworkResourceException ex) {
                return one(Json.createObjectBuilder()
                    .add("error", "network-resource")
                    .add("network-name", network.getControl().name())
                    .add("msg", ex.getMessage()).build());
            } catch (IllegalArgumentException ex) {
                return one(Json.createObjectBuilder()
                    .add("error", "bad-argument").add("msg", ex.getMessage())
                    .build());
            }
        }
    }

    private static final JsonReaderFactory readerFactory =
        Json.createReaderFactory(Collections.emptyMap());
    private static final JsonWriterFactory writerFactory =
        Json.createWriterFactory(Collections.emptyMap());

    private static JsonObject readJson(InputStream in) {
        JsonReader reader =
            readerFactory.createReader(in, StandardCharsets.UTF_8);
        JsonObject cmd = reader.readObject();
        return cmd;
    }

    private static void writeJson(OutputStream out, JsonObject res,
                                  String txn) {
        if (txn != null) {
            JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("txn", txn);
            res.entrySet()
                .forEach(e -> builder.add(e.getKey(), e.getValue()));
            res = builder.build();
        }
        JsonWriter writer = writerFactory.createWriter(out);
        writer.write(res);
    }

    void interact(Session sess) {
        Interaction ixn = new Interaction(sess);
        executor.execute(ixn);
    }

    /**
     * Runs a server containing agents that operate switches.
     * 
     * @param args The following arguments are recognized:
     * 
     * <dl>
     * 
     * <dt><kbd>-s <var>config</var></kbd>
     * 
     * <dd>Specifies the Usmux configuration, normally provided by the
     * Usmux daemon invoking this process.
     * 
     * <dt><kbd>-f <var>config-file</var></kbd>
     * 
     * <dd>Specifies the path to the agent configuration file.
     * 
     * </dl>
     */
    public static void main(String[] args) {
        String usmuxConf = null;
        Path dataplaneConf = null;
        for (int argi = 0; argi < args.length; argi++) {
            String arg = args[argi];
            switch (arg) {
            case "-s":
                if (argi >= args.length - 1) {
                    System.err.printf("Usage: -s <usmux-config>%n");
                    System.exit(1);
                    return;
                }
                usmuxConf = args[++argi];
                break;

            case "-f":
                if (argi >= args.length - 1) {
                    System.err.printf("Usage: -f <dataplane-config-file>%n");
                    System.exit(1);
                    return;
                }
                dataplaneConf = Paths.get(args[++argi]);
                break;

            default:
                System.err.printf("Unknown argument: %s%n", arg);
                System.exit(1);
                return;
            }
        }

        try {
            /* Create the Usmux session server. We don't start it until
             * we have all our configured components in place. */
            SessionServer usmuxServer =
                SessionServerFactory.makeServer(usmuxConf);

            Executor executor = Executors.newCachedThreadPool();

            ConfigurationContext configCtxt = new ConfigurationContext();
            Configuration config;
            config = configCtxt.get(dataplaneConf.toFile());
            NetworkServer us =
                new NetworkServer(executor, usmuxServer, config);

            /* Start to accept calls. */
            usmuxServer.start();
            Session sess;
            while ((sess = usmuxServer.accept()) != null) {
                us.interact(sess);
            }
        } catch (SessionException | IOException | AgentCreationException
            | ServiceCreationException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static <E> List<E> one(E elem) {
        return Collections.singletonList(elem);
    }

    private static List<JsonObject> empty() {
        return one(Json.createObjectBuilder().build());
    }
}
