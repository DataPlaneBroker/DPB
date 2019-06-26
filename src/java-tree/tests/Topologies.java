
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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import uk.ac.lancs.routing.span.Edge;

/**
 * Holds static methods for manipulating topologies.
 * 
 * @author simpsons
 */
public final class Topologies {
    private Topologies() {}

    /**
     * Convert a topology represented as an index from each vertex to
     * its neighbours to one represented as a set of edges. The
     * resultant topology is an undirected graph.
     * 
     * @param <V> the vertex type
     * 
     * @param neighbors the topology as an index from each vertex to its
     * neighbours
     * 
     * @return the set of edges
     */
    public static <V> Collection<Edge<V>>
        convertNeighborsToEdges(Map<? extends V, ? extends Collection<? extends V>> neighbors) {
        Collection<Edge<V>> edges = new HashSet<>();
        for (Map.Entry<? extends V, ? extends Collection<? extends V>> entry : neighbors
            .entrySet()) {
            V a = entry.getKey();
            for (V b : entry.getValue())
                edges.add(Edge.of(a, b));
        }
        return edges;
    }

    /**
     * Convert a topology represented as a set of edges into one
     * represented as an index from each vertex to its neighbours.
     * 
     * @param <V> the vertex type
     * 
     * @param edges the set of edges
     * 
     * @return an index from each vertex to its neighbours
     */
    public static <V> Map<V, Collection<V>>
        convertEdgesToNeighbors(Collection<? extends Edge<? extends V>> edges) {
        final Map<V, Collection<V>> neighbors = new HashMap<>();
        for (Edge<? extends V> edge : edges) {
            V v1 = edge.first();
            V v2 = edge.second();
            neighbors.computeIfAbsent(v1, k -> new HashSet<>()).add(v2);
            neighbors.computeIfAbsent(v2, k -> new HashSet<>()).add(v1);
        }
        return neighbors;
    }

    /**
     * Generate a scale-free graph.
     * 
     * @param <V> the vertex type
     * 
     * @param source a generator of new vertices
     * 
     * @param vertexCount the number of vertices to create
     * 
     * @param newEdgesPerVertex the number of times each new vertex will
     * be attempted to connect to an existing vertex
     * 
     * @param edgeSets a record of the topology, mapping each vertex to
     * its neighbours
     * 
     * @param rng a random-number generator for selecting a vertex to
     * form an edge with
     */
    public static <V> void
        generateTopology(Supplier<V> source, final int vertexCount,
                         IntSupplier newEdgesPerVertex,
                         Map<V, Collection<V>> edgeSets, Random rng) {
        /* Create a starting point. */
        V v0 = source.get();
        V v1 = source.get();
        edgeSets.put(v0, new HashSet<>());
        edgeSets.put(v1, new HashSet<>());
        edgeSets.get(v0).add(v1);
        edgeSets.get(v1).add(v0);
        int edgeCount = 2;

        /* Add more vertices, and link to a random existing one each
         * time. */
        for (int i = 2; i < vertexCount; i++) {
            V latest = source.get();

            /* Identify vertices to link to. */
            Collection<V> chosen = new HashSet<>();
            final int linkCount = newEdgesPerVertex.getAsInt();
            for (int j = 0; j < linkCount; j++) {
                int chosenIndex = rng.nextInt(edgeCount);
                for (Map.Entry<V, Collection<V>> entry : edgeSets
                    .entrySet()) {
                    if (chosenIndex < entry.getValue().size()) {
                        chosen.add(entry.getKey());
                        break;
                    }
                    chosenIndex -= entry.getValue().size();
                }
            }

            /* Link to those vertices. */
            edgeSets.put(latest, new HashSet<>());
            for (V existing : chosen) {
                if (edgeSets.get(latest).add(existing)) {
                    edgeSets.get(existing).add(latest);
                    edgeCount += 2;
                }
            }
        }
    }

}
