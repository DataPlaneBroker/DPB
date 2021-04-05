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

import uk.ac.lancs.dpb.bw.BandwidthPair;

/**
 * Connects two vertices. This actually represents a pair of directed
 * edges in opposite directions, and each has a distinct capacity.
 * However, they both have the same over-all cost.
 * 
 * @param <V> the vertex type
 *
 * @author simpsons
 */
public final class Edge<V> {
    /**
     * The start vertex
     */
    public final V start;

    /**
     * The finish vertex
     */
    public final V finish;

    /**
     * The cumulative cost metric
     */
    public final double cost;

    final BandwidthPair metrics;

    /**
     * Create a connection between two vertices. The edge has a
     * direction, defined by having a start vertex and a finish vertex.
     * Forward metrics express the capacity of the edge for traffic from
     * start to finish, and reverse metrics for the opposite direction.
     * 
     * @param start the starting vertex for forward travel
     * 
     * @param finish the finishing vertex for forward travel
     * 
     * @param cost the cumulative cost metric for this edge
     * 
     * @param metrics the edge capacity, with ingress being from start
     * to finish
     */
    public Edge(V start, V finish, BandwidthPair metrics, double cost) {
        this.start = start;
        this.finish = finish;
        this.metrics = metrics;
        this.cost = cost;
    }
}
