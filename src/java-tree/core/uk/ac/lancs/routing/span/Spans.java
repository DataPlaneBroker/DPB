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
import java.util.Map;
import java.util.NoSuchElementException;
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
 * user. (Bandwidth attribute can then be discarded.) Then call
 * {@link #prune(Collection, Collection)} to eliminate spurs that do not
 * reach the terminals. Then call {@link #route(Collection, Map)},
 * {@link #flatten(Map)} and {@link #span(Collection, Map)} as above.
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
     * @param terminals the subset of destinations that will be the
     * keysets of each FIB
     * 
     * @param links the undirected weighted graph
     * 
     * @param <V> the vertex type
     * 
     * @return the FIBs of all vertices in the graph
     */
    public static <V> Map<V, Map<V, Way<V>>>
        route(Collection<? extends V> terminals,
              Map<Edge<V>, ? extends Number> links) {
        /* From the provided links, for each vertex, create a set of
         * neighbours. */
        Map<V, Map<V, Double>> neighbours = new HashMap<>();
        for (Map.Entry<Edge<V>, ? extends Number> entry : links.entrySet()) {
            Edge<V> link = entry.getKey();
            double weight = entry.getValue().doubleValue();
            neighbours.computeIfAbsent(link.first, k -> new HashMap<>())
                .put(link.second, weight);
            neighbours.computeIfAbsent(link.second, k -> new HashMap<>())
                .put(link.first, weight);
        }

        /* Keep track of vertices with out-of-date tables, and start
         * with the terminals. */
        Collection<V> laggingNodes = new LinkedHashSet<>(terminals);

        /* Keep updating vertices' FIBs until none are out-of-date. */
        Map<V, Map<V, Way<V>>> result = new HashMap<>();
        while (!laggingNodes.isEmpty()) {
            /* Pick and remove one of the lagging vertices, and
             * recompute its FIB. */
            V updating = next(laggingNodes);
            Map<V, Way<V>> newFib = new HashMap<>();
            if (terminals.contains(updating))
                newFib.put(updating, new Way<V>(null, 0.0));
            Map<V, Double> imms =
                neighbours.getOrDefault(updating, Collections.emptyMap());
            for (Map.Entry<V, Double> entry : imms.entrySet()) {
                V neigh = entry.getKey();
                double weight = entry.getValue();
                Map<V, Way<V>> neighFib =
                    result.computeIfAbsent(neigh, k -> new HashMap<>());
                for (Map.Entry<V, Way<V>> subway : neighFib.entrySet()) {
                    V dest = subway.getKey();
                    Way<V> way = subway.getValue();
                    double newDist = way.distance + weight;
                    Way<V> best = newFib.get(dest);
                    if (best == null || newDist < best.distance)
                        newFib.put(dest, new Way<V>(neigh, newDist));
                }
            }

            /* Compare the new and old FIBs. If they differ, invalidate
             * neighbours. */
            Map<V, Way<V>> fib = result.get(updating);
            if (fib == null || !fib.equals(newFib)) {
                result.put(updating, newFib);
                laggingNodes.addAll(imms.keySet());
            }
        }

        return result;
    }

    /**
     * @undocumented
     */
    public static void main(String[] args) throws Exception {
        Map<Edge<String>, Double> graph = new HashMap<>();
        graph.put(Edge.of("A", "D"), 1.0);
        graph.put(Edge.of("E", "D"), 1.0);
        graph.put(Edge.of("E", "F"), 1.0);
        graph.put(Edge.of("E", "G"), 1.0);
        graph.put(Edge.of("G", "F"), 1.0);
        graph.put(Edge.of("G", "H"), 1.0);
        graph.put(Edge.of("H", "I"), 1.0);
        graph.put(Edge.of("I", "D"), 1.0);
        graph.put(Edge.of("I", "J"), 1.0);
        graph.put(Edge.of("J", "H"), 1.0);
        graph.put(Edge.of("F", "C"), 1.0);
        graph.put(Edge.of("B", "H"), 1.0);
        System.out.printf("Original graph: %s%n", graph);

        Collection<String> terminals =
            new HashSet<>(Arrays.asList("A", "B", "C"));
        Spans.prune(terminals, graph.keySet());
        System.out.printf("%nPruned graph: %s for %s%n", graph, terminals);
        System.out.println("  (should be unchanged)");

        Map<String, Map<String, Way<String>>> fibs =
            Spans.route(terminals, graph);
        System.out.printf("%nFIBs: %s%n", fibs);

        Map<Edge<String>, Double> weights = Spans.flatten(fibs);
        System.out.printf("%nSpan-weighted graph: %s%n", weights);

        Collection<Edge<String>> tree = Spans.span(terminals, weights);
        System.out.printf("%nSpanning tree: %s%n", tree);
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
            graph.computeIfAbsent(p.first, k -> new HashSet<>())
                .add(p.second);
            graph.computeIfAbsent(p.second, k -> new HashSet<>())
                .add(p.first);
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
        flatten(Map<? extends V, ? extends Map<? extends V, ? extends Way<V>>> fibs) {
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
        Map<Edge<V>, Tally> ratios = new HashMap<>(fibs.size());
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
                Tally t = ratios.computeIfAbsent(Edge.of(first, second),
                                                 k -> new Tally());
                t.add(way.distance);
            }
        }

        return ratios.entrySet().stream()
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
     * essential vertices
     */
    public static <V> Collection<Edge<V>>
        span(Collection<? extends V> terminals,
             Map<Edge<V>, ? extends Number> links) {
        /* Keep track of vertices required. */
        Collection<V> required = new HashSet<>(terminals);

        /* Keep a set of vertices already included. */
        Collection<V> reached = new HashSet<>();

        /* Keep track of links immediately available. */
        Collection<Edge<V>> reachable = new HashSet<>();

        /* Keep track of links included and available. */
        Collection<Edge<V>> available = new HashSet<>(links.keySet());
        Collection<Edge<V>> result = new HashSet<>();

        /* Start by adding one of the terminals. */
        V adding = terminals.iterator().next();
        for (;;) {
            /* Add this vertex. */
            for (Iterator<Edge<V>> iter = available.iterator(); iter
                .hasNext();) {
                Edge<V> rem = iter.next();
                if ((rem.first.equals(adding)
                    && !reached.contains(rem.second))
                    || (rem.second.equals(adding)
                        && !reached.contains(rem.first))) {
                    reachable.add(rem);
                    iter.remove();
                }
            }
            reached.add(adding);
            required.remove(adding);
            if (required.isEmpty()) break;

            /* Choose another vertex, the nearest to the tree we have so
             * far. */
            double bestDistance = Double.POSITIVE_INFINITY;
            Edge<V> bestLink = null;
            for (Edge<V> cand : reachable) {
                double distance = links.get(cand).doubleValue();
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestLink = cand;
                }
            }

            /* Add the link to the result, and don't use it again. */
            result.add(bestLink);
            reachable.remove(bestLink);

            /* Ensure that vertices reachable one hop from this link are
             * considered. */
            if (reached.contains(bestLink.first))
                adding = bestLink.second;
            else
                adding = bestLink.first;
        }

        /* Get rid of useless spurs. */
        prune(terminals, result);

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
