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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import uk.ac.lancs.dpb.graph.BidiCapacity;
import uk.ac.lancs.dpb.graph.Capacity;
import uk.ac.lancs.dpb.graph.Edge;

/**
 * Contains utilities for creating graphs with properties suitable for
 * testing.
 *
 * @author simpsons
 */
public final class GraphExamples {
    private GraphExamples() {}

    /**
     * Create a flat graph. This is created by choosing a set of
     * vertices out of a rectangular area, creating edges between every
     * pair of vertices, and then selectively eliminating them. When two
     * edges cross, the longer is eliminated. When three edges form a
     * triangle, the longest is removed if the sum of the lengths of the
     * other two edges is more than a certain fraction (the
     * <defn>stretch</defn>) of the longest edge's length, or if another
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
     * @param goalCount the number of vertices to select as goals
     * 
     * @param stretch a factor to eliminate extremely flat triangles.
     * 0.8 is a decent value.
     * 
     * @return a random graph with the specified characteristics
     * 
     * @throws IllegalArgumentException if the width or height is
     * negative; if the population exceeds the area; if there are more
     * goals than vertices
     */
    public static Challenge
        createFlatChallenge(final Random rng, final int width, final int height,
                            final int population, final int goalCount,
                            final double stretch) {
        if (width < 0)
            throw new IllegalArgumentException("-ve width: " + width);
        if (height < 0)
            throw new IllegalArgumentException("-ve height: " + height);
        if (population > width * height)
            throw new IllegalArgumentException("overcrowded: " + population
                + " > " + width + "\u00d7" + height);
        if (goalCount > population)
            throw new IllegalArgumentException("more goals than vertices: "
                + goalCount + " > " + population);
        /* Create vertices dotted around a grid. Choose some of them as
         * goals too. */
        List<Vertex> vertexes = new ArrayList<>();
        List<Vertex> goals = new ArrayList<>();
        int req = population, rem = width * height;
        int greq = goalCount;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++, rem--) {
                if (rng.nextInt(rem) < req) {
                    /* Create a new vertex. */
                    Vertex v = Vertex.at(x, y);
                    vertexes.add(v);

                    /* Decide whether to make this a goal. */
                    if (rng.nextInt(req) < greq) {
                        goals.add(v);
                        greq--;
                    }

                    /* Reduce the odds of making subsequent vertices. */
                    req--;
                }
            }
        }

        List<Edge<Vertex>> edges = new ArrayList<>();
        /* Create edges between every pair of vertices. */
        for (int i = 0; i < vertexes.size() - 1; i++) {
            Vertex v0 = vertexes.get(i);
            for (int j = i + 1; j < vertexes.size(); j++) {
                Vertex v1 = vertexes.get(j);
                double cost = Vertex.distance(v0, v1);
                BidiCapacity cap =
                    BidiCapacity.of(Capacity.at(2.0 + rng.nextDouble() * 8.0),
                                    Capacity.at(2.0 + rng.nextDouble() * 8.0));
                Edge<Vertex> e = new Edge<>(v0, v1, cap, cost);
                edges.add(e);
            }
        }

        /* Prune longer edges that cross others. */
        outer: for (int i = 0; i < edges.size() - 1; i++) {
            Edge<Vertex> e0 = edges.get(i);
            double s0 = Vertex.length(e0);
            for (int j = i + 1; j < edges.size(); j++) {
                Edge<Vertex> e1 = edges.get(j);
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
            final Edge<Vertex> e0 = edges.get(i0);
            final double s0 = Vertex.length(e0);
            outer1: for (int i1 = i0 + 1; i1 < edges.size() - 1; i1++) {
                final Edge<Vertex> e1 = edges.get(i1);
                if (!Vertex.abut(e0, e1)) continue;

                final double s1 = Vertex.length(e1);
                outer2: for (int i2 = i1 + 1; i2 < edges.size(); i2++) {
                    final Edge<Vertex> e2 = edges.get(i2);
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
                     * others. Remove the longest edge in these
                     * cases. */
                    if (s2 > stretch * (s1 + s0) || s1 > stretch * (s2 + s0) ||
                        s0 > stretch * (s2 + s1)) {
                        /* This triangle is very squished. Remove the
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

                    /* See if there's a vertex within the
                     * circumcircle. */
                    final Circle circum =
                        Circle.circumcircle(corners.get(0), corners.get(1),
                                            corners.get(2));
                    for (Vertex v : vertexes) {
                        /* Exclude vertices that form this triangle. */
                        if (corners.contains(v)) continue;

                        /* Exclude vertices that are not in the
                         * circumcircle. */
                        if (!circum.contains(v)) continue;

                        /* Remove the longest edge. */
                        if (s2 > s1 && s2 > s0) {
                            edges.remove(i2--);
                            continue outer2;
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
        }

        return new Challenge(width, height, goals, edges);
    }
}