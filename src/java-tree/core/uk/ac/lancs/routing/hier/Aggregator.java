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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import uk.ac.lancs.routing.span.Edge;
import uk.ac.lancs.routing.span.Spans;
import uk.ac.lancs.routing.span.Way;

/**
 * Implements a virtual switch hierarchically configuring inferior
 * switches.
 * 
 * @todo This will implement {@link Switch}.
 * 
 * @author simpsons
 */
public class Aggregator implements Switch {
    private final Collection<Switch> inferiors = new HashSet<>();
    private final Collection<Trunk> trunks = new HashSet<>();

    /**
     * Make a trunk available for creating connections by this
     * aggregator.
     * 
     * @param trunk the trunk to be used
     */
    public void addTrunk(Trunk trunk) {
        trunks.add(trunk);
        List<Port> ports = trunk.getPorts();
        inferiors.add(ports.get(0).getSwitch());
        inferiors.add(ports.get(1).getSwitch());
    }

    /**
     * Add a new port.
     * 
     * @param config configuration to define the port, or map it to
     * lower layers
     * 
     * @return the newly created port
     * 
     * @throws IllegalArgumentException if the configuration is invalid
     * 
     * @throws NoSuchElementException if the configuration is
     * structurally valid, but does not map to an existing resource
     */
    public Port addPort(Object config) {
        String internalId = config.toString();
        Collection<Port> matches =
            inferiors.stream().map(sw -> sw.findPort(internalId))
                .filter(p -> p != null).collect(Collectors.toSet());
        if (matches.isEmpty())
            throw new NoSuchElementException("unknown configuration "
                + config);
        if (matches.size() > 1)
            throw new IllegalArgumentException("configuration " + config
                + "matches " + matches);
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    private class ExposedPort implements Port {
        private final Port innerPort;

        public ExposedPort(Port innerPort) {
            this.innerPort = innerPort;
        }

        public Port innerPort() {
            return innerPort;
        }

        @Override
        public Switch getSwitch() {
            return Aggregator.this;
        }

        @Override
        public Terminus getEndPoint(short label) {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }
    }

    @Override
    public Terminus findEndPoint(String id) {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    @Override
    public void connect(ConnectionRequest request,
                        ConnectionListener response) {
        /* Map the set of our end points to the corresponding inner
         * ports that our topology consists of. */
        Collection<Port> terminals = request.endPoints().stream()
            .map(Terminus::getPort).map(p -> (ExposedPort) p)
            .map(ExposedPort::innerPort).collect(Collectors.toSet());

        /* Get a subset of all trunks, those with sufficent bandwidth
         * and free tunnels. */
        Collection<Trunk> suitableTrunks = trunks.stream()
            .filter(trunk -> trunk.getAvailableTunnelCount() > 0
                && trunk.getBandwidth() >= request.bandwidth())
            .collect(Collectors.toSet());

        /* Get a set of all switches for our trunks. */
        Collection<Switch> switches = suitableTrunks.stream()
            .flatMap(trunk -> trunk.getPorts().stream().map(Port::getSwitch))
            .collect(Collectors.toSet());

        /* Get edges representing all suitable trunks. */
        Map<Edge<Port>, Double> edges = new HashMap<>(suitableTrunks.stream()
            .collect(Collectors.toMap(Trunk::getEdge, Trunk::getDelay)));

        /* Get models of all switches, and combine their edges with the
         * trunks. */
        edges.entrySet().addAll(switches.stream()
            .flatMap(sw -> getModel(request.bandwidth()).entrySet().stream())
            .collect(Collectors.toSet()));

        /* Get rid of spurs as a small optimization. */
        Spans.prune(terminals, edges.keySet());

        /* Create routing tables for each port. */
        Map<Port, Map<Port, Way<Port>>> fibs = Spans.route(terminals, edges);

        /* Create terminal-aware weights for each edge. */
        Map<Edge<Port>, Double> weightedEdges = Spans.flatten(fibs);

        /* To impose additional constraints on the spanning tree, keep a
         * set of switches already reached. Edges that connect two
         * distinct switches that have both been reached shall be
         * excluded. */
        Collection<Switch> reachedSwitches = new HashSet<>();

        /* Create the spanning tree, keeping track of reached switches,
         * and rejecting edges connecting two already reached
         * switches. */
        Collection<Edge<Port>> tree =
            Spans.<Port>span(terminals, weightedEdges,
                             p -> reachedSwitches.add(p.getSwitch()), e -> {
                                 Switch first = e.first.getSwitch();
                                 Switch second = e.second.getSwitch();
                                 if (first == second) return true;
                                 if (reachedSwitches.contains(first)
                                     && reachedSwitches.contains(second))
                                     return false;
                                 return true;
                             });

        /* TODO: For each edge, identify the corresponding trunk or
         * switch. Reserve bandwidth and a label on each trunk. Create
         * sub-connections on switches. Record all labels and
         * sub-connections in a new connection object, and give back to
         * the caller. */
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    @Override
    public Map<Edge<Port>, Double> getModel(double minimumBandwidth) {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    @Override
    public Port findPort(String id) {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }
}
