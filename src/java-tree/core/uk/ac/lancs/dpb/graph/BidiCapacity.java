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
 * Specifies ingress and egress bandwidth requirements at an endpoint,
 * or bidirectional requirements of an edge. In the case of an edge, the
 * forward bandwidth (with respect to the 'from' and 'to' vertices) is
 * synonymous with the ingress, and the reverse with the egress.
 *
 * @author simpsons
 */
public final class BidiCapacity {
    /**
     * The ingress bandwidth
     */
    public final Capacity ingress;

    /**
     * The egress bandwidth
     */
    public final Capacity egress;

    private BidiCapacity(Capacity ingress, Capacity egress) {
        this.ingress = ingress;
        this.egress = egress;
    }

    /**
     * Get a string representation of this pair of bandwidth ranges.
     * This is the forward/ingress range, an arrow <samp>-&gt;</samp>
     * and the reverse/egress range.
     * 
     * @return a string representation
     */
    @Override
    public String toString() {
        return String.format("%s->%s", ingress, egress);
    }

    /**
     * Create a bandwidth pair with equal ingress and egress rates.
     * 
     * @param value the common rate
     * 
     * @return the requested pair
     * 
     * @constructor
     */
    public static BidiCapacity of(Capacity value) {
        return new BidiCapacity(value, value);
    }

    /**
     * Create a bandwidth pair.
     * 
     * @param ingress the ingress rate
     * 
     * @param egress the egress rate
     * 
     * @return the requested pair
     * 
     * @constructor
     */
    public static BidiCapacity of(Capacity ingress,
                                   Capacity egress) {
        return new BidiCapacity(ingress, egress);
    }

    /**
     * Create a bandwidth pair which is the inverse of this pair.
     * 
     * @return a bandwidth pair with ingress and egress rates swapped
     * 
     * @constructor
     */
    public BidiCapacity invert() {
        return new BidiCapacity(egress, ingress);
    }
}
