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
                         ? extends Collection<? extends Edge<V>>> outwards) {
        Collection<Edge<V>> reachOrder = new LinkedHashSet<>();
        Collection<V> reachables = new HashSet<>();
        Collection<V> newReachables = new LinkedHashSet<>();
        newReachables.addAll(goals);
        while (!newReachables.isEmpty()) {
            /* Pick a vertex that we haven't handled yet, and mark it as
             * handled. */
            V vertex = removeOne(newReachables);
            assert !reachables.contains(vertex);
            reachables.add(vertex);

            /* Find all neighbours of the vertex. */
            Collection<V> cands = new HashSet<>();
            Collection<? extends Edge<V>> inEdges = inwards.get(vertex);
            assert inEdges != null;
            cands.addAll(inEdges.stream().map(e -> e.start)
                .collect(Collectors.toSet()));
            Collection<? extends Edge<V>> outEdges = outwards.get(vertex);
            assert outEdges != null;
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

        return edgeOrder;
    }

    private interface Constraint {
        boolean check(IntUnaryOperator digits);

        void verify(int baseEdge);
    }

    private static <V> String setString(BitSet pat, Index<V> goalIndex) {
        return "{" + pat.stream().mapToObj(goalIndex::get).map(Object::toString)
            .collect(Collectors.joining("+")) + "}";
    }

    @Override
    public <V>
        Iterable<? extends Map<? extends Edge<V>, ? extends BandwidthPair>>
        plot(List<? extends V> goalOrder, BandwidthFunction bwreq,
             Collection<? extends Edge<V>> edges) {
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
            private final Map<Edge<V>, Collection<BitSet>> edgeCaps =
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
                            int goal = goalIndex.getAsInt(edge.start);
                            if (goal >= 0) {
                                assert goal < goalIndex.size();
                                if (!fwd.get(goal)) continue;
                            }
                        }

                        /* If the edge has a goal as its finish, its to
                         * set must include that goal. */
                        {
                            int goal = goalIndex.getAsInt(edge.finish);
                            if (goal >= 0) {
                                assert goal < goalIndex.size();
                                if (fwd.get(goal)) continue;
                            }
                        }

                        /* The edge can be used in this mode. Retain
                         * this fact. TODO: A plain hash map should
                         * suffice, but introduces perturbations making
                         * fault diagnosis difficult. */
                        edgeCaps
                            .computeIfAbsent(edge, e -> new LinkedHashSet<>())
                            .add(fwd);
                    }
                }
            }

            /**
             * Get a deep immutable copy of the selected edges. Call
             * {@link #eliminateIncapaciousEdgeModes()} first.
             */
            Map<Edge<V>, Collection<BitSet>> getEdgeModes() {
                return deepCopy(edgeCaps, Collections::unmodifiableCollection);
                // return
                // edgeCaps.entrySet().stream().collect(Collectors
                // .toMap(Map.Entry::getKey, e ->
                // Map.copyOf(e.getValue())));
            }

            /**
             * Identifies for each vertex the set of edges that finish
             * at that edge. {@link #reachEdges()} must be called to
             * populate this.
             */
            private final Map<V, Collection<Edge<V>>> inwards =
                new IdentityHashMap<>();

            /**
             * Identifies for each vertex the set of edges that start at
             * that edge. {@link #reachEdges()} must be called to
             * populate this.
             */
            private final Map<V, Collection<Edge<V>>> outwards =
                new IdentityHashMap<>();

            /**
             * Holds all reachable vertices, in order of reachability.
             * {@link #reachEdges()} must be called to populate this.
             */
            private final Collection<V> vertexes =
                new LinkedHashSet<>(goalOrder);

            /**
             * Determine the reachability of all vertices through edges.
             */
            void reachEdges() {
                /* For every edge, record which vertices it connects
                 * to. */
                for (Edge<V> edge : edgeCaps.keySet()) {
                    vertexes.add(edge.start);
                    outwards.computeIfAbsent(edge.start, k -> new HashSet<>())
                        .add(edge);
                    vertexes.add(edge.finish);
                    inwards.computeIfAbsent(edge.finish, k -> new HashSet<>())
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
             * Get a deep immutable copy of the edges that finish at
             * each vertex.
             */
            Map<V, Collection<Edge<V>>> getInwardEdges() {
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
            Map<V, Collection<Edge<V>>> getOutwardEdges() {
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
             * Get an index of vertices by reachability.
             */
            Index<V> getVertexOrder() {
                return Index.copyOf(vertexes);
            }
        }

        final Routing routing = new Routing();
        routing.eliminateIncapaciousEdgeModes();
        final Map<Edge<V>, Collection<BitSet>> edgeCaps =
            routing.getEdgeModes();

        /* Discover all vertices, and find out which edges connect each
         * vertex. */
        routing.reachEdges();
        final Map<V, Collection<Edge<V>>> inwards = routing.getInwardEdges();
        final Map<V, Collection<Edge<V>>> outwards = routing.getOutwardEdges();
        final Index<V> vertexOrder = routing.getVertexOrder();

        /* The goal order should be a subsequence of the vertex
         * order. */
        for (var entry : goalIndex.decode().entrySet())
            assert vertexOrder.get(entry.getKey()) == entry.getValue();

        /* Ensure that every goal has an edge. */
        for (V v : goalOrder) {
            Collection<Edge<V>> ins = inwards.get(v);
            Collection<Edge<V>> outs = outwards.get(v);
            assert ins != null;
            assert outs != null;
            if (ins.isEmpty() && outs.isEmpty()) return Collections.emptyList();
        }

        /* Assign each edge's mode to a digit in a multi-base number.
         * Index 0 will be the least significant digit. Edges that are
         * most reachable will correspond to the most significant
         * digits. */
        final Index<Edge<V>> edgeIndex =
            Index.copyOf(getEdgeOrder(goalIndex, inwards, outwards));
        assert edgeIndex.size() == edgeCaps.size();

        /* Create a mapping from mode index to mode pattern for each
         * edge that has at least one valid non-zero mode. The first
         * index is the edge number. The second index is the mode index.
         * The third is either zero or one, giving the from set or the
         * to set. */
        assert edgeCaps.keySet().containsAll(edgeIndex);
        final BitSet[][][] modeMap =
            edgeIndex.stream().map(e -> edgeCaps.get(e).stream().map(fs -> {
                BitSet[] r = new BitSet[2];
                r[0] = fs;
                r[1] = new BitSet();
                r[1].or(r[0]);
                r[1].flip(0, goalIndex.size());
                return r;
            }).toArray(n -> new BitSet[n][])).toArray(n -> new BitSet[n][][]);

        /**
         * Checks that an edge has a valid external set with respect to
         * other edges. It is assumed that all edges are connected to
         * the same vertex.
         */
        class OneExternalPerGoal implements Constraint {
            final int[] edges;

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
                    BitSet ppat = pmi == 0 ? null :
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
                            cur.set(0, modeMap[oen].length);
                            continue;
                        }

                        /* It's always okay if the other edge is not in
                         * use (mode index 0). */
                        cur.set(0);

                        /* Go over all mode indices supported by the
                         * other edge. */
                        for (int omi = 1; omi < modeMap[oen].length; omi++) {
                            final boolean inv = invs.get(oeni + 1);
                            BitSet opat = modeMap[oen][omi - 1][inv ? 1 : 0];

                            if (opat.intersects(ppat)) continue;
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
                        Edge<V> e = edgeIndex.get(en);
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
        }

        class CompleteExternalUnion implements Constraint {
            final int[] edges;

            final BitSet invs = new BitSet();

            CompleteExternalUnion(List<? extends Integer> edges) {
                assert !edges.isEmpty();
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
                        Edge<V> e = edgeIndex.get(en);
                        return String.format(" %s.%s", e,
                                             invs.get(i) ? "to" : "from");
                    }).collect(Collectors.joining());
            }

            @Override
            public boolean check(IntUnaryOperator digits) {
                /* Create a set of all goals, in preparation to
                 * eliminate them. We fail if there are any left, unless
                 * none of the edges are in use at all. */
                BitSet base = new BitSet();
                base.set(0, goalIndex.size());
                assert base.cardinality() == goalOrder.size();

                /* Do any special elimination. */
                augment(base);

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
                    BitSet pat = modeMap[en][mi - 1][inv ? 1 : 0];
                    base.andNot(pat);

                    /* We're okay if we've accounted for all of the
                     * goals. */
                    if (base.isEmpty()) return true;
                }

                /* We didn't account for any of the goals. We're okay
                 * only if all edges are disused (so we accounted for no
                 * goals). */
                return disused;
            }

            public void augment(BitSet base) {}

            @Override
            public void verify(int baseEdge) {
                assert edges[0] == baseEdge;
            }
        }

        class CompleteExternalUnionExceptGoal extends CompleteExternalUnion {
            final int goal;

            CompleteExternalUnionExceptGoal(int goal,
                                            List<? extends Integer> edges) {
                super(edges);
                this.goal = goal;
            }

            @Override
            public String toString() {
                return String.format("except goal %d%s %s", goal,
                                     goalIndex.get(goal), super.toString());
            }

            @Override
            public void augment(BitSet base) {
                base.clear(goal);
            }
        }

        /**
         * Checks that at least on of a set of edges is in use. This
         * should be used on the edges of a goal, as it must have a
         * connecting edge.
         */
        class GoalConnected implements Constraint {
            final int[] edges;

            /**
             * Create a constraint requiring that at least one edge
             * connecting to a goal vertex is in use.
             * 
             * @param goal the goal number
             * 
             * @param edges a list of edge codes. Inward edges are
             * represented by their indices. Outward edges are
             * represented by subtracting their indices from {@code -1}.
             * Whether an edge is inward or outward is ignored.
             */
            GoalConnected(List<? extends Integer> edges) {
                this.edges = new int[edges.size()];
                for (int i = 0; i < this.edges.length; i++) {
                    int en = edges.get(i);
                    if (en < 0) en = -1 - en;
                    this.edges[i] = en;
                }
            }

            @Override
            public String toString() {
                return String
                    .format("active in {%s }",
                            IntStream.range(0, edges.length).mapToObj(i -> {
                                return String.format(" %s",
                                                     edgeIndex.get(edges[i]));
                            }).collect(Collectors.joining()));
            }

            @Override
            public boolean check(IntUnaryOperator digits) {
                for (final int en : edges) {
                    final int mi = digits.applyAsInt(en);

                    /* Ignore this edge if it isn't used. */
                    if (mi != 0) return true;
                }
                return false;
            }

            @Override
            public void verify(int baseEdge) {
                assert edges[0] == baseEdge;
            }
        }

        final Map<Integer, Collection<Constraint>> checkers = new HashMap<>();

        /* Identify constraints. */
        for (V vertex : vertexOrder) {
            /* Get the vertex's inward edges' indices, and the two's
             * complements of its outward egdes' indices. Sort the edges
             * by index. */
            Collection<Edge<V>> outs = outwards.get(vertex);
            Collection<Edge<V>> ins = inwards.get(vertex);
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

                /* Every goal vertex must have at least one edge in use
                 * connected to it. */
                {
                    Constraint constraint = new GoalConnected(ecs);
                    int first = ecs.get(0);
                    if (first < 0) first = -1 - first;
                    checkers.computeIfAbsent(first, k -> new ArrayList<>())
                        .add(constraint);
                }

                /* The union of the external sets and this goal must be
                 * the complete set of goals. */
                {
                    Constraint constraint =
                        new CompleteExternalUnionExceptGoal(goalNumber, ecs);
                    int first = ecs.get(0);
                    if (first < 0) first = -1 - first;
                    checkers.computeIfAbsent(first, k -> new ArrayList<>())
                        .add(constraint);
                }
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
        final Constraint[][] constraints = new Constraint[edgeCaps.size()][];
        for (int i = 0; i < edgeIndex.size(); i++)
            constraints[i] = checkers.getOrDefault(i, Collections.emptySet())
                .toArray(n -> new Constraint[n]);

        {
            /* Display all edge modes. */
            List<Edge<V>> eo = new ArrayList<>(edgeIndex);
            Collections.sort(eo, ComprehensiveTreePlotter::compare);
            for (Edge<V> e : eo) {
                List<BitSet> modes = new ArrayList<>(edgeCaps.get(e));
                Collections.sort(modes, ComprehensiveTreePlotter::compare);
                System.err.printf("%s: %s%n", e, modes);
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
        IntUnaryOperator bases = i -> modeMap[i].length;
        assert modeMap.length == edgeIndex.size();
        assert modeMap.length == edgeCaps.size();
        Function<IntUnaryOperator, Map<Edge<V>, BandwidthPair>> translator =
            digits -> IntStream.range(0, edgeIndex.size())
                .filter(en -> digits.applyAsInt(en) != 0).boxed()
                .collect(Collectors.toMap(edgeIndex::get, en -> bwreq
                    .getPair(modeMap[en][digits.applyAsInt(en)][0])));
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
                new FlatBandwidthFunction(goals.size(), BandwidthRange.at(1.0));
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
                    System.err.printf("acc %g: %s%n", bestScore, best.keySet());
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
            out.printf(" viewBox='%g %g %g %g' width='100%%' height='%g%%'>%n",
                       0.0, 0.0, width + 0.0, height + 0.0,
                       100.0 * height / width);

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
            out.printf("<g fill='#aaa' stroke='none'>%n");
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
