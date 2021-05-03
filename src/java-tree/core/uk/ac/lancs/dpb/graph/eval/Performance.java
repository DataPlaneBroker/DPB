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
import java.util.HashMap;
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
    public static void main(String[] args) throws Exception {
        /* algo, grid size, grid number, goal count, challenge number */
        Map<String,
            Map<Integer,
                Map<Integer, Map<Integer, Map<Integer, Double>>>>> elapsed =
                    new HashMap<>();
        Map<String,
            Map<Integer,
                Map<Integer, Map<Integer, Map<Integer, Double>>>>> fit =
                    new HashMap<>();

        Random rng = new Random(1);

        Map<String, TreePlotter> algos = new LinkedHashMap<>();

        /* The first algorithm must be exhaustive, as it gives us ground
         * truth. */
        algos
            .put("exhaustive",
                 new ComprehensiveTreePlotter(ComprehensiveTreePlotter.ALL_EDGE_MODES));

        algos.put("99", new ComprehensiveTreePlotter(ComprehensiveTreePlotter
            .biasThreshold(0.99)));

        /* Vary the graph dimensions. Do this in the outermost loop
         * because some of these are quite costly. */
        for (int vertexCount = 50; vertexCount <= 50; vertexCount += 50) {
            /* Generate several graphs of this size. */
            for (int graphIter = 0; graphIter < 10; graphIter++) {
                System.out.printf("%nVertices: %d; run %d%n", vertexCount,
                                  graphIter);
                Graph graph = GraphExamples
                    .createElasticScaleFreeGraph(rng, vertexCount, 3, 3,
                                                 (cost, startDegree,
                                                  finishDegree) -> BidiCapacity
                                                      .of(Capacity
                                                          .at(startDegree
                                                              + finishDegree)),
                                                 null);
                try (PrintWriter out = new PrintWriter(new File(String
                    .format("scratch/graph-%d-%d.svg", vertexCount,
                            graphIter)))) {
                    graph.drawSVG(out, null, null, 0.2, 0.3);
                }

                /* Vary the number of goals. */
                for (int goalCountIter = 3; goalCountIter <= 5;
                     goalCountIter++) {
                    final int goalCount = goalCountIter;
                    /* Run several challenges on this graph. */
                    for (int chalIter = 0; chalIter < 10; chalIter++) {
                        List<Vertex> goals = graph.chooseGoals(goalCount, rng);
                        assert goals.size() == goalCount;

                        try (PrintWriter out = new PrintWriter(new File(String
                            .format("scratch/challenge-%d-%d-%d-%d.svg",
                                    vertexCount, graphIter, goalCount,
                                    chalIter)))) {
                            graph.drawSVG(out, goals, null, 0.2, 0.3);
                        }

                        if (true) {
                            /* Allow each goal to send the same amount,
                             * and receive the sum of the others. */
                            DemandFunction demand =
                                new PairDemandFunction(IntStream
                                    .range(0, goals.size())
                                    .mapToObj(i -> BidiCapacity
                                        .of(Capacity.at(1.0),
                                            Capacity.at(1.0 / (goalCount - 1))))
                                    .collect(Collectors.toList()));

                            /* Measure the performance and accuracy of
                             * each algorithm. */
                            for (var namedAlgo : algos.entrySet()) {
                                String name = namedAlgo.getKey();
                                TreePlotter algo = namedAlgo.getValue();

                                System.err.printf("Algo: %s%n", name);
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
                                            score +=
                                                key.cost * (val.ingress.min()
                                                    + val.egress.min());
                                        }
                                        if (best == null || score < bestScore) {
                                            best = cand;
                                            bestScore = score;
                                            System.err
                                                .printf("acc %g: %s%n",
                                                        bestScore,
                                                        best.entrySet().stream()
                                                            .map(e -> e.getKey()
                                                                .toString())
                                                            .collect(Collectors
                                                                .joining(", ")));
                                        }
                                    }

                                    /* Compute the duration. If it's not
                                     * yet added up to a second, tryin
                                     * again. */
                                    final long stop =
                                        System.currentTimeMillis();
                                    if (stop - start < 1000) continue;

                                    /* Record the average duration, and
                                     * stop. */
                                    duration = (stop - start) / 1000.0 / cycles;
                                    break;
                                }

                                /* algo, vertexCount, graphIter,
                                 * goalCount, chalter */
                                elapsed
                                    .computeIfAbsent(name, k -> new TreeMap<>())
                                    .computeIfAbsent(vertexCount,
                                                     k -> new TreeMap<>())
                                    .computeIfAbsent(graphIter,
                                                     k -> new TreeMap<>())
                                    .computeIfAbsent(goalCount,
                                                     k -> new TreeMap<>())
                                    .put(chalIter, duration);

                                File svg = new File(String
                                    .format("scratch/solution-%d-%d-%d-%d-%s.svg",
                                            vertexCount, graphIter, goalCount,
                                            chalIter, name));
                                if (best != null) {
                                    fit.computeIfAbsent(name,
                                                        k -> new TreeMap<>())
                                        .computeIfAbsent(vertexCount,
                                                         k -> new TreeMap<>())
                                        .computeIfAbsent(graphIter,
                                                         k -> new TreeMap<>())
                                        .computeIfAbsent(goalCount,
                                                         k -> new TreeMap<>())
                                        .put(chalIter, bestScore);
                                    try (PrintWriter out =
                                        new PrintWriter(svg)) {
                                        graph.drawSVG(out, goals, best, 0.2,
                                                      0.3);
                                    }
                                    System.err.printf("%d vertices; graph %d;"
                                        + " %d goals;" + " goalset %d;" + " %s"
                                        + " took %g s to find" + " best %g%n",
                                                      vertexCount, graphIter,
                                                      goalCount, chalIter, name,
                                                      duration, bestScore);
                                } else {
                                    svg.delete();
                                    System.err.printf("%d vertices; graph %d;"
                                        + " %d goals;" + " goalset %d;" + " %s"
                                        + " took %g s to find"
                                        + " no solution%n", vertexCount,
                                                      graphIter, goalCount,
                                                      chalIter, name, duration);
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.printf("Elapsed:%n%s%n", elapsed);
        System.out.printf("Scores:%n%s%n", fit);
    }
}
