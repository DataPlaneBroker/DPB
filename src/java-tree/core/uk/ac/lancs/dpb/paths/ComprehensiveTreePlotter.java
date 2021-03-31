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

package uk.ac.lancs.dpb.paths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import uk.ac.lancs.dpb.bw.BandwidthFunction;
import uk.ac.lancs.dpb.bw.BandwidthPair;
import uk.ac.lancs.dpb.bw.BandwidthRange;

/**
 * @param <V> the vertex type
 *
 * @author simpsons
 */
public class ComprehensiveTreePlotter implements TreePlotter {
    private static <E> IntFunction<E[]> newArray() {
        @SuppressWarnings("unchecked")
        IntFunction<E[]> result = size -> (E[]) new Object[size];
        return result;
    }

    private static <E> Iterable<Map.Entry<E, Integer>>
        over(Iterable<? extends E> base) {
        return () -> new Iterator<Map.Entry<E, Integer>>() {
            private final Iterator<? extends E> innerBase = base.iterator();

            private int i = 0;

            @Override
            public boolean hasNext() {
                return innerBase.hasNext();
            }

            @Override
            public Map.Entry<E, Integer> next() {
                final E next = innerBase.next();
                final int cur = i++;
                return new Map.Entry<>() {
                    @Override
                    public E getKey() {
                        return next;
                    }

                    @Override
                    public Integer getValue() {
                        return cur;
                    }

                    @Override
                    public Integer setValue(Integer value) {
                        throw new UnsupportedOperationException("unimplemented");
                    }
                };
            }
        };
    }

    private static <E> Stream<Map.Entry<E, Integer>>
        indexedStream(Iterable<E> base) {
        return StreamSupport.stream(over(base).spliterator(), false);
    }

    private static <E> Iterable<Map.Entry<E, Integer>> over(E[] arr) {
        return () -> new Iterator<Map.Entry<E, Integer>>() {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < arr.length;
            }

            @Override
            public Map.Entry<E, Integer> next() {
                int cur = i++;
                return new Map.Entry<E, Integer>() {
                    @Override
                    public E getKey() {
                        return arr[cur];
                    }

                    @Override
                    public Integer getValue() {
                        return cur;
                    }

                    @Override
                    public Integer setValue(Integer value) {
                        throw new UnsupportedOperationException("unimplemented");
                    }

                };
            }
        };
    }

    private static <E> Stream<Map.Entry<E, Integer>> indexedStream(E[] arr) {
        return StreamSupport.stream(over(arr).spliterator(), false);
    }

    private static BitSet of(int pattern) {
        return BitSet.valueOf(new long[] { pattern });
    }

    private static BitSet xor(BitSet a, BitSet b) {
        BitSet result = new BitSet();
        result.or(a);
        result.xor(b);
        return result;
    }

    /**
     * Get the modes that each edge has enough capacity to support.
     * 
     * @param <V> the vertex type
     * 
     * @param modeCache a mapping from mode minus 1 to goal set
     * 
     * @param bwreq an expression of bandwidth load on an edge given the
     * set of goals reachable from one end
     * 
     * @param edges the set of edges
     * 
     * @return a map from edge to goal set to bandwidth load. Only edges
     * with at least one viable mode are included.
     */
    private static <V> Map<Edge<V>, Map<BitSet, BandwidthPair>>
        getEdgeModes(List<? extends BitSet> modeCache, BandwidthFunction bwreq,
                     Collection<? extends Edge<V>> edges) {
        final int modes = modeCache.size();
        Map<Edge<V>, Map<BitSet, BandwidthPair>> tmp = new IdentityHashMap<>();
        for (var edge : edges) {
            for (int mode = 1; mode <= modes; mode++) {
                /* Work out the bandwidth in the forward direction of
                 * the edge. Skip this mode if it exceeds the edge
                 * ingress capacity. */
                BitSet fwd = modeCache.get(mode - 1);
                BandwidthRange forwardDemand = bwreq.apply(fwd);
                if (forwardDemand.min() > edge.metrics.ingress.min()) continue;

                /* Work out the bandwidth in the reverse direction of
                 * the edge egress capacity. */
                BitSet rev = modeCache.get(modes - mode - 1);
                BandwidthRange inverseDemand = bwreq.apply(rev);
                if (inverseDemand.min() > edge.metrics.egress.min()) continue;

                /* The edge can be used in this mode. Retain this fact,
                 * and cache the amount of bandwidth it would use in
                 * that mode. */
                tmp.computeIfAbsent(edge, e -> new HashMap<>())
                    .put(fwd, BandwidthPair.of(forwardDemand, inverseDemand));
            }
        }
        return Map.copyOf(tmp);
    }

    /**
     * Get the order of edges that indexes an array of those edges'
     * modes. Each element of the array will iterate from zero to the
     * number of modes supported by its corresponding edge. The whole
     * array forms a multi-base number, with its first element being the
     * least significant digit. Although any order will do, we should be
     * able to improve performance in two ways:
     * 
     * <ol>
     * 
     * <li>Edges attached to the same vertex should be as close together
     * in the array order as possible. These edges will have mutual
     * constraints on their modes, so if one changes and the constraints
     * have to be checked again, you should get round to checking the
     * constraints as soon as possible.
     * 
     * <li>Edges nearer (by hops) to the goals should be represented by
     * more significant digits than those further. These edges are going
     * to have fewer options, so you want to iterate over them as few
     * times as possible.
     * 
     * </ol>
     * 
     * @param <V> the vertex type
     * 
     * @param goals the set of vertices that must be connected
     * 
     * @param inwards the set of edges whose finishing point is a given
     * vertex
     * 
     * @param outwards the set of edges whose starting point is a given
     * vertex
     * 
     * @param modeCount the number of modes that an edge supports
     * 
     * @return an optimized order of edges for iterating over all
     * combinations of modes
     */
    private static <V> List<Edge<V>>
        getEdgeOrder(Collection<? extends V> goals,
                     Map<? super V,
                         ? extends Collection<? extends Edge<V>>> inwards,
                     Map<? super V,
                         ? extends Collection<? extends Edge<V>>> outwards,
                     ToIntFunction<? super Edge<V>> modeCount) {
        Collection<Edge<V>> reachOrder = new LinkedHashSet<>();
        Collection<V> reachables = new HashSet<>();
        Collection<V> newReachables = new LinkedHashSet<>();
        newReachables.addAll(goals);
        while (!newReachables.isEmpty()) {
            /* Pick a vertex that we haven't handled yet, and mark it
             * has handled. */
            V vertex = newReachables.iterator().next();
            assert !reachables.contains(vertex);
            reachables.add(vertex);

            /* Find all neighbours of the vertex. */
            Collection<V> cands = new HashSet<>();
            Collection<? extends Edge<V>> inEdges = inwards.get(vertex);
            cands.addAll(inEdges.stream().map(e -> e.start)
                .collect(Collectors.toSet()));
            Collection<? extends Edge<V>> outEdges = outwards.get(vertex);
            cands.addAll(outEdges.stream().map(e -> e.finish)
                .collect(Collectors.toSet()));

            /* Ensure the edges are accounted for. */
            reachOrder.addAll(inEdges);
            reachOrder.addAll(outEdges);

            /* Exclude neighbours that have already been handled. */
            cands.removeAll(reachables);

            /* Add the remaining neighbours to the set yet to be
             * handled. */
            newReachables.addAll(cands);
        }

        /* Extract the order in which edges were added. Reverse it, so
         * that the ones nearest the goals are going to change the
         * least. */
        List<Edge<V>> edgeOrder = new ArrayList<>(reachOrder);
        Collections.reverse(edgeOrder);
        return List.copyOf(edgeOrder);
    }

    private static class Constraint {
        final int[] edges;

        final int[] allowed;

        Constraint(int[] edges, int[] allowed) {
            this.edges = edges;
            this.allowed = allowed;
            assert allowed.length % edges.length == 0;
        }

        boolean check(int[] digits) {
            next_combo: for (int c = 0; c < allowed.length; c += edges.length) {
                /* All edges must have the correspond mode specified at
                 * allowed[c]..., or we must try another combination. */
                for (int i = 0; i < edges.length; i++)
                    if (digits[edges[i]] != allowed[c + i]) continue next_combo;
                /* At least one combination matches the current
                 * situation. */
                return true;
            }

            /* No combinations matched. */
            return false;
        }
    }

    private static class Status {
        final int edge;

        final int mode;

        Status(int edgeNumber, int mode) {
            this.edge = edgeNumber;
            this.mode = mode;
        }

        boolean passes(int[] digits) {
            return digits[edge] == mode;
        }
    }

    @Override
    public <V>
        Iterable<? extends Map<? extends Edge<V>, ? extends BandwidthPair>>
        plot(List<? extends V> goalOrder, BandwidthFunction bwreq,
             Collection<? extends Edge<V>> edges) {
        goalOrder.get(0);

        /* Compute the number of modes each edge can be used in. This is
         * a bit pattern where the position of each bit identifies a
         * goal vertex. If the bit is set, the goal is reachable by
         * exiting an edge through its finish; if clear, through its
         * start. The mode with all bits set is not used, as it places
         * all goals at one end. The zero mode is similarly useless;
         * however, we co-opt it to indicate an edge that isn't used. */
        final int modes = (1 << goalOrder.size()) - 1;

        /* Create an index from mode number to BitSet, so that we can
         * call the bandwidth function. Remember that index 0 of this
         * list corresponds to mode 1. */
        final List<BitSet> modeCache = IntStream.rangeClosed(1, modes)
            .mapToObj(ComprehensiveTreePlotter::of)
            .collect(Collectors.toList());

        /* Find out which modes each edge can cope with. Edges that
         * can't cope with the requirement in any mode will not be
         * included in the map. */
        final Map<Edge<V>, Map<BitSet, BandwidthPair>> edgeCaps =
            getEdgeModes(modeCache, bwreq, edges);

        /* Map each goal vertex to its zero-based goal index. */
        final Index<V> goalIndex = Index.of(goalOrder);

        /* Identify all vertices implied by edges. For each vertex,
         * identify all incoming edges and all outgoing edges. */
        Map<V, Collection<Edge<V>>> inwards = new IdentityHashMap<>();
        Map<V, Collection<Edge<V>>> outwards = new IdentityHashMap<>();
        final List<V> vertexOrder;
        {
            Collection<V> tmp = new LinkedHashSet<>(goalOrder);
            for (Edge<V> edge : edgeCaps.keySet()) {
                tmp.add(edge.start);
                outwards.computeIfAbsent(edge.start, k -> new HashSet<>())
                    .add(edge);
                tmp.add(edge.finish);
                inwards.computeIfAbsent(edge.finish, k -> new HashSet<>())
                    .add(edge);
            }
            vertexOrder = List.copyOf(tmp);
        }

        /* Assign each edge to a digit in a multi-base number. Index 0
         * will be the least significant digit. */
        final Index<Edge<V>> edgeIndex =
            Index.of(getEdgeOrder(goalOrder, inwards, outwards,
                                  e -> edgeCaps.get(e).size()));

        /* Identify which vertices are goals, indexed by our assigned
         * vertex number. */
        BitSet goalIndices = new BitSet();
        for (int i = 0; i < vertexOrder.size(); i++)
            if (goalIndex.encode().containsKey(vertexOrder.get(i)))
                goalIndices.set(i);

        /* In a perhaps vain attempt at optimization, convert the data
         * into arrays that the iterator will access. */

        @SuppressWarnings("unchecked")
        final V[] goals = goalOrder.toArray(n -> (V[]) new Object[n]);

        /* Create a mapping from mode index to mode pattern for each
         * edge that has at least one valid non-zero mode. */
        final BitSet[][] modeMap = new BitSet[edgeIndex.size()][];
        edgeIndex.stream()
            .map(e -> edgeCaps.get(e).keySet().toArray(n -> new BitSet[n]))
            .toArray(n -> new BitSet[n][]);

        /* Indices: primary edge number; primary edge mode */
        Constraint[][] constraints = new Constraint[edgeIndex.size()][];
        /* TODO: Identify combinations of edges in specific modes that
         * are required. Store them in 'constraints'. Out of any set of
         * requirements, only the lowest number in the edge order will
         * have conditions, and only based on edges with higher
         * numbers. */

        return () -> new Iterator<Map<? extends Edge<V>,
                                      ? extends BandwidthPair>>() {
            /**
             * Holds the next state to be tried. Each entry corresponds
             * to an edge, and is the index into the array of
             * {@code modeMap}. Since each edge can have a different
             * number of valid modes, this array represents a multi-base
             * number.
             * 
             * <p>
             * We have arranged for digits of base 1 to be at the end of
             * the array. Other than that, digits of lower bases appear
             * earlier.
             */
            private final int[] digits = new int[edgeIndex.size()];

            private int invalidated = digits.length;

            private boolean found = false;

            private boolean increment() {
                /* All digits below power must be reset. */
                Arrays.fill(digits, 0, invalidated, 0);

                /* Increase each counter until we don't have to
                 * carry. */
                for (; invalidated < digits.length; invalidated++) {
                    int i = invalidated++;
                    if (++digits[i] < modeMap[i].length) {
                        /* We didn't have to carry. */
                        return true;
                    }
                    /* We have incremented a digit beyond its maximum
                     * value. */

                    /* Abort if we have overflowed the most significant
                     * digit. */
                    if (i + 1 == digits.length) break;

                    /* Reset this digit. */
                    digits[i] = 0;
                }
                return false;
            }

            private boolean requireAll(Status[][][] operands) {
                for (var item : operands)
                    if (!requireAny(item)) return false;
                return true;
            }

            private boolean requireAny(Status[][] operands) {
                for (var item : operands)
                    if (requireAll(item)) return true;
                return false;
            }

            private boolean requireAll(Status[] operands) {
                for (var item : operands)
                    if (!item.passes(digits)) return false;
                return true;
            }

            private boolean ensure() {
                /* Have we already got a solution to delivered by
                 * next()? */
                if (found) return true;
                /* We must find the next solution. */

                /* If the most slowly increasing counter has exceeded
                 * its limit, we are done. */
                if (digits[digits.length - 1]
                    >= modeMap[digits.length - 1].length) return false;

                /* Keep looking for a valid combination. */
                next_combination: do {
                    /* For the edges whose modes have changed, skip if
                     * the edge in its current mode belongs to an
                     * illegal combination. */
                    while (invalidated > 0) {
                        int en = --invalidated;

                        /* Check edge against all higher-numbered edges
                         * for conflicts. All requirements must be met.
                         * There will usually only be two, one for each
                         * end of the edge. */
                        if (!constraints[en][digits[en]].check(digits))
                            continue next_combination;
                        /* All requirements were met, so this edge is
                         * validated with respect to all higher-numbered
                         * edges. */
                    }

                    /* TODO: Ensure that all goals have a connecting
                     * edge in non-zero mode. This might be redundant,
                     * if we properly take account of goal vertices in
                     * deriving edge constraints. */
                } while (increment());
                return false;
            }

            @Override
            public boolean hasNext() {
                return ensure();
            }

            @Override
            public Map<? extends Edge<V>, ? extends BandwidthPair> next() {
                if (!ensure()) throw new NoSuchElementException();

                /* Make sure we look for a new solution after this. */
                found = false;

                /* Convert each of the non-zero edge digits into a mode,
                 * and map it to the computed bandwidth pair for that
                 * edge in that mode. */
                return IntStream.range(0, digits.length)
                    .filter(en -> digits[en] != 0).boxed()
                    .collect(Collectors
                        .toMap(en -> edgeIndex.get(en),
                               en -> edgeCaps.get(edgeIndex.get(en))
                                   .get(modeMap[en][digits[en]])));
            }
        };
    }

}
