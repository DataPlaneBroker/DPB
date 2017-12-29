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
package uk.ac.lancs.treespan;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Holds various algorithms pertaining to spanning trees.
 * 
 * @author simpsons
 */
public final class Spans {
    private Spans() {}

    /**
     * Generate forwarding information bases for all nodes in an
     * undirected weighted graph for a subset of destinations.
     * 
     * @param primaries the subset of destinations that will be the
     * keysets of each FIB
     * 
     * @param links the undirected weighted graph
     * 
     * @return the FIBs of all nodes in the graph
     */
    public static <N> Map<N, Map<N, Way<N>>>
        route(Collection<? extends N> primaries,
              Map<Pair<N>, ? extends Number> links) {
        /* From the provided links, for each node, create a set of
         * neighbours. */
        Map<N, Map<N, Double>> neighbours = new HashMap<>();
        for (Map.Entry<Pair<N>, ? extends Number> entry : links.entrySet()) {
            Pair<N> link = entry.getKey();
            double weight = entry.getValue().doubleValue();
            neighbours.computeIfAbsent(link.first, k -> new HashMap<>())
                .put(link.second, weight);
            neighbours.computeIfAbsent(link.second, k -> new HashMap<>())
                .put(link.first, weight);
        }

        /* Keep track of nodes with out-of-date tables, and start with
         * the primaries. */
        Collection<N> laggingNodes = new LinkedHashSet<>(primaries);

        /* Keep updating nodes' FIBs until none are out-of-date. */
        Map<N, Map<N, Way<N>>> result = new HashMap<>();
        while (!laggingNodes.isEmpty()) {
            /* Pick and remove one of the lagging nodes, and recompute
             * its FIB. */
            N updating = next(laggingNodes);
            Map<N, Way<N>> newFib = new HashMap<>();
            if (primaries.contains(updating))
                newFib.put(updating, new Way<N>(null, 0.0));
            Map<N, Double> imms =
                neighbours.getOrDefault(updating, Collections.emptyMap());
            for (Map.Entry<N, Double> entry : imms.entrySet()) {
                N neigh = entry.getKey();
                double weight = entry.getValue();
                Map<N, Way<N>> neighFib =
                    result.computeIfAbsent(neigh, k -> new HashMap<>());
                for (Map.Entry<N, Way<N>> subway : neighFib.entrySet()) {
                    N dest = subway.getKey();
                    Way<N> way = subway.getValue();
                    double newDist = way.distance + weight;
                    Way<N> best = newFib.get(dest);
                    if (best == null || newDist < best.distance)
                        newFib.put(dest, new Way<N>(neigh, newDist));
                }
            }

            /* Compare the new and old FIBs. If they differ, invalidate
             * neighbours. */
            Map<N, Way<N>> fib = result.get(updating);
            if (fib == null || !fib.equals(newFib)) {
                result.put(updating, newFib);
                laggingNodes.addAll(imms.keySet());
            }
        }

        return result;
    }

    public static void main(String[] args) throws Exception {
        Map<Pair<String>, Double> graph = new HashMap<>();
        graph.put(Pair.of("A", "D"), 1.0);
        graph.put(Pair.of("E", "D"), 1.0);
        graph.put(Pair.of("E", "F"), 1.0);
        graph.put(Pair.of("E", "G"), 1.0);
        graph.put(Pair.of("G", "F"), 1.0);
        graph.put(Pair.of("G", "H"), 1.0);
        graph.put(Pair.of("H", "I"), 1.0);
        graph.put(Pair.of("I", "D"), 1.0);
        graph.put(Pair.of("I", "J"), 1.0);
        graph.put(Pair.of("J", "H"), 1.0);
        graph.put(Pair.of("F", "C"), 1.0);
        graph.put(Pair.of("B", "H"), 1.0);
        System.out.printf("Original graph: %s%n", graph);

        Collection<String> primaries =
            new HashSet<>(Arrays.asList("A", "B", "C"));
        prune(primaries, graph.keySet());
        System.out.printf("%nPruned graph: %s for %s%n", graph, primaries);
        System.out.println("  (should be unchanged)");

        Map<String, Map<String, Way<String>>> fibs = route(primaries, graph);
        System.out.printf("%nFIBs: %s%n", fibs);

        Map<Pair<String>, Double> weights = flatten(fibs);
        System.out.printf("%nSpan-weighted graph: %s%n", weights);

        Collection<Pair<String>> tree = span(primaries, weights);
        System.out.printf("%nSpanning tree: %s%n", tree);
    }

    /**
     * Prune useless spurs from an undirected graph.
     * 
     * @param primaries the set of nodes that prevent otherwise useless
     * spurs from being removed
     * 
     * @param links the set of links to be modified
     * 
     * @param <N> the node type
     */
    public static <N> void prune(Collection<? extends N> primaries,
                                 Collection<Pair<N>> links) {
        /* Count how many links each node has. */
        Map<N, Collection<N>> graph = new HashMap<>();
        for (Pair<N> p : links) {
            graph.computeIfAbsent(p.first, k -> new HashSet<>())
                .add(p.second);
            graph.computeIfAbsent(p.second, k -> new HashSet<>())
                .add(p.first);
        }

        /* Identify nodes to be removed. */
        Collection<N> candidates = graph.entrySet().stream()
            .filter(e -> e.getValue().size() <= 1
                && !primaries.contains(e.getKey()))
            .map(Map.Entry::getKey).collect(Collectors.toSet());

        while (!candidates.isEmpty()) {
            Collection<N> current = candidates;
            candidates = new HashSet<>();
            for (N cand : current) {
                /* Tell this candidate's neighbours to lose a link. */
                for (N neigh : graph.remove(cand)) {
                    links.remove(Pair.of(cand, neigh));
                    Collection<N> neighNeigh = graph.get(neigh);
                    neighNeigh.remove(cand);
                    if (neighNeigh.size() <= 1 && !primaries.contains(neigh))
                        candidates.add(neigh);
                }
            }
        }
    }

    /**
     * Given the forwarding information bases of all nodes, create an
     * undirected weighted graph suitable for choosing a spanning tree
     * from.
     * 
     * @param fibs the FIBs of each node, the first key being current
     * node, the second being destination, and the value being the next
     * hop and distance to the destination
     * 
     * @return the weights for each link
     */
    public static <N> Map<Pair<N>, Double>
        flatten(Map<? extends N, ? extends Map<? extends N, ? extends Way<N>>> fibs) {
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
        Map<Pair<N>, Tally> ratios = new HashMap<>(fibs.size());
        Collection<N> primaries = new HashSet<>();
        for (Map.Entry<? extends N, ? extends Map<? extends N, ? extends Way<N>>> nodeFib : fibs
            .entrySet()) {
            N first = nodeFib.getKey();
            Map<? extends N, ? extends Way<N>> fib = nodeFib.getValue();
            for (Map.Entry<? extends N, ? extends Way<N>> entry : fib
                .entrySet()) {
                primaries.add(entry.getKey());
                Way<N> way = entry.getValue();
                N second = way.nextHop;
                if (second == null) continue;
                Tally t = ratios.computeIfAbsent(Pair.of(first, second),
                                                 k -> new Tally());
                t.add(way.distance);
            }
        }

        return ratios.entrySet().stream()
            .collect(Collectors
                .toMap(Map.Entry::getKey,
                       e -> e.getValue().value(primaries.size())));
    }

    /**
     * Generate a spanning tree given a set of links, weights for each
     * link, and a set of essential nodes in the tree.
     * 
     * @param primaries the essential nodes in the tree
     * 
     * @param links the available links and their weights
     * 
     * @return a set of links that form a tree connecting all the
     * essential nodes
     */
    public static <N> Collection<Pair<N>>
        span(Collection<? extends N> primaries,
             Map<Pair<N>, ? extends Number> links) {
        /* Keep track of nodes required. */
        Collection<N> required = new HashSet<>(primaries);

        /* Keep a set of nodes already included. */
        Collection<N> reached = new HashSet<>();

        /* Keep track of links immediately available. */
        Collection<Pair<N>> reachable = new HashSet<>();

        /* Keep track of links included and available. */
        Collection<Pair<N>> available = new HashSet<>(links.keySet());
        Collection<Pair<N>> result = new HashSet<>();

        /* Start by adding one of the primaries. */
        N adding = primaries.iterator().next();
        for (;;) {
            /* Add this node. */
            for (Iterator<Pair<N>> iter = available.iterator(); iter
                .hasNext();) {
                Pair<N> rem = iter.next();
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

            /* Choose another node, the nearest to the tree we have so
             * far. */
            double bestDistance = Double.POSITIVE_INFINITY;
            Pair<N> bestLink = null;
            for (Pair<N> cand : reachable) {
                double distance = links.get(cand).doubleValue();
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestLink = cand;
                }
            }

            /* Add the link to the result, and don't use it again. */
            result.add(bestLink);
            reachable.remove(bestLink);

            /* Ensure that nodes reachable one hop from this link are
             * considered. */
            if (reached.contains(bestLink.first))
                adding = bestLink.second;
            else
                adding = bestLink.first;
        }

        /* Get rid of useless spurs. */
        prune(primaries, result);

        return result;
    }

    private static <E> E next(Collection<E> coll) {
        Iterator<E> iter = coll.iterator();
        E r = iter.next();
        iter.remove();
        return r;
    }
}
