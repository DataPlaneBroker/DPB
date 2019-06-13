
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

import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import uk.ac.lancs.routing.span.Edge;

/**
 * 
 * 
 * @author simpsons
 */
public class AlgoPerfTest {
    private static class Vertex {
        public final int id;

        Vertex() {
            id = nextId++;
            x = 0.3 * id * Math.cos(id);
            y = 0.3 * id * Math.sin(id);
        }

        public double x, y;

        /**
         * Velocity
         */
        public double vx = 0.0, vy = 0.0;

        public double fx = 0.0, fy = 0.0;

        void resetForce() {
            fx = 0.0;
            fy = 0.0;
        }

        double mass = 1.0;

        private static int nextId;
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        final MyTopologyModel topModel = new MyTopologyModel();
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame();
            TopologyPanel panel = new TopologyPanel(topModel);
            topModel.setComponent(panel);
            frame.setContentPane(panel);
            panel.setPreferredSize(new Dimension(300, 300));
            frame.validate();
            frame.pack();
            frame.setVisible(true);
        });

        /* Create a scale-free network. */
        final int vertexCount = 100;
        final int newEdgesPerVertex = 2;
        Collection<Edge<Vertex>> edges = new HashSet<>();
        {
            final Random rng = new Random(20);

            /* Remember which other vertices an vertex joins. */
            Map<Vertex, Collection<Vertex>> edgeSets = new HashMap<>();

            /* Create a starting point. */
            Vertex v0 = new Vertex();
            Vertex v1 = new Vertex();
            edgeSets.put(v0, new HashSet<>());
            edgeSets.put(v1, new HashSet<>());
            edgeSets.get(v0).add(v1);
            edgeSets.get(v1).add(v0);
            int edgeCount = 2;

            /* Add more vertices, and link to a random existing one each
             * time. */
            for (int i = 2; i < vertexCount; i++) {
                Vertex latest = new Vertex();

                /* Identify vertices to link to. */
                Collection<Vertex> chosen = new HashSet<>();
                for (int j = 0; j < newEdgesPerVertex; j++) {
                    int chosenIndex = rng.nextInt(edgeCount);
                    for (Map.Entry<Vertex, Collection<Vertex>> entry : edgeSets
                        .entrySet()) {
                        if (chosenIndex < entry.getValue().size()) {
                            chosen.add(entry.getKey());
                            break;
                        }
                        chosenIndex -= entry.getValue().size();
                    }
                }

                /* Link to those vertices. */
                edgeSets.put(latest, new HashSet<>());
                for (Vertex existing : chosen) {
                    chosen.add(existing);
                    edgeSets.get(latest).add(existing);
                    edgeSets.get(existing).add(latest);
                    edgeCount += 2;
                }
            }

            /* Convert to a set of edges. */
            for (Map.Entry<Vertex, Collection<Vertex>> entry : edgeSets
                .entrySet()) {
                Vertex a = entry.getKey();
                a.mass = entry.getValue().size();
                for (Vertex b : entry.getValue())
                    edges.add(Edge.of(a, b));
            }

            topModel.setData(edges);

            /* Position them. */
            for (Vertex v : edgeSets.keySet()) {
                System.out.printf("  %d: (%g, %g)%n", v.id, v.x, v.y);
            }
            final double maxSpeed = 0.01;
            double elapsed = 0.0;
            for (;;) {
                /* Compute forces given current positions and edges. */
                edgeSets.keySet().forEach(Vertex::resetForce);

                /* Apply an elastic force to each edge. */
                final double optLength = 2.0;
                final double spring = 0.1;
                for (Edge<Vertex> entry : edges) {
                    final Vertex a = entry.first();
                    final Vertex b = entry.second();
                    final double dx = b.x - a.x;
                    final double dy = b.y - a.y;
                    final double r2 = dx * dx + dy * dy;
                    final double dist = Math.sqrt(r2);
                    final double sinth = dy / dist;
                    final double costh = dx / dist;
                    final double force = (dist - optLength) * spring;
                    final double bfx = force * costh;
                    final double bfy = force * sinth;
                    a.fx += bfx;
                    a.fy += bfy;
                    b.fx -= bfx;
                    b.fy -= bfy;
                }

                /* TODO: Make all vertices repel each other. */
                Vertex[] arr = new Vertex[edgeSets.keySet().size()];
                arr = edgeSets.keySet().toArray(arr);
                final double push = 0.3;
                for (int i = 0; i < arr.length - 1; i++) {
                    for (int j = i + 1; j < arr.length; j++) {
                        final Vertex a = arr[i];
                        final Vertex b = arr[j];
                        final double dx = b.x - a.x;
                        final double dy = b.y - a.y;
                        final double r2 = dx * dx + dy * dy;
                        final double bfx = push * dx / r2;
                        final double bfy = push * dy / r2;
                        a.fx -= bfx;
                        a.fy -= bfy;
                        b.fx += bfx;
                        b.fy += bfy;
                    }
                }

                /* Compute air resistance. */
                edgeSets.keySet().forEach(v -> {
                    final double sp2 = v.vx * v.vx + v.vy * v.vy;
                    final double sp = Math.sqrt(sp2);
                    v.fx -= v.vx * sp * 0.5;
                    v.fy -= v.vy * sp * 0.5;
                });

                /* Convert the forces to accelerations. */
                edgeSets.keySet().forEach(v -> {
                    v.fx /= v.mass;
                    v.fy /= v.mass;
                });

                for (Vertex v : edgeSets.keySet()) {
                    System.out.printf("  %d: delta-V (%g, %g)%n", v.id, v.vx,
                                      v.vy);
                }

                /* Find the largest time jump we can afford. */
                double bestDelta = 0.1;
                for (Vertex v : edgeSets.keySet()) {
                    {
                        /* Check acceleration. */
                        final double acc2 = v.fx * v.fx + v.fy * v.fy;
                        final double acc = Math.sqrt(acc2);
                        final double delta = maxSpeed / acc;
                        if (delta < bestDelta) bestDelta = delta;
                    }

                    {
                        /* Check velocity. */
                        final double sp2 = v.vx * v.vx + v.vy * v.vy;
                        final double sp = Math.sqrt(sp2);
                        final double delta = maxSpeed / sp;
                        if (delta < bestDelta) bestDelta = delta;
                    }
                }
                final double delta =
                    (elapsed + bestDelta > elapsed) ? bestDelta : 0.0001;

                /* Apply all accelerations and velocities. */
                edgeSets.keySet().forEach(v -> {
                    v.vx += v.fx * delta;
                    v.vy += v.fy * delta;
                    v.x += v.vx * delta;
                    v.y += v.vy * delta;
                });
                elapsed += delta;

                /* Recentre everything. */
                double xmin = Double.MAX_VALUE, xmax = -Double.MAX_VALUE;
                double ymin = Double.MAX_VALUE, ymax = -Double.MAX_VALUE;
                for (Vertex v : edgeSets.keySet()) {
                    if (v.x > xmax) xmax = v.x;
                    if (v.x < xmin) xmin = v.x;
                    if (v.y > ymax) ymax = v.y;
                    if (v.y < ymin) ymin = v.y;
                }
                final double xshift = xmin + (xmax - xmin) / 2.0;
                final double yshift = ymin + (ymax - ymin) / 2.0;
                for (Vertex v : edgeSets.keySet()) {
                    v.x -= xshift;
                    v.y -= yshift;
                }

                System.out.printf("%nElapsed: %gs (by %gs)%n", elapsed,
                                  delta);
                for (Vertex v : edgeSets.keySet()) {
                    System.out.printf("  %d: (%g, %g)%n", v.id, v.x, v.y);
                }
                topModel.setData(edges);
            }
        }
    }

    @SuppressWarnings("unused")
    private static double positiveQuadraticSolution(double a, double b,
                                                    double c) {
        final double disc = b * b - 4 * a * c;
        final double sqrt = Math.sqrt(disc);
        final double s1 = (-b - sqrt) / 2 / a;
        final double s2 = (-b + sqrt) / 2 / a;
        if (Double.isNaN(s1) || s1 < 0.0) {
            if (Double.isNaN(s2) || s2 < 0.0) return Double.NaN;
            // throw new IllegalArgumentException("a=" + a + "; b=" + b
            // + "; c=" + c);
            return s2;
        } else if (Double.isNaN(s2) || s2 < 0.0) {
            return s1;
        } else {
            return Math.max(s1, s2);
        }
    }

    private static class MyTopologyModel implements TopologyModel {
        private Collection<List<Point2D.Double>> edges;
        private Rectangle2D.Double bounds;
        private JComponent widget;

        synchronized void setComponent(JComponent widget) {
            this.widget = widget;
        }

        void setData(Collection<? extends Edge<? extends Vertex>> edges) {
            /* Convert the edges into Swing/AWT terms. */
            Collection<List<Point2D.Double>> newEdges = new HashSet<>();
            Map<Vertex, Point2D.Double> map = new HashMap<>();
            for (Edge<? extends Vertex> edge : edges) {
                Vertex ov1 = edge.first();
                Vertex ov2 = edge.second();
                Point2D.Double v1 = map
                    .computeIfAbsent(ov1, v -> new Point2D.Double(v.x, v.y));
                Point2D.Double v2 = map
                    .computeIfAbsent(ov2, v -> new Point2D.Double(v.x, v.y));
                newEdges.add(Arrays.asList(v1, v2));
            }

            /* Compute the new bounds in model co-ordinates. */
            double xmin = +Double.MAX_VALUE, ymin = +Double.MAX_VALUE;
            double xmax = +Double.MIN_VALUE, ymax = +Double.MIN_VALUE;
            for (Point2D.Double pt : map.values()) {
                if (pt.x < xmin) xmin = pt.x;
                if (pt.x > xmax) xmax = pt.x;
                if (pt.y < ymin) ymin = pt.y;
                if (pt.y > ymax) ymax = pt.y;
            }
            Rectangle2D.Double newBounds =
                new Rectangle2D.Double(xmin, ymin, xmax - xmin, ymax - ymin);

            /* Thread-safely apply the new data, and repaint the
             * display. */
            final JComponent widget;
            synchronized (this) {
                this.bounds = newBounds;
                this.edges = newEdges;
                widget = this.widget;
            }
            if (widget != null)
                SwingUtilities.invokeLater(() -> widget.repaint());
        }

        @Override
        public synchronized Rectangle2D.Double getBounds() {
            return bounds;
        }

        @Override
        public synchronized
            Collection<? extends List<? extends Point2D.Double>> getEdges() {
            return edges;
        }
    }
}
