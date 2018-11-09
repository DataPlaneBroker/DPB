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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.ac.lancs.agent.Agent;
import uk.ac.lancs.agent.AgentContext;
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
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.networks.mgmt.NetworkManagementException;
import uk.ac.lancs.networks.mgmt.Switch;
import uk.ac.lancs.networks.mgmt.TerminalId;
import uk.ac.lancs.networks.mgmt.Trunk;
import uk.ac.lancs.networks.util.IdleExecutor;

/**
 * Instantiates networks according to configuration, and allows
 * command-line modification to them. The networks are then discarded,
 * so this is only of much use with networks that have external
 * persistent state.
 * 
 * @author simpsons
 */
public final class Commander {
    Commander(Path confFile, Executor executor)
        throws IOException,
            AgentCreationException,
            ServiceCreationException {
        final ConfigurationContext configCtxt =
            new ConfigurationContext(System.getProperties());
        final Configuration config = configCtxt.get(confFile.toUri());

        /* Create an agent context to allow agents to discover each
         * other. */
        final Map<String, Agent> agents = new LinkedHashMap<>();
        AgentContext agentContext = agents::get;

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
            if (name == null) {
                System.err.printf("agent config [%s] has no name%n",
                                  agentConf.prefix());
                throw new IllegalArgumentException();
            }
            String type = agentConf.get("type");
            if (type == null) {
                System.err.printf("agent config [%s] has no type%n",
                                  agentConf.prefix());
                throw new IllegalArgumentException();
            }
            for (AgentFactory factory : ServiceLoader
                .load(AgentFactory.class)) {
                if (!factory.recognize(agentConf)) continue;
                Agent agent = factory.makeAgent(agentContext, agentConf);
                agents.put(name, agent);
                System.out.printf("  Created agent %s as %s%n", name, type);
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

    final Map<String, Network> networks = new HashMap<>();
    Network network = null;
    Switch zwitch = null;
    Aggregator aggregator = null;
    TrafficFlow nextFlow = TrafficFlow.of(0.0, 0.0);
    Map<Circuit, TrafficFlow> circuits = new HashMap<>();
    Service service = null;
    String networkName = null;
    String usage = null;

    boolean process(Iterator<? extends String> iter)
        throws IOException,
            InvalidServiceException,
            NetworkManagementException,
            AgentCreationException,
            ServiceCreationException {
        usage = null;
        final String arg = iter.next();

        if ("-n".equals(arg)) {
            usage = arg + " <network>";
            String name = iter.next();
            network = networks.get(name);
            if (network == null) {
                System.err.printf("Unknown network: %s%n", name);
                return false;
            }
            networkName = name;
            if (network instanceof Switch)
                zwitch = (Switch) network;
            else
                zwitch = null;
            if (network instanceof Aggregator)
                aggregator = (Aggregator) network;
            else
                aggregator = null;
            service = null;
            return true;
        }

        if ("--in".equals(arg)) {
            usage = arg + " <rate>";
            double rate = Double.parseDouble(iter.next());
            nextFlow = nextFlow.withIngress(rate);
            return true;
        }

        if ("--out".equals(arg)) {
            usage = arg + " <rate>";
            double rate = Double.parseDouble(iter.next());
            nextFlow = nextFlow.withEgress(rate);
            return true;
        }

        if ("-b".equals(arg)) {
            usage = arg + " <rate>";
            double rate = Double.parseDouble(iter.next());
            nextFlow = nextFlow.withEgress(rate).withIngress(rate);
            return true;
        }

        if ("close".equals(arg)) {
            usage = arg + " <terminal-name> <label>-<label>";
            String termText = iter.next();
            String rangeText = iter.next();

            Matcher tm = terminalPattern.matcher(termText);
            if (!tm.matches()) {
                System.err.printf("Illegal terminal: %s%n", termText);
                return false;
            }
            String subnet = tm.group(1);
            String subterm = tm.group(2);

            Matcher m = labelRangePattern.matcher(rangeText);
            if (!m.matches()) {
                System.err.printf("Illegal range: %s%n", rangeText);
                return false;
            }
            int start = Integer.parseInt(m.group(1));
            int amount = Integer.parseInt(m.group(2)) - start + 1;

            Trunk tr = aggregator.findTrunk(TerminalId.of(subnet, subterm));
            tr.revokeStartLabelRange(start, amount);
            return true;
        }

        if ("open".equals(arg)) {
            usage = arg + " <terminal-name> <label>-<label>[:<label>]";
            String termText = iter.next();
            String rangeText = iter.next();

            Matcher tm = terminalPattern.matcher(termText);
            if (!tm.matches()) {
                System.err.printf("Illegal terminal: %s%n", termText);
                return false;
            }
            String subnet = tm.group(1);
            String subterm = tm.group(2);

            Matcher m = labelMapPattern.matcher(rangeText);
            if (!m.matches()) {
                System.err.printf("Illegal map: %s%n", rangeText);
                return false;
            }
            int start = Integer.parseInt(m.group(1));
            int amount = Integer.parseInt(m.group(2)) - start + 1;
            int other =
                m.group(3) == null ? start : Integer.parseInt(m.group(3));

            Trunk tr = aggregator.findTrunk(TerminalId.of(subnet, subterm));
            tr.defineLabelRange(start, amount, other);
            return true;
        }

        if ("recommission".equals(arg) || "decommission".equals(arg)) {
            boolean add = arg.charAt(0) == 'r';
            usage = arg + " <terminal-name>";
            String termText = iter.next();

            Matcher tm = terminalPattern.matcher(termText);
            if (!tm.matches()) {
                System.err.printf("Illegal terminal: %s%n", termText);
                return false;
            }
            String subnet = tm.group(1);
            String subterm = tm.group(2);

            Trunk tr = aggregator.findTrunk(TerminalId.of(subnet, subterm));
            if (tr == null) {
                System.err.printf("No trunk for %s:%s%n", subnet, subterm);
                return false;
            }
            if (add)
                tr.recommission();
            else
                tr.decommission();
        }

        if ("provide".equals(arg) || "withdraw".equals(arg)) {
            boolean add = arg.charAt(0) == 'p';
            usage = arg + " <terminal-name> <rate>[:<rate>]";
            String termText = iter.next();
            String rateText = iter.next();

            Matcher tm = terminalPattern.matcher(termText);
            if (!tm.matches()) {
                System.err.printf("Illegal terminal: %s%n", termText);
                return false;
            }
            String subnet = tm.group(1);
            String subterm = tm.group(2);

            Matcher m = bandwidthPattern.matcher(rateText);
            if (!m.matches()) {
                System.err.printf("Unrecognized rate: %s%n", rateText);
                return false;
            }
            double uprate = Double.parseDouble(m.group(1));
            double downrate =
                m.group(2) == null ? uprate : Double.parseDouble(m.group(2));
            Trunk tr = aggregator.findTrunk(TerminalId.of(subnet, subterm));
            if (tr == null) {
                System.err.printf("No trunk for %s:%s%n", subnet, subterm);
                return false;
            }
            if (add)
                tr.provideBandwidth(uprate, downrate);
            else
                tr.withdrawBandwidth(uprate, downrate);
            return true;
        }

        if ("wait".equals(arg)) {
            IdleExecutor.processAll();
            return true;
        }

        if ("-s".equals(arg)) {
            usage = arg + " <service-id>";
            String sid = iter.next();
            int id = Integer.parseInt(sid);
            service = network.getControl().getService(id);
            return true;
        }

        if ("new".equals(arg)) {
            service = network.getControl().newService();
            System.out.printf("Created new service: %d on %s%n", service.id(),
                              networkName);
            return true;
        }
        if ("-e".equals(arg)) {
            usage = arg + " <terminal-name>:<label>";
            String epid = iter.next();
            Circuit ep = findCircuit(epid);
            if (ep == null) {
                System.err.printf("No circuit [%s]%n", epid);
                return false;
            }
            circuits.put(ep, nextFlow);
            return true;
        }

        if ("initiate".equals(arg)) {
            service.define(Segment.create(circuits));
            circuits.clear();
            nextFlow = TrafficFlow.of(0.0, 0.0);
            return true;
        }
        if ("watch".equals(arg)) {
            final Service myService = service;
            ServiceListener me = new ServiceListener() {
                @Override
                public void newStatus(ServiceStatus e) {
                    System.err.printf("srv %d: %s%n", myService.id(), e);
                    if (e == ServiceStatus.RELEASED)
                        myService.removeListener(this);
                }
            };
            service.addListener(me);
            System.err.printf("Listener attached...%n");
            return true;
        }

        if ("activate".equals(arg)) {
            service.activate();
            return true;
        }

        if ("deactivate".equals(arg)) {
            service.deactivate();
            return true;
        }

        if ("release".equals(arg)) {
            if (service != null) service.release();
            service = null;
            return true;
        }

        if ("set-delay".equals(arg)) {
            if (aggregator == null) {
                System.err.printf("Network %s is not an aggregator%n",
                                  networkName);
                return false;
            }
            usage = arg + " <network-id>:<terminal-name> <delay>";
            String termText = iter.next();
            double delay = Double.parseDouble(iter.next());

            Matcher tm = terminalPattern.matcher(termText);
            if (!tm.matches()) {
                System.err.printf("Illegal terminal: %s%n", termText);
                return false;
            }
            String subnet = tm.group(1);
            String subterm = tm.group(2);

            Trunk trunk =
                aggregator.findTrunk(TerminalId.of(subnet, subterm));
            trunk.setDelay(delay);
            return true;
        }

        if ("add-trunk".equals(arg)) {
            if (aggregator == null) {
                System.err.printf("Network %s is not an aggregator%n",
                                  networkName);
                return false;
            }
            usage = arg + " <network-id>:<terminal-name>"
                + " <network-id>:<terminal-name>";
            String fromText = iter.next();
            String toText = iter.next();

            Matcher mFrom = terminalPattern.matcher(fromText);
            if (!mFrom.matches()) {
                System.err.printf("Illegal terminal: %s%n", fromText);
                return false;
            }
            String subnetFrom = mFrom.group(1);
            String subtermFrom = mFrom.group(2);

            Matcher mTo = terminalPattern.matcher(toText);
            if (!mTo.matches()) {
                System.err.printf("Illegal terminal: %s%n", toText);
                return false;
            }
            String subnetTo = mTo.group(1);
            String subtermTo = mTo.group(2);

            aggregator.addTrunk(TerminalId.of(subnetFrom, subtermFrom),
                                TerminalId.of(subnetTo, subtermTo));
            return true;
        }

        if ("remove-trunk".equals(arg)) {
            if (aggregator == null) {
                System.err.printf("Network %s is not an aggregator%n",
                                  networkName);
                return false;
            }
            usage = arg + " <network-id>:<terminal-name>";
            String fromText = iter.next();

            Matcher mFrom = terminalPattern.matcher(fromText);
            if (!mFrom.matches()) {
                System.err.printf("Illegal terminal: %s%n", fromText);
                return false;
            }
            String subnetFrom = mFrom.group(1);
            String subtermFrom = mFrom.group(2);

            aggregator.removeTrunk(TerminalId.of(subnetFrom, subtermFrom));
            return true;
        }

        if ("remove-terminal".equals(arg)) {
            usage = arg + " <terminal-name>";
            String name = iter.next();
            if (network == null) {
                System.err.println("Network unspecified");
                return false;
            }
            network.removeTerminal(name);
            return true;
        }

        if ("add-terminal".equals(arg)) {
            usage = arg + " <terminal-name>";
            if (aggregator == null)
                usage += " <desc>";
            else
                usage += " <inner-terminal-name>";
            String name = iter.next();
            String desc = iter.next();
            if (aggregator != null) {
                Terminal inner = findTerminal(desc);
                aggregator.addTerminal(name, TerminalId
                    .of(inner.getNetwork().name(), inner.name()));
            } else if (zwitch != null) {
                zwitch.addTerminal(name, desc);
            } else {
                System.err.printf("No network to add terminal to: %s (%s)%n",
                                  name, desc);
                return false;
            }
            return true;
        }

        if ("dump".equals(arg)) {
            if (network == null) {
                System.err.printf("No network to dump%n");
                return false;
            }
            network.dumpStatus(new PrintWriter(System.out));
            return true;
        }

        System.err.printf("Unknown command: %s%n", arg);
        return false;
    }

    private static final String realPattern =
        "[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?";

    private static final Pattern bandwidthPattern =
        Pattern.compile("^(" + realPattern + ")(?::(" + realPattern + "))?$");

    private static final Pattern terminalPattern =
        Pattern.compile("^([^:]+):([^:]+)$");

    private Terminal findTerminal(String name) {
        Matcher m = terminalPattern.matcher(name);
        if (!m.matches())
            throw new IllegalArgumentException("not a terminal: " + name);
        String networkName = m.group(1);
        String terminalName = m.group(2);
        Network network = this.networks.get(networkName);
        if (network == null)
            throw new IllegalArgumentException("unknown network in terminal: "
                + name);
        return network.getControl().getTerminal(terminalName);
    }

    private static final Pattern CIRCUIT_PATTERN =
        Pattern.compile("^([^:]+):([\\d]+)$");

    private Circuit findCircuit(String name) {
        Matcher m = CIRCUIT_PATTERN.matcher(name);
        if (!m.matches())
            throw new IllegalArgumentException("not a circuit: " + name);
        String terminalName = m.group(1);
        int label = Integer.parseInt(m.group(2));
        if (network == null)
            throw new IllegalArgumentException("network not set"
                + " to find circuit: " + name);
        Terminal terminal = network.getControl().getTerminal(terminalName);
        if (terminal == null) return null;
        return terminal.circuit(label);
    }

    private static final Pattern labelRangePattern =
        Pattern.compile("^(\\d+)-(\\d+)$");

    private static final Pattern labelMapPattern =
        Pattern.compile("^(\\d+)-(\\d+)(?::(\\d+))?$");

    void process(String[] args)
        throws IOException,
            InvalidServiceException,
            NetworkManagementException,
            AgentCreationException,
            ServiceCreationException {
        try {
            for (Iterator<String> iter = Arrays.asList(args).iterator(); iter
                .hasNext();) {
                if (!process(iter)) break;
            }
        } catch (NoSuchElementException ex) {
            System.err.printf("Usage: %s%n", usage);
        } finally {
            service = null;
            network = null;
            zwitch = null;
            aggregator = null;
        }
    }

    /**
     * Load networks from configuration, then modify them according to
     * command-line arguments. Agents are provided with an
     * {@link AgentContext} that includes an agent called
     * <samp>system</samp>, which in turn defines a default
     * {@link Executor} service, and a keyed {@link NetworkControl}
     * service indexed by network name.
     * 
     * <dl>
     * 
     * <dt><samp>network.config.client</samp>
     * 
     * <dd>Specifies the client-side network configuration.
     * 
     * </dl>
     * 
     * @param args The following switches are recognized:
     * 
     * <dl>
     * 
     * <dt><samp>-n <var>network</var></samp>
     * 
     * <dd>Apply subsequent commands to the specified network.
     * 
     * <dt><samp>-s <var>service</var></samp>
     * 
     * <dd>Select the numbered service of the current network.
     * 
     * <dt><samp>add-terminal <var>name</var> <var>mapping</var></samp>
     * 
     * <dd>Add a terminal with the specified name and mapping. For an
     * aggregator, <var>mapping</var> is an inferior network's terminal.
     * Otherwise, <var>mapping</var> is a back-end interface
     * description.
     * 
     * <dt><samp>remove-terminal <var>name</var></samp>
     * 
     * <dd>Remove the named terminal.
     * 
     * <dt><samp>add-trunk <var>network</var>:<var>terminal</var>
     * <var>network</var>:<var>terminal</var></samp>
     * 
     * <dd>Create a trunk in the current aggregator connecting two
     * terminals of inferior networks.
     * 
     * <dt><samp>remove-trunk
     * <var>network</var>:<var>terminal</var></samp>
     * 
     * <dd>Remove a trunk in the current aggregator connecting the
     * specified terminal.
     * 
     * <dt><samp>provide <var>terminal</var>
     * <var>rate[</var>:<var>]rate</var></samp>
     * <dt><samp>withdraw <var>terminal</var>
     * <var>rate[</var>:<var>]rate</var></samp>
     * 
     * <dd>Provide/withdraw bandwidth to/from the trunk connecting the
     * specified terminal. The first rate is from the terminal to its
     * peer, and the second vice versa. The second rate defaults to the
     * first.
     * 
     * <dt><samp>open <var>terminal</var>
     * <var>low</var>-<var>high[</var>:<var>peer-low]</var></samp>
     * 
     * <dd>Make labels <var>low</var> to <var>high</var> available on
     * the trunk at the specified terminal. Map <var>low</var> to
     * <var>peer-low</var> on the peer terminal.
     * 
     * <dt><samp>new</samp>
     * 
     * <dd>Create a new service for the current network.
     * 
     * <dt><samp>--in <var>rate</var></samp>
     * <dt><samp>--out <var>rate</var></samp>
     * <dt><samp>-b <var>rate</var></samp>
     * 
     * <dd>Set the ingress/egress rate or both rates of subsequent
     * circuits.
     * 
     * <dt><samp>-e <var>terminal</var>:<var>label</var></samp>
     * 
     * <dd>Add a circuit with the current rate settings to the set used
     * to initiate a service.
     * 
     * <dt><samp>initiate</samp>
     * 
     * <dd>Initiate the current service with the current set of circuits
     * and rates.
     * 
     * <dt><samp>activate</samp>
     * <dt><samp>deactivate</samp>
     * <dt><samp>release</samp>
     * 
     * <dd>Activate, deactivate or release the current service.
     * 
     * <dt><samp>dump</samp>
     * 
     * <dd>Dump the current network's status.
     * 
     * <dt><samp>wait</samp>
     * 
     * <dd>Wait for inactivity before next command or exit.
     * 
     * <dt><samp>watch</samp>
     * 
     * <dd>Listen and report events for the current service.
     * 
     * </dl>
     * 
     * @throws Exception if something went wrong
     */
    public static void main(String[] args) throws Exception {
        Path dataplaneConf =
            Paths.get(System.getProperty("network.config.client"));
        ExecutorService executor = Executors.newCachedThreadPool();
        Commander me = new Commander(dataplaneConf, executor);
        me.process(args);
        executor.shutdown();
    }
}
