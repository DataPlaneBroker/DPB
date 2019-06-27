
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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.function.Function;

import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.mgmt.Aggregator;
import uk.ac.lancs.networks.mgmt.SubterminalBusyException;
import uk.ac.lancs.networks.mgmt.Switch;
import uk.ac.lancs.networks.mgmt.TerminalId;
import uk.ac.lancs.networks.mgmt.TerminalNameException;
import uk.ac.lancs.networks.mgmt.Trunk;
import uk.ac.lancs.networks.mgmt.UnknownSubnetworkException;
import uk.ac.lancs.networks.mgmt.UnknownSubterminalException;
import uk.ac.lancs.networks.transients.DummySwitch;
import uk.ac.lancs.networks.transients.TransientAggregator;
import uk.ac.lancs.routing.span.Edge;

/**
 * 
 * 
 * @author simpsons
 */
public class AlgoPerfTest {
    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        /* Create a scale-free topology. */
        final int vertexCount = 100;
        final int newEdgesPerVertex = 3;
        Collection<Edge<Vertex>> edges = new HashSet<>();
        {
            final Random rng = new Random();

            /* Remember which other vertices an vertex joins. */
            Map<Vertex, Collection<Vertex>> neighbors = new HashMap<>();

            Topologies.generateTopology(Vertex::new, vertexCount, () -> rng
                .nextInt(rng.nextInt(rng.nextInt(newEdgesPerVertex) + 1) + 1)
                + 1, neighbors, rng);

            /* Convert to a set of edges. */
            edges = Topologies.convertNeighborsToEdges(neighbors);

            /* Give each vertex a mass proportional to its degree. */
            neighbors.forEach((a, n) -> a.mass = n.size());

            /* Allow vertices to find stable, optimum positions. */
            Topologies.alignTopology(Vertex.ATTRIBUTION, edges,
                                     (sp, ed) -> {}, Pauser.NULL);
            System.out.printf("Complete%n");
        }

        Map<Vertex, Collection<Vertex>> neighbors =
            Topologies.convertEdgesToNeighbors(edges);

        /* Keep track of vertices that have already had switches created
         * for them. */
        Map<Vertex, Switch> vertexToSwitch = new IdentityHashMap<>();

        Map<String, NetworkControl> subnets = new HashMap<>();
        Aggregator aggr =
            new TransientAggregator(Executors.newCachedThreadPool(), "aggr",
                                    subnets::get);
        /* Keep a set of aggregator terminals to use for test circuits,
         * and the proportion of likelihood of being chosen. */
        Map<Terminal, Double> candidates = new HashMap<>();

        /* Build a transient broker network out of the topology. */
        for (Edge<Vertex> edge : edges) {
            /* Ensure the switches corresponding to these vertices
             * exist. Derive their names from the vertices, and ensure
             * they have one initial terminal that will not be used to
             * form trunks. */
            Vertex v1 = edge.first();
            int d1 = neighbors.get(v1).size();
            Vertex v2 = edge.second();
            int d2 = neighbors.get(v2).size();
            Function<Vertex, DummySwitch> maker = v -> {
                String name = "v" + v;
                DummySwitch r = new DummySwitch(name);
                subnets.put(name, r.getControl());
                try {
                    Terminal t = r.addTerminal("host", "meaningless");
                    Terminal st = aggr.addTerminal("a" + v, TerminalId
                        .of(r.getControl().name(), t.name()));
                    int d = neighbors.get(v).size();
                    candidates.put(st, 1.0 / d);
                } catch (SubterminalBusyException
                    | UnknownSubterminalException | TerminalNameException
                    | UnknownSubnetworkException e) {
                    throw new AssertionError("unreachable", e);
                }
                return r;
            };
            Switch s1 = vertexToSwitch.computeIfAbsent(v1, maker);
            Switch s2 = vertexToSwitch.computeIfAbsent(v2, maker);

            /* Create terminals to connect as a trunk. */
            Terminal t1 = s1.addTerminal("to" + v2, v2.toString());
            Terminal t2 = s2.addTerminal("to" + v1, v1.toString());

            /* Create a trunk between the switches. Give it a few
             * hundred labels, and bandwidth proportional to the smaller
             * degree of its two vertices. */
            Trunk trunk = aggr
                .addTrunk(TerminalId.of(s1.getControl().name(), t1.name()),
                          TerminalId.of(s2.getControl().name(), t2.name()));
            trunk.defineLabelRange(1000, 300);
            trunk.provideBandwidth(1000.0 * Math.min(d1, d2));
        }

    }
}
