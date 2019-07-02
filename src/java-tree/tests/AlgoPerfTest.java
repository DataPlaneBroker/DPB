
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

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.Segment;
import uk.ac.lancs.networks.Service;
import uk.ac.lancs.networks.ServiceException;
import uk.ac.lancs.networks.ServiceStatus;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.mgmt.Aggregator;
import uk.ac.lancs.networks.mgmt.LabelsInUseException;
import uk.ac.lancs.networks.mgmt.LabelsUnavailableException;
import uk.ac.lancs.networks.mgmt.SubterminalBusyException;
import uk.ac.lancs.networks.mgmt.Switch;
import uk.ac.lancs.networks.mgmt.TerminalConfigurationException;
import uk.ac.lancs.networks.mgmt.TerminalExistsException;
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
        final int nodeCount = 54;
        final int vertexCount = 2;
        final long seed = new SecureRandom().nextLong();

        Path coordsFile = Paths
            .get("scratch/" + nodeCount + "-" + vertexCount + "-coords.csv");
        Path edgesFile = Paths
            .get("scratch/" + nodeCount + "-" + vertexCount + "-edges.csv");
        Path graphFile = Paths
            .get("scratch/" + nodeCount + "-" + vertexCount + "-graph.svg");
        Path lbsFile = Paths.get("scratch/" + nodeCount + "-" + vertexCount
            + "-latency-by-service.csv");
        Path lbcFile = Paths.get("scratch/" + nodeCount + "-" + vertexCount
            + "-latency-by-circuit.csv");

        final Random rng = new Random(seed);

        /* Create a scale-free topology. */
        Collection<Edge<Vertex>> edges =
            createTopology(rng, nodeCount, vertexCount);
        System.err.printf("Topology created%n");

        {
            /* Record the topology. */
            Map<Vertex, Collection<Vertex>> neighbors =
                Topologies.convertEdgesToNeighbors(edges);
            try (PrintWriter out =
                new PrintWriter(Files.newBufferedWriter(coordsFile))) {
                for (Vertex v : neighbors.keySet())
                    out.printf("%s, %g, %g%n", v, v.x, v.y);
            }
            try (PrintWriter out =
                new PrintWriter(Files.newBufferedWriter(edgesFile))) {
                for (Edge<Vertex> edge : edges)
                    out.printf("%s, %s%n", edge.first(), edge.second());
            }
            double xmin = +Double.MAX_VALUE, xmax = -Double.MAX_VALUE;
            double ymin = +Double.MAX_VALUE, ymax = -Double.MIN_VALUE;
            for (Vertex v : neighbors.keySet()) {
                if (v.x > xmax) xmax = v.x;
                if (v.x < xmin) xmin = v.x;
                if (v.y > ymax) ymax = v.y;
                if (v.y < ymin) ymin = v.y;
            }
            final double margin = Math.max(xmax - xmin, ymax - ymin) / 100.0;
            xmin -= margin;
            ymin -= margin;
            xmax += margin;
            ymax += margin;

            try (PrintWriter out =
                new PrintWriter(Files.newBufferedWriter(graphFile))) {
                out.println("<?xml version=\"1.0\" " + "standalone=\"no\"?>");
                out.println("<!DOCTYPE svg PUBLIC");
                out.println(" \"-//W3C//DTD SVG 20000303 Stylable//EN\"");
                out.println(" \"http://www.w3.org/TR/2000/03/"
                    + "WD-SVG-20000303/DTD/svg-20000303-stylable.dtd\">");
                out.printf("<!-- seed: %d -->%n", seed);
                out.println("<svg xmlns=\"http://www.w3.org/2000/svg\"");
                out.printf(" viewBox='%g %g %g %g'>%n", xmin, ymin,
                           xmax - xmin, ymax - ymin);
                out.printf("<g fill='none' stroke='black'"
                    + " stroke-width='%g' stroke-linecap='round'>%n", margin);
                for (Edge<Vertex> edge : edges)
                    out.printf("<line x1='%g' y1='%g' x2='%g' y2='%g' />%n",
                               edge.first().x, edge.first().y,
                               edge.second().x, edge.second().y);
                out.println("</g>");
                out.println("</svg>");
            }
        }
        System.err.printf("Graph recorded%n");

        Statistics latencyByService = new Statistics();
        Statistics latencyByCircuit = new Statistics();

        for (int run = 0; run < 101; run++) {
            /* Ignore statistics of the first run, as they might be
             * heavily influenced by Java optimizations. */
            if (run == 1) {
                latencyByCircuit.reset();
                latencyByService.reset();
            }

            /* Create a simulated network based on the provided
             * topology. */
            Scenario scen = new Scenario(edges);

            /* Keep track of which services are up, and how many
             * circuits are in use. */
            BitSet services = new BitSet();
            int circuitCount = 0;

            /* Keep adding services. */
            int nextLabel = 1;
            Collection<ServiceStatus> preaccept = new HashSet<>(Arrays
                .asList(ServiceStatus.INACTIVE, ServiceStatus.FAILED));
            Collection<ServiceStatus> acceptables = new HashSet<>(Arrays
                .asList(ServiceStatus.ACTIVE, ServiceStatus.FAILED));
            try {
                for (;;) {
                    /* Choose a description of the circuit. */
                    final Segment seg;
                    /* Choose the number of circuits to connect. */
                    final int cc = rng.nextInt(rng.nextInt(2) + 1) + 2;
                    {
                        final int label = nextLabel++;
                        Collection<Terminal> chosen =
                            scen.chooseTerminals(rng, cc);
                        Segment.Builder builder = Segment.start();
                        for (Terminal t : chosen)
                            builder.add(t, label, 10.0, 10.0);
                        seg = builder.create();
                    }
                    // System.err.printf("%nseg: %s%n",
                    // seg.circuitFlows());

                    /* Create the service, and time it. */
                    final long start = System.nanoTime();
                    Service srv = scen.getNetwork().newService();
                    srv.define(seg);
                    while (!preaccept
                        .contains(srv.awaitStatus(preaccept, 10000)))
                        ;
                    srv.activate();
                    while (!acceptables
                        .contains(srv.awaitStatus(acceptables, 10000)))
                        ;
                    final long end = System.nanoTime();
                    if (false) System.err.printf("%d %d Service %d: %s%n",
                                                 services.cardinality(),
                                                 circuitCount, srv.id(),
                                                 srv.status());

                    /* Record the timings with our averages. */
                    latencyByService.incorporate(services.cardinality(),
                                                 end - start);
                    latencyByCircuit.incorporate(circuitCount, end - start);

                    /* Record the new number of services and engaged
                     * circuits. */
                    services.set(srv.id());
                    circuitCount += cc;
                }
            } catch (ServiceException ex) {
                System.err.printf("terminating run %d "
                    + "due to service exception%n", run);
            }
            scen.discard();
            System.err.printf("Threads: %d%n", Thread.activeCount());
            System.gc();
        }

        try (PrintWriter out =
            new PrintWriter(Files.newBufferedWriter(lbsFile))) {
            for (Statistics.Row row : latencyByService) {
                out.printf("%d, %d, %g, %g%n", row.key, row.count, row.mean,
                           row.stddev);
            }
        }

        try (PrintWriter out =
            new PrintWriter(Files.newBufferedWriter(lbcFile))) {
            for (Statistics.Row row : latencyByCircuit) {
                out.printf("%d, %d, %g, %g%n", row.key, row.count, row.mean,
                           row.stddev);
            }
        }
    }

    static Collection<Edge<Vertex>>
        createTopology(final Random rng, final int vertexCount,
                       final int newEdgesPerVertex) {
        Collection<Edge<Vertex>> edges = new HashSet<>();
        /* Remember which other vertices an vertex joins. */
        Map<Vertex, Collection<Vertex>> neighbors = new HashMap<>();

        Topologies.generateTopology(Vertex::new, vertexCount, () -> rng
            .nextInt(rng.nextInt(rng.nextInt(newEdgesPerVertex) + 1) + 1) + 1,
                                    neighbors, rng);

        /* Convert to a set of edges. */
        edges = Topologies.convertNeighborsToEdges(neighbors);

        /* Give each vertex a mass proportional to its degree. */
        neighbors.forEach((a, n) -> a.mass = n.size());

        /* Allow vertices to find stable, optimum positions. */
        Topologies.alignTopology(Vertex.ATTRIBUTION, edges, (sp, ed) -> {},
                                 Pauser.NULL);
        return edges;
    }

    static class Scenario {
        Scenario(Collection<Edge<Vertex>> edges)
            throws TerminalExistsException,
                TerminalNameException,
                TerminalConfigurationException,
                SubterminalBusyException,
                UnknownSubterminalException,
                UnknownSubnetworkException,
                LabelsUnavailableException,
                LabelsInUseException {
            Map<Vertex, Collection<Vertex>> neighbors =
                Topologies.convertEdgesToNeighbors(edges);

            /* Keep track of vertices that have already had switches
             * created for them. */
            Map<Vertex, Switch> vertexToSwitch = new IdentityHashMap<>();

            Map<String, NetworkControl> subnets = new HashMap<>();
            aggr = new TransientAggregator(executor, "aggr", subnets::get);

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

        public void discard() {
            executor.shutdown();
        }

        private final ExecutorService executor =
            Executors.newFixedThreadPool(1);

        /* Keep a set of aggregator terminals to use for test circuits,
         * and the proportion of likelihood of being chosen. */
        private final Map<Terminal, Double> candidates = new HashMap<>();
        private final double candidateSum;

        private final Aggregator aggr;

        NetworkControl getNetwork() {
            return aggr.getControl();
        }

        Collection<Terminal> chooseTerminals(Random rng, int cc) {
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
            return chosen;
        }
    }

    static class Statistics implements Iterable<Statistics.Row> {
        private double[] sum = new double[100], sum2 = new double[100];
        private long[] count = new long[100];

        public void reset() {
            Arrays.setAll(sum, i -> 0.0);
            Arrays.setAll(sum2, i -> 0.0);
            Arrays.setAll(count, i -> 0);
        }

        public void incorporate(int key, double sample) {
            if (key >= count.length) {
                int newLength =
                    Math.max(count.length + count.length / 2, key + 1);
                sum = Arrays.copyOf(sum, newLength);
                sum2 = Arrays.copyOf(sum2, newLength);
                count = Arrays.copyOf(count, newLength);
            }
            sum[key] += sample;
            sum2[key] += sample * sample;
            count[key]++;
        }

        static class Row {
            public final int key;
            public final double mean, stddev;
            public final long count;

            Row(int key, long count, double mean, double stddev) {
                this.key = key;
                this.count = count;
                this.mean = mean;
                this.stddev = stddev;
            }
        }

        @Override
        public Iterator<Row> iterator() {
            return new Iterator<Row>() {
                int cand = 0;
                int next = -1;

                private boolean ensureNext() {
                    if (next >= 0) return true;
                    while (cand < count.length && count[cand] == 0)
                        cand++;
                    if (cand < count.length) {
                        next = cand++;
                        return true;
                    }
                    return false;
                }

                @Override
                public boolean hasNext() {
                    return ensureNext();
                }

                @Override
                public Row next() {
                    if (!ensureNext()) throw new NoSuchElementException();
                    int ch = next;
                    next = -1;
                    double mean = sum[ch] / count[ch];
                    double var = sum2[ch] / count[ch] - mean * mean;
                    double stddev = Math.sqrt(var);
                    return new Row(ch, count[ch], mean, stddev);
                }
            };
        }
    }
}
