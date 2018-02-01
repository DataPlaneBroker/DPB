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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Holds various algorithms pertaining to spanning trees. A typical use
 * would be as follows:
 * 
 * <pre>
 * // Create the graph and identify terminal vertices.
 * Map&lt;Edge&lt;String&gt;, Double&gt; graph = new HashMap&lt;&gt;();
 * graph.put(Edge.of("A", "D"), 1.0);
 * graph.put(Edge.of("E", "D"), 1.0);
 * &lt;var&gt;...&lt;/var&gt;
 * Collection&lt;String&gt; terminals =
 *     new HashSet&lt;&gt;(Arrays.asList("A", "B", "C"));
 * 
 * // Create routing tables in each vertex for the terminal vertices.
 * Map&lt;String, Map&lt;String, Way&lt;String&gt;&gt;&gt; fibs =
 *     Spans.route(terminals, graph);
 * 
 * // Create terminal-aware weights for each edge.
 * Map&lt;Edge&lt;String&gt;, Double&gt; weights = Spans.flatten(fibs);
 * 
 * // Create an optimal spanning tree based on the given weights.
 * Collection&lt;Edge&lt;String&gt;&gt; tree =
 *     Spans.span(terminals, weights);
 * </pre>
 * 
 * <p>
 * For bandwidth-aware routing, one would start with edges that have
 * remaining bandwidths as well as weights. Begin by eliminating edges
 * that have insufficient bandwidth for the amount specified by the
 * user. (The bandwidth attribute can then be discarded.) Then call
 * {@link #prune(Collection, Collection)} to eliminate spurs that do not
 * reach the terminals. Then call {@link #route(Collection, Map)},
 * {@link #flatten(Map, BiFunction)} and {@link #span(Collection, Map)}
 * as above.
 * 
 * <p>
 * From the results, one could identify vertices with two edges, and
 * implement them with a pair of OpenFlow rules, directing anything from
 * one port to the other, and vice versa. Vertices with more edges could
 * be implemented as learning switches.
 * 
 * @author simpsons
 */
public final class Spans {
    private Spans() {}

    /**
     * Generate forwarding information bases for all vertices in an
     * undirected weighted graph for a subset of destinations.
     * 
     * @param result the map to hold the result, the FIBs of all
     * vertices in the graph
     * 
     * @param terminals the subset of destinations that will be the
     * keysets of each FIB
     * 
     * @param links the undirected weighted graph
     * 
     * @param <V> the vertex type
     */
    public static <V> Map<V, Map<V, Way<V>>>
        route(Collection<? extends V> terminals,
              Map<? extends Edge<V>, ? extends Number> links) {
        /* From the provided links, create a mapping from each vertex to
         * each of its neighbours and the distance to each one. */
        Map<V, Map<V, Double>> neighbours = new HashMap<>();
        for (Map.Entry<? extends Edge<V>, ? extends Number> entry : links
            .entrySet()) {
            Edge<V> link = entry.getKey();
            double weight = entry.getValue().doubleValue();
            neighbours.computeIfAbsent(link.first(), k -> new HashMap<>())
                .put(link.second(), weight);
            neighbours.computeIfAbsent(link.second(), k -> new HashMap<>())
                .put(link.first(), weight);
        }

        /* Keep track of vertices with out-of-date tables, and start
         * with the terminals. */
        Collection<V> laggingVertices = new LinkedHashSet<>(terminals);

        /* Keep updating vertices' FIBs until none are out-of-date. */
        Map<V, Map<V, Way<V>>> result = new HashMap<>();
        while (!laggingVertices.isEmpty()) {
            /* Pick and remove one of the lagging vertices for its FIB
             * to be recomputed. */
            V updating = next(laggingVertices);

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
                    result.computeIfAbsent(neighbour, k -> new HashMap<>());

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
            Map<V, Way<V>> fib = result.get(updating);
            if (fib == null || !fib.equals(newFib)) {
                result.put(updating, newFib);
                laggingVertices.addAll(neighbourWeights.keySet());
            }
        }

        return result;
    }

    /**
     * Determine which keys have changed values.
     * 
     * @param map1 one of the maps
     * 
     * @param map2 the other map
     * 
     * @return a set of keys whose values differ in the supplied maps,
     * including keys present in only one map
     */
    @SuppressWarnings("unused")
    private static <K, V> Collection<K> getChangedKeys(Map<K, V> map1,
                                                       Map<K, V> map2) {
        /* Identify keys present in both maps. Their values will have to
         * be compared. */
        Collection<K> commonKeys = new HashSet<>(map1.keySet());
        commonKeys.retainAll(map2.keySet());

        /* Add all keys present in only one map. */
        Collection<K> result = new HashSet<>(map1.keySet());
        result.addAll(map2.keySet());
        result.removeAll(commonKeys);

        /* Add common keys only if their values differ. */
        result.addAll(commonKeys.stream()
            .filter(k -> !map1.get(k).equals(map2.get(k)))
            .collect(Collectors.toSet()));
        return result;
    }

    /**
     * Prune useless spurs from an undirected graph.
     * 
     * @param terminals the set of vertices that prevent otherwise
     * useless spurs from being removed
     * 
     * @param links the set of links to be modified
     * 
     * @param <N> the vertex type
     */
    public static <N> void prune(Collection<? extends N> terminals,
                                 Collection<Edge<N>> links) {
        /* Count how many links each vertex has. */
        Map<N, Collection<N>> graph = new HashMap<>();
        for (Edge<N> p : links) {
            graph.computeIfAbsent(p.first(), k -> new HashSet<>())
                .add(p.second());
            graph.computeIfAbsent(p.second(), k -> new HashSet<>())
                .add(p.first());
        }

        /* Identify vertices to be removed. */
        Collection<N> candidates = graph.entrySet().stream()
            .filter(e -> e.getValue().size() <= 1
                && !terminals.contains(e.getKey()))
            .map(Map.Entry::getKey).collect(Collectors.toSet());

        while (!candidates.isEmpty()) {
            Collection<N> current = candidates;
            candidates = new HashSet<>();
            for (N cand : current) {
                /* Tell this candidate's neighbours to lose a link. */
                for (N neigh : graph.remove(cand)) {
                    links.remove(Edge.of(cand, neigh));
                    Collection<N> neighNeigh = graph.get(neigh);
                    neighNeigh.remove(cand);
                    if (neighNeigh.size() <= 1 && !terminals.contains(neigh))
                        candidates.add(neigh);
                }
            }
        }
    }

    /**
     * Given the forwarding information bases of all vertices, create an
     * undirected weighted graph suitable for choosing a spanning tree
     * from.
     * 
     * <p>
     * The weight is computed by summing the distances of the ways
     * applying in either direction on each link, counting those ways
     * per link, subtracting the count from one plus the maximum number
     * of distinct destinations implied by ways, and multiplying the
     * difference by the distance sum.
     * 
     * @param fibs the FIBs of each vertex, the first key being current
     * vertex, the second being destination, and the value being the
     * next hop and distance to the destination
     * 
     * @param <V> the vertex type
     * 
     * @return the weights for each link
     */
    public static <V> Map<Edge<V>, Double>
        flatten(Map<? extends V, ? extends Map<? extends V, ? extends Way<V>>> fibs,
                BiFunction<? super V, ? super V, Edge<V>> edgeMaker) {
        /**
         * Holds a tally for a link.
         * 
         * @author simpsons
         */
        class Tally {
            double dividend = 0;
            int divisor = 0;

            void add(double distance) {
                dividend += distance;
                divisor++;
            }

            double value(int size) {
                return dividend * (size + 1 - divisor);
            }
        }

        /* Accumulate tallies for each link. */
        Map<Edge<V>, Tally> tallies = new HashMap<>(fibs.size());
        Collection<V> terminals = new HashSet<>();
        for (Map.Entry<? extends V, ? extends Map<? extends V, ? extends Way<V>>> nodeFib : fibs
            .entrySet()) {
            V first = nodeFib.getKey();
            Map<? extends V, ? extends Way<V>> fib = nodeFib.getValue();
            for (Map.Entry<? extends V, ? extends Way<V>> entry : fib
                .entrySet()) {
                terminals.add(entry.getKey());
                Way<V> way = entry.getValue();
                V second = way.nextHop;
                if (second == null) continue;
                Tally t =
                    tallies.computeIfAbsent(edgeMaker.apply(first, second),
                                            k -> new Tally());
                t.add(way.distance);
            }
        }

        return tallies.entrySet().stream()
            .collect(Collectors
                .toMap(Map.Entry::getKey,
                       e -> e.getValue().value(terminals.size())));
    }

    /**
     * Generate a spanning tree given a set of links, weights for each
     * link, and a set of essential vertices in the tree.
     * 
     * @param terminals the essential vertices in the tree
     * 
     * @param links the available links and their weights
     * 
     * @param <V> the vertex type
     * 
     * @return a set of links that form a tree connecting all the
     * essential vertices, or {@code null} if no spanning tree could be
     * formed connecting all terminals
     */
    public static <V> Collection<Edge<V>>
        span(Collection<? extends V> terminals,
             Map<? extends Edge<V>, ? extends Number> links) {
        return span(terminals, links, vertex -> {}, edge -> true);
    }

    /**
     * Generate a spanning tree given a set of links, weights for each
     * link, and a set of essential vertices in the tree.
     * 
     * @param terminals the essential vertices in the tree
     * 
     * @param links the available links and their weights
     *
     * @param onReached informed each time a vertex is reached
     *
     * @param edgeChecker consulted before adding an edge to a candidate
     * set
     * 
     * @param <V> the vertex type
     * 
     * @return a set of links that form a tree connecting all the
     * essential vertices, or {@code null} if no spanning tree could be
     * formed connecting all terminals
     */
    public static <V> Collection<Edge<V>>
        span(Collection<? extends V> terminals,
             Map<? extends Edge<V>, ? extends Number> links,
             Consumer<? super V> onReached,
             Predicate<? super Edge<V>> edgeChecker) {
        /* Get a set of edges connected to each vertex. */
        Map<V, Collection<Edge<V>>> outwards = new HashMap<>();
        for (Edge<V> link : links.keySet()) {
            outwards.computeIfAbsent(link.first(), k -> new HashSet<>())
                .add(link);
            outwards.computeIfAbsent(link.second(), k -> new HashSet<>())
                .add(link);
        }

        /* Keep track of vertices required. */
        Collection<V> required = new HashSet<>(terminals);

        /* Keep a set of vertices already included. */
        Collection<V> reached = new HashSet<>();

        /* Keep track of links immediately available. */
        Collection<Edge<V>> reachable = new HashSet<>();

        /* Keep track of links included in the result. */
        Collection<Edge<V>> result = new HashSet<>();

        /* Start by choosing one of the terminal vertices to be
         * added. */
        V adding = terminals.iterator().next();

        for (;;) {
            /* All edges from the new vertex are new candidates for the
             * next hop. */
            reachable.addAll(outwards.getOrDefault(adding,
                                                   Collections.emptySet()));

            /* Add the chosen vertex, and indicate that it has been
             * reached, stopping if we've now reached all terminals.
             * Abort if there are no more options. */
            reached.add(adding);
            onReached.accept(adding);
            required.remove(adding);
            if (required.isEmpty()) break;
            if (reachable.isEmpty()) return null;

            /* Choose another vertex, the nearest to the tree we have so
             * far. */
            double bestDistance = Double.POSITIVE_INFINITY;
            Edge<V> bestLink = null;
            for (Iterator<Edge<V>> iter = reachable.iterator(); iter
                .hasNext();) {
                Edge<V> cand = iter.next();

                /* Drop this edge altogether if both ends have been
                 * reached. */
                if ((reached.contains(cand.first())
                    && reached.contains(cand.second()))
                    || !edgeChecker.test(cand)) {
                    iter.remove();
                    continue;
                }

                double distance = links.get(cand).doubleValue();
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestLink = cand;
                }
            }

            /* If there's no other hop to make, we've failed. */
            if (bestLink == null) {
                assert !required.isEmpty();
                return null;
            }

            /* Add the link to the result, and don't use it again. */
            result.add(bestLink);
            reachable.remove(bestLink);

            /* Ensure that vertices reachable one hop from this link are
             * considered. */
            if (reached.contains(bestLink.first()))
                adding = bestLink.second();
            else
                adding = bestLink.first();
        }

        /* Get rid of useless spurs. */
        prune(terminals, result);

        return result;
    }

    /**
     * Get a set of adjacent vertices for each vertex, given a set of
     * edges.
     * 
     * @param edges the set of edges
     * 
     * @return the adjacencies for each vertex mentioned in the graph
     */
    public static <V> Map<V, Collection<V>>
        getAdjacencies(Collection<? extends Edge<V>> edges) {
        Map<V, Collection<V>> result = new HashMap<>();
        for (Edge<V> edge : edges) {
            result.computeIfAbsent(edge.first(), k -> new HashSet<>())
                .add(edge.second());
            result.computeIfAbsent(edge.second(), k -> new HashSet<>())
                .add(edge.first());
        }
        return result;
    }

    /**
     * For each vertex mentioned in a set of adjacencies, get the sets
     * of reachable vertices from each vertex.
     * 
     * @param adjacencies the set of adjacencies
     * 
     * @return the set of groups, indexed by members
     */
    public static <V> Map<V, Collection<V>>
        getGroups(Map<? extends V, ? extends Collection<? extends V>> adjacencies) {
        Map<V, Collection<? extends V>> copy = new HashMap<>(adjacencies);
        Map<V, Collection<V>> result = new HashMap<>();
        while (!copy.isEmpty()) {
            Map.Entry<V, Collection<? extends V>> entry =
                next(copy.entrySet());
            Collection<V> group = new HashSet<>();
            group.add(entry.getKey());
            Collection<V> remaining = new HashSet<>(entry.getValue());
            while (!remaining.isEmpty()) {
                V rem = next(remaining);
                if (!group.add(rem)) continue;
                remaining.addAll(copy.remove(rem));
            }
            for (V mem : group)
                result.put(mem, group);
        }
        return result;
    }

    /**
     * Remove and acquire an arbitrary element from a collection.
     * 
     * @param coll the collection to be modified
     * 
     * @return the removed element
     * 
     * @throws NoSuchElementException if the collection is empty
     */
    private static <E> E next(Collection<E> coll) {
        Iterator<E> iter = coll.iterator();
        E r = iter.next();
        iter.remove();
        return r;
    }
}
