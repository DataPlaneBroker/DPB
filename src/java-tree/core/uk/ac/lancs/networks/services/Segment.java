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

package uk.ac.lancs.networks.services;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import uk.ac.lancs.networks.Circuit;
import uk.ac.lancs.networks.TrafficFlow;

/**
 * Specifies several circuits that should be connected at Layer 2, with
 * varying ingress and egress bandwidths, a bandwidth cap, and maximum
 * delay and error rate between any two endpoints.
 * 
 * @author simpsons
 */
public final class Segment {
    private final double cap;
    private final double maxDelay;
    private final double maxError;
    private final Map<Circuit, TrafficFlow> endPoints;

    /**
     * Get the bandwidth cap applied to all edges.
     * 
     * @return the current cap
     */
    public double cap() {
        return cap;
    }

    /**
     * Get the maximum tolerable delay.
     * 
     * @return the delay
     */
    public double maxDelay() {
        return maxDelay;
    }

    /**
     * Get the maximum tolerable error rate.
     * 
     * @return the error rate
     */
    public double maxError() {
        return maxError;
    }

    /**
     * Get an immutable set of circuits and their required ingress and
     * egress bandwidths.
     * 
     * @return the set of endpoints
     */
    public Map<? extends Circuit, ? extends TrafficFlow> endpoints() {
        return endPoints;
    }

    Segment(double cap, double maxDelay, double maxError,
            Map<? extends Circuit, ? extends TrafficFlow> endPoints) {
        this.cap = cap;
        this.maxDelay = maxDelay;
        this.maxError = maxError;
        this.endPoints =
            Collections.unmodifiableMap(new HashMap<>(endPoints));
    }

    /**
     * Creates a segment descriptor in stages.
     * 
     * @author simpsons
     */
    public static class Builder {
        double cap = Double.MAX_VALUE;
        double maxDelay = Double.MAX_VALUE;
        double maxError = 1.0;
        Map<Circuit, TrafficFlow> endPoints = new HashMap<>();

        double ingressBandwidth = 0.1, egressBandwidth = Double.MAX_VALUE;

        /**
         * Create the segment with the current configuration.
         * 
         * @constructor
         * 
         * @return the requested segment
         */
        public Segment done() {
            return new Segment(cap, maxDelay, maxError, endPoints);
        }

        /**
         * Set the maximum tolerable delay.
         * 
         * @param maxDelay the new delay limit
         * 
         * @return this object
         */
        public Builder maxDelay(double maxDelay) {
            if (maxDelay <= 0.0)
                throw new IllegalArgumentException("maxDelay: " + maxDelay);
            this.maxDelay = maxDelay;
            return this;
        }

        /**
         * Set the maximum tolerable error rate.
         * 
         * @param maxError the new error-rate limit
         * 
         * @return this object
         */
        public Builder maxError(double maxError) {
            if (maxError < 0.0 || maxError > 1.0)
                throw new IllegalArgumentException("maxError: " + maxError);
            this.maxError = maxError;
            return this;
        }

        /**
         * Set the bandwidth cap for any edge.
         * 
         * @param bandwidth the bandwidth cap
         * 
         * @return this object
         */
        public Builder cap(double bandwidth) {
            if (bandwidth < 0.0)
                throw new IllegalArgumentException("bandwdith cap: "
                    + bandwidth);
            this.cap = bandwidth;
            return this;
        }

        /**
         * Remove the bandwidth cap.
         * 
         * @return this object
         */
        public Builder uncap() {
            this.cap = Double.MAX_VALUE;
            return this;
        }

        /**
         * Set the bandwidth for endpoints added later with
         * {@link #endpoint(Circuit)}.
         * 
         * @param bandwidth the ingress and egress bandwidth
         * 
         * @return this object
         */
        public Builder bandwidth(double bandwidth) {
            if (bandwidth < 0.0)
                throw new IllegalArgumentException("bandwdith: " + bandwidth);
            this.ingressBandwidth = this.egressBandwidth = bandwidth;
            return this;
        }

        /**
         * Set the ingress bandwidth for endpoints added later with
         * {@link #endpoint(Circuit)}.
         * 
         * @param bandwidth the ingress bandwidth
         * 
         * @return this object
         */
        public Builder ingressBandwidth(double bandwidth) {
            if (bandwidth < 0.0)
                throw new IllegalArgumentException("ingress bandwdith: "
                    + bandwidth);
            this.ingressBandwidth = bandwidth;
            return this;
        }

        /**
         * Set the egress bandwidth for endpoints added later with
         * {@link #endpoint(Circuit)}.
         * 
         * @param bandwidth the egress bandwidth
         * 
         * @return this object
         */
        public Builder egressBandwidth(double bandwidth) {
            if (bandwidth < 0.0)
                throw new IllegalArgumentException("egress bandwdith: "
                    + bandwidth);
            this.egressBandwidth = bandwidth;
            return this;
        }

        /**
         * Add an endpoint. Its ingress and egress bandwidths are
         * determined by the most recent calls to
         * {@link #bandwidth(double)}, {@link #egressBandwidth(double)}
         * and {@link #ingressBandwidth(double)}.
         * 
         * @param circ the circuit to be connected
         * 
         * @return this object
         */
        public Builder endpoint(Circuit circ) {
            TrafficFlow flow =
                TrafficFlow.of(ingressBandwidth, egressBandwidth);
            endPoints.put(circ, flow);
            return this;
        }
    }
}
