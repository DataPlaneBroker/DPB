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
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.config.ConfigurationContext;
import uk.ac.lancs.networks.InvalidServiceException;
import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.Service;
import uk.ac.lancs.networks.ServiceDescription;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.TrafficFlow;
import uk.ac.lancs.networks.end_points.EndPoint;
import uk.ac.lancs.networks.mgmt.ManagedAggregator;
import uk.ac.lancs.networks.mgmt.ManagedNetwork;
import uk.ac.lancs.networks.mgmt.ManagedSwitch;
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.networks.mgmt.NetworkContext;
import uk.ac.lancs.networks.mgmt.NetworkFactory;
import uk.ac.lancs.networks.mgmt.Trunk;

/**
 * Instantiates networks according to configuration, and allows
 * command-line modification to them. The networks are then discarded,
 * so this is only of much use with networks that have external
 * persistent state.
 * 
 * @author simpsons
 */
public final class Commander {
    Map<String, Network> networks = new HashMap<>();
    ConfigurationContext configCtxt = new ConfigurationContext();
    Configuration config = null;
    Network network = null;
    ManagedNetwork managedNetwork = null;
    ManagedSwitch zwitch = null;
    ManagedAggregator aggregator = null;
    TrafficFlow nextFlow = TrafficFlow.of(0.0, 0.0);
    Map<EndPoint<Terminal>, TrafficFlow> endPoints = new HashMap<>();
    Service service = null;
    String networkName = null;
    String usage = null;

    boolean process(Iterator<? extends String> iter)
        throws IOException,
            InvalidServiceException {
        usage = null;
        final String arg = iter.next();
        if (config == null) {
            config = configCtxt.get(arg);
            for (Configuration nwc : config.references("networks")) {
                String type = nwc.get("type");
                NetworkContext ctxt = new NetworkContext() {
                    @Override
                    public Function<? super String, ? extends NetworkControl>
                        inferiors() {
                        return s -> networks.get(s).getControl();
                    }

                    @Override
                    public Executor executor() {
                        return IdleExecutor.INSTANCE;
                    }
                };
                for (NetworkFactory factory : ServiceLoader
                    .load(NetworkFactory.class)) {
                    if (!factory.recognize(type)) continue;
                    Network nw = factory.makeNetwork(ctxt, nwc);
                    String name = nwc.get("name");
                    networks.put(name, nw);
                    System.out.printf("Creating network %s as %s%n", name,
                                      type);
                    break;
                }
            }
            return true;
        }

        if ("-n".equals(arg)) {
            usage = arg + " <network>";
            String name = iter.next();
            network = networks.get(name);
            if (network == null) {
                System.err.printf("Unknown network: %s%n", name);
                return false;
            }
            networkName = name;
            if (network instanceof ManagedNetwork)
                managedNetwork = (ManagedNetwork) network;
            else
                managedNetwork = null;
            if (managedNetwork instanceof ManagedSwitch)
                zwitch = (ManagedSwitch) managedNetwork;
            else
                zwitch = null;
            if (managedNetwork instanceof ManagedAggregator)
                aggregator = (ManagedAggregator) managedNetwork;
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

        if ("close".equals(arg)) {
            usage = arg + " <terminal-name> <label>-<label>";
            Terminal term = findTerminal(iter.next());
            String rangeText = iter.next();
            Matcher m = labelRangePattern.matcher(rangeText);
            if (!m.matches()) {
                System.err.printf("Illegal range: %s%n", rangeText);
                return false;
            }
            int start = Integer.parseInt(m.group(1));
            int amount = Integer.parseInt(m.group(2)) - start + 1;

            Trunk tr = aggregator.findTrunk(term);
            if (tr.position(term) == 1) {
                tr.revokeEndLabelRange(start, amount);
            } else {
                tr.revokeStartLabelRange(start, amount);
            }
            return true;
        }

        if ("open".equals(arg)) {
            usage = arg + " <terminal-name> <label>-<label>[:<label>]";
            Terminal term = findTerminal(iter.next());
            String rangeText = iter.next();
            Matcher m = labelMapPattern.matcher(rangeText);
            if (!m.matches()) {
                System.err.printf("Illegal map: %s%n", rangeText);
                return false;
            }
            int start = Integer.parseInt(m.group(1));
            int amount = Integer.parseInt(m.group(2)) - start + 1;
            int other =
                m.group(3) == null ? start : Integer.parseInt(m.group(3));

            Trunk tr = aggregator.findTrunk(term);
            if (tr.position(term) == 1) {
                int tmp = other;
                other = start;
                start = tmp;
            }
            tr.defineLabelRange(start, amount, other);
            return true;
        }

        if ("provide".equals(arg) || "withdraw".equals(arg)) {
            boolean add = arg.charAt(0) == 'p';
            usage = arg + " <terminal-name> <rate>[:<rate>]";
            Terminal term = findTerminal(iter.next());
            String rateText = iter.next();
            Matcher m = bandwidthPattern.matcher(rateText);
            if (!m.matches()) {
                System.err.printf("Unrecognized rate: %s%n", rateText);
                return false;
            }
            double uprate = Double.parseDouble(m.group(1));
            double downrate =
                m.group(2) == null ? uprate : Double.parseDouble(m.group(2));
            Trunk tr = aggregator.findTrunk(term);
            if (tr.position(term) == 1) {
                double tmp = uprate;
                uprate = downrate;
                downrate = tmp;
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
            service = managedNetwork.getControl().getService(id);
            return true;
        }

        if ("new".equals(arg)) {
            service = managedNetwork.getControl().newService();
            System.out.printf("Created new service: %d on %s%n", service.id(),
                              networkName);
            return true;
        }
        if ("-e".equals(arg)) {
            usage = arg + " <terminal-name>:<label>";
            String epid = iter.next();
            EndPoint<Terminal> ep = findEndPoint(epid);
            endPoints.put(ep, nextFlow);
            return true;
        }

        if ("initiate".equals(arg)) {
            service.initiate(ServiceDescription.create(endPoints));
            endPoints.clear();
            nextFlow = TrafficFlow.of(0.0, 0.0);
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
            service.release();
            service = null;
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
            String fromName = iter.next();
            String toName = iter.next();
            Terminal from = findTerminal(fromName);
            Terminal to = findTerminal(toName);
            aggregator.addTrunk(from, to);
            return true;
        }

        if ("remove-trunk".equals(arg)) {
            if (aggregator == null) {
                System.err.printf("Network %s is not an aggregator%n",
                                  networkName);
                return false;
            }
            usage = arg + " <network-id>:<terminal-name>";
            String fromName = iter.next();
            Terminal from = findTerminal(fromName);
            aggregator.removeTrunk(from);
            return true;
        }

        if ("remove-terminal".equals(arg)) {
            usage = arg + " <terminal-name>";
            String name = iter.next();
            if (network == null) {
                System.err.println("Network unspecified");
                return false;
            }
            if (managedNetwork == null) {
                System.err.printf("Can't remove terminals from %s%n",
                                  networkName);
                return false;
            }
            managedNetwork.removeTerminal(name);
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
                aggregator.addTerminal(name, inner);
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
            if (managedNetwork == null) {
                System.err.printf("No network to dump%n");
                return false;
            }
            managedNetwork.dumpStatus(new PrintWriter(System.out));
            return true;
        }

        System.err.printf("Unknown command: %s%n", arg);
        return false;
    }

    private static final String realPattern =
        "[0-9]*\\\\.?[0-9]+(?:[eE][-+]?[0-9]+)?";

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
        return network.getTerminal(terminalName);
    }

    private static final Pattern endPointPattern =
        Pattern.compile("^([^:]+):([\\d]+)$");

    private EndPoint<Terminal> findEndPoint(String name) {
        Matcher m = endPointPattern.matcher(name);
        if (!m.matches())
            throw new IllegalArgumentException("not an end point: " + name);
        String terminalName = m.group(1);
        int label = Integer.parseInt(m.group(2));
        if (managedNetwork == null)
            throw new IllegalArgumentException("network not set"
                + " to find end point: " + name);
        Terminal terminal = managedNetwork.getTerminal(terminalName);
        return terminal.getEndPoint(label);
    }

    private static final Pattern labelRangePattern =
        Pattern.compile("^(\\d+)-(\\d+)$");

    private static final Pattern labelMapPattern =
        Pattern.compile("^(\\d+)-(\\d+)(?::(\\d+))$");

    void process(String[] args) throws IOException, InvalidServiceException {
        try {
            for (Iterator<String> iter = Arrays.asList(args).iterator(); iter
                .hasNext();) {
                if (!process(iter)) break;
            }
        } catch (NoSuchElementException ex) {
            System.err.printf("Usage: %s%n", usage);
        }
    }

    /**
     * Load networks from configuration, then modify them according to
     * command-line arguments.
     * 
     * @param args The first argument must be a configuraton file. The
     * following switches are recognized:
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
     * <dt><samp>add-trunk</samp> <var>network</var>:<var>terminal</var>
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
     * 
     * <dd>Set the ingress/egress rate of subsequent end points.
     * 
     * <dt><samp>-e <var>terminal</var>:<var>label</var></samp>
     * 
     * <dd>Add an end point with the current rate settings to the set
     * used to initiate a service.
     * 
     * <dt><samp>initiate</samp>
     * 
     * <dd>Initiate the current service with the current set of end
     * points and rates.
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
     * </dl>
     */
    public static void main(String[] args) throws Exception {
        Commander me = new Commander();
        me.process(args);
        IdleExecutor.processAll();
    }
}
