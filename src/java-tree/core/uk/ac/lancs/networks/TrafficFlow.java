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
package uk.ac.lancs.networks;

/**
 * A bidirectional flow is modelled, having distinct ingress and egress
 * flow rates.
 * 
 * @resume A description of traffic flow through a point on a surface
 * 
 * @author simpsons
 */
public final class TrafficFlow {
    /**
     * @resume The flow of traffic into the surface
     */
    public final double ingress;

    /**
     * @resume The flow of traffic out of the surface
     */
    public final double egress;

    /**
     * Describe a traffic flow at an implicit point on an implicit
     * surface.
     * 
     * @param ingress the flow of traffic into the surface
     * 
     * @param egress the flow of traffic out of the surface
     * 
     * @return the requested flow
     * 
     * @throws IllegalArgumentException if either flow is negative
     */
    public static TrafficFlow of(double ingress, double egress) {
        return new TrafficFlow(ingress, egress);
    }

    /**
     * Describe the aggregate of this flow and another.
     * 
     * @param other the other flow
     * 
     * @return the sum of this flow and the other
     */
    public TrafficFlow add(TrafficFlow other) {
        return TrafficFlow.of(ingress + other.ingress, egress + other.egress);
    }

    private TrafficFlow(double ingress, double egress) {
        if (ingress < 0.0)
            throw new IllegalArgumentException("illegal negative"
                + " flow (ingress): " + ingress);
        if (egress < 0.0)
            throw new IllegalArgumentException("illegal negative"
                + " flow (egress): " + egress);
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
     * This takes the form
     * <samp>&lt;+<var>in</var>,-<var>out</var>&gt;</samp>, where
     * <var>in</var> is the ingress rate and <var>out</var> is the
     * egress rate.
     * 
     * @return a string representation of this flow description
     */
    @Override
    public String toString() {
        return "<+" + ingress + ",-" + egress + ">";
    }

    /**
     * Get the hash code for this object.
     * 
     * @return the object's hash code
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        long temp;
        temp = Double.doubleToLongBits(egress);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(ingress);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    /**
     * Determine whether this object equals another object.
     * 
     * @param obj the other object
     * 
     * @return {@code true} iff the other object is a traffic flow with
     * the same rates as this one
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        TrafficFlow other = (TrafficFlow) obj;
        if (Double.doubleToLongBits(egress) != Double
            .doubleToLongBits(other.egress)) return false;
        if (Double.doubleToLongBits(ingress) != Double
            .doubleToLongBits(other.ingress)) return false;
        return true;
    }

    /**
     * Get a new traffic flow with the same ingress rate but a new
     * egress rate.
     * 
     * @param egress the new egress rate
     * 
     * @return the new traffic flow with the specified egress rate
     */
    public TrafficFlow withEgress(double egress) {
        return new TrafficFlow(ingress, egress);
    }

    /**
     * Get a new traffic flow with the same egress rate but a new
     * ingress rate.
     * 
     * @param ingress the new ingress rate
     * 
     * @return the new traffic flow with the specified ingress rate
     */
    public TrafficFlow withIngress(double ingress) {
        return new TrafficFlow(ingress, egress);
    }
}
