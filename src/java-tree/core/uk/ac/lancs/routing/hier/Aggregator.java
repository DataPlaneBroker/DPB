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
package uk.ac.lancs.routing.hier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import uk.ac.lancs.routing.span.Edge;
import uk.ac.lancs.routing.span.HashableEdge;
import uk.ac.lancs.routing.span.Spans;
import uk.ac.lancs.routing.span.Way;

/**
 * Implements a virtual switch hierarchically configuring inferior
 * switches.
 * 
 * @author simpsons
 */
public class Aggregator implements SwitchManagement {
    private final String name;
    private final Collection<Trunk> trunks = new HashSet<>();

    private final Map<String, ExposedPort> ports = new HashMap<>();

    public Aggregator(String name) {
        this.name = name;
    }

    /**
     * Make a trunk available for creating connections by this
     * aggregator.
     * 
     * @param trunk the trunk to be used
     */
    public void addTrunk(Trunk trunk) {
        trunks.add(trunk);
    }

    /**
     * Add a new port exposing an inferior switch's port.
     * 
     * @param name the local name of the port
     * 
     * @param inner the port of an inferior switch
     * 
     * @return the newly created port
     */
    public Port addPort(String name, Port inner) {
        if (ports.containsKey(name))
            throw new IllegalArgumentException("name in use: " + name);
        ExposedPort result = new ExposedPort(name, inner);
        ports.put(name, result);
        return result;
    }

    /**
     * Remove a port from this switch.
     * 
     * @param name the port's local name
     */
    public void removePort(String name) {
        ports.remove(name);
    }

    private class ExposedPort implements Port {
        private final String name;
        private final Port innerPort;

        public ExposedPort(String name, Port innerPort) {
            this.name = name;
            this.innerPort = innerPort;
        }

        public Port innerPort() {
            return innerPort;
        }

        @Override
        public SwitchControl getSwitch() {
            return getControl();
        }

        @Override
        public EndPoint getEndPoint(short label) {
            return new ExposedTerminus(label);
        }

        private class ExposedTerminus implements EndPoint {
            private final short label;

            public ExposedTerminus(short label) {
                this.label = label;
            }

            @Override
            public Port getPort() {
                return ExposedPort.this;
            }

            @Override
            public short getLabel() {
                return label;
            }

            @Override
            public String toString() {
                return ExposedPort.this + ":" + label;
            }
        }

        @Override
        public String toString() {
            return Aggregator.this.name + ":" + name;
        }
    }

    @Override
    public Port findPort(String id) {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    @Override
    public SwitchControl getControl() {
        return control;
    }

    private class AggregateConnection implements Connection {
        private class SwitchClient implements ConnectionListener {
            Connection connection;

            SwitchClient(double bandwidth, SwitchControl control,
                         Collection<EndPoint> terminals) {
                ConnectionRequest request =
                    ConnectionRequest.of(terminals, bandwidth);
                control.connect(request, this);
                incompleteRequests++;
            }

            @Override
            public void ready(Connection conn) {
                this.connection = conn;
                incompleteRequests--;
                checkCompletion();
            }

            @Override
            public void failed() {
                failedRequests++;
                incompleteRequests--;
                checkCompletion();
            }
        }

        boolean active;
        ConnectionListener user;
        final int subnetworks;
        final double bandwidth;
        final List<SwitchClient> switchClients;
        int incompleteRequests, failedRequests;
        final Map<Trunk, EndPoint> tunnels;

        void checkCompletion() {
            /* Are we done yet? */
            if (incompleteRequests > 0) return;
            if (switchClients.size() < subnetworks) return;

            if (failedRequests > 0) {
                user.failed();
                release();
            } else {
                user.ready(this);
            }

            user = null;
        }

        AggregateConnection(ConnectionListener user, double bandwidth,
                            Map<SwitchControl, Collection<EndPoint>> subterminals,
                            Map<Trunk, EndPoint> tunnels) {
            this.user = user;
            this.bandwidth = bandwidth;
            this.tunnels = tunnels;
            this.subnetworks = subterminals.size();
            switchClients = new ArrayList<>(this.subnetworks);
            for (Map.Entry<SwitchControl, Collection<EndPoint>> entry : subterminals
                .entrySet()) {
                SwitchClient cli = new SwitchClient(bandwidth, entry.getKey(),
                                                    entry.getValue());
                switchClients.add(cli);
            }
        }

        @Override
        public void activate() {
            if (active) return;
            for (SwitchClient cli : switchClients) {
                assert cli.connection != null;
                cli.connection.activate();
            }
            active = true;
        }

        @Override
        public void deactivate() {
            if (!active) return;
            for (SwitchClient cli : switchClients) {
                assert cli.connection != null;
                cli.connection.deactivate();
            }
            active = false;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void release() {
            /* Release switch resources. */
            for (SwitchClient cli : switchClients) {
                if (cli.connection != null) {
                    cli.connection.release();
                    cli.connection = null;
                }
            }

            /* Release tunnel resources. */
            for (Map.Entry<Trunk, EndPoint> entry : tunnels.entrySet()) {
                Trunk trunk = entry.getKey();
                EndPoint ep = entry.getValue();
                trunk.releaseBandwidth(bandwidth);
                trunk.releaseTunnel(ep);
            }
        }
    }

    private final SwitchControl control = new SwitchControl() {
        @Override
        public void connect(ConnectionRequest request,
                            ConnectionListener response) {
            /* Map the set of caller's termini to the corresponding
             * inner termini that our topology consists of. */
            Collection<EndPoint> innerTerminalEndPoints =
                request.terminals.stream().map(t -> {
                    ExposedPort xp = (ExposedPort) t.getPort();
                    Port ip = xp.innerPort();
                    return ip.getEndPoint(t.getLabel());
                }).collect(Collectors.toSet());

            /* Get the set of ports that will be used as destinations in
             * routing. */
            Collection<Port> innerTerminalPorts = innerTerminalEndPoints
                .stream().map(EndPoint::getPort).collect(Collectors.toSet());

            /* Get a subset of all trunks, those with sufficent
             * bandwidth and free tunnels. */
            Collection<Trunk> adequateTrunks = trunks.stream()
                .filter(trunk -> trunk.getAvailableTunnelCount() > 0
                    && trunk.getBandwidth() >= request.bandwidth)
                .collect(Collectors.toSet());

            /* Maintain a reverse map from port to trunk. We need this
             * after running the spanning-tree algorithm to find which
             * trunks to allocate resources from. */
            Map<Port, Trunk> portToTrunkMap = new IdentityHashMap<>();
            for (Trunk trunk : adequateTrunks)
                for (Port port : trunk.getPorts())
                    portToTrunkMap.put(port, trunk);

            /* Get edges representing all suitable trunks. */
            Map<Edge<Port>, Double> edges =
                new HashMap<>(adequateTrunks.stream()
                    .collect(Collectors
                        .toMap(t -> HashableEdge.of(t.getPorts()),
                               Trunk::getDelay)));

            {
                /* Get a set of all switches for our trunks. */
                Collection<SwitchControl> switches =
                    adequateTrunks.stream()
                        .flatMap(trunk -> trunk.getPorts().stream()
                            .map(Port::getSwitch))
                        .collect(Collectors.toSet());

                /* Get models of all switches connected to the selected
                 * trunks, and combine their edges with the trunks. */
                edges.entrySet()
                    .addAll(switches
                        .stream().flatMap(sw -> getModel(request.bandwidth)
                            .entrySet().stream())
                        .collect(Collectors.toSet()));
            }

            /* Get rid of spurs as a small optimization. */
            Spans.prune(innerTerminalPorts, edges.keySet());

            /* Create routing tables for each port. */
            Map<Port, Map<Port, Way<Port>>> fibs =
                Spans.route(innerTerminalPorts, edges);

            /* Create terminal-aware weights for each edge. */
            Map<Edge<Port>, Double> weightedEdges =
                Spans.flatten(fibs, (p1, p2) -> HashableEdge.of(p1, p2));

            /* To impose additional constraints on the spanning tree,
             * keep a set of switches already reached. Edges that
             * connect two distinct switches that have both been reached
             * shall be excluded. */
            Collection<SwitchControl> reachedSwitches = new HashSet<>();

            /* Create the spanning tree, keeping track of reached
             * switches, and rejecting edges connecting two already
             * reached switches. */
            Collection<Edge<Port>> tree =
                Spans.span(innerTerminalPorts, weightedEdges,
                           p -> reachedSwitches.add(p.getSwitch()), e -> {
                               SwitchControl first = e.first().getSwitch();
                               SwitchControl second = e.second().getSwitch();
                               if (first == second) return true;
                               if (reachedSwitches.contains(first)
                                   && reachedSwitches.contains(second))
                                   return false;
                               return true;
                           });

            /* Identify all the end points for each switch. We have to
             * gather them all like this because they don't all come at
             * once. */
            Map<SwitchControl, Collection<EndPoint>> subterminals =
                new HashMap<>();

            /* Remember all tunnels we're about to create, so we can
             * delete them when the connection is closed. */
            Map<Trunk, EndPoint> tunnels = new HashMap<>();

            for (Edge<Port> edge : tree) {
                SwitchControl firstSwitch = edge.first().getSwitch();
                SwitchControl secondSwitch = edge.second().getSwitch();
                if (firstSwitch == secondSwitch) {
                    /* This is an edge across an inferior switch. We
                     * don't handle it directly, but infer it by the
                     * edges that connect to it. */
                    continue;
                }
                /* This is an edge runnning along a trunk. */

                /* Create a tunnel along this trunk, and remember one
                 * end of it. */
                Trunk firstTrunk = portToTrunkMap.get(edge.first());
                EndPoint ep1 = firstTrunk.allocateTunnel();
                tunnels.put(firstTrunk, ep1);

                /* Get both end points, find out what switches they
                 * correspond to, and add each end point to its switch's
                 * respective set of end points. */
                EndPoint ep2 = firstTrunk.getPeer(ep1);
                subterminals.computeIfAbsent(ep1.getPort().getSwitch(),
                                             k -> new HashSet<>())
                    .add(ep1);
                subterminals.computeIfAbsent(ep2.getPort().getSwitch(),
                                             k -> new HashSet<>())
                    .add(ep2);
            }

            /* Make sure the caller's end points are included in their
             * switches' corresponding sets. */
            for (EndPoint t : innerTerminalEndPoints)
                subterminals.computeIfAbsent(t.getPort().getSwitch(),
                                             k -> new HashSet<>())
                    .add(t);

            /* Create the connection, and let it pass itself to the user
             * when complete. */
            new AggregateConnection(response, request.bandwidth, subterminals,
                                    tunnels);
        }

        @Override
        public Map<Edge<Port>, Double> getModel(double minimumBandwidth) {
            /* Map the set of our termini to the corresponding inner
             * ports that our topology consists of. */
            Collection<Port> terminals = ports.values().stream()
                .map(ExposedPort::innerPort).collect(Collectors.toSet());

            /* Get a subset of all trunks, those with sufficent
             * bandwidth and free tunnels. */
            Collection<Trunk> suitableTrunks = trunks.stream()
                .filter(trunk -> trunk.getAvailableTunnelCount() > 0
                    && trunk.getBandwidth() >= minimumBandwidth)
                .collect(Collectors.toSet());

            /* Get a set of all switches for our trunks. */
            Collection<SwitchControl> switches = suitableTrunks.stream()
                .flatMap(trunk -> trunk.getPorts().stream()
                    .map(Port::getSwitch))
                .collect(Collectors.toSet());

            /* Get edges representing all suitable trunks. */
            Map<Edge<Port>, Double> edges =
                new HashMap<>(suitableTrunks.stream()
                    .collect(Collectors.toMap(t -> HashableEdge
                        .of(t.getPorts().get(0), t.getPorts().get(1)),
                                              Trunk::getDelay)));

            /* Get models of all switches, and combine their edges with
             * the trunks. */
            edges.entrySet().addAll(switches.stream()
                .flatMap(sw -> getModel(minimumBandwidth).entrySet().stream())
                .collect(Collectors.toSet()));

            /* Get rid of spurs as a small optimization. */
            Spans.<Port>prune(terminals, edges.keySet());

            /* Create routing tables for each port. */
            Map<Port, Map<Port, Way<Port>>> fibs =
                Spans.route(terminals, edges);

            /* Convert our exposed ports to a sequence so we can form
             * every combination of two ports. */
            final List<ExposedPort> portSeq = new ArrayList<>(ports.values());
            final int size = portSeq.size();

            /* For every combination of our exposed ports, store the
             * total distance as part of the result. */
            Map<Edge<Port>, Double> result = new HashMap<>();
            for (int i = 0; i + 1 < size; i++) {
                final ExposedPort start = portSeq.get(i);
                final Port innerStart = start.innerPort();
                final Map<Port, Way<Port>> startFib = fibs.get(innerStart);
                if (startFib == null) continue;
                for (int j = i + 1; j < size; j++) {
                    final ExposedPort end = portSeq.get(j);
                    final Port innerEnd = end.innerPort();
                    final Way<Port> way = startFib.get(innerEnd);
                    if (way == null) continue;
                    final Edge<Port> edge = HashableEdge.of(start, end);
                    result.put(edge, way.distance);
                }
            }

            return result;
        }

        @Override
        public EndPoint findEndPoint(String id) {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }
    };
}
