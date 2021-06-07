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

package uk.ac.lancs.dpb.tree.mixed_radix;

import uk.ac.lancs.dpb.tree.LinkedIdentityHashMap;
import uk.ac.lancs.dpb.tree.IdentityPair;
import uk.ac.lancs.dpb.tree.Sequence;
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
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import uk.ac.lancs.dpb.graph.BidiCapacity;
import uk.ac.lancs.dpb.graph.Capacity;
import uk.ac.lancs.dpb.graph.DemandFunction;
import uk.ac.lancs.dpb.graph.FlatDemandFunction;
import uk.ac.lancs.dpb.graph.MatrixDemandFunction;
import uk.ac.lancs.dpb.graph.PairDemandFunction;
import uk.ac.lancs.dpb.graph.QualifiedEdge;
import uk.ac.lancs.dpb.tree.TreePlotter;

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
public class MixedRadixTreePlotter implements TreePlotter {
    /**
     * Determines whether a {@link MixedRadixTreePlotter} should
     * eliminate biased edges.
     */
    public interface Assessor {
        /**
         * Determine what bias to use to eliminate edge modes.
         * 
         * @param currentThreshold the current threshold
         * 
         * @param radixes the radices that would be used if elimination
         * stopped now
         * 
         * @return the new threshold in [0, 1] and less than the current
         * threshold to continue with another round of elimination; or
         * negative or at least the current threshold to stop
         */
        double getBiasThreshold(double currentThreshold, int[] radixes);
    }

    /**
     * Indicates that no edge modes are to be removed for bias.
     */
    public static final Assessor ALL_EDGE_MODES =
        (currentThreshold, radixes) -> -1.0;

    /**
     * Get an assessor that uses specifies a static threshold. Only one
     * round of elimination will take place, and at this threshold.
     * 
     * @param threshold the fixed bias threshold
     * 
     * @return the requested assessor
     */
    public static Assessor biasThreshold(double threshold) {
        return (currentThreshold, radixes) -> threshold;
    }

    private final Assessor assessor;

    private final long timeout;

    /**
     * Create a comprehensive tree plotter.
     * 
     * @param assessor an agent deciding whether to remove biased edges
     * 
     * @param timeout the timeout in milliseconds after which to abandon
     * iteration; negative to disable
     */
    public MixedRadixTreePlotter(Assessor assessor, long timeout) {
        this.assessor = assessor;
        this.timeout = timeout;
    }

    public MixedRadixTreePlotter(Assessor assessor) {
        this(assessor, -1L);
    }

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
     * Create a deep immutable copy of a map, using identity of keys.
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
    private static <K, V> Map<K, V> deepIdentityCopy(Map<K, V> input,
                                                     UnaryOperator<V> op) {
        Map<K, V> output = new IdentityHashMap<>(input);
        for (var entry : output.entrySet())
            entry.setValue(op.apply(entry.getValue()));
        return Collections.unmodifiableMap(output);
    }

    /**
     * Create a set from an identity hash map.
     * 
     * @param <E> the element type
     * 
     * @return the initially empty identity set
     */
    private static <E> Collection<E> newIdentityHashSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    /**
     * Create a set from an identity hash map an an initial membership.
     * 
     * @param <E> the element type
     * 
     * @return the identity set populated with the initial members
     */
    private static <E> Collection<E>
        newIdentityHashSet(Collection<? extends E> initial) {
        Collection<E> result = newIdentityHashSet();
        result.addAll(initial);
        return result;
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
    private static <V> int compare(QualifiedEdge<V> a, QualifiedEdge<V> b) {
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

    private static <V> String setString(BitSet pat, Sequence<V> goalIndex) {
        return "{" + pat.stream().mapToObj(goalIndex::get).map(Object::toString)
            .collect(Collectors.joining("+")) + "}";
    }

    private static <V> Collection<V> patternToSet(BitSet pattern,
                                                  List<? extends V> sequence) {
        return newIdentityHashSet(pattern.stream().mapToObj(sequence::get)
            .collect(Collectors.toList()));
    }

    @Override
    public <P, V>
        Iterable<? extends Map<? extends QualifiedEdge<P>,
                               ? extends Map.Entry<? extends BitSet,
                                                   ? extends BidiCapacity>>>
        plot(List<? extends V> goalOrder, DemandFunction bwreq,
             Function<? super P, ? extends V> portMap,
             Collection<? extends QualifiedEdge<P>> edges) {
        /* If there are fewer than two goals, the solution is simple. No
         * edges are used. */
        if (goalOrder.size() < 2)
            return Collections.singleton(Collections.emptyMap());

        /* Assign each goal an integer. */
        final Sequence<V> goalSequence = Sequence.identityCopyOf(goalOrder);

        /* Compute the number of modes each edge can be used in. Each
         * mode is a bit pattern where the position of each bit
         * identifies a goal vertex in the goalOrder list. If the bit is
         * set, the goal is reachable by exiting an edge through its
         * finish; if clear, through its start. The mode with all bits
         * set is not used, as it places all goals at one end. The zero
         * mode is similarly useless; however, we co-opt it to indicate
         * an edge that isn't used. This number is always 1 less than a
         * power of 2. */
        final int modeCount = (1 << goalSequence.size()) - 1;

        class Routing {
            private double biasThreshold = 1.1;

            private final Map<QualifiedEdge<P>, BitSet> edgeCaps =
                new LinkedHashMap<>();

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
                    BidiCapacity req = bwreq.getPair(fwd);

                    for (var edge : edges) {
                        /* Skip this mode if the ingress demand exceeds
                         * the edge's forward capacity. */
                        if (req.ingress.min() > edge.capacity.ingress.min())
                            continue;

                        /* Skip this mode if the egress demand exceeds
                         * the edge's reverse capacity. */
                        if (req.egress.min() > edge.capacity.egress.min())
                            continue;

                        /* If the edge has a goal as its start, its from
                         * set must include that goal. */
                        {
                            int goal = goalSequence
                                .getAsInt(portMap.apply(edge.start));
                            if (goal >= 0) {
                                assert goal < goalSequence.size();
                                if (!fwd.get(goal)) continue;
                            }
                        }

                        /* If the edge has a goal as its finish, its to
                         * set must include that goal. */
                        {
                            int goal = goalSequence
                                .getAsInt(portMap.apply(edge.finish));
                            if (goal >= 0) {
                                assert goal < goalSequence.size();
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
            Map<QualifiedEdge<P>, BitSet> getEdgeModes() {
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
            private final Map<V, Collection<QualifiedEdge<P>>> inwards =
                new IdentityHashMap<>();

            /**
             * Identifies for each vertex the set of edges that start at
             * that vertex. {@link #reachEdges()} must be called to
             * populate this.
             */
            private final Map<V, Collection<QualifiedEdge<P>>> outwards =
                new IdentityHashMap<>();

            /**
             * Holds all reachable vertices, in order of reachability.
             * {@link #reachEdges()} must be called to populate this.
             */
            private final Collection<V> vertexes =
                LinkedIdentityHashMap.asSet();

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
                for (QualifiedEdge<P> edge : edgeCaps.keySet()) {
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

                /* Clear out leaves. */
                Collection<V> cands = LinkedIdentityHashMap.asSet(vertexes);
                for (V v : remainingIn(cands)) {
                    /* Don't remove goal leaves. */
                    if (goalSequence.contains(v)) continue;

                    var ins = inwards.get(v);
                    var outs = outwards.get(v);
                    Collection<QualifiedEdge<P>> es = Stream
                        .concat(ins.stream(), outs.stream()).collect(Collectors
                            .toCollection(MixedRadixTreePlotter::newIdentityHashSet));
                    if (es.size() >= 2) continue;
                    vertexes.remove(v);
                    if (es.isEmpty()) continue;
                    QualifiedEdge<P> e = es.iterator().next();
                    edgeCaps.get(e).clear();
                    V start = portMap.apply(e.start);
                    V finish = portMap.apply(e.finish);
                    if (start == v) {
                        /* Remove the outward edge from v and the inward
                         * edge from the finish. */
                        outs.remove(e);
                        inwards.get(finish).remove(e);
                        cands.add(finish);
                    } else {
                        assert finish == v;
                        /* Remove the inward edge from v and the outward
                         * edge from the start. */
                        ins.remove(e);
                        outwards.get(start).remove(e);
                        cands.add(start);
                    }
                }
                assert vertexes.containsAll(goalSequence);
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
            final Collection<IdentityPair<V, V>> invalidGoals =
                new LinkedHashSet<>();

            /**
             * Keeps track of goals across edges that might be out of
             * date.
             */
            final Collection<IdentityPair<QualifiedEdge<P>, V>> invalidEdges =
                new LinkedHashSet<>();

            /**
             * Invalidate an entry for a goal in the distance table of a
             * vertex.
             * 
             * @return {@code true} if a change was made
             */
            boolean invalidateDistance(V vertex, V goal) {
                return invalidGoals.add(new IdentityPair<>(vertex, goal));
            }

            /**
             * Invalidate an edge's bidirectional suitability for a
             * goal.
             * 
             * @return {@code true} if a change was made
             */
            boolean invalidateEdgeGoal(QualifiedEdge<P> edge, V goal) {
                return invalidEdges.add(new IdentityPair<>(edge, goal));
            }

            void updateDistance(V vertex, V goal) {
                /* We never compute the distance to ourselves. */
                if (vertex == goal) return;

                final int gn = goalSequence.getAsInt(goal);
                final int bit = 1 << gn;
                Path<V> best = null;

                /* Go over all the inward edges, getting the best
                 * distance. Only use an edge if at least one of the
                 * 'from' sets of its remaining modes includes the
                 * goal. */
                for (QualifiedEdge<P> edge : inwards.get(vertex)) {
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
                for (QualifiedEdge<P> edge : outwards.get(vertex)) {
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
                for (QualifiedEdge<P> edge : inwards.get(vertex)) {
                    assert portMap.apply(edge.finish) == vertex;
                    V other = portMap.apply(edge.start);
                    invalidateDistance(other, goal);
                    invalidateEdgeGoal(edge, goal);
                }
                for (QualifiedEdge<P> edge : outwards.get(vertex)) {
                    assert portMap.apply(edge.start) == vertex;
                    V other = portMap.apply(edge.finish);
                    invalidateDistance(other, goal);
                    invalidateEdgeGoal(edge, goal);
                }
            }

            boolean updateEdge(QualifiedEdge<P> edge, V goal) {
                boolean changed = false;
                Path<V> startPath =
                    getDistance(portMap.apply(edge.start), goal);
                Path<V> finishPath =
                    getDistance(portMap.apply(edge.finish), goal);
                if (startPath == null || finishPath == null) {
                    final int gn = goalSequence.getAsInt(goal);
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
                    final int gn = goalSequence.getAsInt(goal);
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
                    final int gn = goalSequence.getAsInt(goal);
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

            /**
             * Consult the assessor on what bias threshold to use next.
             * {@link #biasThreshold} will be updated if the new value
             * is non-negative and less than the current value, and
             * {@code true} will be returned, indicating that a further
             * round of mode elimination should proceed. Otherwise,
             * {@code false} is returned.
             * 
             * @return whether to proceed with another round of mode
             * elimination
             */
            boolean adequateReduction() {
                /* Get the proposed over-unity radices to offer to the
                 * user. */
                int[] rdx =
                    edgeCaps.values().stream().mapToInt(BitSet::cardinality)
                        .filter(i -> i > 0).map(i -> i + 1).toArray();
                double newThreshold =
                    assessor.getBiasThreshold(biasThreshold, rdx);
                if (newThreshold < 0.0 || newThreshold >= biasThreshold ||
                    newThreshold > 1.0) return false;
                biasThreshold = newThreshold;
                return true;
            }

            /**
             * Eliminate biased edge modes. Routing tables of the goals
             * are first populated with zero-distance paths to
             * themselves. Tables of all vertices are then updated until
             * they converge. Each remaining edge mode's bias is then
             * computed, and those above a threshold are eliminated,
             * invalidating the routing tables. Routing update and mode
             * elimination repeats. A round of routing update and mode
             * elimination proceeds only if there are invalid routes or
             * the threshold is reduced.
             */
            void route() {
                if (false) System.err.printf("Routing...%n");
                /* Record each goal as zero distance from itself, and
                 * invalidate its neighbours. */
                for (V goal : goalSequence)
                    setDistance(goal, goal, Path.root(goal));

                /* Keep telling the user how big the problem is, and
                 * asking whether a lower threshold should be used. Stop
                 * when they say 'no'. */
                while (adequateReduction()) {
                    /* Keep updating routes and eliminating edge modes
                     * as long as there are invalid entries in the
                     * routing tables. */
                    while (!invalidGoals.isEmpty()) {
                        /* Get the routing tables up-to-date. */
                        for (var pair : remainingIn(invalidGoals)) {
                            updateDistance(pair.item1, pair.item2);
                        }

                        /* Look for edge modes to eliminate. */
                        for (var pair : remainingIn(invalidEdges)) {
                            updateEdge(pair.item1, pair.item2);
                        }
                    }
                }

                /* Clear out edges that have no in-use modes
                 * remaining. */
                for (var iter = edgeCaps.entrySet().iterator();
                     iter.hasNext();) {
                    var item = iter.next();
                    if (item.getValue().isEmpty()) iter.remove();
                }
                if (false) System.err.printf("Routing complete%n");
            }

            /**
             * Get a deep immutable copy of the edges that finish at
             * each vertex.
             */
            Map<V, Collection<QualifiedEdge<P>>> getInwardEdges() {
                return deepIdentityCopy(inwards,
                                        Collections::unmodifiableCollection);
            }

            /**
             * Get a deep immutable copy of the edges that start at each
             * vertex.
             */
            Map<V, Collection<QualifiedEdge<P>>> getOutwardEdges() {
                return deepIdentityCopy(outwards,
                                        Collections::unmodifiableCollection);
            }

            /**
             * Get an almost arbitrary sequence of vertices. The only
             * guaranteed ordering is that the initial vertices are also
             * the goals, in the originally specified order.
             */
            Sequence<V> getVertexOrder() {
                return Sequence.identityCopyOf(vertexes);
            }

            /**
             * Determine the reachability of all vertices through edges.
             * {@link #deriveVertexes()} must be called before this
             * method.
             */
            Sequence<QualifiedEdge<P>> getEdgeOrder() {
                Collection<QualifiedEdge<P>> reachOrder = new LinkedHashSet<>();
                Collection<V> reachables = newIdentityHashSet();
                Collection<V> newReachables =
                    LinkedIdentityHashMap.asSet(goalSequence);
                while (!newReachables.isEmpty()) {
                    /* Pick a vertex that we haven't handled yet, and
                     * mark it as handled. */
                    V vertex = removeOne(newReachables);
                    assert !reachables.contains(vertex);
                    reachables.add(vertex);

                    /* Find all neighbours of the vertex. */
                    Collection<? extends QualifiedEdge<P>> inEdges =
                        inwards.get(vertex);
                    Collection<? extends QualifiedEdge<P>> outEdges =
                        outwards.get(vertex);
                    if (inEdges == null || outEdges == null) {
                        /* A goal has been completely detached by
                         * elimination of its edges. */
                        if (false) {
                            System.err.printf("vertex %s is detached%n",
                                              vertex);
                            System.err.printf("in vertices: %s%n",
                                              inwards.keySet());
                            inwards.keySet().stream()
                                .filter(e -> e.toString()
                                    .equals(vertex.toString()))
                                .filter(e -> e != vertex)
                                .forEach(e -> System.err
                                    .printf("unmatched: %s(%d)(%d)  %s(%d)(%d)%n",
                                            e, e.hashCode(),
                                            System.identityHashCode(e), vertex,
                                            vertex.hashCode(),
                                            System.identityHashCode(vertex)));
                            System.err.printf("out vertices: %s%n",
                                              outwards.keySet());
                            outwards.keySet().stream()
                                .filter(e -> e.toString()
                                    .equals(vertex.toString()))
                                .filter(e -> e != vertex)
                                .forEach(e -> System.err
                                    .printf("unmatched: %s(%d)(%d)  %s(%d)(%d)%n",
                                            e, e.hashCode(),
                                            System.identityHashCode(e), vertex,
                                            vertex.hashCode(),
                                            System.identityHashCode(vertex)));
                            assert !inwards.keySet().contains(vertex);
                        }
                        assert goalSequence.contains(vertex);

                        /* Both in and out edges must be null because
                         * deriveVertexes() always ensures that any
                         * referenced vertex has an entry in both
                         * 'inwards' and 'outwards'. */
                        assert inEdges == outEdges;

                        /* Indicate that we can't fulfil this
                         * request. */
                        return null;
                    }

                    /* Identify the neighbour vertices of this one. */
                    Collection<V> cands = newIdentityHashSet();
                    assert inEdges != null;
                    for (var e : inEdges)
                        cands.add(portMap.apply(e.start));
                    assert outEdges != null;
                    for (var e : outEdges)
                        cands.add(portMap.apply(e.finish));

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
                List<QualifiedEdge<P>> edgeOrder = new ArrayList<>(reachOrder);
                Collections.reverse(edgeOrder);

                return Sequence.copyOf(edgeOrder);
            }
        }

        final Routing routing = new Routing();
        routing.eliminateIncapaciousEdgeModes();
        routing.deriveVertexes();
        routing.route();

        routing.deriveVertexes();
        final Map<QualifiedEdge<P>, BitSet> edgeCaps = routing.getEdgeModes();
        if (false) System.err.printf("caps: %s%n", edgeCaps);

        final Map<V, Collection<QualifiedEdge<P>>> inwards =
            routing.getInwardEdges();
        final Map<V, Collection<QualifiedEdge<P>>> outwards =
            routing.getOutwardEdges();
        final Sequence<V> vertexOrder = routing.getVertexOrder();

        /* The goal order should be a subsequence of the vertex
         * order. */
        for (var entry : goalSequence.decode().entrySet())
            assert vertexOrder.get(entry.getKey()) == entry.getValue();

        /* Ensure that every goal has an edge. */
        for (V v : goalOrder) {
            Collection<QualifiedEdge<P>> ins = inwards.get(v);
            Collection<QualifiedEdge<P>> outs = outwards.get(v);
            assert ins != null;
            assert outs != null;
            if (ins.isEmpty() && outs.isEmpty()) return Collections.emptyList();
        }

        /* Assign each edge's mode to a digit in a multi-base number.
         * Index 0 will be the least significant digit. Edges that are
         * most reachable will correspond to the most significant
         * digits. This call returns null if it detects a detached
         * goal. */
        final Sequence<QualifiedEdge<P>> edgeIndex = routing.getEdgeOrder();
        if (edgeIndex == null) return Collections.emptyList();
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
                        QualifiedEdge<P> e = edgeIndex.get(en);
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
                        QualifiedEdge<P> e = edgeIndex.get(en);
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
                                     goalSequence.get(goal), super.toString());
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
            Collection<QualifiedEdge<P>> outs = outwards.get(vertex);
            Collection<QualifiedEdge<P>> ins = inwards.get(vertex);
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

            final int goalNumber = goalSequence.getAsInt(vertex);
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

        if (false) {
            /* Display all edge modes. */
            for (var entry : edgeIndex.decode().entrySet()) {
                var e = entry.getValue();
                int i = entry.getKey();
                List<BitSet> modes = edgeCaps.get(e).stream()
                    .mapToObj(MixedRadixTreePlotter::of)
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

        /* Prepare to map the results into something the user can
         * understand, namely, a map from each used edge to its source
         * set and consumed capacity. We first translate each digit's
         * index (an integer) into a pair of integers (its index and its
         * digit value), and filter out those with a digit value of 0
         * (which also corresponds to a mode of 0, i.e., a disused
         * edge). */

        /* TODO: Move this closer to where we expect it or depend on
         * it. */
        assert modeMap.length <= edgeCaps.size();

        /* The key mapping is just a translation from the first member
         * (the edge index) into the corresponding QualifiedEdge
         * object. */
        assert modeMap.length == edgeIndex.size();
        Function<int[], QualifiedEdge<P>> keyMapper =
            ar -> edgeIndex.get(ar[0]);

        /* The value mapping first converts the digit value into a
         * source set (as a bit set), and then simultaneously converts
         * that into a Collection of user-defined vertices and into a
         * resource consumption. These are presented as a map entry. */
        Function<int[], Map.Entry<BitSet, BidiCapacity>> valueMapper = ar -> {
            /* The first element is the edge number. */
            final int en = ar[0];

            /* The second element is the digit value. */
            final int digit = ar[1];

            /* Convert the digit into a source set. */
            BitSet srcset = of(modeMap[en][digit - 1][0]);

            /* Convert the source set into a collection mapped to its
             * bandwidth consumption. */
            return Map.entry(srcset, bwreq.getPair(srcset));
        };

        /* Define how to convert each solution into the format the user
         * wants. */
        Function<IntUnaryOperator,
                 Map<QualifiedEdge<P>,
                     Map.Entry<BitSet, BidiCapacity>>> translator =
                         digits -> IntStream.range(0, edgeIndex.size())
                             .mapToObj(pairer(digits))
                             .filter(MixedRadixTreePlotter::isInUse)
                             .collect(Collectors.toMap(keyMapper, valueMapper));

        /* Specify how to check that all constraints are met for a
         * prefix of most significant digits. 'en' is the edge number
         * (or digit number) of the least significant digit to check.
         * 'digits' provides the state of all digits. */
        MixedRadixValidator validator = (en, digits) -> {
            for (Constraint c : constraints[en])
                if (!c.check(digits)) return false;
            return true;
        };

        /* Specify the radices of each digit as the number of in-use
         * modes supported by the corresponding edge, plus 1 for the
         * disused mode. */
        IntUnaryOperator bases = i -> modeMap[i].length + 1;

        /* Prepare to iterate over the edge modes while checking
         * constraints. */
        return MixedRadixIterable.to(translator).timeout(timeout)
            .over(modeMap.length, bases).constrainedBy(validator).build();
    }

    /**
     * Map an integer to a 2-element array of integers, the first being
     * the original value, and the second computed from it by a unary
     * operator.
     * 
     * @param op the operator to generate the second element
     * 
     * @return an array of the input and the result of applying the
     * operator to the input
     */
    private static IntFunction<int[]> pairer(IntUnaryOperator op) {
        return i -> new int[] { i, op.applyAsInt(i) };
    }

    /**
     * Determine whether an edge is in use, given an array of its index
     * and digit value.
     * 
     * @param ar an array of at least 2 elements, with the second
     * specifying a digit value
     * 
     * @return {@code true} if the second element is not zero;
     * {@code false} otherwise
     */
    private static boolean isInUse(int[] ar) {
        return ar[1] != 0;
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

    private static boolean cross(QualifiedEdge<Vertex> e0,
                                 QualifiedEdge<Vertex> e1) {
        return edgesCross(e0.start, e0.finish, e1.start, e1.finish);
    }

    private static double length(QualifiedEdge<Vertex> e) {
        return distance(e.start, e.finish);
    }

    private static boolean abut(QualifiedEdge<Vertex> e0,
                                QualifiedEdge<Vertex> e1) {
        if (e0 == e1) return false;
        if (e0.start == e1.start) return true;
        if (e0.start == e1.finish) return true;
        if (e0.finish == e1.start) return true;
        if (e0.finish == e1.finish) return true;
        return false;
    }

    private static double minSum(Map<? extends QualifiedEdge<Vertex>,
                                     ? extends BidiCapacity> cand) {
        return cand.entrySet().parallelStream().mapToDouble(entry -> {
            QualifiedEdge<Vertex> key = entry.getKey();
            BidiCapacity val = entry.getValue();
            return key.cost * (val.ingress.min() + val.egress.min());
        }).sum();
    }

    private static double postScaledMinSum(Map<? extends QualifiedEdge<Vertex>,
                                               ? extends BidiCapacity> cand) {
        return cand.entrySet().parallelStream().mapToDouble(entry -> {
            QualifiedEdge<Vertex> key = entry.getKey();
            BidiCapacity val = entry.getValue();
            double edgeScore = val.ingress.min() + val.egress.min();
            edgeScore /= val.egress.max() / key.capacity.egress.max();
            edgeScore /= val.ingress.max() / key.capacity.ingress.max();
            return key.cost * edgeScore;
        }).sum();
    }

    private static double preScaledMinSum(Map<? extends QualifiedEdge<Vertex>,
                                              ? extends BidiCapacity> cand) {
        return cand.entrySet().parallelStream().mapToDouble(entry -> {
            QualifiedEdge<Vertex> key = entry.getKey();
            BidiCapacity val = entry.getValue();
            double edgeScore = val.ingress.min() * val.ingress.max()
                / key.capacity.ingress.max();
            edgeScore +=
                val.egress.min() * val.egress.max() / key.capacity.egress.max();
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

        List<QualifiedEdge<Vertex>> edges = new ArrayList<>();
        {
            /* Create edges between every pair of vertices. */
            for (int i = 0; i < vertexes.size() - 1; i++) {
                Vertex v0 = vertexes.get(i);
                for (int j = i + 1; j < vertexes.size(); j++) {
                    Vertex v1 = vertexes.get(j);
                    double cost = distance(v0, v1);
                    BidiCapacity cap = BidiCapacity
                        .of(Capacity.at(2.0 + rng.nextDouble() * 8.0),
                            Capacity.at(2.0 + rng.nextDouble() * 8.0));
                    QualifiedEdge<Vertex> e =
                        new QualifiedEdge<>(v0, v1, cap, cost);
                    edges.add(e);
                }
            }

            /* Prune longer edges that cross others. */
            outer: for (int i = 0; i < edges.size() - 1; i++) {
                QualifiedEdge<Vertex> e0 = edges.get(i);
                double s0 = length(e0);
                for (int j = i + 1; j < edges.size(); j++) {
                    QualifiedEdge<Vertex> e1 = edges.get(j);
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
                final QualifiedEdge<Vertex> e0 = edges.get(i0);
                final double s0 = length(e0);
                outer1: for (int i1 = i0 + 1; i1 < edges.size() - 1; i1++) {
                    final QualifiedEdge<Vertex> e1 = edges.get(i1);
                    if (!abut(e0, e1)) continue;

                    final double s1 = length(e1);
                    outer2: for (int i2 = i1 + 1; i2 < edges.size(); i2++) {
                        final QualifiedEdge<Vertex> e2 = edges.get(i2);
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
                best = Math.max(best, e.capacity.ingress.min());
                best = Math.max(best, e.capacity.egress.min());
            }
            maxCap = best;
        }

        /* Choose a tree. */
        final Map<QualifiedEdge<Vertex>,
                  Map.Entry<? extends BitSet, ? extends BidiCapacity>> tree;
        if (true) {
            TreePlotter plotter =
                new MixedRadixTreePlotter(MixedRadixTreePlotter
                    .biasThreshold(0.99));
            final DemandFunction bwreq;
            if (true) {
                bwreq = new PairDemandFunction(IntStream.range(0, goals.size())
                    .mapToObj(i -> BidiCapacity.of(Capacity.at(4.0),
                                                   Capacity.at(2.5)))
                    .collect(Collectors.toList()));
            } else if (true) {
                bwreq =
                    MatrixDemandFunction.forTree(goals.size(), 0, BidiCapacity
                        .of(Capacity.at(1.4), Capacity.at(0.5)), null);
            } else {
                bwreq = new FlatDemandFunction(goals.size(), Capacity.at(3.0));
            }

            for (int m = 1; m < (1 << bwreq.degree()) - 1; m++) {
                BitSet bs = of(m);
                BidiCapacity bw = bwreq.getPair(bs);
                System.out.printf("%2d %12s %s%n", m, bs, bw);
            }
            Map<? extends QualifiedEdge<Vertex>,
                ? extends Map.Entry<? extends BitSet,
                                    ? extends BidiCapacity>> best = null;
            double bestScore = Double.MAX_VALUE;
            assert bwreq.degree() == goals.size();
            for (var cand : plotter.plot(goals, bwreq, edges)) {
                double score = 0.0;
                for (var entry : cand.entrySet()) {
                    QualifiedEdge<Vertex> key = entry.getKey();
                    BidiCapacity val = entry.getValue().getValue();
                    score += key.cost * (val.ingress.min() + val.egress.min());
                }
                if (best == null || score < bestScore) {
                    best = cand;
                    bestScore = score;
                    if (false) System.err
                        .printf("acc %g: %s%n", bestScore,
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
            for (QualifiedEdge<Vertex> e : edges) {
                final double len = length(e);
                final double dx = e.finish.x - e.start.x;
                final double dy = e.finish.y - e.start.y;
                final double startFrac =
                    e.capacity.ingress.min() / maxCap * vertexRadius;
                final double endFrac =
                    e.capacity.egress.min() / maxCap * vertexRadius;
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
                QualifiedEdge<Vertex> e = entry.getKey();
                BidiCapacity bw = entry.getValue().getValue();
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
