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

import java.util.stream.Stream;

/**
 * Connects two ports. This actually represents a pair of directed edges
 * in opposite directions.
 * 
 * <p>
 * No edge equals another edge that is not the same object.
 * 
 * @author simpsons
 */
public class Edge<P> {
    /**
     * The start port
     */
    public final P start;

    /**
     * The finish port
     */
    public final P finish;

    /**
     * Create a connection between two ports.
     * 
     * @param start the starting port for forward travel
     * 
     * @param finish the finishing port for forward travel
     */
    public Edge(P start, P finish) {
        this.start = start;
        this.finish = finish;
    }

    /**
     * Get a stream of the two ports.
     * 
     * @return a stream of the two ports
     */
    public final Stream<P> stream() {
        return Stream.of(start, finish);
    }

    /**
     * Get a string representation of this edge.
     *
     * @return the string representations of the ports, joined by a
     * hyphen-minus
     */
    @Override
    public String toString() {
        return start + "-" + finish;
    }

    /**
     * Get the hash code for this object. This uses the identity hash
     * code.
     * 
     * @see System#identityHashCode(Object)
     * 
     * @return the hash code for this object
     */
    @Override
    public final int hashCode() {
        return System.identityHashCode(this);
    }

    /**
     * Test whether another object equals this edge. No two edges are
     * equal unless they are the same object.
     * 
     * @param obj the other object
     * 
     * @return {@code true} if the other object is this object;
     * {@code false} otherwise
     */
    @Override
    public final boolean equals(Object obj) {
        return this == obj;
    }
}
