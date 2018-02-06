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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Creates spanning trees over a graph.
 * 
 * @param <V> the vertex type
 * 
 * @author simpsons
 */
public final class SpanningTreeComputer<V> {
    /**
     * Collects parameters for building a spanning tree.
     * 
     * @param <V> the vertex type
     * 
     * @author simpsons
     */
    public static final class Builder<V> {
        Collection<? extends Edge<V>> edges;
        Collection<? extends V> terminals;
        Consumer<? super V> onReached = v -> {};
        Predicate<? super Edge<V>> edgeEliminator = e -> false;
        boolean inverted;
        Comparator<? super Edge<V>> edgePreference;

        private Builder() {}

        /**
         * Specify the edges of the graph.
         * 
         * @param edges the edges of the graph
         * 
         * @return this object
         */
        public Builder<V> withEdges(Collection<? extends Edge<V>> edges) {
            this.edges = edges;
            return this;
        }

        /**
         * Specify the terminals, the subset of vertices that must be
         * spanned.
         * 
         * @param terminals the vertices to be spanned
         * 
         * @return this object
         */
        public Builder<V> withTerminals(Collection<? extends V> terminals) {
            this.terminals = terminals;
            return this;
        }

        /**
         * Specify an object to be informed whenever a vertex is
         * reached.
         * 
         * @param observer the object to be informed
         * 
         * @return this object
         */
        public Builder<V> notifying(Consumer<? super V> observer) {
            this.onReached = observer;
            return this;
        }

        /**
         * Specify no notification of vertices being reached.
         * 
         * @return this object
         */
        public Builder<V> notNotifying() {
            return notifying(v -> {});
        }

        /**
         * Set the inversion status of the spanner. When inverted,
         * larger weights are preferred over smaller ones.
         * 
         * @param status the new inversion status
         * 
         * @return this object
         */
        public Builder<V> inverted(boolean status) {
            inverted = status;
            return this;
        }

        /**
         * Specify a way to eliminate candidate edges.
         * 
         * @param edgeEliminator an object returning {@code true} iff
         * the supplied edge is no longer eligible
         * 
         * @return this object
         */
        public Builder<V>
            eliminating(Predicate<? super Edge<V>> edgeEliminator) {
            this.edgeEliminator = edgeEliminator;
            return this;
        }

        /**
         * Specify that all original edges remain available during
         * spanning.
         * 
         * @return this object
         */
        public Builder<V> notEliminating() {
            return this.eliminating(e -> false);
        }

        /**
         * Specify the weights of the edges.
         * 
         * @param weights the edge weights
         * 
         * @return this object
         */
        public Builder<V>
            withWeights(Function<? super Edge<V>, ? extends Number> weights) {
            Comparator<Edge<V>> pref = (a, b) -> {
                double av = weights.apply(a).doubleValue();
                double bv = weights.apply(b).doubleValue();
                return Double.compare(av, bv);
            };
            return this.withEdgePreference(pref);
        }

        /**
         * Specify the edges of the graph, with weights.
         * 
         * @param weightedEdges a map from edge to weight
         * 
         * @return this object
         */
        public Builder<V>
            withWeightedEdges(Map<? extends Edge<V>, ? extends Number> weightedEdges) {
            return withEdges(weightedEdges.keySet())
                .withWeights(weightedEdges::get);
        }

        /**
         * Specify how to select one edge over another. The supplied
         * comparator should return negative if its first operand is
         * better, positive if its second is better, or zero if there is
         * no distinction.
         * 
         * @param preference a comparator that identifies earlier edges
         * as preferable
         * 
         * @return this object
         */
        public Builder<V>
            withEdgePreference(Comparator<? super Edge<V>> preference) {
            this.edgePreference = preference;
            return this;
        }

        /**
         * Create the spanner.
         * 
         * @return the new spanner
         */
        public SpanningTreeComputer<V> create() {
            return new SpanningTreeComputer<>(this);
        }
    }

    /**
     * Start collecting parameters for computing a spanning tree.
     * 
     * @return a fresh parameter collector
     */
    public static <V> Builder<V> start(Class<V> type) {
        return new Builder<>();
    }

    private SpanningTreeComputer(Builder<V> builder) {
        this.edges = builder.edges;
        this.edgeEliminator = builder.edgeEliminator;
        this.onReached = builder.onReached;
        this.terminals = builder.terminals;
        this.edgePreference = builder.inverted
            ? builder.edgePreference.reversed() : builder.edgePreference;
    }

    private final Collection<? extends Edge<V>> edges;
    private final Collection<? extends V> terminals;
    private final Consumer<? super V> onReached;
    private final Predicate<? super Edge<V>> edgeEliminator;
    private final Comparator<? super Edge<V>> edgePreference;

    /**
     * Get a spanning tree across the terminals, starting from one of
     * the terminals. The initial vertex is chosen arbitrarily.
     * 
     * @return a spanning tree connecting all terminals, or {@code null}
     * if none found
     */
    public Collection<Edge<V>> getSpanningTree() {
        return getSpanningTree(terminals.iterator().next());
    }

    /**
     * Get a spanning tree across the terminals, with a given starting
     * point.
     * 
     * @param start the starting point
     * 
     * @return a spanning tree connecting all terminals, or {@code null}
     * if none found
     */
    public Collection<Edge<V>> getSpanningTree(V start) {
        /* Get a set of edges connected to each vertex. */
        Map<V, Collection<Edge<V>>> outwards = new HashMap<>();
        for (Edge<V> link : edges) {
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
        V adding = start;
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
            Edge<V> bestLink = null;
            for (Iterator<Edge<V>> iter = reachable.iterator(); iter
                .hasNext();) {
                Edge<V> cand = iter.next();

                /* Drop this edge altogether if both ends have been
                 * reached, or the caller says the edge is otherwise no
                 * longer available. */
                if ((reached.contains(cand.first())
                    && reached.contains(cand.second()))
                    || edgeEliminator.test(cand)) {
                    iter.remove();
                    continue;
                }

                if (bestLink == null
                    || edgePreference.compare(cand, bestLink) < 0) {
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
        Graphs.prune(terminals, result);

        return result;
    }
}
