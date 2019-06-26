
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
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
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

        @Override
        public String toString() {
            return Integer.toString(id);
        }

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

        double mass = 1.0;

        private static int nextId;

        static final VertexAttribution<Vertex> ATTRIBUTION =
            new VertexAttribution<Vertex>() {
                @Override
                public double y(Vertex vert) {
                    return vert.y;
                }

                @Override
                public double x(Vertex vert) {
                    return vert.x;
                }

                @Override
                public double vy(Vertex vert) {
                    return vert.vy;
                }

                @Override
                public double vx(Vertex vert) {
                    return vert.vx;
                }

                @Override
                public void step(Vertex vert, double delta) {
                    vert.vx += vert.fx * delta;
                    vert.vy += vert.fy * delta;
                    vert.x += vert.vx * delta;
                    vert.y += vert.vy * delta;
                }

                @Override
                public void resetForce(Vertex vert) {
                    vert.fx = 0.0;
                    vert.fy = 0.0;
                }

                @Override
                public void move(Vertex vert, double dx, double dy) {
                    vert.x += dx;
                    vert.y += dy;
                }

                @Override
                public double mass(Vertex vert) {
                    return vert.mass;
                }

                @Override
                public double force(Vertex vert) {
                    final double fx = vert.fx;
                    final double fy = vert.fy;
                    return Math.sqrt(fx * fx + fy * fy);
                }

                @Override
                public void diminishForce(Vertex vert) {
                    vert.fx /= vert.mass;
                    vert.fy /= vert.mass;
                }

                @Override
                public void addForce(Vertex vert, double dx, double dy) {
                    vert.fx += dx;
                    vert.fy += dy;
                }
            };
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        final MyTopologyModel topModel = new MyTopologyModel();
        SwingUtilities.invokeLater(() -> {
            GraphicsDevice device = GraphicsEnvironment
                .getLocalGraphicsEnvironment().getScreenDevices()[0];
            JFrame frame = new JFrame();
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setUndecorated(true);
            device.setFullScreenWindow(frame);
            TopologyPanel panel = new TopologyPanel(topModel);
            topModel.setComponent(panel);
            frame.setContentPane(panel);
            panel.setPreferredSize(new Dimension(800, 800));
            frame.validate();
            frame.pack();
            frame.setVisible(true);
        });

        /* Create a scale-free network. */
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

            topModel.setData(0.0, edges);
            Thread.sleep(3 * 1000);

            Topologies.alignTopology(Vertex.ATTRIBUTION, edges, topModel,
                                     Pauser.NULL);
            topModel.setData(-1.0, edges);
            System.out.printf("Complete%n");
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

    private static class MyTopologyModel
        implements TopologyModel, TopologyDisplay<Vertex> {
        private Collection<List<Point2D.Double>> edges;
        private Rectangle2D.Double bounds;
        private JComponent widget;
        private double speed = 0.2;

        synchronized void setComponent(JComponent widget) {
            this.widget = widget;
        }

        @Override
        public void
            setData(double speed,
                    Collection<? extends Edge<? extends Vertex>> edges) {
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
                this.speed = speed;
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

        @Override
        public double speed() {
            return speed;
        }
    }
}
