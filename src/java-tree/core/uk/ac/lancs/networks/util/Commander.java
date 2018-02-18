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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.config.ConfigurationContext;
import uk.ac.lancs.networks.EndPoint;
import uk.ac.lancs.networks.InvalidServiceException;
import uk.ac.lancs.networks.Service;
import uk.ac.lancs.networks.ServiceDescription;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.TrafficFlow;
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.networks.mgmt.NetworkFactory;
import uk.ac.lancs.networks.mgmt.PluggableAggregator;
import uk.ac.lancs.networks.mgmt.PluggableNetwork;
import uk.ac.lancs.networks.mgmt.UnpluggableNetwork;

/**
 * Instantiates networks according to configuration, and allows
 * command-line modification to them. The networks are then discarded,
 * so this is only of much use with networks that have external
 * persistent state.
 * 
 * @author simpsons
 */
public final class Commander {
    Map<String, UnpluggableNetwork> networks = new HashMap<>();
    ConfigurationContext configCtxt = new ConfigurationContext();
    Configuration config = null;
    UnpluggableNetwork network = null;
    PluggableNetwork baseNetwork = null;
    PluggableAggregator aggregator = null;
    TrafficFlow nextFlow = TrafficFlow.of(0.0, 0.0);
    Map<EndPoint<Terminal>, TrafficFlow> endPoints = new HashMap<>();
    Service service = null;

    void process(Iterator<? extends String> iter)
        throws IOException,
            InvalidServiceException {
        final String arg = iter.next();
        if (config == null) {
            config = configCtxt.get(arg);
            for (Configuration nwc : config.references("networks")) {
                String type = nwc.get("type");
                for (NetworkFactory factory : ServiceLoader
                    .load(NetworkFactory.class)) {
                    if (!factory.recognize(type)) continue;
                    UnpluggableNetwork nw =
                        factory.makeNetwork(IdleExecutor.INSTANCE, nwc);
                    String name = nwc.get("name");
                    networks.put(name, nw);
                    break;
                }
            }
            return;
        }

        if ("-n".equals(arg)) {
            String name = iter.next();
            network = networks.get(name);
            if (network == null) {
                System.err.printf("Unknown network: %s%n", name);
                System.exit(1);
            }
            if (network instanceof PluggableNetwork)
                baseNetwork = (PluggableNetwork) network;
            else
                baseNetwork = null;
            if (network instanceof PluggableAggregator)
                aggregator = (PluggableAggregator) network;
            else
                aggregator = null;
            service = null;
            return;
        }

        if ("--in".equals(arg)) {
            double rate = Double.parseDouble(iter.next());
            nextFlow = nextFlow.withIngress(rate);
            return;
        }

        if ("--out".equals(arg)) {
            double rate = Double.parseDouble(iter.next());
            nextFlow = nextFlow.withEgress(rate);
            return;
        }

        if ("-w".equals(arg)) {
            IdleExecutor.processAll();
            return;
        }

        if ("-s".equals(arg)) {
            String sid = iter.next();
            int id = Integer.parseInt(sid);
            service = network.getControl().getService(id);
            return;
        }

        if ("-e".equals(arg)) {
            String epid = iter.next();
            EndPoint<Terminal> ep = findEndPoint(epid);
            endPoints.put(ep, nextFlow);
            return;
        }

        if ("-i".equals(arg)) {
            service.initiate(ServiceDescription.create(endPoints));
            endPoints.clear();
            nextFlow = TrafficFlow.of(0.0, 0.0);
            return;
        }

        if ("-a".equals(arg)) {
            service.activate();
            return;
        }

        if ("+a".equals(arg)) {
            service.deactivate();
            return;
        }

        if ("-d".equals(arg)) {
            service.release();
            service = null;
            return;
        }

        if ("+t".equals(arg)) {
            String name = iter.next();
            network.removeTerminal(name);
            return;
        }

        if ("-t".equals(arg)) {
            String name = iter.next();
            String desc = iter.next();
            if (aggregator != null) {
                Terminal inner = findTerminal(desc);
                aggregator.addTerminal(name, inner);
            } else if (baseNetwork != null) {
                baseNetwork.addTerminal(name, desc);
            } else {
                System.err.printf("No network to add terminal to: %s (%s)%n",
                                  name, desc);
                System.exit(1);
            }
            return;
        }
    }

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
        if (network == null)
            throw new IllegalArgumentException("network not set"
                + " to find end point: " + name);
        Terminal terminal = network.getTerminal(terminalName);
        return terminal.getEndPoint(label);
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
     * <dt><samp>+t <var>terminal</var></samp>
     * 
     * <dd>Remove the specified terminal from the current network. The
     * name of the terminal is local to the network.
     * 
     * </dl>
     */
    public static void main(String[] args) throws Exception {
        Commander me = new Commander();
        for (Iterator<String> iter = Arrays.asList(args).iterator(); iter
            .hasNext();) {
            me.process(iter);
        }
        IdleExecutor.processAll();
    }
}
