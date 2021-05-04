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
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
    private final double push = 0.3;

    /**
     * The maximum allowed speed of any vertex
     */
    private final double maxSpeed = 0.01;

    /**
     * The strength of air resistance to damp motion
     */
    private final double airResistance = 0.9;

    /**
     * The maximum simulation step
     */
    private final double maxDelta = 0.1;

    /**
     * Re-centre all vertices on the centre of mass.
     */
    private void recenter() {
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
    private double computeRotationVariance() {
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

    /**
     * A movable vertex with a record of velocity and
     * force/acceleration.
     */
    private class Mote extends Vertex {
        /**
         * The X position
         */
        double x;

        /**
         * The Y position
         */
        double y;

        /**
         * The X velocity
         */
        double vx;

        /**
         * The Y velocity
         */
        double vy;

        /**
         * The X force or acceleration
         */
        double fx;

        /**
         * The Y force or acceleration
         */
        double fy;

        /**
         * The mass
         */
        double mass = 1.0;

        final int id;

        /**
         * Create a mote. Each new mote is placed along a spiral around
         * the origin as its initial position.
         */
        Mote() {
            /* Arrange each vertex in a spiral. */
            this.id = nextId++;
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

        /**
         * Adjust the velocity by the acceleration, then the position by
         * the velocity, over a short time period.
         * {@link #convertForceToAcceleration()} should be called first.
         * 
         * @param delta the time period
         */
        void step(double delta) {
            vx += fx * delta;
            vy += fy * delta;
            x += vx * delta;
            y += vy * delta;
        }

        /**
         * Get the angular velocity of the mote around the origin.
         * 
         * @return the angular velocity, possibly in radians per second
         */
        double getRotation() {
            final double r2 = x * x + y * y;
            return (vy * x - vx * y) / r2;
        }

        /**
         * Set the force back to zero. This should be called before
         * summing the forces with {@link #addForce(double, double)}.
         */
        void resetForce() {
            fx = fy = 0.0;
        }

        /**
         * Adjust the force by a vector.
         * 
         * @param dfx the change in the X dimension
         * 
         * @param dfy the change in the Y dimension
         */
        void addForce(double dfx, double dfy) {
            fx += dfx;
            fy += dfy;
        }

        /**
         * Translate the mote by a vector.
         * 
         * @param dx the change in the X dimension
         * 
         * @param dy the change in the Y dimension
         */
        void move(double dx, double dy) {
            x += dx;
            y += dy;
        }

        /**
         * Scale the mote's position about the origin.
         * 
         * @param factor the scale factor in both dimensions
         */
        void scale(double factor) {
            x *= factor;
            y *= factor;
        }

        /**
         * Divide the force sum by the mote's mass. This should be
         * called before {@link #step(double)} and after several calls
         * to {@link #addForce(double, double)}.
         */
        void convertForceToAcceleration() {
            fx /= mass;
            fy /= mass;
        }

        /**
         * Get the largest delta that this mote can cope with without
         * losing significant accuracy.
         * 
         * @return the largest permitted delta
         */
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
    private final long[] steadyLimit = { 1000, 10000, 100000 };

    private final double[] targetFraction = { 0.00001, 0.0001, 0.001 };

    private final double[] lowTarget = new double[steadyLimit.length];

    private final double[] highTarget = new double[steadyLimit.length];

    private final long[] steady = new long[steadyLimit.length];

    private void trackRotation() {
        final double signal = computeRotationVariance();
        for (int i = 0; i < steady.length; i++) {
            if (signal > highTarget[i] || signal < lowTarget[i]) {
                lowTarget[i] = signal * (1.0 - targetFraction[i]);
                highTarget[i] = signal * (1.0 + targetFraction[i]);
                steady[i] = cycle;
            }
        }
    }

    private final Collection<Edge<Mote>> edges;

    private final List<Mote> vertexes;

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
        Collection<Edge<Mote>> newEdges = new HashSet<>();
        Map<Vertex, Mote> movers = new IdentityHashMap<>();
        List<Mote> newMotes = new ArrayList<>();
        for (var e : edges) {
            Mote smv = movers.computeIfAbsent(e.start, k -> {
                Mote m = new Mote();
                newMotes.add(m);
                return m;
            });
            Mote fmv = movers.computeIfAbsent(e.finish, k -> {
                Mote m = new Mote();
                newMotes.add(m);
                return m;
            });
            newEdges.add(new Edge<>(smv, fmv));
        }
        this.edges = Set.copyOf(newEdges);
        this.vertexes = List.copyOf(newMotes);
        assert this.vertexes.containsAll(movers.values());
        assert movers.values().containsAll(this.vertexes);
        IntStream.range(0, this.vertexes.size()).forEach(i -> {
            Mote m = this.vertexes.get(i);
            assert m.id == i;
        });

        /* Determine the degree of each vertex. */
        Map<Mote, Integer> degrees = new IdentityHashMap<>();
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

        this.forceSums =
            new double[this.vertexes.size() * this.vertexes.size() * 2];
    }

    private final double[] forceSums;

    private final Map<Mote, Integer> degrees;

    /**
     * Keeps track of the number of calls to {@link #advance()}. This is
     * used to detect stability in the rotation variance, to recommend
     * ending the simulation.
     */
    private long cycle = 0L;

    /**
     * Edges with this length are in equilibrium with respect to the
     * elastic force.
     */
    private final double optLength = 0.3;

    /**
     * The elastic force strength per unit of length difference from the
     * optimum
     */
    private final double spring = 0.1;

    /**
     * The elapsed simulation time, incremented by the chosen delta on
     * each cycle
     */
    private double elapsed = 0.0;

    /**
     * Advance the morphing by one step.
     * 
     * @return {@code true} if more morphing is required; {@code false}
     * otherwise
     */
    public boolean advance() {
        final boolean parallel = false;

        if (vertexes.isEmpty()) return false;

        // System.err.printf("%nVertices: %s%n", vertexes);

        final int arrlen = vertexes.size();

        if (parallel) {
            /* Reset forces on all vertices so we can compute forces
             * given current positions and edges. */
            IntStream.range(0, forceSums.length).forEach(n -> forceSums[n] = 0);

            /* Apply an elastic force to each edge. */
            edges.parallelStream().forEach((entry) -> {
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

                final int aoff = (a.id * arrlen + b.id) * 2;
                forceSums[aoff] += bfx;
                forceSums[aoff + 1] += bfy;
                final int boff = (b.id * arrlen + a.id) * 2;
                forceSums[boff] -= bfx;
                forceSums[boff + 1] -= bfy;
            });

            /* Make all vertices repel each other. */
            IntStream.range(0, arrlen * (arrlen - 1)).parallel().forEach(n -> {
                final int j = n / (arrlen - 1);
                final int nm = n % (arrlen - 1);
                final int i = nm + (nm >= j ? 1 : 0);
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

                final int aoff = (a.id * arrlen + b.id) * 2;
                forceSums[aoff] += bmass * bfx;
                forceSums[aoff + 1] += bmass * bfy;
                final int boff = (b.id * arrlen + a.id) * 2;
                forceSums[boff] += amass * bfx;
                forceSums[boff + 1] += amass * bfy;
            });

            /* Compute air resistance. */
            vertexes.parallelStream().forEach(v -> {
                final double sp = -Math.hypot(v.vx, v.vy);
                final int off = v.id * (arrlen + 1) * 2;
                forceSums[off] += v.vx * sp * airResistance;
                forceSums[off + 1] += v.vy * sp * airResistance;
            });

            /* Convert the forces to accelerations. */
            IntStream.range(0, arrlen).parallel().forEach(i -> {
                Mote m = vertexes.get(i);
                m.fx = IntStream.range(0, arrlen).parallel()
                    .mapToDouble(j -> forceSums[(i * arrlen + j) * 2]).sum()
                    / m.mass;
                m.fy = IntStream.range(0, arrlen).parallel()
                    .mapToDouble(j -> forceSums[(i * arrlen + j) * 2 + 1]).sum()
                    / m.mass;
            });
        } else {
            /* Reset forces on all vertices so we can compute forces
             * given current positions and edges. */
            vertexes.parallelStream().forEach(Mote::resetForce);

            /* Apply an elastic force to each edge. */
            for (Edge<Mote> entry : edges) {
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
            }

            /* Make all vertices repel each other. */
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
                }
            }

            /* Compute air resistance. */
            vertexes.parallelStream().forEach(v -> {
                final double sp = -Math.hypot(v.vx, v.vy);
                v.addForce(v.vx * sp * airResistance,
                           v.vy * sp * airResistance);
            });

            /* Convert the forces to accelerations. */
            vertexes.parallelStream().forEach(Mote::convertForceToAcceleration);
        }

        /* Find the largest time jump we can afford. */
        assert !vertexes.isEmpty();
        final double bestDelta = Math.min(maxDelta, vertexes.parallelStream()
            .mapToDouble(Mote::getDelta).min().getAsDouble());
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

    private static <E> Collection<E> newIdentityHashSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static <E> Collection<E>
        sumIdentityHashSets(Collection<? extends E> a,
                            Collection<? extends E> b) {
        Collection<E> result = newIdentityHashSet();
        result.addAll(a);
        result.addAll(b);
        return result;
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

        /* Create a fixed vertex for each moving one. */
        Collection<Mote> motes = edges.stream().flatMap(Edge::stream)
            .collect(GraphMorpher::newIdentityHashSet, Collection::add,
                     GraphMorpher::sumIdentityHashSets);
        Map<Mote, Vertex> fixed = new IdentityHashMap<>();
        for (Mote m : motes)
            fixed.put(m, Vertex.at(m.x, m.y));

        /* Compute the cost of every edge as its length. */
        final Map<Edge<Mote>, Double> costs = edges.stream().collect(Collectors
            .toMap(e -> e, e -> Vertex.distance(e.start, e.finish)));

        /* Find the maximum cost of any edge. */
        final double maxCost = costs.values().stream()
            .mapToDouble(Number::doubleValue).max().getAsDouble();

        /* Find the maximum degree of any vertex. */
        final int maxDegree = degrees.values().stream()
            .mapToInt(Number::intValue).max().getAsInt();

        /* Convert the edges so their costs match their lengths, and
         * have capacities based on their degrees. Build the edges out
         * of the fixed versions of their vertices. */
        Collection<QualifiedEdge<Vertex>> newEdges = edges.stream().map(e -> {
            var a = e.start;
            var b = e.finish;
            double cost = costs.get(e);
            int ad = degrees.get(a);
            int bd = degrees.get(b);
            BidiCapacity cap =
                caps.getCapacity(cost, ad, bd, maxDegree, maxCost);
            Vertex fa = fixed.get(a);
            Vertex fb = fixed.get(b);
            return new QualifiedEdge<>(fa, fb, cap, cost);
        }).collect(Collectors.toSet());

        return new Graph((int) Math.ceil(width * scale) + 1,
                         (int) Math.ceil(height * scale) + 1, newEdges);
    }
}
