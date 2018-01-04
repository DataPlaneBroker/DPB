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

            /* Maintain a reverse map from port to trunk. */
            Map<Port, Trunk> portToTrunkMap = new IdentityHashMap<>();
            for (Trunk trunk : adequateTrunks)
                for (Port port : trunk.getPorts())
                    portToTrunkMap.put(port, trunk);

            /* Get edges representing all suitable trunks. */
            Map<Edge<Port>, Double> edges = new HashMap<>(adequateTrunks
                .stream()
                .collect(Collectors.toMap(Trunk::getEdge, Trunk::getDelay)));

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
            Map<Edge<Port>, Double> weightedEdges = Spans.flatten(fibs);

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

            /* For each switch, identify all its involved ports, and
             * identify trunks that must be modified. */
            class TrunkClient {
                final Trunk trunk;
                final EndPoint mouth;

                public TrunkClient(Trunk trunk, EndPoint mouth) {
                    this.trunk = trunk;
                    this.mouth = mouth;
                }
            }
            Map<SwitchControl, Collection<EndPoint>> mouthsPerSwitch =
                new HashMap<>();
            Collection<TrunkClient> trunkClients = new HashSet<>();
            for (Edge<Port> edge : tree) {
                SwitchControl firstSwitch = edge.first().getSwitch();
                SwitchControl secondSwitch = edge.second().getSwitch();
                if (firstSwitch == secondSwitch) {
                    /* This is an edge across an inferior switch. We
                     * don't handle it directly, but infer it by the
                     * edges that connect to it. */
                    continue;
                }

                /* Remember how we use this trunk. */
                Trunk firstTrunk = portToTrunkMap.get(edge.first());
                EndPoint ep1 = firstTrunk.allocateTunnel();
                TrunkClient tc = new TrunkClient(firstTrunk, ep1);
                trunkClients.add(tc);

                /* */
                EndPoint ep2 = firstTrunk.getPeer(ep1);
                mouthsPerSwitch.computeIfAbsent(ep1.getPort().getSwitch(),
                                                k -> new HashSet<>())
                    .add(ep1);
                mouthsPerSwitch.computeIfAbsent(ep2.getPort().getSwitch(),
                                                k -> new HashSet<>())
                    .add(ep2);
            }

            /* Make sure the caller's termini are included. */
            for (EndPoint t : innerTerminalEndPoints)
                mouthsPerSwitch.computeIfAbsent(t.getPort().getSwitch(),
                                                k -> new HashSet<>())
                    .add(t);

            class SwitchClient {
                final SwitchControl control;

                SwitchClient(SwitchControl control) {
                    this.control = control;
                }
            }

            /* TODO: For each edge, identify the corresponding trunk or
             * switch. Reserve bandwidth and a label on each trunk.
             * Create sub-connections on switches. Record all labels and
             * sub-connections in a new connection object, and give back
             * to the caller. */
            throw new UnsupportedOperationException("unimplemented"); // TODO
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
            Map<Edge<Port>, Double> edges = new HashMap<>(suitableTrunks
                .stream()
                .collect(Collectors.toMap(Trunk::getEdge, Trunk::getDelay)));

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
                    final Edge<Port> edge = Edge.of(start, end);
                    result.put(edge, way.distance);
                }
            }

            return result;
        }

        @Override
        public EndPoint findTerminus(String id) {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }
    };
}
