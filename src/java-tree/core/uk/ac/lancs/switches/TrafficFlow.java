/*
 * Copyright 2017, Regents of the University of Lancaster
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
package uk.ac.lancs.switches;

/**
 * Describes traffic flow through a point on a surface.
 * 
 * @author simpsons
 */
public final class TrafficFlow {
    /**
     * @summary The flow of traffic into the surface
     */
    public final double ingress;

    /**
     * @summary The flow of traffic out of the surface
     */
    public final double egress;

    /**
     * Describe a traffic flow at an implicit point on an implicit
     * surface.
     * 
     * @param ingress the flow of traffic into the surface
     * 
     * @param egress the flow of traffic out of the surface
     */
    public static TrafficFlow of(double ingress, double egress) {
        return new TrafficFlow(ingress, egress);
    }

    private TrafficFlow(double ingress, double egress) {
        this.ingress = ingress;
        this.egress = egress;
    }

    /**
     * Describe the corresponding inverted traffic flow.
     * 
     * @return the inverted traffic flow
     */
    public TrafficFlow invert() {
        return new TrafficFlow(egress, ingress);
    }

    /**
     * Get a string representation of this traffic flow description.
     * 
     * @return a string representation of this flow description
     */
    @Override
    public String toString() {
        return "<in:" + ingress + ",out:" + egress + ">";
    }
}