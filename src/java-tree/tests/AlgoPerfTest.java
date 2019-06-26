
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
import java.util.ArrayList;
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

            VertexAttribution<Vertex> attrs =
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
            alignTopology(attrs, edges, topModel, Pauser.NULL);
            topModel.setData(-1.0, edges);
            System.out.printf("Complete%n");
        }
    }

    /**
     * Allow the vertices of a topology to move around each other to
     * form a stable, dispersed structure. A simulation is run in which
     * gravity pushes all vertices apart, and edges behave like ideal
     * springs. Stop when all vertices are rotating around the centre of
     * mass uniformly.
     * 
     * @param <V> the vertex type
     * 
     * @param attrs a means to manipulate the physical attributes of a
     * vertex
     * 
     * @param edges the topology expressed as a set of edges
     * 
     * @param display an object to report the status to after each cycle
     * 
     * @param pauser an object to regulate reporting
     */
    public static <V> void
        alignTopology(VertexAttribution<V> attrs,
                      Collection<? extends Edge<? extends Vertex>> edges,
                      TopologyDisplay<Vertex> display, Pauser pauser) {
        /* The repulsive gravitational constant */
        final double push = 0.3;

        /* The maximum allowed speed of any vertex */
        final double maxSpeed = 0.01;

        /* The strength of air resistance to damp motion */
        final double airResistance = 0.9;

        /* The maximum simulation step */
        final double maxDelta = 0.1;

        /* Prepare to detect the end of the simulation. The first two
         * arrays must have the same length. steadyLimit indicates how
         * many cycles must pass to stop if the rotation variance is
         * stable within the threshold given by targetFraction. The
         * remaining arrays are derived from the first two, and act as
         * working state or precomputed values during the simulation. */
        final long[] steadyLimit = { 1000, 10000, 100000 };
        final double[] targetFraction = { 0.00001, 0.0001, 0.001 };
        final double[] lowTarget = new double[steadyLimit.length];
        final double[] highTarget = new double[steadyLimit.length];
        final long[] steady = new long[steadyLimit.length];

        /* Create an index from each vertex to its neighbours. */
        final Map<Vertex, Collection<Vertex>> neighbors =
            Topologies.convertEdgesToNeighbors(edges);

        /* Create a repeatable sequence of vertices, so we can iterate
         * over unique pairs. */
        final List<Vertex> arr = new ArrayList<>(neighbors.keySet());
        final int arrlen = arr.size();

        double elapsed = 0.0;
        cycles:
        for (long cycle = 0;; cycle++) {
            /* Compute forces given current positions and edges. */
            neighbors.keySet().forEach(Vertex::resetForce);

            /* Apply an elastic force to each edge. */
            final double optLength = 2.0;
            final double spring = 0.1;
            for (Edge<? extends Vertex> entry : edges) {
                final V a = (V) entry.first();
                final V b = (V) entry.second();
                final double dx = attrs.xDiff(b, a);
                final double dy = attrs.yDiff(b, a);
                final double r2 = dx * dx + dy * dy;
                final double dist = Math.sqrt(r2);
                final double sinth = dy / dist;
                final double costh = dx / dist;
                final double force = (dist - optLength) * spring;
                final double bfx = force * costh;
                final double bfy = force * sinth;
                attrs.addForce(a, bfx, bfy);
                attrs.addForce(b, -bfx, -bfy);
            }

            /* Make all vertices repel each other. */
            for (int i = 0; i < arrlen - 1; i++) {
                for (int j = i + 1; j < arrlen; j++) {
                    final V a = (V) arr.get(i);
                    final V b = (V) arr.get(j);
                    final double dx = attrs.xDiff(b, a);
                    final double dy = attrs.yDiff(b, a);
                    final double r2 = dx * dx + dy * dy;
                    final double r3 = Math.pow(r2, 1.5);
                    final double bfx = push * dx / r3;
                    final double bfy = push * dy / r3;
                    final double bmass = -attrs.mass(b);
                    final double amass = attrs.mass(a);
                    attrs.addForce(a, bmass * bfx, bmass * bfy);
                    attrs.addForce(b, amass * bfx, amass * bfy);
                }
            }

            /* Compute air resistance. */
            neighbors.keySet().forEach(v -> {
                final double vx = attrs.vx((V) v);
                final double vy = attrs.vy((V) v);
                final double sp2 = vx * vx + vy * vy;
                final double sp = -Math.sqrt(sp2);
                attrs.addForce((V) v, vx * sp * airResistance,
                               vy * sp * airResistance);
            });

            /* Convert the forces to accelerations. */
            neighbors.keySet().forEach(v -> attrs.diminishForce((V) v));

            if (false) for (Vertex v : neighbors.keySet()) {
                System.out.printf("  %s: delta-V (%g, %g)%n", v,
                                  attrs.vx((V) v), attrs.vy((V) v));
            }

            /* Find the largest time jump we can afford. */
            double bestDelta = maxDelta;
            for (Vertex v : neighbors.keySet()) {
                {
                    /* Check acceleration. */
                    final double acc = attrs.force((V) v);
                    final double delta = maxSpeed / acc;
                    if (delta < bestDelta) bestDelta = delta;
                }

                {
                    /* Check velocity. */
                    final double sp = attrs.speed((V) v);
                    final double delta = maxSpeed / sp;
                    if (delta < bestDelta) bestDelta = delta;
                }
            }
            final double delta =
                (elapsed + bestDelta > elapsed) ? bestDelta : 0.0001;

            /* Apply all accelerations and velocities. */
            neighbors.keySet().forEach(v -> attrs.step((V) v, delta));
            elapsed += delta;

            {
                /* Recentre by finding the centre of gravity. */
                double x = 0.0, y = 0.0, mass = 0.0;
                for (Vertex v : neighbors.keySet()) {
                    final double vm = attrs.mass((V) v);
                    mass += vm;
                    x += vm * attrs.x((V) v);
                    y += vm * attrs.y((V) v);
                }
                final double xshift = x / mass;
                final double yshift = y / mass;
                neighbors.keySet()
                    .forEach(v -> attrs.move((V) v, -xshift, -yshift));
            }

            {
                /* Compute mean rotation. */
                double sum = 0.0, sqsum = 0.0;
                for (Vertex v : neighbors.keySet()) {
                    final double x = attrs.x((V) v);
                    final double y = attrs.y((V) v);
                    final double r2 = x * x + y * y;
                    final double rot =
                        (attrs.vy((V) v) * x - attrs.vx((V) v) * y) / r2;
                    sum += rot;
                    sqsum += rot * rot;
                }
                sum /= neighbors.size();
                sqsum /= neighbors.size();
                final double var = sqsum - sum * sum;
                // final double stddev = Math.sqrt(var);
                if (false)
                    System.out.printf("rotation: (var %g) %d %d %d = %d%n",
                                      var, steady[0], steady[1], steady[2],
                                      cycle);

                /* Detect stability in the variance. */
                final double signal = var;
                for (int i = 0; i < steady.length; i++) {
                    if (signal > highTarget[i] || signal < lowTarget[i]) {
                        lowTarget[i] = signal * (1.0 - targetFraction[i]);
                        highTarget[i] = signal * (1.0 + targetFraction[i]);
                        steady[i] = cycle;
                    }
                }
            }

            if (false) {
                System.out.printf("%nElapsed: %gs (by %gs)%n", elapsed,
                                  delta);
                for (Vertex v : neighbors.keySet()) {
                    System.out.printf("  %s: (%g, %g)%n", v, attrs.x((V) v),
                                      attrs.y((V) v));
                }
            }
            pauser.pause(cycle);

            {
                /* Report the latest positions. */
                final double minDelta = 1e-6;
                assert delta <= maxDelta;
                final double ratio =
                    (Math.log(Math.max(minDelta, delta)) - Math.log(minDelta))
                        / (Math.log(maxDelta) - Math.log(minDelta));
                assert ratio >= 0.0;
                assert ratio <= 1.0;
                display.setData(ratio, edges);
            }

            if (false) {
                /* Find the average velocity. */
                double vxsum = 0.0, vysum = 0.0;
                for (Vertex v : neighbors.keySet()) {
                    vxsum += attrs.vx((V) v);
                    vysum += attrs.vy((V) v);
                }
                final double vxmean = vxsum / neighbors.size();
                final double vymean = vysum / neighbors.size();

                /* What's the total energy in the system? */
                double energy = 0.0;
                double mass = 0.0;
                for (Vertex v : neighbors.keySet()) {
                    final double vx = attrs.x((V) v) - vxmean;
                    final double vy = attrs.y((V) v) - vymean;
                    final double vm = attrs.mass((V) v);
                    energy += vm * (vx * vx + vy * vy) / 2.0;
                    mass += vm;
                }
                System.out.printf("Energy density: %g (delta %g)%n",
                                  energy / mass, delta);
            }

            // if (cycle > 100 && delta == maxDelta) break;
            for (int i = 0; i < steady.length; i++)
                if (cycle - steady[i] > steadyLimit[i]) break cycles;
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
