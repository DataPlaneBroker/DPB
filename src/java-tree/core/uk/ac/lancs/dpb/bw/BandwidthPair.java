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

package uk.ac.lancs.dpb.bw;

/**
 * Specifies ingress and egress bandwidth requirements at an endpoint.
 *
 * @author simpsons
 */
public final class BandwidthPair {
    /**
     * The ingress bandwidth
     */
    public final BandwidthRange ingress;

    /**
     * The egress bandwidth
     */
    public final BandwidthRange egress;

    private BandwidthPair(BandwidthRange ingress, BandwidthRange egress) {
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
    public static BandwidthPair of(BandwidthRange value) {
        return new BandwidthPair(value, value);
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
    public static BandwidthPair of(BandwidthRange ingress,
                                   BandwidthRange egress) {
        return new BandwidthPair(ingress, egress);
    }

    /**
     * Create a bandwidth pair which is the inverse of this pair.
     * 
     * @return a bandwidth pair with ingress and egress rates swapped
     * 
     * @constructor
     */
    public BandwidthPair invert() {
        return new BandwidthPair(egress, ingress);
    }
}
