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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import uk.ac.lancs.dpb.graph.BidiCapacity;
import uk.ac.lancs.dpb.graph.Edge;
import uk.ac.lancs.dpb.graph.QualifiedEdge;

/**
 * Morphs a 2-dimensional graph into something visually comprehensible.
 *
 * @author simpsons
 */
public class GraphMorpher {
    /**
     * The repulsive gravitational constant
     */
    final double push = 0.3;

    /**
     * The maximum allowed speed of any vertex
     */
    final double maxSpeed = 0.01;

    /**
     * The strength of air resistance to damp motion
     */
    final double airResistance = 0.9;

    /**
     * The maximum simulation step
     */
    final double maxDelta = 0.1;

    /**
     * Re-centre all vertices on the centre of mass.
     */
    void recenter() {
        double x = 0.0;
        double y = 0.0;
        double mass = 0.0;
        for (var v : vertexes) {
            mass += v.mass;
            x += v.mass * v.x;
            y += v.mass * v.y;
        }
        final double xsh = x / -mass;
        final double ysh = y / -mass;
        vertexes.parallelStream().forEach(v -> v.move(xsh, ysh));
    }

    /**
     * Compute the variance of the rotation of all vertices.
     *
     * @return the variance of rotation of all vertices
     */
    double computeRotationVariance() {
        double sum = 0.0;
        double sqsum = 0.0;
        for (var v : vertexes) {
            final double rot = v.getRotation();
            sum += rot;
            sqsum += rot * rot;
        }
        sum /= vertexes.size();
        sqsum /= vertexes.size();
        return sqsum - sum * sum;
    }

    /**
     * Holds the count of created vertices. This allows each new vertex
     * to be initially placed in a spiral around the origin, ensuring
     * that every vertex has a unique starting position.
     */
    private int nextId;

    class MovingVertex extends Vertex {
        double x;

        double y;

        double vx;

        double vy;

        double fx;

        double fy;

        double mass = 1.0;

        MovingVertex() {
            /* Arrange each vertex in a spiral. */
            int id = nextId++;
            double pid = 0.3 * Math.pow(id, 1.2);
            this.x = pid * Math.cos(id);
            this.y = pid * Math.sin(id);
        }

        @Override
        public double x() {
            return x;
        }

        @Override
        public double y() {
            return y;
        }

        void step(double delta) {
            vx += fx * delta;
            vy += fy * delta;
            x += vx * delta;
            y += vy * delta;
        }

        double getRotation() {
            final double r2 = x * x + y * y;
            return (vy * x - vx * y) / r2;
        }

        void resetForce() {
            fx = fy = 0.0;
        }

        void addForce(double dfx, double dfy) {
            fx += dfx;
            fy += dfy;
        }

        void move(double dx, double dy) {
            x += dx;
            y += dy;
        }

        void scale(double factor) {
            x *= factor;
            y *= factor;
        }

        void convertForceToAcceleration() {
            fx /= mass;
            fy /= mass;
        }

        double getDelta() {
            final double acc = Math.hypot(fx, fy);
            final double accDelta = maxSpeed / acc;
            final double speed = Math.hypot(vx, vy);
            final double speedDelta = maxSpeed / speed;
            return Math.min(accDelta, speedDelta);
        }
    }

    /* Prepare to detect the end of the simulation. The first two arrays
     * must have the same length. steadyLimit indicates how many cycles
     * must pass to stop if the rotation variance is stable within the
     * threshold given by targetFraction. The remaining arrays are
     * derived from the first two, and act as working state or
     * precomputed values during the simulation. */
    final long[] steadyLimit = { 1000, 10000, 100000 };

    final double[] targetFraction = { 0.00001, 0.0001, 0.001 };

    final double[] lowTarget = new double[steadyLimit.length];

    final double[] highTarget = new double[steadyLimit.length];

    final long[] steady = new long[steadyLimit.length];

    void trackRotation() {
        final double signal = computeRotationVariance();
        for (int i = 0; i < steady.length; i++) {
            if (signal > highTarget[i] || signal < lowTarget[i]) {
                lowTarget[i] = signal * (1.0 - targetFraction[i]);
                highTarget[i] = signal * (1.0 + targetFraction[i]);
                steady[i] = cycle;
            }
        }
    }

    private final Collection<Edge<MovingVertex>> edges;

    private final List<MovingVertex> vertexes;

    private final TopologyDisplay<Vertex> display;

    /**
     * Create a morpher for a given graph.
     * 
     * @param edges the graph to morph
     * 
     * @param display a display to be updated after each advance
     */
    public GraphMorpher(Collection<? extends Edge<? extends Vertex>> edges,
                        TopologyDisplay<Vertex> display) {
        this.display = display == null ? (s, e) -> {} : display;

        /* Create a moving vertex for every implied input vertex. Create
         * a new edge for every supplied edge, based on the new
         * vertices. */
        Collection<Edge<MovingVertex>> newEdges = new HashSet<>();
        Map<Vertex, MovingVertex> movers = new IdentityHashMap<>();
        for (var e : edges) {
            MovingVertex smv =
                movers.computeIfAbsent(e.start, k -> new MovingVertex());
            MovingVertex fmv =
                movers.computeIfAbsent(e.finish, k -> new MovingVertex());
            newEdges.add(new Edge<>(smv, fmv));
        }
        this.edges = Set.copyOf(newEdges);
        this.vertexes = List.copyOf(movers.values());

        /* Determine the degree of each vertex. */
        Map<MovingVertex, Integer> degrees = new IdentityHashMap<>();
        for (var edge : this.edges) {
            degrees.merge(edge.start, 1, (v0, v1) -> v0 + v1);
            degrees.merge(edge.finish, 1, (v0, v1) -> v0 + v1);
        }
        this.degrees = Collections.unmodifiableMap(degrees);

        if (false) {
            Map<Integer, Integer> counts = new TreeMap<>();
            for (var entry : degrees.entrySet()) {
                int key = entry.getValue();
                counts.merge(key, 1, (a, b) -> a + b);
            }
            System.err.printf("Population distribution: %s%n", counts);
            System.err.printf("Population distribution: %s%n", counts.entrySet()
                .stream()
                .collect(Collectors
                    .toMap(Map.Entry::getKey,
                           e -> -Math
                               .log(e.getValue() / (double) this.degrees.size())
                               / Math.log(e.getKey()))));
        }

        /* Give each vertex a mass one more than its degree. */
        assert this.degrees.size() == movers.size();
        for (var entry : this.degrees.entrySet())
            entry.getKey().mass = 1.0 + entry.getValue();
    }

    final Map<MovingVertex, Integer> degrees;

    long cycle = 0L;

    final double optLength = 0.3;

    final double spring = 0.1;

    double elapsed = 0.0;

    /**
     * Advance the morphing by one step.
     * 
     * @return {@code true} if more morphing is required; {@code false}
     * otherwise
     */
    public boolean advance() {
        if (vertexes.isEmpty()) return false;

        // System.err.printf("%nVertices: %s%n", vertexes);

        /* Reset forces on all vertices so we can compute forces given
         * current positions and edges. */
        vertexes.forEach(MovingVertex::resetForce);

        /* Apply an elastic force to each edge. */
        for (Edge<MovingVertex> entry : edges) {
            final var a = entry.start;
            final var b = entry.finish;
            final double dx = b.x - a.x;
            final double dy = b.y - a.y;
            final double dist = Math.hypot(dx, dy);
            final double sinth = dy / dist;
            final double costh = dx / dist;
            final double force = (dist - optLength) * spring;
            final double bfx = force * costh;
            final double bfy = force * sinth;
            a.addForce(bfx, bfy);
            b.addForce(-bfx, -bfy);
            // System.err.printf("Elastic %s by (%g,%g) (%g)%n", entry,
            // bfx, bfy,
            // dist);
        }

        /* Make all vertices repel each other. */
        final int arrlen = vertexes.size();
        for (int i = 0; i < arrlen - 1; i++) {
            for (int j = i + 1; j < arrlen; j++) {
                final var a = vertexes.get(i);
                final var b = vertexes.get(j);
                final double dx = b.x - a.x;
                final double dy = b.y - a.y;
                final double r2 = dx * dx + dy * dy;
                final double r3 = Math.pow(r2, 1.5);
                final double bfx = push * dx / r3;
                final double bfy = push * dy / r3;
                final double bmass = -b.mass;
                final double amass = a.mass;
                a.addForce(bmass * bfx, bmass * bfy);
                b.addForce(amass * bfx, amass * bfy);
                // System.err.printf("Repel %s=%s by (%g,%g)%n", a, b,
                // bfx, bfy);
            }
        }

        /* Compute air resistance. */
        vertexes.parallelStream().forEach(v -> {
            final double sp = -Math.hypot(v.vx, v.vy);
            v.addForce(v.vx * sp * airResistance, v.vy * sp * airResistance);
        });

        /* Convert the forces to accelerations. */
        vertexes.parallelStream()
            .forEach(MovingVertex::convertForceToAcceleration);

        /* Find the largest time jump we can afford. */
        assert !vertexes.isEmpty();
        final double bestDelta = Math.min(maxDelta, vertexes.parallelStream()
            .mapToDouble(MovingVertex::getDelta).min().getAsDouble());
        final double delta =
            (elapsed + bestDelta > elapsed) ? bestDelta : 0.0001;
        // System.err.printf("Cycle: %d: delta: %g%n", cycle, delta);

        /* Apply all accelerations and velocities. */
        vertexes.parallelStream().forEach(v -> v.step(delta));
        elapsed += delta;

        /* Recentre by finding the centre of gravity. */
        recenter();

        display.setData(delta, edges);

        /* Track changes in rotation. */
        trackRotation();

        /* Stop when we detect stability. */
        for (int i = 0; i < steady.length; i++)
            if (cycle - steady[i] > steadyLimit[i]) return false;
        cycle++;
        return true;
    }

    /**
     * Get a frozen copy of the graph. It will be scaled to an ideal
     * size if too small, and its top left will be aligned to the
     * origin.
     * 
     * @param ideal an ideal size for the graph
     * 
     * @param caps capacities to be applied to each generated edge
     * 
     * @return the resultant graph
     */
    public Graph freeze(final int ideal, CapacitySupply caps) {
        /* Find the minimum and maximum positions. */
        assert !vertexes.isEmpty();
        // System.err.printf("vertices: (%d) %s%n", vertexes.size(),
        // vertexes);
        final double minx = vertexes.parallelStream().mapToDouble(Vertex::x)
            .min().getAsDouble();
        final double maxx = vertexes.parallelStream().mapToDouble(Vertex::x)
            .max().getAsDouble();
        final double miny = vertexes.parallelStream().mapToDouble(Vertex::y)
            .min().getAsDouble();
        final double maxy = vertexes.parallelStream().mapToDouble(Vertex::y)
            .max().getAsDouble();

        /* Push the left-most and upper-most to the origin. */
        vertexes.parallelStream().forEach(v -> v.move(-minx, -miny));

        /* Get the width and height to compare with the ideal. */
        final double width = maxx - minx;
        final double height = maxy - miny;
        final double xscale = ideal / width;
        final double yscale = ideal / height;
        final double scale = Math.max(1.0, Math.max(xscale, yscale));

        /* Scale everything to an ideal size. */
        vertexes.parallelStream().forEach(v -> v.scale(scale));

        /* Convert the edges so their costs match their lengths, and
         * have capacities based on their degrees. */
        Collection<QualifiedEdge<Vertex>> newEdges = edges.stream().map(e -> {
            var a = e.start;
            var b = e.finish;
            double cost = Vertex.distance(a, b);
            int ad = degrees.get(a);
            int bd = degrees.get(b);
            BidiCapacity cap = caps.getCapacity(cost, ad, bd);
            Vertex fa = Vertex.at(a.x, a.y);
            Vertex fb = Vertex.at(b.x, b.y);
            return new QualifiedEdge<>(fa, fb, cap, cost);
        }).collect(Collectors.toSet());

        return new Graph((int) Math.ceil(width * scale) + 1,
                         (int) Math.ceil(height * scale) + 1, newEdges);
    }
}
