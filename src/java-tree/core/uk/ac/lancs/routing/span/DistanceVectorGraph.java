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
package uk.ac.lancs.routing.span;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maintains a distance-vector computation over a graph of vertices and
 * edges.
 * 
 * @param <V> the vertex type
 * 
 * @author simpsons
 */
public final class DistanceVectorGraph<V> {
    private final Collection<V> terminals = new HashSet<>();

    /**
     * Holds the neighbours of every vertex and the distances to them.
     */
    private final Map<V, Map<V, Double>> neighbours = new HashMap<>();

    /**
     * Identifies vertices that might have out-of-date FIBs.
     */
    private final Collection<V> invalid = new LinkedHashSet<>();

    /**
     * Holds the FIBs of each node.
     */
    private final Map<V, Map<V, Way<V>>> fibs = new HashMap<>();

    /**
     * Create structures to maintain FIBs with distance-vector
     * information, with initially no graph or terminal information.
     */
    public DistanceVectorGraph() {}

    /**
     * Create structures to maintain FIBs with distance-vector
     * information, using a specific graph and terminal set.
     * 
     * @param terminals the set of vertices which consitute the keys of
     * each vertex's FIB
     * 
     * @param links the edges between vertices, and their distances (or
     * weights/costs)
     */
    public DistanceVectorGraph(Collection<? extends V> terminals,
                               Map<? extends Edge<? extends V>, ? extends Number> links) {
        this.terminals.addAll(terminals);

        /* Treat all vertices as having out-of-date FIBs. */
        this.invalid.addAll(terminals);

        /* Indicate how to walk from each node to its neighbours. */
        for (Map.Entry<? extends Edge<? extends V>, ? extends Number> entry : links
            .entrySet()) {
            Edge<? extends V> link = entry.getKey();
            double weight = entry.getValue().doubleValue();
            addNeighbour(link.first(), link.second(), weight);
            addNeighbour(link.second(), link.first(), weight);
        }
    }

    private void addNeighbour(V from, V to, double weight) {
        neighbours.computeIfAbsent(from, k -> new HashMap<>()).put(to,
                                                                   weight);
    }

    /**
     * Determine whether the FIBs are up-to-date.
     * 
     * @return {@code true} iff the FIBs are up-to-date
     */
    public boolean isUpdated() {
        return invalid.isEmpty();
    }

    /**
     * Get the load on each edge.
     * 
     * @return a map from each edge to a pair of maps listing each
     * terminal routed over the edge with its distance
     */
    public Map<Edge<V>, List<Map<V, Double>>> getEdgeLoads() {
        Map<Edge<V>, List<Map<V, Double>>> result = new HashMap<>();
        for (Map.Entry<V, Map<V, Way<V>>> entry : fibs.entrySet()) {
            V first = entry.getKey();
            for (Map.Entry<V, Way<V>> way : entry.getValue().entrySet()) {
                V second = way.getValue().nextHop;
                if (second == null) continue;
                Edge<V> edge = Edge.of(first, second);
                List<Map<V, Double>> list =
                    result.computeIfAbsent(edge, k -> Arrays
                        .asList(new HashMap<>(), new HashMap<>()));
                double distance = way.getValue().distance;
                list.get(edge.vertices().indexOf(first)).put(way.getKey(),
                                                             distance);
            }
        }
        return result;
    }

    /**
     * Get the FIBs of all nodes.
     * 
     * @return an immutable collection of FIBs
     */
    public Map<V, Map<V, Way<V>>> getFIBs() {
        return Collections.unmodifiableMap(fibs.entrySet().stream()
            .collect(Collectors
                .toMap(Map.Entry::getKey,
                       e -> Collections.unmodifiableMap(e.getValue()))));
    }

    /**
     * Add a set of vertices as new terminals. The FIBs are rendered
     * out-of-date if any of the provided vertices are not already
     * terminals.
     * 
     * @param terminals the set of vertices to be added as terminals
     */
    public void addTerminals(Collection<? extends V> terminals) {
        terminals.forEach(this::addTerminal);
    }

    /**
     * Add a terminal vertex to the graph. The FIBs are out-of-date if
     * the vertex was not already a terminal.
     * 
     * @param terminal the terminal to be added
     */
    public void addTerminal(V terminal) {
        if (terminals.add(terminal)) invalid.add(terminal);
    }

    /**
     * Remove vertices from the set of terminals. The FIBs are not
     * rendered out-of-date.
     * 
     * @param terminals the set of vertices to be no longer considered
     * terminals
     */
    public void removeTerminals(Collection<? extends V> terminals) {
        terminals.forEach(this::removeTerminal);
    }

    /**
     * Remove a vertex from the set of terminals. The FIBs are not
     * rendered out-of-date.
     * 
     * @param terminal the vertex to be no longer considered a terminal
     */
    public void removeTerminal(V terminal) {
        if (terminals.remove(terminal))
            fibs.values().stream().forEach(fib -> fib.remove(terminal));
    }

    /**
     * Remove an edge from the graph. The FIBs become out-of-date.
     * 
     * @param edge an edge between two vertices
     */
    public void removeEdge(Edge<? extends V> edge) {
        V first = edge.first();
        V second = edge.second();
        neighbours.computeIfAbsent(first, k -> new HashMap<>())
            .remove(second);
        neighbours.computeIfAbsent(second, k -> new HashMap<>())
            .remove(first);
        invalid.addAll(edge.vertices());
    }

    /**
     * Remove multiple edges from the graph. The FIBs become out-of-date
     * 
     * @param edges the set of edges to remove
     */
    public void
        removeEdges(Collection<? extends Edge<? extends V>> edges) {
        edges.stream().forEach(this::removeEdge);
    }

    /**
     * Add multiple edges from to graph, or update them. The FIBs become
     * out-of-date
     * 
     * @param edges the set of edges to add, with their weights
     */
    public void
        addEdges(Map<? extends Edge<? extends V>, ? extends Number> edges) {
        edges.entrySet().stream()
            .forEach(e -> addEdge(e.getKey(), e.getValue().doubleValue()));
    }

    /**
     * Add a new edge to the graph, or update it. The FIBs become
     * out-of-date.
     * 
     * @param edge an edge between two vertices
     * 
     * @param weight the weight of the edge
     */
    public void addEdge(Edge<? extends V> edge, double weight) {
        V first = edge.first();
        V second = edge.second();
        addNeighbour(first, second, weight);
        addNeighbour(second, first, weight);
        invalid.addAll(edge.vertices());
    }

    /**
     * Ensure that all FIBs are up-to-date. In the initial state with
     * the non-zero-args constructor, or after a call to
     * {@link #removeEdge(Edge)},
     * {@link #addEdge(Edge, double)}, {@link #addEdges(Map)},
     * {@link #addTerminal(Object)}, {@link #addTerminals(Collection)}
     * or {@link #removeEdges(Collection)}, the FIBs should be
     * considered out-of-date.
     */
    public void update() {
        Iterator<V> iter;
        while ((iter = invalid.iterator()).hasNext()) {
            /* Pick and remove one of the lagging vertices for its FIB
             * to be recomputed. */
            V updating = iter.next();
            iter.remove();

            /* Start with a fresh FIB for this vertex. */
            Map<V, Way<V>> newFib = new HashMap<>();

            /* If this vertex is a terminal, ensure it has a
             * zero-distance way to itself. */
            if (terminals.contains(updating))
                newFib.put(updating, Way.of(null, 0.0));

            /* Get the distances to each neighbour vertex. */
            Map<V, Double> neighbourWeights =
                neighbours.getOrDefault(updating, Collections.emptyMap());

            for (Map.Entry<V, Double> entry : neighbourWeights.entrySet()) {
                V neighbour = entry.getKey();
                double weight = entry.getValue();

                /* Consult the neighbour's routing table. */
                Map<V, Way<V>> neighbourFib =
                    fibs.computeIfAbsent(neighbour, k -> new HashMap<>());

                /* For each entry in the neighbour's table, compute a
                 * potential new entry for ours. */
                for (Map.Entry<V, Way<V>> subway : neighbourFib.entrySet()) {
                    /* Don't bother with this entry if it routes back to
                     * us. */
                    Way<V> way = subway.getValue();
                    if (updating.equals(way.nextHop)) continue;

                    /* Compute a potential route for this destination,
                     * accounting for the fact that the neighbour is
                     * some distance from us. */
                    V dest = subway.getKey();
                    double newDist = way.distance + weight;

                    /* Get the best we have for this destination in our
                     * table. Replace it with the computed one if that
                     * is better. */
                    Way<V> best = newFib.get(dest);
                    if (best == null || newDist < best.distance)
                        newFib.put(dest, Way.of(neighbour, newDist));
                }
            }

            /* Compare the new and old FIBs. If they differ, invalidate
             * neighbours. */
            Map<V, Way<V>> fib = fibs.get(updating);
            if (fib == null || !fib.equals(newFib)) {
                fibs.put(updating, newFib);
                invalid.addAll(neighbourWeights.keySet());
            }
        }
    }
}
