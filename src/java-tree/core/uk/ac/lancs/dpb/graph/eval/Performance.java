/*
 * Copyright (c) 2021, Regents of the University of Lancaster
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the
 *   distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *
 *  Author: Steven Simpson <s.simpson@lancaster.ac.uk>
 */

package uk.ac.lancs.dpb.graph.eval;

import java.io.File;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import uk.ac.lancs.dpb.graph.BidiCapacity;
import uk.ac.lancs.dpb.graph.Capacity;
import uk.ac.lancs.dpb.graph.DemandFunction;
import uk.ac.lancs.dpb.graph.PairDemandFunction;
import uk.ac.lancs.dpb.graph.QualifiedEdge;
import uk.ac.lancs.dpb.tree.ComprehensiveTreePlotter;
import uk.ac.lancs.dpb.tree.TreePlotter;

/**
 *
 * @author simpsons
 */
public class Performance {
    private static class MySupply implements CapacitySupply {
        private final double factor;

        public MySupply(double factor) {
            this.factor = factor;
        }

        @Override
        public BidiCapacity getCapacity(double cost, int startDegree,
                                        int finishDegree, int maxDegree,
                                        double maxCost) {
            double msd =
                Math.pow(startDegree / (double) maxDegree, factor) * maxDegree;
            double mfd =
                Math.pow(finishDegree / (double) maxDegree, factor) * maxDegree;
            return BidiCapacity.of(Capacity.at(msd + mfd));
        }
    }

    public static void main(String[] args) throws Exception {
        CapacitySupply supply = new MySupply(1.0 / 3.0);

        /* This is the number of vertices in each generated graph. */
        final int[] vertexCounts =
            IntStream.rangeClosed(1, 1).map(i -> i * 50).toArray();

        /* This is the number of times we use one graph with all
         * variations of other parameters. */
        final int graphRuns = 10;

        /* This is the number of goals we select to create a challenge
         * from a graph. */
        final int[] goalSetSizes = IntStream.rangeClosed(3, 5).toArray();

        /* This is the number of times we use goal sets of the same
         * size. */
        final int goalSetRuns = 10;

        class Measurement {
            final double elapsed;

            final double fit;

            public Measurement(double elapsed, double fit) {
                this.elapsed = elapsed;
                this.fit = fit;
            }

            @Override
            public String toString() {
                return String.format("%g,%g", elapsed, fit);
            }
        }

        /* algo, grid size, grid number, goal count, challenge number */
        Map<String,
            Map<Integer,
                Map<Integer,
                    Map<Integer, Map<Integer, Measurement>>>>> measurements =
                        new LinkedHashMap<>();

        Random rng = new Random(1);

        Map<String, TreePlotter> algos = new LinkedHashMap<>();

        /* The first algorithm must be exhaustive, as it gives us ground
         * truth. */
        algos
            .put("exh",
                 new ComprehensiveTreePlotter(ComprehensiveTreePlotter.ALL_EDGE_MODES));

        algos.put("99999", new ComprehensiveTreePlotter(ComprehensiveTreePlotter
            .biasThreshold(0.99999)));

        algos.put("9999", new ComprehensiveTreePlotter(ComprehensiveTreePlotter
            .biasThreshold(0.9999)));

        algos.put("999", new ComprehensiveTreePlotter(ComprehensiveTreePlotter
            .biasThreshold(0.999)));

        algos.put("99", new ComprehensiveTreePlotter(ComprehensiveTreePlotter
            .biasThreshold(0.99)));

        /* Vary the graph dimensions. Do this in the outermost loop
         * because some of these are quite costly. */
        for (final int vertexCount : vertexCounts) {
            /* Generate several graphs of this size. */
            for (final int graphIter : IntStream.range(0, graphRuns)
                .toArray()) {
                System.out.printf("%nVertices: %d; run %d%n", vertexCount,
                                  graphIter);
                Graph graph = GraphExamples
                    .createElasticScaleFreeGraph(rng, vertexCount, 3, 3, supply,
                                                 null);
                try (PrintWriter out = new PrintWriter(new File(String
                    .format("scratch/graph-%d-%d.svg", vertexCount,
                            graphIter)))) {
                    graph.drawSVG(out, null, null, 0.3, 0.9);
                }

                /* Vary the number of goals. */
                for (final int goalCount : goalSetSizes) {
                    /* Run several challenges on this graph. */
                    for (final int chalIter : IntStream.range(0, goalSetRuns)
                        .toArray()) {
                        List<Vertex> goals = graph.chooseGoals(goalCount, rng);
                        assert goals.size() == goalCount;

                        try (PrintWriter out = new PrintWriter(new File(String
                            .format("scratch/challenge-%d-%d-%d-%d.svg",
                                    vertexCount, graphIter, goalCount,
                                    chalIter)))) {
                            graph.drawSVG(out, goals, null, 0.3, 0.9);
                        }

                        /* Allow each goal to send the same amount, and
                         * receive the sum of the others. */
                        DemandFunction demand = new PairDemandFunction(IntStream
                            .range(0, goals.size())
                            .mapToObj(i -> BidiCapacity
                                .of(Capacity.at(6.0 / (goalCount - 1)),
                                    Capacity.at(6.0)))
                            .collect(Collectors.toList()));

                        /* Measure the performance and accuracy of each
                         * algorithm. */
                        for (var namedAlgo : algos.entrySet()) {
                            String name = namedAlgo.getKey();
                            TreePlotter algo = namedAlgo.getValue();

                            // System.err.printf("Algo: %s%n", name);
                            final double duration;
                            double bestScore = Double.POSITIVE_INFINITY;
                            Map<? extends QualifiedEdge<Vertex>,
                                ? extends BidiCapacity> best = null;
                            final long start = System.currentTimeMillis();
                            for (int cycles = 1;; cycles++) {
                                for (var cand : algo.plot(goals, demand,
                                                          graph.edges)) {
                                    double score = 0.0;
                                    for (var entry : cand.entrySet()) {
                                        QualifiedEdge<Vertex> key =
                                            entry.getKey();
                                        BidiCapacity val = entry.getValue();
                                        score += key.cost * (val.ingress.min()
                                            + val.egress.min());
                                    }
                                    if (best == null || score < bestScore) {
                                        best = cand;
                                        bestScore = score;
                                        if (false) System.err
                                            .printf("acc %g: %s%n", bestScore,
                                                    best.entrySet().stream()
                                                        .map(e -> e.getKey()
                                                            .toString())
                                                        .collect(Collectors
                                                            .joining(", ")));
                                    }
                                }

                                /* Compute the duration. If it's not yet
                                 * added up to a second, tryin again. */
                                final long stop = System.currentTimeMillis();
                                if (stop - start < 2000) continue;

                                /* Record the average duration, and
                                 * stop. */
                                duration = (stop - start) / 1000.0 / cycles;
                                break;
                            }

                            File svg = new File(String
                                .format("scratch/solution-%d-%d-%d-%d-%s.svg",
                                        vertexCount, graphIter, goalCount,
                                        chalIter, name));
                            final Measurement measurement;
                            if (best != null) {
                                measurement =
                                    new Measurement(duration, bestScore);
                                try (PrintWriter out = new PrintWriter(svg)) {
                                    graph.drawSVG(out, goals, best, 0.3, 0.9);
                                }
                                System.err.printf("%d vertices; graph %d;"
                                    + " %d goals;" + " goalset %d;" + " %s"
                                    + " took %g s to find" + " best %g%n",
                                                  vertexCount, graphIter,
                                                  goalCount, chalIter, name,
                                                  duration, bestScore);
                            } else {
                                measurement =
                                    new Measurement(duration,
                                                    Double.POSITIVE_INFINITY);
                                svg.delete();
                                System.err.printf("%d vertices; graph %d;"
                                    + " %d goals;" + " goalset %d;" + " %s"
                                    + " took %g s to find" + " no solution%n",
                                                  vertexCount, graphIter,
                                                  goalCount, chalIter, name,
                                                  duration);
                            }

                            /* algo, vertexCount, graphIter, goalCount,
                             * chalter */
                            measurements
                                .computeIfAbsent(name, k -> new TreeMap<>())
                                .computeIfAbsent(vertexCount,
                                                 k -> new TreeMap<>())
                                .computeIfAbsent(graphIter,
                                                 k -> new TreeMap<>())
                                .computeIfAbsent(goalCount,
                                                 k -> new TreeMap<>())
                                .put(chalIter, measurement);
                        }
                    }
                }
            }
        }
        System.out.printf("Measurements:%n%s%n", measurements);
        for (var e1 : measurements.entrySet()) {
            final String algo = e1.getKey();
            for (var e2 : e1.getValue().entrySet()) {
                final int gridSize = e2.getKey();
                for (var e3 : e2.getValue().entrySet()) {
                    final int gridInstance = e3.getKey();
                    for (var e4 : e3.getValue().entrySet()) {
                        final int goalCount = e4.getKey();
                        for (var e5 : e4.getValue().entrySet()) {
                            final int goalSet = e5.getKey();
                            final Measurement m = e5.getValue();
                            System.out.printf("%s,%d,%d,%d,%d,%s%n", algo,
                                              gridSize, gridInstance, goalCount,
                                              goalSet, m);
                        }
                    }
                }
            }
        }
    }
}
