/*
 * Copyright 2018,2019, Regents of the University of Lancaster
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Guides a spanning-tree algorithm based on FIBs available on each
 * vertex. The method {@link #reached(Object)} should be passed to
 * {@link SpanningTreeComputer.Builder#notifying(java.util.function.Consumer)},
 * and {@link #select(Edge, Edge)} should be passed to
 * {@link SpanningTreeComputer.Builder#withEdgePreference(java.util.Comparator)}.
 * 
 * @param <V> the vertex type
 * 
 * @author simpsons
 */
public final class FIBSpanGuide<V> {
    private final List<V> sequence;
    private final Map<Edge<V>, Map<V, Double>> waysPerEdge = new HashMap<>();

    /**
     * Create a guide based on FIBs on all vertices. The set of all keys
     * of the inner maps is interpreted as the set of terminals.
     * 
     * @param fibs a map from each vertex to its FIB
     */
    public FIBSpanGuide(Map<? extends V, ? extends Map<? extends V, ? extends Way<? extends V>>> fibs) {
        /* Collate distances to each terminal by edge. */
        Collection<V> terminals = new HashSet<>();
        for (Map.Entry<? extends V, ? extends Map<? extends V, ? extends Way<? extends V>>> entry : fibs
            .entrySet()) {
            V v1 = entry.getKey();
            for (Map.Entry<? extends V, ? extends Way<? extends V>> route : entry
                .getValue().entrySet()) {

                /* Get a full set of terminals while we're at it. */
                V term = route.getKey();
                terminals.add(term);

                Way<? extends V> way = route.getValue();
                V v2 = way.nextHop;
                if (v2 == null) continue;
                Edge<V> edge = Edge.of(v1, v2);
                waysPerEdge.computeIfAbsent(edge, k -> new HashMap<>())
                    .put(term, way.distance);
            }
        }

        /* Find the longest distance to each terminal from each
         * other. */
        Map<V, Double> worst = new HashMap<>();
        for (V v1 : terminals) {
            for (Map.Entry<? extends V, ? extends Way<? extends V>> route : fibs
                .get(v1).entrySet()) {
                V dest = route.getKey();
                Double soFar = worst.get(dest);
                double cand = route.getValue().distance;
                if (soFar == null || cand > soFar) worst.put(dest, cand);
            }
        }

        /* Sort terminals by longest distance. We will connect the
         * longest ones first. */
        sequence = new ArrayList<>(worst.keySet());
        Collections.sort(sequence, (a, b) -> {
            double ad = worst.get(a);
            double bd = worst.get(b);
            return Double.compare(bd, ad);
        });
    }

    /**
     * Mark a vertex as reached. If the vertex is not a terminal this
     * call has no effect. Otherwise, the guide will stop using routing
     * entries to it for further expansion of the tree.
     * 
     * @param v the vertex that has been reached
     */
    public void reached(V v) {
        /* If the newly reached vertex is a terminal, we will stop using
         * routes to it. */
        sequence.remove(v);
    }

    /**
     * Choose an edge.
     * 
     * @param a one edge
     * 
     * @param b another edge
     * 
     * @return negative if <code>a</code> is a better edge than
     * <code>b</code>
     */
    public int select(Edge<V> a, Edge<V> b) {
        /* Return negative if a is preferable to b. */
        V target = sequence.get(0);
        double ad = waysPerEdge.getOrDefault(a, Collections.emptyMap())
            .getOrDefault(target, Double.MAX_VALUE);
        double bd = waysPerEdge.getOrDefault(b, Collections.emptyMap())
            .getOrDefault(target, Double.MAX_VALUE);
        return Double.compare(ad, bd);
    }

    /**
     * Suggest a vertex to start from.
     * 
     * @return the vertex to start building a spanning tree from
     */
    public V first() {
        return sequence.get(0);
    }
}
