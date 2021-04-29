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

import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import uk.ac.lancs.dpb.graph.BidiCapacity;
import uk.ac.lancs.dpb.graph.Capacity;
import uk.ac.lancs.dpb.graph.Edge;
import uk.ac.lancs.dpb.graph.QualifiedEdge;

/**
 * Contains utilities for creating graphs with properties suitable for
 * testing.
 *
 * @author simpsons
 */
public final class GraphExamples {
    private GraphExamples() {}

    /**
     * Test whether there are no other vertices in the circumcircle of a
     * triangle.
     * 
     * @param corners the three corners forming a triangle
     * 
     * @param vertexes the set of all vertices to search
     * 
     * @return {@code true} if none of the listed vertices are in the
     * circumcircle of the triangle; {@code false} if a vertex is found
     * within the circumcircle
     */
    private static boolean
        emptyCircumcircle(List<? extends Vertex> corners,
                          Collection<? extends Vertex> vertexes) {
        final Circle circum =
            Circle.circumcircle(corners.get(0), corners.get(1), corners.get(2));
        for (Vertex v : vertexes) {
            /* Exclude vertices that form this triangle. */
            if (corners.contains(v)) continue;

            /* Exclude vertices that are not in the circumcircle. */
            if (!circum.contains(v)) continue;

            /* The triangle is not empty. */
            return false;
        }
        return true;
    }

    /**
     * Create a flat graph. This is created by choosing a set of
     * vertices out of a rectangular area, creating edges between every
     * pair of vertices, and then selectively eliminating them. When two
     * edges cross, the longer is eliminated. When three edges form a
     * triangle, the longest is removed if the sum of the lengths of the
     * other two edges is more than a certain fraction (the
     * <dfn>stretch</dfn>) of the longest edge's length, or if another
     * vertex lies within the circumcircle of the triangle.
     * 
     * @param rng a random number generator
     * 
     * @param width the width of the graph
     * 
     * @param height the height of the graph
     * 
     * @param population the number of vertices to place in the graph
     * 
     * @param stretch a factor to eliminate extremely flat triangles.
     * 0.8 is a decent value.
     * 
     * @param capSupply a supply of capacities for generated edges,
     * based on the cost (length) of the edge and the degree of its
     * defining vertices
     * 
     * @return a random graph with the specified characteristics
     * 
     * @throws IllegalArgumentException if the width or height is
     * negative; if the population exceeds the area; if there are more
     * goals than vertices
     */
    public static Graph
        createFlatChallenge(final Random rng, final int width, final int height,
                            final int population, final double stretch,
                            final CapacitySupply capSupply) {
        if (width < 0)
            throw new IllegalArgumentException("-ve width: " + width);
        if (height < 0)
            throw new IllegalArgumentException("-ve height: " + height);
        if (population > width * height)
            throw new IllegalArgumentException("overcrowded: " + population
                + " > " + width + "\u00d7" + height);

        /* Create vertices dotted around a grid. Choose some of them as
         * goals too. */
        List<Vertex> vertexes = new ArrayList<>();
        int req = population, rem = width * height;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++, rem--) {
                if (rng.nextInt(rem) < req) {
                    /* Create a new vertex. */
                    Vertex v = Vertex.at(x, y);
                    vertexes.add(v);

                    /* Reduce the odds of making subsequent vertices. */
                    req--;
                }
            }
        }

        List<QualifiedEdge<Vertex>> edges = new ArrayList<>();
        /* Create edges between every pair of vertices. */
        final BidiCapacity cap = BidiCapacity.of(Capacity.at(0.0));
        for (int i = 0; i < vertexes.size() - 1; i++) {
            Vertex v0 = vertexes.get(i);
            for (int j = i + 1; j < vertexes.size(); j++) {
                Vertex v1 = vertexes.get(j);
                double cost = Vertex.distance(v0, v1);
                QualifiedEdge<Vertex> e =
                    new QualifiedEdge<>(v0, v1, cap, cost);
                edges.add(e);
            }
        }

        /* Prune longer edges that cross others. */
        outer: for (int i = 0; i < edges.size() - 1; i++) {
            QualifiedEdge<Vertex> e0 = edges.get(i);
            double s0 = Vertex.length(e0);
            for (int j = i + 1; j < edges.size(); j++) {
                QualifiedEdge<Vertex> e1 = edges.get(j);
                if (!Vertex.cross(e0, e1)) continue;
                double s1 = Vertex.length(e1);
                if (s1 >= s0) {
                    edges.remove(j--);
                } else {
                    edges.remove(i--);
                    continue outer;
                }
            }
        }

        /* Find edges that form a triangle. */
        outer0: for (int i0 = 0; i0 < edges.size() - 2; i0++) {
            final QualifiedEdge<Vertex> e0 = edges.get(i0);
            final double s0 = Vertex.length(e0);
            outer1: for (int i1 = i0 + 1; i1 < edges.size() - 1; i1++) {
                final QualifiedEdge<Vertex> e1 = edges.get(i1);
                if (!Vertex.abut(e0, e1)) continue;

                final double s1 = Vertex.length(e1);
                outer2: for (int i2 = i1 + 1; i2 < edges.size(); i2++) {
                    final QualifiedEdge<Vertex> e2 = edges.get(i2);
                    if (!Vertex.abut(e2, e1)) continue;
                    if (!Vertex.abut(e2, e0)) continue;

                    /* Identify the corners and compute the
                     * circumcircle. */
                    final Collection<Vertex> set = new HashSet<>();
                    set.add(e0.start);
                    set.add(e0.finish);
                    set.add(e1.start);
                    set.add(e1.finish);
                    set.add(e2.start);
                    set.add(e2.finish);
                    /* If we don't have exactly 3 corners, we've
                     * probably just picked three edges of the same
                     * vertex. */
                    if (set.size() != 3) continue;

                    /* Now we have a triangle. */
                    final List<Vertex> corners = List.copyOf(set);
                    assert corners.size() == 3;
                    final double s2 = Vertex.length(e2);

                    /* Very flat triangles will have the longest edge
                     * being only slightly shorter than the sum of the
                     * others. Also look for other vertices within the
                     * circumcircle of the triangle. If the triangle is
                     * too flat, or the circumcircle is not empty, the
                     * longest edge should be remove. */
                    if (s2 <= stretch * (s1 + s0) &&
                        s1 <= stretch * (s2 + s0) &&
                        s0 <= stretch * (s2 + s1) &&
                        emptyCircumcircle(corners, vertexes)) continue;
                    /* This triangle is very squished, or its
                     * circumcircle contains another vertex. Remove the
                     * longest edge. */

                    if (s2 > s1 && s2 > s0) {
                        edges.remove(i2--);
                        continue;
                    }

                    if (s1 > s2 && s1 > s0) {
                        edges.remove(i1--);
                        continue outer1;
                    }

                    edges.remove(i0--);
                    continue outer0;
                }
            }
        }

        /* Determine the degree of each vertex. */
        Map<Vertex, Integer> degrees = new IdentityHashMap<>();
        for (var edge : edges) {
            degrees.merge(edge.start, 1, (v0, v1) -> v0 + v1);
            degrees.merge(edge.finish, 1, (v0, v1) -> v0 + v1);
        }

        /* Recreate the remaining edges, but with capacities computed
         * from their costs and their vertex degrees. */
        Collection<QualifiedEdge<Vertex>> cappedEdges = edges.stream()
            .map(e -> new QualifiedEdge<>(e.start, e.finish, capSupply
                .getCapacity(e.cost, degrees.get(e.start),
                             degrees.get(e.finish)), e.cost))
            .collect(Collectors.toSet());

        return new Graph(width, height, cappedEdges);
    }

    /**
     * Select a random set of elements from a list.
     * 
     * @param <E> the element type
     * 
     * @param rng a random number generator
     * 
     * @param amount the maximum number of elements to select
     * 
     * @param from the element source
     * 
     * @return the selected elements
     */
    private static <E> Collection<E> select(Random rng, int amount,
                                            List<? extends E> from) {
        Collection<E> result =
            Collections.newSetFromMap(new IdentityHashMap<>());
        if (!from.isEmpty()) for (int i = 0; i < amount; i++)
            result.add(from.get(rng
                .nextInt(rng.nextInt(rng.nextInt(from.size()) + 1) + 1)));
        return result;
    }

    /**
     * Create an elastically positioned scale-free graph.
     * 
     * @param rng a random number generator for choosing
     * 
     * @param vertexCount the number of vertices to create
     * 
     * @param connectivity the maximum number of edges to form from each
     * new vertex
     * 
     * @param caps capacities to be applied to each resultant edge
     * 
     * @param display a display to be notified of each simulation
     * advance
     * 
     * @return the requested graph
     */
    public static Graph
        createElasticScaleFreeGraph(Random rng, int vertexCount,
                                    int connectivity, CapacitySupply caps,
                                    TopologyDisplay<Vertex> display) {
        Collection<Edge<Vertex>> edges = new HashSet<>();
        List<Vertex> vertexes = new ArrayList<>(vertexCount);
        for (int i = 0; i < vertexCount; i++) {
            Vertex nv = Vertex.at(i, 0.0);
            for (Vertex av : select(rng, connectivity, vertexes)) {
                Edge<Vertex> e = new Edge<>(nv, av);
                edges.add(e);
            }
            vertexes.add(nv);
        }
        GraphMorpher morpher = new GraphMorpher(edges, display);
        for (;;)
            if (!morpher.advance()) break;
        return morpher.freeze((int) (Math.sqrt(vertexCount) * 2.0), caps);
    }

    /**
     * @undocumented
     */
    public static void main(String[] args) throws Exception {
        /* Create a model that can be displayed and updated. */
        SwingTopologyModelDisplay topoModel = new SwingTopologyModelDisplay();

        /* Create a Swing window that displays the model. */
        SwingUtilities.invokeLater(() -> {
            GraphicsDevice device = GraphicsEnvironment
                .getLocalGraphicsEnvironment().getScreenDevices()[0];
            JFrame frame = new JFrame();
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setUndecorated(true);
            device.setFullScreenWindow(frame);
            TopologyPanel panel = new TopologyPanel(topoModel);
            topoModel.setComponent(panel);
            frame.setContentPane(panel);
            panel.setPreferredSize(new Dimension(800, 800));
            frame.validate();
            frame.pack();
            frame.setVisible(true);
        });

        Graph g = GraphExamples
            .createElasticScaleFreeGraph(new Random(1), 10, 2,
                                         (cost, startDegree,
                                          finishDegree) -> BidiCapacity
                                              .of(Capacity.at(1.0)),
                                         topoModel);
        try (PrintWriter out =
            new PrintWriter(new File("scratch/scale-free-graph.svg"))) {
            System.err.printf("Writing...%n");
            g.drawSVG(out, null, null, 0.2, 0.3);
        }
        System.exit(0);
    }
}
