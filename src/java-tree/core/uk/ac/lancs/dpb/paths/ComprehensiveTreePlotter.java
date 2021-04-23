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

import java.io.File;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import uk.ac.lancs.dpb.bw.BandwidthFunction;
import uk.ac.lancs.dpb.bw.BandwidthPair;
import uk.ac.lancs.dpb.bw.BandwidthRange;
import uk.ac.lancs.dpb.bw.FlatBandwidthFunction;

/**
 * Enumerates over all possible trees that meet the bandwidth
 * constraints. This works by maintaining an array of 'digits', one per
 * edge, and each specifying the mode in which an edge is being used.
 * For each edge, modes that would incur a use of bandwidth greater than
 * the edge's capacity are eliminated, so the number of modes in each
 * digit may vary. Additionally, combinations of modes of edges
 * surrounding a vertex have constraints placed upon them. These
 * constraints are tested as soon as an edge adopts a new mode. Failure
 * results in the edge mode being incremented, and modes of less
 * significant edges being reset.
 * 
 * <p>
 * This class does not use the cost metric of an edge.
 * 
 * @param <V> the vertex type
 *
 * @author simpsons
 */
public class ComprehensiveTreePlotter implements TreePlotter {
    private static final double biasThreshold = 0.99;

    private static class Path<V> {
        final double distance;

        final V vertex;

        final Path<V> previous;

        Path(double distance, V vertex, Path<V> previous) {
            this.distance = distance;
            this.vertex = vertex;
            this.previous = previous;
        }

        @Override
        public String toString() {
            return String.format("%.3f %s%s", distance, vertex,
                                 previous == null ? "" : ("; " + previous));
        }

        static <V> boolean sameAs(Path<V> a, Path<V> b) {
            if (a == null) return b == null;
            if (b == null) return false;
            if (a.vertex != b.vertex) return false;
            if (a.distance != b.distance) return false;
            return sameAs(a.previous, b.previous);
        }

        static <V> Path<V> root(V root) {
            return new Path<>(0.0, root, null);
        }

        boolean contains(V self) {
            return vertex == self ||
                (previous != null && previous.contains(self));
        }

        static <V> Path<V> append(Path<V> prev, double extension, V self,
                                  Path<V> best) {
            if (prev == null) return null;
            if (prev.contains(self)) return null;
            final double newDist = prev.distance + extension;
            if (best != null && newDist >= best.distance) return null;
            return new Path<>(newDist, self, prev);
        }
    }

    private static class Pair<V1, V2> {
        public final V1 item1;

        public final V2 item2;

        public Pair(V1 item1, V2 item2) {
            this.item1 = item1;
            this.item2 = item2;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 37 * hash + System.identityHashCode(item1);
            hash = 37 * hash + System.identityHashCode(item2);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            final Pair<?, ?> other = (Pair<?, ?>) obj;
            if (this.item1 != other.item1) return false;
            return this.item2 == other.item2;
        }
    }

    private static <E> Iterable<E> remainingIn(Collection<? extends E> coll) {
        return () -> new Iterator<E>() {
            @Override
            public boolean hasNext() {
                return !coll.isEmpty();
            }

            @Override
            public E next() {
                return removeOne(coll);
            }
        };
    }

    /**
     * Create a deep immutable copy of a map, preserving iteration
     * order.
     * 
     * @param <K> the key type
     * 
     * @param <V> the value type
     * 
     * @param input the map to copy
     * 
     * @param op a means to create immutable values
     * 
     * @return an unmodifiable map with the same contents as the input
     * map, but with each value passed through the operator
     */
    private static <K, V> Map<K, V> deepCopy(Map<K, V> input,
                                             UnaryOperator<V> op) {
        Map<K, V> output = new LinkedHashMap<>(input);
        for (var entry : output.entrySet())
            entry.setValue(op.apply(entry.getValue()));
        return Collections.unmodifiableMap(output);
    }

    /**
     * Convert a bit set to a big integer.
     * 
     * @param bs the bit set
     * 
     * @return an equivalent big integer whose
     * {@link BigInteger#testBit(int)} yields the same as the input's
     * {@link BitSet#get(int)}
     */
    private static BigInteger toBigInteger(BitSet bs) {
        byte[] bytes = bs.toByteArray();
        final int lim = bytes.length / 2;
        for (int i = 0; i < lim; i++) {
            byte tmp = bytes[i];
            bytes[i] = bytes[bytes.length - i - 1];
            bytes[bytes.length - i - 1] = tmp;
        }

        /* The top bit of the first byte is interpreted by BigInteger as
         * a sign bit, so ensure it is clear by inserting an extra zero
         * byte at the start. */
        if (bytes.length > 0 && (bytes[0] & 0x80) == 0x80) {
            byte[] tmp = new byte[bytes.length + 1];
            System.arraycopy(bytes, 0, tmp, 1, bytes.length);
            bytes = tmp;
        }

        return new BigInteger(bytes);
    }

    /**
     * Compare two edges' string representations.
     * {@link Object#toString()} is invoked on each argument, and then
     * {@link String#compareTo(String)} is invoked on the result of the
     * first, with the result of the second as its argument.
     * 
     * @param <V> the vertex type
     * 
     * @param a one of the edges
     * 
     * @param b the other edge
     * 
     * @return 0 if the edges have no ordering; positive if the first
     * argument comes later than the second; negative otherwise
     */
    private static <V> int compare(Edge<V> a, Edge<V> b) {
        return a.toString().compareTo(b.toString());
    }

    /**
     * Compare two bit sets by comparing their {@link BigInteger}
     * representations. Each set is converted to a {@link BigInteger}
     * using {@link #toBigInteger(BitSet)}, and then
     * {@link BigInteger#compareTo(BigInteger)} is invoked on the result
     * of the first, with the result of the second as its argument.
     * 
     * @param a one of the bit sets
     * 
     * @param b the other bit set
     * 
     * @return 0 if the bit sets are identical; positive if the first
     * argument's integer representation is greater than the second's;
     * negative otherwise
     */
    private static int compare(BitSet a, BitSet b) {
        return toBigInteger(a).compareTo(toBigInteger(b));
    }

    /**
     * Compare two bit sets by comparing their highest bits. The highest
     * set bits of each argument are identified. If they have the same
     * position, the next highest bits are identified, and their
     * positions are compared; and so on.
     * 
     * @param a one of the bit sets
     * 
     * @param b the other bit set
     * 
     * @return 0 if the bit sets are identical; positive if the first
     * argument's highest non-identical bit to the second's is set;
     * negative otherwise
     */
    private static int fastCompare(BitSet a, BitSet b) {
        int ac = a.cardinality() - 1;
        int bc = b.cardinality() - 1;
        do {
            if (ac > bc) return +1;
            if (bc < ac) return -1;
            ac = a.previousSetBit(ac - 1);
            bc = a.previousSetBit(ac - 1);
        } while (true);
    }

    private static BitSet of(int pattern) {
        return BitSet.valueOf(new long[] { pattern });
    }

    private static <E> E removeOne(Collection<? extends E> coll) {
        Iterator<? extends E> iter = coll.iterator();
        E result = iter.next();
        iter.remove();
        return result;
    }

    private interface Constraint {
        boolean check(IntUnaryOperator digits);

        void verify(int baseEdge);

        IntStream edges();

        boolean isOwn(int en);
    }

    private static <V> String setString(BitSet pat, Index<V> goalIndex) {
        return "{" + pat.stream().mapToObj(goalIndex::get).map(Object::toString)
            .collect(Collectors.joining("+")) + "}";
    }

    @Override
    public <P, V>
        Iterable<? extends Map<? extends Edge<P>, ? extends BandwidthPair>>
        plot(List<? extends V> goalOrder, BandwidthFunction bwreq,
             Function<? super P, ? extends V> portMap,
             Collection<? extends Edge<P>> edges) {
        /* Assign each goal an integer. */
        final Index<V> goalIndex = Index.copyOf(goalOrder);

        /* Compute the number of modes each edge can be used in. Each
         * mode is a bit pattern where the position of each bit
         * identifies a goal vertex in the goalOrder list. If the bit is
         * set, the goal is reachable by exiting an edge through its
         * finish; if clear, through its start. The mode with all bits
         * set is not used, as it places all goals at one end. The zero
         * mode is similarly useless; however, we co-opt it to indicate
         * an edge that isn't used. This number is always 1 less than a
         * power of 2. */
        final int modeCount = (1 << goalIndex.size()) - 1;

        class Routing {
            private final Map<Edge<P>, BitSet> edgeCaps = new LinkedHashMap<>();

            /**
             * Work out how much bandwidth each mode requires, and
             * compare it with each edge, selecting only modes for which
             * the edge has sufficient capacity. Also store the required
             * bandwidth for each edge.
             */
            void eliminateIncapaciousEdgeModes() {
                for (int mode = 1; mode < modeCount; mode++) {
                    /* Work out the bandwidth in the forward (ingress)
                     * and reverse (egrees) directions of the edge. */
                    BitSet fwd = of(mode);
                    BandwidthPair req = bwreq.getPair(fwd);

                    for (var edge : edges) {
                        /* Skip this mode if the ingress demand exceeds
                         * the edge's forward capacity. */
                        if (req.ingress.min() > edge.metrics.ingress.min())
                            continue;

                        /* Skip this mode if the egress demand exceeds
                         * the edge's reverse capacity. */
                        if (req.egress.min() > edge.metrics.egress.min())
                            continue;

                        /* If the edge has a goal as its start, its from
                         * set must include that goal. */
                        {
                            int goal =
                                goalIndex.getAsInt(portMap.apply(edge.start));
                            if (goal >= 0) {
                                assert goal < goalIndex.size();
                                if (!fwd.get(goal)) continue;
                            }
                        }

                        /* If the edge has a goal as its finish, its to
                         * set must include that goal. */
                        {
                            int goal =
                                goalIndex.getAsInt(portMap.apply(edge.finish));
                            if (goal >= 0) {
                                assert goal < goalIndex.size();
                                if (fwd.get(goal)) continue;
                            }
                        }

                        /* The edge can be used in this mode. Retain
                         * this fact. TODO: A plain hash map should
                         * suffice, but introduces perturbations making
                         * fault diagnosis difficult. */
                        edgeCaps.computeIfAbsent(edge, e -> new BitSet())
                            .set(mode);
                    }
                }
            }

            /**
             * Get a deep immutable copy of the selected edges. Call
             * {@link #eliminateIncapaciousEdgeModes()} first.
             */
            Map<Edge<P>, BitSet> getEdgeModes() {
                return deepCopy(edgeCaps, i -> i);
                // return
                // edgeCaps.entrySet().stream().collect(Collectors
                // .toMap(Map.Entry::getKey, e ->
                // Map.copyOf(e.getValue())));
            }

            /**
             * Identifies for each vertex the set of edges that finish
             * at that vertex. {@link #reachEdges()} must be called to
             * populate this.
             */
            private final Map<V, Collection<Edge<P>>> inwards =
                new IdentityHashMap<>();

            /**
             * Identifies for each vertex the set of edges that start at
             * that vertex. {@link #reachEdges()} must be called to
             * populate this.
             */
            private final Map<V, Collection<Edge<P>>> outwards =
                new IdentityHashMap<>();

            /**
             * Holds all reachable vertices, in order of reachability.
             * {@link #reachEdges()} must be called to populate this.
             */
            private final Collection<V> vertexes = new LinkedHashSet<>();

            /**
             * Identify all vertices, and how to walk from a vertex to
             * an edge.
             */
            void deriveVertexes() {
                vertexes.clear();
                vertexes.addAll(goalOrder);
                outwards.clear();
                inwards.clear();

                /* For every edge, record which vertices it connects
                 * to. */
                for (Edge<P> edge : edgeCaps.keySet()) {
                    V start = portMap.apply(edge.start);
                    vertexes.add(start);
                    outwards.computeIfAbsent(start, k -> new HashSet<>())
                        .add(edge);
                    V finish = portMap.apply(edge.finish);
                    vertexes.add(finish);
                    inwards.computeIfAbsent(finish, k -> new HashSet<>())
                        .add(edge);
                }

                /* Some vertices might have only inward edges, and some
                 * only outward, meaning that there might be holes in
                 * the key sets of our maps. Make sure the vertices also
                 * have sets for the opposite direction. */
                for (V v : vertexes) {
                    outwards.computeIfAbsent(v, k -> new HashSet<>());
                    inwards.computeIfAbsent(v, k -> new HashSet<>());
                }
            }

            /**
             * Holds a routing table per vertex, per goal.
             */
            final Map<V, Map<V, Path<V>>> distances = new IdentityHashMap<>();

            Path<V> getDistance(V vertex, V goal) {
                Map<V, Path<V>> dists = distances.get(vertex);
                if (dists == null) return null;
                return dists.get(goal);
            }

            /**
             * Keeps track of which goals in which vertices' routing
             * tables we need to update.
             */
            final Collection<Pair<V, V>> invalidGoals = new LinkedHashSet<>();

            /**
             * Keeps track of goals across edges that might be out of
             * date.
             */
            final Collection<Pair<Edge<P>, V>> invalidEdges =
                new LinkedHashSet<>();

            /**
             * Invalidate an entry for a goal in the distance table of a
             * vertex.
             * 
             * @return {@code true} if a change was made
             */
            boolean invalidateDistance(V vertex, V goal) {
                return invalidGoals.add(new Pair<>(vertex, goal));
            }

            /**
             * Invalidate an edge's bidirectional suitability for a
             * goal.
             * 
             * @return {@code true} if a change was made
             */
            boolean invalidateEdgeGoal(Edge<P> edge, V goal) {
                return invalidEdges.add(new Pair<>(edge, goal));
            }

            void updateDistance(V vertex, V goal) {
                /* We never compute the distance to ourselves. */
                if (vertex == goal) return;

                final int gn = goalIndex.getAsInt(goal);
                final int bit = 1 << gn;
                Path<V> best = null;

                /* Go over all the inward edges, getting the best
                 * distance. Only use an edge if at least one of the
                 * 'from' sets of its remaining modes includes the
                 * goal. */
                for (Edge<P> edge : inwards.get(vertex)) {
                    /* Route over this edge only if the goal is in the
                     * 'from' set. */
                    BitSet modes = edgeCaps.get(edge);
                    if (modes.stream().noneMatch(m -> (m & bit) != 0)) continue;

                    /* Compute the distance, and retain the best. */
                    assert portMap.apply(edge.finish) == vertex;
                    V other = portMap.apply(edge.start);
                    Path<V> newPath = Path.append(getDistance(other, goal),
                                                  edge.cost, vertex, best);
                    if (newPath != null) best = newPath;
                }

                /* Try to improve the distance with the outward edges.
                 * Only use an edge if at least one of the 'to' sets of
                 * its remaining modes includes the goal. */
                for (Edge<P> edge : outwards.get(vertex)) {
                    /* Route over this edge only if the goal is in the
                     * 'from' set. */
                    BitSet modes = edgeCaps.get(edge);
                    if (modes.stream().noneMatch(m -> (m & bit) == 0)) continue;

                    /* Compute the distance, and retain the best. */
                    assert portMap.apply(edge.start) == vertex;
                    V other = portMap.apply(edge.finish);
                    Path<V> newPath = Path.append(getDistance(other, goal),
                                                  edge.cost, vertex, best);
                    if (newPath != null) best = newPath;
                }

                setDistance(vertex, goal, best);
            }

            void setDistance(V vertex, V goal, Path<V> path) {
                if (path == null) {
                    Path<V> old =
                        distances.getOrDefault(vertex, Collections.emptyMap())
                            .remove(goal);
                    if (old != null) invalidateNeighbours(vertex, goal);
                    if (false) System.err.printf("%s -> %s unreachable%n",
                                                 vertex, goal);
                    return;
                }
                /* Store the new distance, and see if we have changed
                 * it. */
                Path<V> old = distances
                    .computeIfAbsent(vertex, k -> new IdentityHashMap<>())
                    .put(goal, path);
                if (Path.sameAs(old, path)) return;
                if (false) System.err.printf("%s -> %s takes %s%n", vertex,
                                             goal, path);

                /* The distance has changed, so invalidate the tables in
                 * the neighbours. */
                invalidateNeighbours(vertex, goal);
            }

            void invalidateNeighbours(V vertex, V goal) {
                for (Edge<P> edge : inwards.get(vertex)) {
                    assert portMap.apply(edge.finish) == vertex;
                    V other = portMap.apply(edge.start);
                    invalidateDistance(other, goal);
                    invalidateEdgeGoal(edge, goal);
                }
                for (Edge<P> edge : outwards.get(vertex)) {
                    assert portMap.apply(edge.start) == vertex;
                    V other = portMap.apply(edge.finish);
                    invalidateDistance(other, goal);
                    invalidateEdgeGoal(edge, goal);
                }
            }

            boolean updateEdge(Edge<P> edge, V goal) {
                boolean changed = false;
                Path<V> startPath =
                    getDistance(portMap.apply(edge.start), goal);
                Path<V> finishPath =
                    getDistance(portMap.apply(edge.finish), goal);
                if (startPath == null || finishPath == null) {
                    final int gn = goalIndex.getAsInt(goal);
                    BitSet modes = edgeCaps.get(edge);
                    modes.clear();
                    if (false) System.err
                        .printf("  removed for no route to goal %d%n", gn);
                    return true;
                }
                double startDistance = startPath.distance;
                double finishDistance = finishPath.distance;
                double unsuitability =
                    (startDistance - finishDistance) / edge.cost;
                if (unsuitability > biasThreshold) {
                    /* The finish is significantly more distant from the
                     * goal than the start is. Remove modes from this
                     * edge where the 'from' set includes the goal. */
                    final int gn = goalIndex.getAsInt(goal);
                    final int bit = 1 << gn;
                    BitSet modes = edgeCaps.get(edge);
                    for (int mode = modes.nextSetBit(0); mode >= 0;
                         mode = modes.nextSetBit(mode + 1)) {
                        if ((mode & bit) != 0) {
                            modes.clear(mode);
                            if (false) System.err
                                .printf("  removed %s for having goal %d%n",
                                        of(mode), gn);
                            changed = true;
                        }
                    }
                } else if (unsuitability < -biasThreshold) {
                    /* The start is significantly more distant from the
                     * goal than the finish is. Remove modes from this
                     * edge where the 'from' set excludes the goal. */
                    final int gn = goalIndex.getAsInt(goal);
                    final int bit = 1 << gn;
                    BitSet modes = edgeCaps.get(edge);
                    for (int mode = modes.nextSetBit(0); mode >= 0;
                         mode = modes.nextSetBit(mode + 1)) {
                        if ((mode & bit) == 0) {
                            modes.clear(mode);
                            if (false) System.err
                                .printf("  removed %s for not having goal %d%n",
                                        of(mode), gn);
                            changed = true;
                        }
                    }
                }
                if (changed) {
                    if (false)
                        System.err.printf("%s -> %s: %g (%.3f - %.3f / %.3f)%n",
                                          edge, goal, unsuitability,
                                          startDistance, finishDistance,
                                          edge.cost);
                    invalidateDistance(portMap.apply(edge.start), goal);
                    invalidateDistance(portMap.apply(edge.finish), goal);
                }

                return changed;
            }

            void route() {
                System.err.printf("Routing...%n");
                /* Record each goal as zero distance from itself, and
                 * invalidate its neighbours. */
                for (V goal : goalIndex)
                    setDistance(goal, goal, Path.root(goal));

                while (!invalidGoals.isEmpty()) {
                    /* Get the routing tables up-to-date. */
                    for (Pair<V, V> pair : remainingIn(invalidGoals)) {
                        updateDistance(pair.item1, pair.item2);
                    }

                    /* Look for edge modes to eliminate. */
                    for (Pair<Edge<P>, V> pair : remainingIn(invalidEdges)) {
                        updateEdge(pair.item1, pair.item2);
                    }
                }

                /* Clear out edges that have no in-use modes
                 * remaining. */
                for (var iter = edgeCaps.entrySet().iterator();
                     iter.hasNext();) {
                    var item = iter.next();
                    if (item.getValue().isEmpty()) iter.remove();
                }
                System.err.printf("Routing complete%n");
            }

            /**
             * Get a deep immutable copy of the edges that finish at
             * each vertex.
             */
            Map<V, Collection<Edge<P>>> getInwardEdges() {
                /* TODO: Collectors.toMap and Set.copyOf should suffice,
                 * but they cause perturbations that make fault
                 * diagnosis difficult. */
                return deepCopy(inwards, Collections::unmodifiableCollection);
                // return inwards.entrySet().stream().collect(Collectors
                // .toMap(Map.Entry::getKey, e ->
                // Set.copyOf(e.getValue())));
            }

            /**
             * Get a deep immutable copy of the edges that start at each
             * vertex.
             */
            Map<V, Collection<Edge<P>>> getOutwardEdges() {
                /* TODO: Collectors.toMap and Set.copyOf should suffice,
                 * but they cause perturbations that make fault
                 * diagnosis difficult. */
                return deepCopy(outwards, Collections::unmodifiableCollection);
                // return
                // outwards.entrySet().stream().collect(Collectors
                // .toMap(Map.Entry::getKey, e ->
                // Set.copyOf(e.getValue())));
            }

            /**
             * Get an almost arbitrary index of vertices. The only
             * guaranteed ordering is that the initial vertices are also
             * the goals, in the originally specified order.
             */
            Index<V> getVertexOrder() {
                return Index.copyOf(vertexes);
            }

            /**
             * Determine the reachability of all vertices through edges.
             * {@link #deriveVertexes()} must be called before this
             * method.
             */
            Index<Edge<P>> getEdgeOrder() {
                Collection<Edge<P>> reachOrder = new LinkedHashSet<>();
                Collection<V> reachables = new HashSet<>();
                Collection<V> newReachables = new LinkedHashSet<>();
                newReachables.addAll(goalIndex);
                while (!newReachables.isEmpty()) {
                    /* Pick a vertex that we haven't handled yet, and
                     * mark it as handled. */
                    V vertex = removeOne(newReachables);
                    assert !reachables.contains(vertex);
                    reachables.add(vertex);

                    /* Find all neighbours of the vertex. */
                    Collection<V> cands = new HashSet<>();
                    Collection<? extends Edge<P>> inEdges = inwards.get(vertex);
                    assert inEdges != null;
                    cands.addAll(inEdges.stream().map(e -> e.start).map(portMap)
                        .collect(Collectors.toSet()));
                    Collection<? extends Edge<P>> outEdges =
                        outwards.get(vertex);
                    assert outEdges != null;
                    cands.addAll(outEdges.stream().map(e -> e.finish)
                        .map(portMap).collect(Collectors.toSet()));

                    /* Ensure the edges are accounted for. */
                    reachOrder.addAll(outEdges);
                    reachOrder.addAll(inEdges);

                    /* Exclude neighbours that have already been
                     * handled. */
                    cands.removeAll(reachables);

                    /* Add the remaining neighbours to the set yet to be
                     * handled. */
                    newReachables.addAll(cands);
                }

                /* Extract the order in which edges were added. Reverse
                 * it, so that the ones nearest the edges are going to
                 * change the least, and get validated first. */
                List<Edge<P>> edgeOrder = new ArrayList<>(reachOrder);
                Collections.reverse(edgeOrder);

                return Index.copyOf(edgeOrder);
            }
        }

        final Routing routing = new Routing();
        routing.eliminateIncapaciousEdgeModes();
        routing.deriveVertexes();
        routing.route();

        routing.deriveVertexes();
        final Map<Edge<P>, BitSet> edgeCaps = routing.getEdgeModes();
        System.err.printf("caps: %s%n", edgeCaps);

        final Map<V, Collection<Edge<P>>> inwards = routing.getInwardEdges();
        final Map<V, Collection<Edge<P>>> outwards = routing.getOutwardEdges();
        final Index<V> vertexOrder = routing.getVertexOrder();

        /* The goal order should be a subsequence of the vertex
         * order. */
        for (var entry : goalIndex.decode().entrySet())
            assert vertexOrder.get(entry.getKey()) == entry.getValue();

        /* Ensure that every goal has an edge. */
        for (V v : goalOrder) {
            Collection<Edge<P>> ins = inwards.get(v);
            Collection<Edge<P>> outs = outwards.get(v);
            assert ins != null;
            assert outs != null;
            if (ins.isEmpty() && outs.isEmpty()) return Collections.emptyList();
        }

        /* Assign each edge's mode to a digit in a multi-base number.
         * Index 0 will be the least significant digit. Edges that are
         * most reachable will correspond to the most significant
         * digits. */
        final Index<Edge<P>> edgeIndex = routing.getEdgeOrder();
        assert edgeIndex.size() <= edgeCaps.size();

        /* Create a mapping from mode index to mode pattern for each
         * edge that has at least one valid non-zero mode. The first
         * index is the edge number. The second index is the mode index.
         * The third is either zero or one, giving the from set or the
         * to set. */
        assert edgeCaps.keySet().containsAll(edgeIndex);
        final int[][][] modeMap = edgeIndex.stream()
            .map(e -> edgeCaps.get(e).stream().mapToObj(m -> new int[]
            { m, m ^ modeCount }).toArray(n -> new int[n][]))
            .toArray(n -> new int[n][][]);

        /**
         * Checks that an edge has a valid external set with respect to
         * other edges. It is assumed that all edges are connected to
         * the same vertex.
         */
        class OneExternalPerGoal implements Constraint {
            final int[] edges;

            @Override
            public boolean isOwn(int en) {
                return IntStream.of(edges).anyMatch(i -> i == en);
            }

            final BitSet[][] accept;

            /**
             * Identifies outward edges, whose external sets are their
             * to sets.
             */
            final BitSet invs = new BitSet();

            /**
             * Create a constraint requiring that the external sets of
             * some edges do not have overlapping goals.
             * 
             * @param edges a list of edge codes. Inward edges are
             * represented by their indices. Outward edges are
             * represented by subtracting their indices from {@code -1}.
             */
            OneExternalPerGoal(List<? extends Integer> edges) {
                assert edges.size() >= 2;
                this.edges = new int[edges.size()];
                for (int i = 0; i < this.edges.length; i++) {
                    int en = edges.get(i);
                    if (en < 0) {
                        // an outward edge; external is to
                        invs.set(i);
                        en = -1 - en;
                    } else {
                        // an inward edge; external is from
                    }
                    this.edges[i] = en;
                }

                /* We need a bit-set array for each mode the primary
                 * edge can have. */
                final int pen = this.edges[0];
                final int oenc = this.edges.length - 1;
                accept = new BitSet[modeMap[pen].length + 1][];
                for (int pmi = 0; pmi < accept.length; pmi++) {
                    accept[pmi] = new BitSet[oenc];
                    final int ppat = pmi == 0 ? 0 :
                        modeMap[pen][pmi - 1][invs.get(0) ? 1 : 0];

                    /* In this mode, there must be a bit set per other
                     * edge. */
                    for (int oeni = 0; oeni < oenc; oeni++) {
                        /* oeni is the other edge's index in this.edges
                         * minus 1. */
                        BitSet cur = accept[pmi][oeni] = new BitSet();

                        /* Identify the other edge. */
                        final int oen = this.edges[oeni + 1];

                        /* If the primary edge is not in use, it is
                         * compatible with all other edges in all
                         * modes. */
                        if (pmi == 0) {
                            cur.set(0, modeMap[oen].length + 1);
                            continue;
                        }
                        assert ppat != 0;

                        /* It's always okay if the other edge is not in
                         * use (mode index 0). */
                        cur.set(0);

                        /* Go over all mode indices supported by the
                         * other edge. */
                        for (int omi = 1; omi <= modeMap[oen].length; omi++) {
                            final boolean inv = invs.get(oeni + 1);
                            final int opat = modeMap[oen][omi - 1][inv ? 1 : 0];

                            if ((opat & ppat) != 0) continue;
                            /* This mode 'omi' in this edge 'oen' is
                             * compatible with our primary edge 'pen' in
                             * its current mode 'pmi'. */
                            cur.set(omi);
                        }
                    }
                }
            }

            @Override
            public String toString() {
                List<String> parts =
                    IntStream.range(0, edges.length).mapToObj(i -> {
                        int en = edges[i];
                        Edge<P> e = edgeIndex.get(en);
                        return String.format(" %s.%s", e,
                                             invs.get(i) ? "to" : "from");
                    }).collect(Collectors.toList());
                return String.format("edge%s not intersect%s", parts.get(0),
                                     parts.subList(1, parts.size()).stream()
                                         .collect(Collectors.joining()));
            }

            @Override
            public void verify(int baseEdge) {
                assert edges[0] == baseEdge;
            }

            @Override
            public boolean check(IntUnaryOperator digits) {
                /* Get the primary edge. */
                final int pen = edges[0];

                /* Get the primary edge's mode index. */
                final int pmi = digits.applyAsInt(pen);

                /* See if the cached check says we're okay. */
                for (int oeni = 1; oeni < edges.length; oeni++) {
                    // The other edge
                    final int oen = edges[oeni];
                    // The other edge's mode index
                    final int omi = digits.applyAsInt(oen);

                    /* Detect incompatibility between the primary's mode
                     * and this other edge's mode. */
                    if (!accept[pmi][oeni - 1].get(omi)) return false;
                }

                /* There were no conflicts. */
                return true;
            }

            @Override
            public IntStream edges() {
                return IntStream.range(0, edges.length)
                    .map(i -> edges[edges.length - i - 1]);
            }
        }

        abstract class CompleteExternalUnionBase implements Constraint {
            @Override
            public IntStream edges() {
                return IntStream.range(0, edges.length)
                    .map(i -> edges[edges.length - i - 1]);
            }

            @Override
            public boolean isOwn(int en) {
                return IntStream.of(edges).anyMatch(i -> i == en);
            }

            final boolean disusedOkay;

            final int[] edges;

            final BitSet invs = new BitSet();

            CompleteExternalUnionBase(boolean disusedOkay,
                                      List<? extends Integer> edges) {
                assert !edges.isEmpty();
                this.disusedOkay = disusedOkay;
                this.edges = new int[edges.size()];
                for (int i = 0; i < this.edges.length; i++) {
                    int en = edges.get(i);
                    if (en < 0) {
                        // an outward edge; external set is to set
                        invs.set(i);
                        en = -1 - en;
                    } else {
                        // an inward edge; external set is from set
                    }
                    this.edges[i] = en;
                }
            }

            @Override
            public String toString() {
                return "complete union of"
                    + IntStream.range(0, edges.length).mapToObj(i -> {
                        int en = edges[i];
                        Edge<P> e = edgeIndex.get(en);
                        return String.format(" %s.%s", e,
                                             invs.get(i) ? "to" : "from");
                    }).collect(Collectors.joining());
            }

            @Override
            public boolean check(IntUnaryOperator digits) {
                /* Create a set of all goals, in preparation to
                 * eliminate them. We fail if there are any left, unless
                 * none of the edges are in use at all. */
                int base = modeCount;

                /* Do any special elimination. */
                base = augment(base);

                /* Eliminate each edge's external set. */
                boolean disused = true;
                for (int i = 0; i < edges.length; i++) {
                    final int en = edges[i];
                    final int mi = digits.applyAsInt(en);

                    /* An unused edge contributes nothing. */
                    if (mi == 0) continue;
                    disused = false;

                    /* Get the external set for this edge, and account
                     * for its members. */
                    final boolean inv = invs.get(i);
                    int pat = modeMap[en][mi - 1][inv ? 1 : 0];
                    base &= ~pat;

                    /* We're okay if we've accounted for all of the
                     * goals. */
                    if (base == 0) return true;
                }

                /* We didn't account for any of the goals. We're okay
                 * only if all edges are disused (so we accounted for no
                 * goals). */
                return disusedOkay && disused;
            }

            public abstract int augment(int base);

            @Override
            public void verify(int baseEdge) {
                assert edges[0] == baseEdge;
            }
        }

        class CompleteExternalUnion extends CompleteExternalUnionBase {
            public CompleteExternalUnion(List<? extends Integer> edges) {
                super(true, edges);
            }

            @Override
            public int augment(int base) {
                return base;
            }
        }

        class CompleteExternalUnionExceptGoal
            extends CompleteExternalUnionBase {
            final int goal;

            final int mask;

            CompleteExternalUnionExceptGoal(int goal,
                                            List<? extends Integer> edges) {
                super(false, edges);
                this.goal = goal;
                this.mask = ~(1 << goal);
            }

            @Override
            public String toString() {
                return String.format("except goal %d%s %s", goal,
                                     goalIndex.get(goal), super.toString());
            }

            @Override
            public int augment(int base) {
                return base & mask;
            }
        }

        final Map<Integer, Collection<Constraint>> checkers = new HashMap<>();

        /* Identify constraints. */
        for (V vertex : vertexOrder) {
            /* Get the vertex's inward edges' indices, and the two's
             * complements of its outward egdes' indices. Sort the edges
             * by index. */
            Collection<Edge<P>> outs = outwards.get(vertex);
            Collection<Edge<P>> ins = inwards.get(vertex);
            assert outs.size() + ins.size() > 0;
            List<Integer> ecs =
                IntStream
                    .concat(ins.stream().mapToInt(edgeIndex::getAsInt),
                            outs.stream()
                                .mapToInt(e -> -1 - edgeIndex.getAsInt(e)))
                    .boxed().collect(Collectors.toList());
            Collections.sort(ecs, (a, b) -> {
                int ai = a, bi = b;
                if (ai < 0) ai = -1 - ai;
                if (bi < 0) bi = -1 - bi;
                return Integer.compare(ai, bi);
            });
            assert !ecs.isEmpty();

            /* Identify constraints on external sets of these edges.
             * There are none if the vertex has fewer than 2 edges. */
            if (ecs.size() >= 2) {
                /* Define a constraint for every tail of the sequence.
                 * No edge's external set may overlap with another's. */
                for (int i = 0; i < ecs.size() - 1; i++) {
                    List<Integer> sub = ecs.subList(i, ecs.size());
                    assert sub.size() >= 2;
                    int first = sub.get(0);
                    if (first < 0) first = -1 - first;
                    Constraint constraint = new OneExternalPerGoal(sub);
                    checkers.computeIfAbsent(first, k -> new ArrayList<>())
                        .add(constraint);
                }
            }

            final int goalNumber = goalIndex.getAsInt(vertex);
            if (goalNumber >= 0) {
                assert goalOrder.get(goalNumber) == vertex;

                /* The union of the external sets and this goal must be
                 * the complete set of goals, and at least one edge must
                 * be in use. */
                Constraint constraint =
                    new CompleteExternalUnionExceptGoal(goalNumber, ecs);
                int first = ecs.get(0);
                if (first < 0) first = -1 - first;
                checkers.computeIfAbsent(first, k -> new ArrayList<>())
                    .add(constraint);
            } else {
                /* The union of the external sets must be the complete
                 * set of goals. */
                Constraint constraint = new CompleteExternalUnion(ecs);
                int first = ecs.get(0);
                if (first < 0) first = -1 - first;
                checkers.computeIfAbsent(first, k -> new ArrayList<>())
                    .add(constraint);
            }
        }

        /* Convert the constraints to arrays (for speed?). */
        final Constraint[][] constraints = new Constraint[edgeIndex.size()][];
        for (int i = 0; i < edgeIndex.size(); i++)
            constraints[i] = checkers.getOrDefault(i, Collections.emptySet())
                .toArray(n -> new Constraint[n]);

        {
            /* Display all edge modes. */
            for (var entry : edgeIndex.decode().entrySet()) {
                var e = entry.getValue();
                int i = entry.getKey();
                List<BitSet> modes = edgeCaps.get(e).stream()
                    .mapToObj(ComprehensiveTreePlotter::of)
                    .collect(Collectors.toList());
                System.err.printf("%2d %15s: %s%n", i, e, modes);
            }

            /* Display all constraints. */
            for (int i = constraints.length - 1; i >= 0; i--) {
                Constraint[] ccs = constraints[i];
                for (int j = 0; j < ccs.length; j++) {
                    System.err.printf("%s: %s%n", edgeIndex.get(i), ccs[j]);
                    ccs[j].verify(i);
                }
            }
        }

        /* Prepare to iterate over the edge modes while checking
         * constraints. */
        IntUnaryOperator bases = i -> modeMap[i].length + 1;
        assert modeMap.length == edgeIndex.size();
        assert modeMap.length <= edgeCaps.size();
        Function<IntUnaryOperator, Map<Edge<P>, BandwidthPair>> translator =
            digits -> IntStream.range(0, edgeIndex.size())
                .filter(en -> digits.applyAsInt(en) != 0).boxed()
                .collect(Collectors.toMap(edgeIndex::get, en -> bwreq
                    .getPair(of(modeMap[en][digits.applyAsInt(en) - 1][0]))));
        MixedRadixValidator validator = (en, digits) -> {
            for (Constraint c : constraints[en])
                if (!c.check(digits)) return false;
            return true;
        };
        return MixedRadixIterable.to(translator).over(modeMap.length, bases)
            .constrainedBy(validator).build();
    }

    private static class Vertex {
        final double x, y;

        Vertex(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public Vertex minus(Vertex other) {
            return new Vertex(x - other.x, y - other.y);
        }

        @Override
        public String toString() {
            return String.format("(%.0f,%.0f)", x, y);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 37 * hash + (int) (Double.doubleToLongBits(this.x) ^
                (Double.doubleToLongBits(this.x) >>> 32));
            hash = 37 * hash + (int) (Double.doubleToLongBits(this.y) ^
                (Double.doubleToLongBits(this.y) >>> 32));
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            final Vertex other = (Vertex) obj;
            if (Double.doubleToLongBits(this.x)
                != Double.doubleToLongBits(other.x)) return false;
            if (Double.doubleToLongBits(this.y)
                != Double.doubleToLongBits(other.y)) return false;
            return true;
        }

    }

    private static double distance(Vertex v0, Vertex v1) {
        double dx = v1.x - v0.x;
        double dy = v1.y - v0.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static class Circle extends Vertex {
        public final double radius;

        public Circle(double x, double y, double radius) {
            super(x, y);
            this.radius = radius;
        }

        public boolean contains(Vertex other) {
            return distance(this, other) < radius;
        }

        @Override
        public String toString() {
            return super.toString() + radius;
        }
    }

    private static Circle circumcircle(Vertex a, Vertex b, Vertex c) {
        Vertex bp = b.minus(a);
        Vertex cp = c.minus(a);
        final double bp2 = bp.x * bp.x + bp.y * bp.y;
        final double cp2 = cp.x * cp.x + cp.y * cp.y;
        final double dp = 2.0 * (bp.x * cp.y - bp.y * cp.x);
        final double upx = (cp.y * bp2 - bp.y * cp2) / dp;
        final double upy = (bp.x * cp2 - cp.x * bp2) / dp;
        final double r = Math.hypot(upx, upy);
        return new Circle(upx + a.x, upy + a.y, r);
    }

    private static boolean edgesCross(Vertex v1, Vertex v2, Vertex v3,
                                      Vertex v4) {
        if (v1 == v3 || v1 == v4 || v2 == v3 || v2 == v4) return false;
        final double deter =
            (v1.x - v2.x) * (v3.y - v4.y) - (v1.y - v2.y) * (v3.x - v4.x);
        final double x1y2my1x2 = v1.x * v2.y - v1.y * v2.x;
        final double x3y4my3x4 = v3.x * v4.y - v3.y * v4.x;
        final double x3mx4 = v3.x - v4.x;
        final double x1mx2 = v1.x - v2.x;
        final double y3my4 = v3.y - v4.y;
        final double y1my2 = v1.y - v2.y;
        final double px = (x1y2my1x2 * x3mx4 - x1mx2 * x3y4my3x4) / deter;
        final double py = (x1y2my1x2 * y3my4 - y1my2 * x3y4my3x4) / deter;
        if (px < v1.x && px < v2.x) return false;
        if (px < v3.x && px < v4.x) return false;
        if (px > v1.x && px > v2.x) return false;
        if (px > v3.x && px > v4.x) return false;
        if (py < v1.y && py < v2.y) return false;
        if (py < v3.y && py < v4.y) return false;
        if (py > v1.y && py > v2.y) return false;
        if (py > v3.y && py > v4.y) return false;
        return true;
    }

    private static boolean cross(Edge<Vertex> e0, Edge<Vertex> e1) {
        return edgesCross(e0.start, e0.finish, e1.start, e1.finish);
    }

    private static double length(Edge<Vertex> e) {
        return distance(e.start, e.finish);
    }

    private static boolean abut(Edge<Vertex> e0, Edge<Vertex> e1) {
        if (e0 == e1) return false;
        if (e0.start == e1.start) return true;
        if (e0.start == e1.finish) return true;
        if (e0.finish == e1.start) return true;
        if (e0.finish == e1.finish) return true;
        return false;
    }

    private static double
        minSum(Map<? extends Edge<Vertex>, ? extends BandwidthPair> cand) {
        return cand.entrySet().parallelStream().mapToDouble(entry -> {
            Edge<Vertex> key = entry.getKey();
            BandwidthPair val = entry.getValue();
            return key.cost * (val.ingress.min() + val.egress.min());
        }).sum();
    }

    private static double postScaledMinSum(Map<? extends Edge<Vertex>,
                                               ? extends BandwidthPair> cand) {
        return cand.entrySet().parallelStream().mapToDouble(entry -> {
            Edge<Vertex> key = entry.getKey();
            BandwidthPair val = entry.getValue();
            double edgeScore = val.ingress.min() + val.egress.min();
            edgeScore /= val.egress.max() / key.metrics.egress.max();
            edgeScore /= val.ingress.max() / key.metrics.ingress.max();
            return key.cost * edgeScore;
        }).sum();
    }

    private static double preScaledMinSum(Map<? extends Edge<Vertex>,
                                              ? extends BandwidthPair> cand) {
        return cand.entrySet().parallelStream().mapToDouble(entry -> {
            Edge<Vertex> key = entry.getKey();
            BandwidthPair val = entry.getValue();
            double edgeScore = val.ingress.min() * val.ingress.max()
                / key.metrics.ingress.max();
            edgeScore +=
                val.egress.min() * val.egress.max() / key.metrics.egress.max();
            return key.cost * edgeScore;
        }).sum();
    }

    /**
     * @undocumented
     */
    public static void main(String[] args) throws Exception {

        final int width = 20;
        final int height = 15;
        final int population = width * height * 8 / 100;
        final int goalCount = 4;
        final double goalRadius = 0.3;
        final double vertexRadius = 0.2;
        final double stretch = 0.8;

        /* Create vertices dotted around a grid. */
        List<Vertex> vertexes = new ArrayList<>();
        List<Vertex> goals = new ArrayList<>();
        Random rng = new Random(1);
        {
            int req = population, rem = width * height;
            int greq = goalCount;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++, rem--) {
                    if (rng.nextInt(rem) < req) {
                        /* Create a new vertex. */
                        Vertex v = new Vertex(x, y);
                        vertexes.add(v);

                        /* Decide whether to make this a goal. */
                        if (rng.nextInt(req) < greq) {
                            goals.add(v);
                            greq--;
                        }

                        /* Reduce the odds of making subsequent
                         * vertices. */
                        req--;
                    }
                }
            }
        }

        List<Edge<Vertex>> edges = new ArrayList<>();
        {
            /* Create edges between every pair of vertices. */
            for (int i = 0; i < vertexes.size() - 1; i++) {
                Vertex v0 = vertexes.get(i);
                for (int j = i + 1; j < vertexes.size(); j++) {
                    Vertex v1 = vertexes.get(j);
                    double cost = distance(v0, v1);
                    BandwidthPair cap = BandwidthPair
                        .of(BandwidthRange.at(2.0 + rng.nextDouble() * 8.0),
                            BandwidthRange.at(2.0 + rng.nextDouble() * 8.0));
                    Edge<Vertex> e = new Edge<>(v0, v1, cap, cost);
                    edges.add(e);
                }
            }

            /* Prune longer edges that cross others. */
            outer: for (int i = 0; i < edges.size() - 1; i++) {
                Edge<Vertex> e0 = edges.get(i);
                double s0 = length(e0);
                for (int j = i + 1; j < edges.size(); j++) {
                    Edge<Vertex> e1 = edges.get(j);
                    if (!cross(e0, e1)) continue;
                    double s1 = length(e1);
                    if (s1 >= s0) {
                        edges.remove(j--);
                    } else {
                        edges.remove(i--);
                        continue outer;
                    }
                }
            }

            /* Find edges that form a triangle. */
            outer0: for (int i0 = 0; i0 < edges.size() - 2; i0++) {
                final Edge<Vertex> e0 = edges.get(i0);
                final double s0 = length(e0);
                outer1: for (int i1 = i0 + 1; i1 < edges.size() - 1; i1++) {
                    final Edge<Vertex> e1 = edges.get(i1);
                    if (!abut(e0, e1)) continue;

                    final double s1 = length(e1);
                    outer2: for (int i2 = i1 + 1; i2 < edges.size(); i2++) {
                        final Edge<Vertex> e2 = edges.get(i2);
                        if (!abut(e2, e1)) continue;
                        if (!abut(e2, e0)) continue;

                        /* Identify the corners and compute the
                         * circumcircle. */
                        final Collection<Vertex> set = new HashSet<>();
                        set.add(e0.start);
                        set.add(e0.finish);
                        set.add(e1.start);
                        set.add(e1.finish);
                        set.add(e2.start);
                        set.add(e2.finish);
                        /* If we don't have exactly 3 corners, we've
                         * probably just picked three edges of the same
                         * vertex. */
                        if (set.size() != 3) continue;

                        /* Now we have a triangle. */
                        final List<Vertex> corners = List.copyOf(set);
                        assert corners.size() == 3;
                        final double s2 = length(e2);

                        if (false)
                            System.out.printf("%s-%s-%s%n", corners.get(0),
                                              corners.get(1), corners.get(2));

                        /* Very flat triangles will have the longest
                         * edge being only slightly shorter than the sum
                         * of the others. Remove the longest edge in
                         * these cases. */
                        if (s2 > stretch * (s1 + s0) ||
                            s1 > stretch * (s2 + s0) ||
                            s0 > stretch * (s2 + s1)) {
                            /* This triangle is very squished. Remove
                             * the longest edge. */
                            if (s2 > s1 && s2 > s0) {
                                if (false) System.out
                                    .printf("  removing 3rd %s%n", e2);
                                edges.remove(i2--);
                                continue;
                            }

                            if (s1 > s2 && s1 > s0) {
                                if (false) System.out
                                    .printf("  removing 2nd %s%n", e1);
                                edges.remove(i1--);
                                continue outer1;
                            }

                            if (false)
                                System.out.printf("  removing 1st %s%n", e0);
                            edges.remove(i0--);
                            continue outer0;
                        }

                        /* See if there's a vertex within the
                         * circumcircle. */
                        final Circle circum =
                            circumcircle(corners.get(0), corners.get(1),
                                         corners.get(2));
                        for (Vertex v : vertexes) {
                            /* Exclude vertices that form this
                             * triangle. */
                            if (corners.contains(v)) continue;

                            /* Exclude vertices that are not in the
                             * circumcircle. */
                            if (!circum.contains(v)) continue;

                            if (false) System.out.printf("  contains %s%n", v);

                            /* Remove the longest edge. */
                            if (s2 > s1 && s2 > s0) {
                                if (false) System.out
                                    .printf("  removing 3rd %s%n", e2);
                                edges.remove(i2--);
                                continue outer2;
                            }

                            if (s1 > s2 && s1 > s0) {
                                if (false) System.out
                                    .printf("  removing 2nd %s%n", e1);
                                edges.remove(i1--);
                                continue outer1;
                            }

                            if (false)
                                System.out.printf("  removing 1st %s%n", e0);
                            edges.remove(i0--);
                            continue outer0;
                        }
                    }
                }
            }

            /* The order in which we supply the edges to the plotter
             * should not matter, but we need an ordering that triggers
             * the wrong result. */
            Collections.shuffle(edges, new Random(4));
        }

        /* Find the largest edge capacity. */
        final double maxCap;
        {
            double best = 0.0;
            for (var e : edges) {
                best = Math.max(best, e.metrics.ingress.min());
                best = Math.max(best, e.metrics.egress.min());
            }
            maxCap = best;
        }

        /* Choose a tree. */
        final Map<Edge<Vertex>, BandwidthPair> tree;
        if (true) {
            TreePlotter plotter = new ComprehensiveTreePlotter();
            BandwidthFunction bwreq =
                new FlatBandwidthFunction(goals.size(), BandwidthRange.at(3.0));
            // new PairBandwidthFunction(IntStream.range(0,
            // goals.size())
            // .mapToObj(i ->
            // BandwidthPair.of(BandwidthRange.at(0.1),
            // BandwidthRange.at(0.2)))
            // .collect(Collectors.toList()));
            Map<? extends Edge<Vertex>, ? extends BandwidthPair> best = null;
            double bestScore = Double.MAX_VALUE;
            assert bwreq.degree() == goals.size();
            for (var cand : plotter.plot(goals, bwreq, edges)) {
                double score = 0.0;
                for (var entry : cand.entrySet()) {
                    Edge<Vertex> key = entry.getKey();
                    BandwidthPair val = entry.getValue();
                    score += key.cost * (val.ingress.min() + val.egress.min());
                }
                if (best == null || score < bestScore) {
                    best = cand;
                    bestScore = score;
                    System.err.printf("acc %g: %s%n", bestScore,
                                      best.entrySet().stream()
                                          .map(e -> e.getKey().toString())
                                          .collect(Collectors.joining(", ")));
                }
            }
            tree = best == null ? Collections.emptyMap() : Map.copyOf(best);
        } else {
            tree = Collections.emptyMap();
        }

        /* Draw out the result. */
        try (PrintWriter out =
            new PrintWriter(new File("scratch/treeplot.svg"))) {
            out.println("<?xml version=\"1.0\" " + "standalone=\"no\"?>\n");
            out.println("<!DOCTYPE svg PUBLIC");
            out.println(" \"-//W3C//DTD SVG 20000303 Stylable//EN\"");
            out.println(" \"http://www.w3.org/TR/2000/03/"
                + "WD-SVG-20000303/DTD/svg-20000303-stylable.dtd\">");
            out.println("<svg xmlns=\"http://www.w3.org/2000/svg\"");
            out.println(" xmlns:xlink=\"http://www.w3.org/1999/xlink\"");
            out.printf(" viewBox='%g %g %g %g'>%n", 0.0, 0.0, width + 0.0,
                       height + 0.0);

            /* Create the background. */
            out.printf("<rect fill='white' stroke='none'"
                + " x='%g' y='%g' width='%g' height='%g'/>%n", 0.0, 0.0,
                       width + 0.0, height + 0.0);

            /* Create the grid. */
            out.printf("<g fill='none' stroke='#ccc' stroke-width='0.03'>%n");
            for (int i = 0; i < width; i++)
                out.printf("<line x1='%g' y1='%g' x2='%g' y2='%g'/>%n", i + 0.5,
                           0.0, i + 0.5, height + 0.0);
            for (int i = 0; i < height; i++)
                out.printf("<line x1='%g' y1='%g' x2='%g' y2='%g'/>%n", 0.0,
                           i + 0.5, width + 0.0, i + 0.5);
            out.println("</g>");

            /* Create the border. */
            if (false)
                out.printf("<rect fill='none' stroke='red' stroke-width='0.2'"
                    + " x='%g' y='%g' width='%g' height='%g'/>%n", 0.0, 0.0,
                           width + 0.0, height + 0.0);

            /* Highlight the goals. */
            out.printf("<g fill='red' stroke='none'>%n");
            for (Vertex g : goals) {
                out.printf("<circle cx='%g' cy='%g' r='%g' />%n", g.x + 0.5,
                           g.y + 0.5, goalRadius);
            }
            out.println("</g>");

            /* Draw out the edge capacities. */
            out.printf("<g fill='#ccc' stroke='none'>%n");
            for (Edge<Vertex> e : edges) {
                final double len = length(e);
                final double dx = e.finish.x - e.start.x;
                final double dy = e.finish.y - e.start.y;
                final double startFrac =
                    e.metrics.ingress.min() / maxCap * vertexRadius;
                final double endFrac =
                    e.metrics.egress.min() / maxCap * vertexRadius;
                out.printf("<path d='M%g %g L%g %g L%g %g L%g %g z' />%n",
                           e.start.x - startFrac * dy / len + 0.5,
                           e.start.y + startFrac * dx / len + 0.5,
                           e.finish.x - endFrac * dy / len + 0.5,
                           e.finish.y + endFrac * dx / len + 0.5,
                           e.finish.x + endFrac * dy / len + 0.5,
                           e.finish.y - endFrac * dx / len + 0.5,
                           e.start.x + startFrac * dy / len + 0.5,
                           e.start.y - startFrac * dx / len + 0.5);
            }
            out.println("</g>");

            /* Draw out the tree. */
            out.printf("<g fill='black' stroke='none'>%n");
            for (var entry : tree.entrySet()) {
                Edge<Vertex> e = entry.getKey();
                BandwidthPair bw = entry.getValue();
                final double len = length(e);
                final double dx = e.finish.x - e.start.x;
                final double dy = e.finish.y - e.start.y;
                final double startFrac =
                    bw.ingress.min() / maxCap * vertexRadius;
                final double endFrac = bw.egress.min() / maxCap * vertexRadius;
                out.printf("<path d='M%g %g L%g %g L%g %g L%g %g z' />%n",
                           e.start.x - startFrac * dy / len + 0.5,
                           e.start.y + startFrac * dx / len + 0.5,
                           e.finish.x - endFrac * dy / len + 0.5,
                           e.finish.y + endFrac * dx / len + 0.5,
                           e.finish.x + endFrac * dy / len + 0.5,
                           e.finish.y - endFrac * dx / len + 0.5,
                           e.start.x + startFrac * dy / len + 0.5,
                           e.start.y - startFrac * dx / len + 0.5);
            }
            out.println("</g>");

            /* Draw the vertices. */
            out.printf("<g fill='black' stroke='none'>%n");
            for (Vertex g : vertexes) {
                out.printf("<circle cx='%g' cy='%g' r='%g' />%n", g.x + 0.5,
                           g.y + 0.5, vertexRadius);
            }
            out.println("</g>");

            out.println("</svg>");
        }
    }
}
