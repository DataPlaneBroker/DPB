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
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import uk.ac.lancs.dpb.bw.BandwidthFunction;
import uk.ac.lancs.dpb.bw.BandwidthPair;
import uk.ac.lancs.dpb.bw.BandwidthRange;

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
    private static BitSet of(int pattern) {
        return BitSet.valueOf(new long[] { pattern });
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
                BandwidthRange forwardDemand = bwreq.get(fwd);
                if (forwardDemand.min() > edge.metrics.ingress.min()) continue;

                /* Work out the bandwidth in the reverse direction of
                 * the edge egress capacity. */
                BitSet rev = modeCache.get(modes - mode - 1);
                BandwidthRange inverseDemand = bwreq.get(rev);
                if (inverseDemand.min() > edge.metrics.egress.min()) continue;

                /* The edge can be used in this mode. Retain this fact,
                 * and cache the amount of bandwidth it would use in
                 * that mode. */
                tmp.computeIfAbsent(edge, e -> new HashMap<>())
                    .put(fwd, BandwidthPair.of(forwardDemand, inverseDemand));
            }
        }

        /* Store a deep immutable map. */
        return tmp.entrySet().stream().collect(Collectors
            .toMap(Map.Entry::getKey, e -> Map.copyOf(e.getValue())));
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

        return edgeOrder;
    }

    private interface Constraint {
        boolean check(IntUnaryOperator digits);
    }

    @Override
    public <V>
        Iterable<? extends Map<? extends Edge<V>, ? extends BandwidthPair>>
        plot(List<? extends V> goalOrder, BandwidthFunction bwreq,
             Collection<? extends Edge<V>> edges) {
        /* Compute the number of modes each edge can be used in. Each
         * mode is a bit pattern where the position of each bit
         * identifies a goal vertex in the goalOrder list. If the bit is
         * set, the goal is reachable by exiting an edge through its
         * finish; if clear, through its start. The mode with all bits
         * set is not used, as it places all goals at one end. The zero
         * mode is similarly useless; however, we co-opt it to indicate
         * an edge that isn't used. This number is always 1 less than a
         * power of 2. */
        final int modeCount = (1 << goalOrder.size()) - 1;

        /* Create an index from mode number to BitSet, so that we can
         * call the bandwidth function. Remember that index 0 of this
         * list corresponds to mode 1. */
        final List<BitSet> modeCache = IntStream.rangeClosed(1, modeCount)
            .mapToObj(ComprehensiveTreePlotter::of)
            .collect(Collectors.toList());

        /* Record which modes each edge can cope with, and how much
         * bandwidth they will use in that mode. Edges that can't cope
         * with the requirement in any mode will not be included in the
         * map. The map for a given edge will not include modes it has
         * insufficient capacity for. */
        final Map<Edge<V>, Map<BitSet, BandwidthPair>> edgeCaps =
            getEdgeModes(modeCache, bwreq, edges);

        /* Assign each goal an integer. */
        final Index<V> goalIndex = Index.copyOf(goalOrder);

        /* Discover all vertices, and find out which edges connect each
         * vertex. */
        final Map<V, Collection<Edge<V>>> inwards;
        final Map<V, Collection<Edge<V>>> outwards;
        final Index<V> vertexOrder;
        {
            /* Start identifying all vertices implied by edges by
             * including the goals. */
            Collection<V> tmp = new LinkedHashSet<>(goalOrder);

            /* Also keep track of the edges of each vertex. */
            Map<V, Collection<Edge<V>>> ins = new IdentityHashMap<>();
            Map<V, Collection<Edge<V>>> outs = new IdentityHashMap<>();

            /* For every edge, record which vertices it connects to. */
            for (Edge<V> edge : edgeCaps.keySet()) {
                tmp.add(edge.start);
                outs.computeIfAbsent(edge.start, k -> new HashSet<>())
                    .add(edge);
                tmp.add(edge.finish);
                ins.computeIfAbsent(edge.finish, k -> new HashSet<>())
                    .add(edge);
            }

            /* Get deep immutable copies of these structures. */
            inwards = ins.entrySet().stream().collect(Collectors
                .toMap(Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
            outwards = outs.entrySet().stream().collect(Collectors
                .toMap(Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
            vertexOrder = Index.copyOf(tmp);
        }

        /* Assign each edge's mode to a digit in a multi-base number.
         * Index 0 will be the least significant digit. Edges that are
         * most reachable will correspond to the most significant
         * digits. */
        final Index<Edge<V>> edgeIndex =
            Index.copyOf(getEdgeOrder(goalIndex, inwards, outwards));

        /* Create a mapping from mode index to mode pattern for each
         * edge that has at least one valid non-zero mode. The first
         * index is the edge number. The second index is the mode index.
         * The third is either zero or one, giving the from set or the
         * to set. */
        assert edgeCaps.keySet().containsAll(edgeIndex);
        final BitSet[][][] modeMap = edgeIndex.stream()
            .map(e -> edgeCaps.get(e).keySet().stream().map(fs -> {
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
                        invs.set(i);
                        en = -1 - en;
                    }
                    this.edges[i] = en;
                }
            }

            @Override
            public boolean check(IntUnaryOperator digits) {
                /* Get the primary edge. */
                final int pen = edges[0];

                /* Get the primary edge's mode index. */
                final int pmi = digits.applyAsInt(pen);

                /* If the primary isn't used, its external set can't
                 * conflict with anything else. */
                if (pmi == 0) return true;

                /* The primary is in use, so get its external set. */
                BitSet ppat = modeMap[pen][pmi - 1][invs.get(0) ? 1 : 0];

                /* Get the external sets of the other edges. Abort if
                 * they include the same goals. */
                for (int i = 1; i < edges.length; i++) {
                    // The other peer
                    final int oen = edges[i];
                    // The other peer's mode index
                    final int omi = digits.applyAsInt(oen);

                    /* If the edge is unused, its external set doesn't
                     * conflict with the primary's. */
                    if (omi == 0) continue;

                    final boolean inv = invs.get(i);
                    BitSet opat = modeMap[oen][omi][inv ? 1 : 0];
                    if (opat.intersects(ppat)) return false;
                }

                /* There were no conflicts. */
                return true;
            }
        }

        class CompleteExternalUnion implements Constraint {
            final int[] edges;

            final BitSet invs = new BitSet();

            CompleteExternalUnion(List<? extends Integer> edges) {
                this.edges = new int[edges.size()];
                for (int i = 0; i < this.edges.length; i++) {
                    int en = edges.get(i);
                    if (en < 0) {
                        invs.set(i);
                        en = -1 - en;
                    }
                    this.edges[i] = en;
                }
            }

            @Override
            public boolean check(IntUnaryOperator digits) {
                /* Create a set of all goals, in preparation to
                 * eliminate them. */
                BitSet base = new BitSet();
                base.or(modeCache.get(modeCount - 1));
                assert base.cardinality() == goalOrder.size();

                /* Do any special elimination. */
                augment(base);

                /* Eliminate each edge's external set. */
                for (int i = 0; i < edges.length; i++) {
                    final int en = edges[i];
                    final int mi = digits.applyAsInt(en);

                    /* An unused edge contributes nothing. */
                    if (mi == 0) continue;
                    final boolean inv = invs.get(i);
                    BitSet pat = modeMap[en][mi][inv ? 1 : 0];
                    base.andNot(pat);
                }

                return base.isEmpty();
            }

            public void augment(BitSet base) {}
        }

        class CompleteExternalUnionExceptGoal extends CompleteExternalUnion {
            final int goal;

            CompleteExternalUnionExceptGoal(int goal,
                                            List<? extends Integer> edges) {
                super(edges);
                this.goal = goal;
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
            final int goal;

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
            GoalConnected(int goal, List<? extends Integer> edges) {
                this.goal = goal;
                this.edges = new int[edges.size()];
                for (int i = 0; i < this.edges.length; i++) {
                    int en = edges.get(i);
                    if (en < 0) en = -1 - en;
                    this.edges[i] = en;
                }
            }

            @Override
            public boolean check(IntUnaryOperator digits) {
                for (int i = 0; i < edges.length; i++) {
                    final int en = edges[i];
                    final int mi = digits.applyAsInt(en);

                    /* Ignore this edge if it isn't used. */
                    if (mi == 0) continue;
                    return true;
                }
                return false;
            }
        }

        /**
         * Checks that an edge's internal set includes a goal. This
         * should be used when a vertex has been identified as a goal.
         */
        class InternalSetIncludesGoal implements Constraint {
            final int goal;

            final int edge;

            final int invert;

            /**
             * Create a constraint requiring that an edge's internal set
             * includes a specific goal.
             * 
             * @param goal the goal index, as defined by
             * {@code vertexOrder}
             * 
             * @param edge the edge index if it is an inward edge;
             * {@code -1} minus the edge index if it is an outward edge
             */
            InternalSetIncludesGoal(int goal, int edge) {
                this.goal = goal;
                if (edge < 0) {
                    /* This is an outward edge. Its internal set is its
                     * from set. */
                    this.edge = -1 - edge;
                    this.invert = 0;
                } else {
                    /* This is an inward edge. Its internal set is its
                     * to set. */
                    this.edge = edge;
                    this.invert = 1;
                }
            }

            @Override
            public boolean check(IntUnaryOperator digits) {
                final int mi = digits.applyAsInt(edge);
                if (mi == 0) return true;
                final BitSet mode = modeMap[edge][mi][invert];
                return mode.get(goal);
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

            /* Identify constraints on external sets of these edges.
             * There are none if the vertex has fewer than 2 edges. */
            if (ecs.size() >= 2) {
                /* Define a constraint for every tail of the sequence.
                 * No edge's external set may overlap with another's. */
                for (int i = 0; i < ecs.size() - 2; i++) {
                    List<Integer> sub = ecs.subList(i, ecs.size());
                    Constraint constraint = new OneExternalPerGoal(sub);
                    checkers.computeIfAbsent(ecs.get(0), k -> new ArrayList<>())
                        .add(constraint);
                }
            }

            final int goalNumber = vertexOrder.getAsInt(vertex);
            assert goalNumber >= 0;
            if (goalNumber < goalOrder.size()) {
                assert goalOrder.get(goalNumber) == vertex;

                /* No edge connected to a goal may include the goal in
                 * its external set. */
                for (int i : ecs) {
                    Constraint constraint =
                        new InternalSetIncludesGoal(goalNumber, i);
                    checkers.computeIfAbsent(ecs.get(0), k -> new ArrayList<>())
                        .add(constraint);
                }

                /* Every goal vertex must have at least one edge in use
                 * connected to it. */
                {
                    Constraint constraint = new GoalConnected(goalNumber, ecs);
                    checkers.computeIfAbsent(ecs.get(0), k -> new ArrayList<>())
                        .add(constraint);
                }

                /* The union of the external sets and this goal must be
                 * the complete set of goals. */
                {
                    Constraint constraint =
                        new CompleteExternalUnionExceptGoal(goalNumber, ecs);
                    checkers.computeIfAbsent(ecs.get(0), k -> new ArrayList<>())
                        .add(constraint);
                }
            } else {
                /* The union of the external sets must be the complete
                 * set of goals. */
                Constraint constraint = new CompleteExternalUnion(ecs);
                checkers.computeIfAbsent(ecs.get(0), k -> new ArrayList<>())
                    .add(constraint);
            }
        }

        /* Convert the constraints to arrays (for speed?). */
        final Constraint[][] constraints = new Constraint[edgeCaps.size()][];
        for (int i = 0; i < edgeCaps.size(); i++)
            constraints[i] = checkers.getOrDefault(i, Collections.emptySet())
                .toArray(n -> new Constraint[n]);

        /* Prepare to iterate over the edge modes while checking
         * constraints. */
        IntUnaryOperator bases = i -> modeMap[i].length;
        assert modeMap.length == edgeIndex.size();
        Function<IntUnaryOperator,
                 Map<? extends Edge<V>, ? extends BandwidthPair>> translator =
                     digits -> IntStream.range(0, edgeIndex.size())
                         .filter(en -> digits.applyAsInt(en) != 0).boxed()
                         .collect(Collectors
                             .toMap(en -> edgeIndex.get(en), en -> edgeCaps
                                 .get(edgeIndex.get(en))
                                 .get(modeMap[en][digits.applyAsInt(en)][0])));
        MixedRadixValidator validator = (en, digits) -> {
            for (Constraint c : constraints[en])
                if (!c.check(digits)) return false;
            return true;
        };
        return MixedRadixIterable.to(translator).over(modeMap.length, bases)
            .constrainedBy(validator).build();
    }
}
