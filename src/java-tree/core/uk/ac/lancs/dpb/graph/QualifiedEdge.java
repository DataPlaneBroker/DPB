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

package uk.ac.lancs.dpb.graph;

/**
 * Connects two ports with cost and capacity. This actually represents a
 * pair of directed edges in opposite directions, and each has a
 * distinct capacity. However, they both have the same over-all cost.
 * 
 * @param <P> the port type
 *
 * @author simpsons
 */
public final class QualifiedEdge<P> extends Edge<P> {
    /**
     * The cumulative cost metric
     */
    public final double cost;

    /**
     * The bidirectional capacity
     */
    public final BidiCapacity capacity;

    /**
     * Create a connection between two ports. The edge has a direction,
     * defined by having a start port and a finish port. Forward metrics
     * express the capacity of the edge for traffic from start to
     * finish, and reverse metrics for the opposite direction.
     * 
     * @param start the starting port for forward travel
     * 
     * @param finish the finishing port for forward travel
     * 
     * @param cost the cumulative cost metric for this edge
     * 
     * @param capacity the edge capacity, with ingress being from start
     * to finish
     */
    public QualifiedEdge(P start, P finish, BidiCapacity capacity,
                         double cost) {
        super(start, finish);
        this.capacity = capacity;
        this.cost = cost;
    }
}
