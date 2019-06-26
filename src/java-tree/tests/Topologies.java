
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import uk.ac.lancs.routing.span.Edge;

/**
 * Holds static methods for manipulating topologies.
 * 
 * @author simpsons
 */
public final class Topologies {
    private Topologies() {}

    /**
     * Convert a topology represented as an index from each vertex to
     * its neighbours to one represented as a set of edges. The
     * resultant topology is an undirected graph.
     * 
     * @param <V> the vertex type
     * 
     * @param neighbors the topology as an index from each vertex to its
     * neighbours
     * 
     * @return the set of edges
     */
    public static <V> Collection<Edge<V>>
        convertNeighborsToEdges(Map<? extends V, ? extends Collection<? extends V>> neighbors) {
        Collection<Edge<V>> edges = new HashSet<>();
        for (Map.Entry<? extends V, ? extends Collection<? extends V>> entry : neighbors
            .entrySet()) {
            V a = entry.getKey();
            for (V b : entry.getValue())
                edges.add(Edge.of(a, b));
        }
        return edges;
    }

    /**
     * Convert a topology represented as a set of edges into one
     * represented as an index from each vertex to its neighbours.
     * 
     * @param <V> the vertex type
     * 
     * @param edges the set of edges
     * 
     * @return an index from each vertex to its neighbours
     */
    public static <V> Map<V, Collection<V>>
        convertEdgesToNeighbors(Collection<? extends Edge<? extends V>> edges) {
        final Map<V, Collection<V>> neighbors = new HashMap<>();
        for (Edge<? extends V> edge : edges) {
            V v1 = edge.first();
            V v2 = edge.second();
            neighbors.computeIfAbsent(v1, k -> new HashSet<>()).add(v2);
            neighbors.computeIfAbsent(v2, k -> new HashSet<>()).add(v1);
        }
        return neighbors;
    }

    /**
     * Generate a scale-free graph.
     * 
     * @param <V> the vertex type
     * 
     * @param source a generator of new vertices
     * 
     * @param vertexCount the number of vertices to create
     * 
     * @param newEdgesPerVertex the number of times each new vertex will
     * be attempted to connect to an existing vertex
     * 
     * @param edgeSets a record of the topology, mapping each vertex to
     * its neighbours
     * 
     * @param rng a random-number generator for selecting a vertex to
     * form an edge with
     */
    public static <V> void
        generateTopology(Supplier<V> source, final int vertexCount,
                         IntSupplier newEdgesPerVertex,
                         Map<V, Collection<V>> edgeSets, Random rng) {
        /* Create a starting point. */
        V v0 = source.get();
        V v1 = source.get();
        edgeSets.put(v0, new HashSet<>());
        edgeSets.put(v1, new HashSet<>());
        edgeSets.get(v0).add(v1);
        edgeSets.get(v1).add(v0);
        int edgeCount = 2;

        /* Add more vertices, and link to a random existing one each
         * time. */
        for (int i = 2; i < vertexCount; i++) {
            V latest = source.get();

            /* Identify vertices to link to. */
            Collection<V> chosen = new HashSet<>();
            final int linkCount = newEdgesPerVertex.getAsInt();
            for (int j = 0; j < linkCount; j++) {
                int chosenIndex = rng.nextInt(edgeCount);
                for (Map.Entry<V, Collection<V>> entry : edgeSets
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
            for (V existing : chosen) {
                if (edgeSets.get(latest).add(existing)) {
                    edgeSets.get(existing).add(latest);
                    edgeCount += 2;
                }
            }
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
                      Collection<? extends Edge<? extends V>> edges,
                      TopologyDisplay<V> display, Pauser pauser) {
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
        final Map<V, Collection<V>> neighbors =
            convertEdgesToNeighbors(edges);
    
        /* Create a repeatable sequence of vertices, so we can iterate
         * over unique pairs. */
        final List<V> arr = new ArrayList<>(neighbors.keySet());
        final int arrlen = arr.size();
    
        double elapsed = 0.0;
        cycles:
        for (long cycle = 0;; cycle++) {
            /* Compute forces given current positions and edges. */
            neighbors.keySet().forEach(v -> attrs.resetForce(v));
    
            /* Apply an elastic force to each edge. */
            final double optLength = 2.0;
            final double spring = 0.1;
            for (Edge<? extends V> entry : edges) {
                final V a = entry.first();
                final V b = entry.second();
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
                    final V a = arr.get(i);
                    final V b = arr.get(j);
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
                final double vx = attrs.vx(v);
                final double vy = attrs.vy(v);
                final double sp2 = vx * vx + vy * vy;
                final double sp = -Math.sqrt(sp2);
                attrs.addForce(v, vx * sp * airResistance,
                               vy * sp * airResistance);
            });
    
            /* Convert the forces to accelerations. */
            neighbors.keySet().forEach(v -> attrs.diminishForce(v));
    
            if (false) for (V v : neighbors.keySet()) {
                System.out.printf("  %s: delta-V (%g, %g)%n", v, attrs.vx(v),
                                  attrs.vy(v));
            }
    
            /* Find the largest time jump we can afford. */
            double bestDelta = maxDelta;
            for (V v : neighbors.keySet()) {
                {
                    /* Check acceleration. */
                    final double acc = attrs.force(v);
                    final double delta = maxSpeed / acc;
                    if (delta < bestDelta) bestDelta = delta;
                }
    
                {
                    /* Check velocity. */
                    final double sp = attrs.speed(v);
                    final double delta = maxSpeed / sp;
                    if (delta < bestDelta) bestDelta = delta;
                }
            }
            final double delta =
                (elapsed + bestDelta > elapsed) ? bestDelta : 0.0001;
    
            /* Apply all accelerations and velocities. */
            neighbors.keySet().forEach(v -> attrs.step(v, delta));
            elapsed += delta;
    
            {
                /* Recentre by finding the centre of gravity. */
                double x = 0.0, y = 0.0, mass = 0.0;
                for (V v : neighbors.keySet()) {
                    final double vm = attrs.mass(v);
                    mass += vm;
                    x += vm * attrs.x(v);
                    y += vm * attrs.y(v);
                }
                final double xshift = x / mass;
                final double yshift = y / mass;
                neighbors.keySet()
                    .forEach(v -> attrs.move(v, -xshift, -yshift));
            }
    
            {
                /* Compute mean rotation. */
                double sum = 0.0, sqsum = 0.0;
                for (V v : neighbors.keySet()) {
                    final double x = attrs.x(v);
                    final double y = attrs.y(v);
                    final double r2 = x * x + y * y;
                    final double rot =
                        (attrs.vy(v) * x - attrs.vx(v) * y) / r2;
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
                for (V v : neighbors.keySet()) {
                    System.out.printf("  %s: (%g, %g)%n", v, attrs.x(v),
                                      attrs.y(v));
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
                for (V v : neighbors.keySet()) {
                    vxsum += attrs.vx(v);
                    vysum += attrs.vy(v);
                }
                final double vxmean = vxsum / neighbors.size();
                final double vymean = vysum / neighbors.size();
    
                /* What's the total energy in the system? */
                double energy = 0.0;
                double mass = 0.0;
                for (V v : neighbors.keySet()) {
                    final double vx = attrs.x(v) - vxmean;
                    final double vy = attrs.y(v) - vymean;
                    final double vm = attrs.mass(v);
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

}
