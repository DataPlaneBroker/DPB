
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.function.Function;

import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.Segment;
import uk.ac.lancs.networks.Service;
import uk.ac.lancs.networks.ServiceStatus;
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
        final Random rng = new Random(111);

        /* Create a scale-free topology. */
        final int vertexCount = 10;
        final int newEdgesPerVertex = 2;
        Collection<Edge<Vertex>> edges = new HashSet<>();
        {
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

        final Aggregator aggr;

        /* Keep a set of aggregator terminals to use for test circuits,
         * and the proportion of likelihood of being chosen. */
        final Map<Terminal, Double> candidates = new HashMap<>();
        final double candidateSum;

        {
            Map<Vertex, Collection<Vertex>> neighbors =
                Topologies.convertEdgesToNeighbors(edges);

            /* Keep track of vertices that have already had switches
             * created for them. */
            Map<Vertex, Switch> vertexToSwitch = new IdentityHashMap<>();

            Map<String, NetworkControl> subnets = new HashMap<>();
            MyExecutor exec = new MyExecutor();
            exec.start();
            aggr = new TransientAggregator(exec, "aggr", subnets::get);

            /* Build a transient broker network out of the topology. */
            for (Edge<Vertex> edge : edges) {
                /* Ensure the switches corresponding to these vertices
                 * exist. Derive their names from the vertices, and
                 * ensure they have one initial terminal that will not
                 * be used to form trunks. */
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
                 * hundred labels, and bandwidth proportional to the
                 * smaller degree of its two vertices. Set its metric to
                 * the length of the vertex. */
                Trunk trunk =
                    aggr.addTrunk(TerminalId.of(s1.getControl().name(),
                                                t1.name()),
                                  TerminalId.of(s2.getControl().name(),
                                                t2.name()));
                trunk.defineLabelRange(1000, 300);
                trunk.provideBandwidth(1000.0 * Math.min(d1, d2));
                final double dx = v1.x - v2.x;
                final double dy = v1.y - v2.y;
                trunk.setDelay(Math.hypot(dx, dy));
            }

            /* Compute the node-selecting probability denominator. */
            candidateSum =
                candidates.values().stream().mapToDouble(d -> d).sum();
        }

        /* Keep track of which services are up, and how many circuits
         * are in use. */
        BitSet services = new BitSet();
        int circuitCount = 0;

        /* Keep adding services. */
        int nextLabel = 1;
        Collection<ServiceStatus> preaccept = new HashSet<>(Arrays
            .asList(ServiceStatus.INACTIVE, ServiceStatus.FAILED));
        Collection<ServiceStatus> acceptables = new HashSet<>(Arrays
            .asList(ServiceStatus.ACTIVE, ServiceStatus.FAILED));
        for (;;) {
            /* Choose a description of the circuit. */
            final Segment seg;
            /* Choose the number of circuits to connect. */
            final int cc = rng.nextInt(rng.nextInt(2) + 1) + 2;
            {
                final int label = nextLabel++;
                Collection<Terminal> chosen = new HashSet<>();
                double sum = candidateSum;
                for (int i = 0; i < cc; i++) {
                    /* Choose a random terminal. */
                    double pick = rng.nextDouble() * sum;
                    for (Map.Entry<Terminal, Double> entry : candidates
                        .entrySet()) {
                        /* Skip over terminals we've already chosen. */
                        Terminal t = entry.getKey();
                        if (chosen.contains(t)) continue;

                        /* See if this is the selected one. */
                        double amount = entry.getValue();
                        pick -= amount;
                        if (pick >= 0.0) continue;

                        /* Record this as selected, and ensure we don't
                         * select it again. */
                        sum -= amount;
                        chosen.add(t);
                        break;
                    }
                }
                Segment.Builder builder = Segment.start();
                for (Terminal t : chosen)
                    builder.add(t, label, 10.0, 10.0);
                seg = builder.create();
            }
            System.err.printf("%nseg: %s%n", seg.circuitFlows());

            /* Create the service. */
            Service srv = aggr.getControl().newService();
            services.set(srv.id());
            circuitCount += cc;
            srv.define(seg);
            while (!preaccept.contains(srv.awaitStatus(preaccept, 10000)))
                ;
            System.err.printf("activating...%n");
            srv.activate();
            System.err.printf("Service %d: %s%n", srv.id(), srv.status());
            while (!acceptables.contains(srv.awaitStatus(acceptables, 10000)))
                ;
            System.err.printf("%d %d Service %d: %s%n",
                              services.cardinality(), circuitCount, srv.id(),
                              srv.status());
        }

        /* Clear out all services so we can start again. */
    }

    private static class MyExecutor extends Thread implements Executor {
        private final List<Runnable> queue = new ArrayList<>();

        public MyExecutor() {
            setDaemon(false);
        }

        @Override
        public synchronized void execute(Runnable command) {
            queue.add(command);
            notify();
        }

        private synchronized Runnable get() {
            while (queue.isEmpty())
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new UnsupportedOperationException("unimplemented",
                                                            e);
                }
            return queue.remove(0);
        }

        @Override
        public void run() {
            Runnable r;
            while ((r = get()) != null)
                r.run();
        }
    }
}
