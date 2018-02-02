import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import uk.ac.lancs.routing.span.DistanceVectorGraph;
import uk.ac.lancs.routing.span.Edge;
import uk.ac.lancs.routing.span.Spans;

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

/**
 * 
 * 
 * @author simpsons
 */
public class TestGeographicSpan {
    private static class Vertex {
        public final double x, y;

        @Override
        public String toString() {
            return "(" + x + ", " + y + ")";
        }

        public Vertex(double x, double y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            long temp;
            temp = Double.doubleToLongBits(x);
            result = prime * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(y);
            result = prime * result + (int) (temp ^ (temp >>> 32));
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            Vertex other = (Vertex) obj;
            if (Double.doubleToLongBits(x) != Double
                .doubleToLongBits(other.x)) return false;
            if (Double.doubleToLongBits(y) != Double
                .doubleToLongBits(other.y)) return false;
            return true;
        }

        public Vertex minus(Vertex other) {
            return new Vertex(x - other.x, y - other.y);
        }

        @SuppressWarnings("unused")
        public Vertex plus(Vertex other) {
            return new Vertex(x + other.x, y + other.y);
        }

        @SuppressWarnings("unused")
        public double distanceToOrigin() {
            return Math.hypot(x, y);
        }
    }

    private static double distance(Vertex a, Vertex b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    private static class Circle extends Vertex {
        public final double radius;

        public Circle(double x, double y, double radius) {
            super(x, y);
            this.radius = radius;
        }

        public boolean contains(Vertex other) {
            return distance(this, other) < radius;
        }

        @Override
        public String toString() {
            return super.toString() + radius;
        }
    }

    private static Circle circumcircle(Vertex a, Vertex b, Vertex c) {
        Vertex bp = b.minus(a);
        Vertex cp = c.minus(a);
        final double bp2 = bp.x * bp.x + bp.y * bp.y;
        final double cp2 = cp.x * cp.x + cp.y * cp.y;
        final double dp = 2.0 * (bp.x * cp.y - bp.y * cp.x);
        final double upx = (cp.y * bp2 - bp.y * cp2) / dp;
        final double upy = (bp.x * cp2 - cp.x * bp2) / dp;
        final double r = Math.hypot(upx, upy);
        return new Circle(upx + a.x, upy + a.y, r);
    }

    private static double length(Edge<? extends Vertex> edge) {
        return distance(edge.first(), edge.second());
    }

    private static boolean edgesCross(Edge<? extends Vertex> e1,
                                      Edge<? extends Vertex> e2) {
        final double epsilon = 0.01;
        final Vertex v1 = e1.first();
        final Vertex v2 = e1.second();
        final Vertex v3 = e2.first();
        final Vertex v4 = e2.second();
        final double deter =
            (v1.x - v2.x) * (v3.y - v4.y) - (v1.y - v2.y) * (v3.x - v4.x);
        if (deter < epsilon) return false;
        final double x1y2my1x2 = v1.x * v2.y - v1.y * v2.x;
        final double x3y4my3x4 = v3.x * v4.y - v3.y * v4.x;
        final double x3mx4 = v3.x - v4.x;
        final double x1mx2 = v1.x - v2.x;
        final double y3my4 = v3.y - v4.y;
        final double y1my2 = v1.y - v2.y;
        final double px = (x1y2my1x2 * x3mx4 - x1mx2 * x3y4my3x4) / deter;
        final double py = (x1y2my1x2 * y3my4 - y1my2 * x3y4my3x4) / deter;
        if (px < v1.x && px < v2.x) return false;
        if (px < v3.x && px < v4.x) return false;
        if (px > v1.x && px > v2.x) return false;
        if (px > v3.x && px > v4.x) return false;
        if (py < v1.y && py < v2.y) return false;
        if (py < v3.y && py < v4.y) return false;
        if (py > v1.y && py > v2.y) return false;
        if (py > v3.y && py > v4.y) return false;
        return true;
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        Random rng = new Random();

        /* Create a random set of vertices. */
        Collection<Vertex> vertices = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            Vertex v =
                new Vertex(rng.nextInt(10) * 10.0, rng.nextInt(10) * 10.0);
            vertices.add(v);
        }

        /* Create edges between every pair of vertices. */
        Collection<Edge<Vertex>> edges = new HashSet<>();
        for (Vertex v1 : vertices) {
            for (Vertex v2 : vertices) {
                if (v1.equals(v2)) continue;
                edges.add(Edge.of(v1, v2));
            }
        }

        /* Identify every distinct pair of edges with distinct vertices.
         * If they cross, eliminate the longer one. */
        for (Vertex v1 : vertices) {
            next_first_edge:
            for (Vertex v2 : vertices) {
                if (v2.equals(v1)) continue;
                Edge<Vertex> v12 = Edge.of(v1, v2);
                if (!edges.contains(v12)) continue;
                for (Vertex v3 : vertices) {
                    if (v12.contains(v3)) continue;
                    for (Vertex v4 : vertices) {
                        if (v4.equals(v3)) continue;
                        if (v12.contains(v4)) continue;
                        Edge<Vertex> v34 = Edge.of(v3, v4);
                        if (!edges.contains(v34)) continue;

                        if (!edgesCross(v12, v34)) continue;
                        if (length(v12) > length(v34)) {
                            edges.remove(v12);
                            continue next_first_edge;
                        } else {
                            edges.remove(v34);
                        }
                    }
                }
            }
        }
        if (false) {
            /* For every three vertices, check to see if any other
             * vertex lies in the circumcircle. If there is one, remove
             * the longest edge. */
            for (Vertex v1 : vertices) {
                for (Vertex v2 : vertices) {
                    if (v2.equals(v1)) continue;
                    Edge<Vertex> v12 = Edge.of(v1, v2);
                    if (!edges.contains(v12)) continue;
                    for (Vertex v3 : vertices) {
                        if (v3.equals(v1)) continue;
                        if (v3.equals(v2)) continue;
                        Edge<Vertex> v23 = Edge.of(v2, v3);
                        Edge<Vertex> v31 = Edge.of(v1, v3);
                        if (!edges.contains(v23)) continue;
                        if (!edges.contains(v31)) continue;
                        /* v1, v2, v3 now form a triangle whose edges
                         * are still present. */
                        Circle circumcircle = circumcircle(v1, v2, v3);

                        for (Vertex v4 : vertices) {
                            if (v1.equals(v4)) continue;
                            if (v2.equals(v4)) continue;
                            if (v3.equals(v4)) continue;
                            if (!circumcircle.contains(v4)) continue;

                            /* The fourth vertex is inside the triangle.
                             * Choose the longest edge and remove it. */
                            List<Edge<Vertex>> cands =
                                Arrays.asList(v12, v23, v31);
                            Collections.sort(cands, (a, b) -> Double
                                .compare(length(b), length(a)));
                            edges.remove(cands.get(0));
                            break;
                        }
                    }
                }
            }
        }

        /* Randomly select some vertices as terminals. */
        final int toBeSelected = 2 + rng.nextInt(4);
        Collection<Vertex> terminals = new HashSet<>();
        List<Vertex> remaining = new ArrayList<>(vertices);
        for (int i = 0; i < toBeSelected; i++) {
            int index = rng.nextInt(remaining.size());
            terminals.add(remaining.remove(index));
        }

        /* Create routing tables on all nodes for all terminals. */
        DistanceVectorGraph<Vertex> dv = new DistanceVectorGraph<>();
        dv.addTerminals(terminals);
        dv.addEdges(edges.stream()
            .collect(Collectors.toMap(e -> e, e -> length(e))));
        dv.update();

        /* Compute terminal-aware weights for all edges, and use to pick
         * a spanning tree. */
        Map<Edge<Vertex>, Double> spanWeights =
            Spans.flatten(dv.getFIBs(), Edge::of);
        Collection<Edge<Vertex>> span = Spans.span(terminals, spanWeights);

        try (PrintWriter out = new PrintWriter(new File("scratch/geo.svg"))) {
            out.println("<?xml version=\"1.0\" " + "standalone=\"no\"?>\n");
            out.println("<!DOCTYPE svg PUBLIC");
            out.println(" \"-//W3C//DTD SVG 20000303 Stylable//EN\"");
            out.println(" \"http://www.w3.org/TR/2000/03/"
                + "WD-SVG-20000303/DTD/svg-20000303-stylable.dtd\">");
            out.println("<svg xmlns=\"http://www.w3.org/2000/svg\"");
            out.println(" xmlns:xlink=\"http://www.w3.org/1999/xlink\"");
            out.println(" viewBox='-10 -10 110 110'>");
            out.println("<rect style='fill: white; "
                + "stroke: black; stroke-width: 0.5'");
            out.println(" x='-10' y='-10' width='110' height='110' />");
            out.println("<g style='fill: none;"
                + " stroke: rgb(200,200,200); stroke-width: 0.5'>");
            for (Edge<Vertex> edge : edges) {
                out.printf("<line x1='%g' y1='%g' x2='%g' y2='%g' />%n",
                           edge.first().x, edge.first().y, edge.second().x,
                           edge.second().y);
            }
            out.println("</g>");
            out.println("<g style='fill: none;"
                + " stroke: rgb(200,0,0); stroke-width: 1.0'>");
            for (Edge<Vertex> edge : span) {
                out.printf("<line x1='%g' y1='%g' x2='%g' y2='%g' />%n",
                           edge.first().x, edge.first().y, edge.second().x,
                           edge.second().y);
            }
            out.println("</g>");
            out.println("<g style='fill: red; stroke: none'>");
            for (Vertex v : terminals) {
                out.printf("<circle cx='%g' cy='%g' r='%g' />%n", v.x, v.y,
                           2.5);
            }
            out.println("</g>");
            out.println("<g style='fill: black; stroke: none'>");
            for (Vertex v : vertices) {
                out.printf("<circle cx='%g' cy='%g' r='%g' />%n", v.x, v.y,
                           2.0);
            }
            out.println("</g>");
            out.println("</svg>");
        }
    }
}
