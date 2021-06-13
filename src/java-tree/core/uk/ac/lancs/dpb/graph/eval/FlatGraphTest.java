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
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import uk.ac.lancs.dpb.graph.BidiCapacity;
import uk.ac.lancs.dpb.graph.Capacity;
import uk.ac.lancs.dpb.graph.DemandFunction;
import uk.ac.lancs.dpb.graph.FlatDemandFunction;
import uk.ac.lancs.dpb.graph.MatrixDemandFunction;
import uk.ac.lancs.dpb.graph.PairDemandFunction;
import uk.ac.lancs.dpb.graph.QualifiedEdge;
import uk.ac.lancs.dpb.tree.TreePlotter;
import uk.ac.lancs.dpb.tree.mixed_radix.MixedRadixTreePlotter;

/**
 * Runs a tree-plotting algorithm on a simple flat graph.
 * 
 * @author simpsons
 */
public class FlatGraphTest {
    private static double minSum(Map<? extends QualifiedEdge<Vertex>,
                                     ? extends BidiCapacity> cand) {
        return cand.entrySet().parallelStream().mapToDouble(entry -> {
            QualifiedEdge<Vertex> key = entry.getKey();
            BidiCapacity val = entry.getValue();
            return key.cost * (val.ingress.min() + val.egress.min());
        }).sum();
    }

    private static double postScaledMinSum(Map<? extends QualifiedEdge<Vertex>,
                                               ? extends BidiCapacity> cand) {
        return cand.entrySet().parallelStream().mapToDouble(entry -> {
            QualifiedEdge<Vertex> key = entry.getKey();
            BidiCapacity val = entry.getValue();
            double edgeScore = val.ingress.min() + val.egress.min();
            edgeScore /= val.egress.max() / key.capacity.egress.max();
            edgeScore /= val.ingress.max() / key.capacity.ingress.max();
            return key.cost * edgeScore;
        }).sum();
    }

    private static double preScaledMinSum(Map<? extends QualifiedEdge<Vertex>,
                                              ? extends BidiCapacity> cand) {
        return cand.entrySet().parallelStream().mapToDouble(entry -> {
            QualifiedEdge<Vertex> key = entry.getKey();
            BidiCapacity val = entry.getValue();
            double edgeScore = val.ingress.min() * val.ingress.max()
                / key.capacity.ingress.max();
            edgeScore +=
                val.egress.min() * val.egress.max() / key.capacity.egress.max();
            return key.cost * edgeScore;
        }).sum();
    }

    /**
     * 
     */
    public static void main(String[] args) throws Exception {
        final int width = 20;
        final int height = 15;
        final int population = width * height * 8 / 100;
        final int goalCount = 6;
        final double goalRadius = 0.3;
        final double vertexRadius = 0.2;
        final double stretch = 0.8;

        Random rng = new Random(3);
        CapacitySupply capSupply =
            (cost, startDegree, finishDegree, maxDegree,
             maxCost) -> BidiCapacity
                 .of(Capacity.at(2.0 + rng.nextDouble() * 8.0),
                     Capacity.at(2.0 + rng.nextDouble() * 8.0));
        Graph graph =
            GraphExamples.createFlatChallenge(rng, width, height, population,
                                              stretch, capSupply);
        List<Vertex> goals = graph.chooseGoals(goalCount, new Random());

        /* Choose a tree. */
        final Map<QualifiedEdge<Vertex>, ? extends BidiCapacity> tree;
        TreePlotter plotter = new MixedRadixTreePlotter(MixedRadixTreePlotter
            .biasThreshold(0.99999999));
        final DemandFunction bwreq;
        if (false) {
            bwreq = new PairDemandFunction(IntStream.range(0, goals.size())
                .mapToObj(i -> BidiCapacity.of(Capacity.at(4.0),
                                               Capacity.at(0.5)))
                .collect(Collectors.toList()));
        } else if (true) {
            bwreq =
                MatrixDemandFunction.forTree(goals.size(), 0,
                                             BidiCapacity.of(Capacity.at(1.0),
                                                             Capacity.at(0.01)),
                                             null);
        } else {
            bwreq = new FlatDemandFunction(goals.size(), Capacity.at(3.0));
        }

        for (int m = 1; m < (1 << bwreq.degree()) - 1; m++) {
            BitSet bs = BitSet.valueOf(new long[] { m });
            BidiCapacity bw = bwreq.getPair(bs);
            System.out.printf("%2d %12s %s%n", m, bs, bw);
        }
        Map<? extends QualifiedEdge<Vertex>, ? extends BidiCapacity> best =
            null;
        double bestScore = Double.MAX_VALUE;
        assert bwreq.degree() == goals.size();
        for (var cand : plotter.plot(goals, bwreq, graph.edges)) {
            double score = 0.0;
            for (var entry : cand.entrySet()) {
                QualifiedEdge<Vertex> key = entry.getKey();
                BidiCapacity val = entry.getValue().getValue();
                score += key.cost * (val.ingress.min() + val.egress.min());
            }
            if (best == null || score < bestScore) {
                best = TreePlotter.pullCapacities(cand);
                bestScore = score;
                if (false)
                    System.err.printf("acc %g: %s%n", bestScore,
                                      best.entrySet().stream()
                                          .map(e -> e.getKey().toString())
                                          .collect(Collectors.joining(", ")));
            }
        }
        tree = best == null ? Collections.emptyMap() : Map.copyOf(best);

        /* Draw out the result. */
        try (PrintWriter out =
            new PrintWriter(new File("scratch/treeplot.svg"))) {
            graph.drawSVG(out, goals, tree, vertexRadius, goalRadius);
        }
    }
}
