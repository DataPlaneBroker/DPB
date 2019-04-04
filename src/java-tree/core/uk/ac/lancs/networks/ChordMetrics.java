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

import java.util.function.DoubleConsumer;

/**
 * Describes the state of a chord across a network. This consists of
 * optional delay, upstream bandwidth and downstream bandwidth. Other
 * fields might be added in future.
 * 
 * @author simpsons
 */
public final class ChordMetrics {
    /**
     * Express solely a chord's delay.
     * 
     * @param delay the chord's delay
     * 
     * @return chord metrics expressing the specified characteristics
     * 
     * @constructor
     */
    public static final ChordMetrics ofDelay(double delay) {
        return new ChordMetrics(delay, null, null);
    }

    /**
     * Express a chord's independent upstream and downstream bandwidth
     * capacities.
     * 
     * @param upstream the upstream bandwidth capacity
     * 
     * @param downstream the downstream bandwidth capacity
     * 
     * @return chord metrics expressing the specified characteristics
     * 
     * @constructor
     */
    public static final ChordMetrics ofBandwidth(double upstream,
                                                 double downstream) {
        return new ChordMetrics(null, upstream, downstream);
    }

    /**
     * Express a chord's bandwidth capacity in both directions.
     * 
     * @param rate the bandwidth capacity in both directions
     * 
     * @return chord metrics expressing the specified characteristics
     * 
     * @constructor
     */
    public static final ChordMetrics ofBandwidth(double rate) {
        return ofBandwidth(rate, rate);
    }

    /**
     * Express a chord's delay and independent upstream and downstream
     * bandwidth capacities.
     * 
     * @param delay the chord's delay
     * 
     * @param upstream the upstream bandwidth capacity
     * 
     * @param downstream the downstream bandwidth capacity
     * 
     * @return chord metrics expressing the specified characteristics
     * 
     * @constructor
     */
    public static final ChordMetrics of(double delay, double upstream,
                                        double downstream) {
        return new ChordMetrics(delay, upstream, downstream);
    }

    /**
     * Express a chord's delay and bandwidth capacity in both
     * directions.
     * 
     * @param delay the chord's delay
     * 
     * @param rate the bandwidth capacity in both directions
     * 
     * @return chord metrics expressing the specified characteristics
     * 
     * @constructor
     */
    public static final ChordMetrics of(double delay, double rate) {
        return of(delay, rate, rate);
    }

    private final Double delay;
    private final Double upstream;
    private final Double downstream;

    /**
     * Get the delay if specified, or throw an exception.
     * 
     * @return the specified delay
     * 
     * @throws NullPointerException if the delay was not specified
     */
    public double delay() {
        if (delay == null) throw new NullPointerException("delay");
        return delay;
    }

    /**
     * Get the downstream capacity if specified, or throw an exception.
     * 
     * @return the specified downstream capacity
     * 
     * @throws NullPointerException if the downstream capacity was not
     * specified
     */
    public double downstream() {
        if (downstream == null) throw new NullPointerException("downstream");
        return downstream;
    }

    /**
     * Get the upstream capacity if specified, or throw an exception.
     * 
     * @return the specified upstream capacity
     * 
     * @throws NullPointerException if the upstream capacity was not
     * specified
     */
    public double upstream() {
        if (upstream == null) throw new NullPointerException("upstream");
        return upstream;
    }

    /**
     * Get the delay if specified, or a default value.
     * 
     * @param defaultValue the value to return if the delay was not
     * specified
     * 
     * @return the specified delay, or the provided default value
     */
    public double delay(double defaultValue) {
        if (delay == null) return defaultValue;
        return delay;
    }

    /**
     * Get the downstream capacity if specified, or a default value.
     * 
     * @param defaultValue the value to return if the downstream
     * capacity was not specified
     * 
     * @return the specified downstream capacity, or the provided
     * default value
     */
    public double downstream(double defaultValue) {
        if (downstream == null) return defaultValue;
        return downstream;
    }

    /**
     * Get the upstream capacity if specified, or a default value.
     * 
     * @param defaultValue the value to return if the upstream capacity
     * was not specified
     * 
     * @return the specified upstream capacity, or the provided default
     * value
     */
    public double upstream(double defaultValue) {
        if (upstream == null) return defaultValue;
        return upstream;
    }

    private ChordMetrics(Double delay, Double upstream, Double downstream) {
        this.delay = delay;
        this.upstream = upstream;
        this.downstream = downstream;
    }

    /**
     * Determine whether these metrics include a specified delay.
     * 
     * @return {@code true} iff the metrics include a delay
     */
    public boolean hasDelay() {
        return delay != null;
    }

    /**
     * Determine whether these metrics include a specified upstream
     * bandwidth.
     * 
     * @return {@code true} iff the metrics include an upstream
     * bandwidth
     */
    public boolean hasUpstream() {
        return upstream != null;
    }

    /**
     * Determine whether these metrics include a specified downstream
     * bandwidth.
     * 
     * @return {@code true} iff the metrics include a downpstream
     * bandwidth
     */
    public boolean hasDownstream() {
        return downstream != null;
    }

    /**
     * If present, submit the delay to a consumer.
     * 
     * @param dest the destination for the delay
     */
    public void copyDelay(DoubleConsumer dest) {
        if (delay != null) dest.accept(delay);
    }

    /**
     * If present, submit the upstream capacity to a consumer.
     * 
     * @param dest the destination for the upstream capacity
     */
    public void copyUpstream(DoubleConsumer dest) {
        if (upstream != null) dest.accept(upstream);
    }

    /**
     * If present, submit the downstream capacity to a consumer.
     * 
     * @param dest the destination for the downstream capacity
     */
    public void copyDownstream(DoubleConsumer dest) {
        if (downstream != null) dest.accept(downstream);
    }

    /**
     * Prepares creation of a chord metric.
     * 
     * @author simpsons
     */
    public static class Builder {
        private Double delay, upstream, downstream;

        Builder() {}

        /**
         * Set the delay value.
         * 
         * @param value the new value
         * 
         * @return this object
         */
        public Builder withDelay(double value) {
            this.delay = value;
            return this;
        }

        /**
         * Set the upstream value.
         * 
         * @param value the new value
         * 
         * @return this object
         */
        public Builder withUpstream(double value) {
            this.upstream = value;
            return this;
        }

        /**
         * Set the downstream value.
         * 
         * @param value the new value
         * 
         * @return this object
         */
        public Builder withDownstream(double value) {
            this.downstream = value;
            return this;
        }

        /**
         * Swap the upstream and downstream values.
         * 
         * @return this object
         */
        public Builder reverse() {
            Double tmp = downstream;
            downstream = upstream;
            upstream = tmp;
            return this;
        }

        /**
         * Build the metrics object from the current settings.
         * 
         * @return the new metrics object
         */
        public ChordMetrics build() {
            return new ChordMetrics(delay, upstream, downstream);
        }
    }

    /**
     * Start to build metrics.
     * 
     * @return an object to build the metrics gradually
     * 
     * @constructor
     */
    public static Builder start() {
        return new Builder();
    }

    /**
     * Get metrics representing the chord in the opposite direction.
     * Upstream and downstream bandwidths are swapped.
     * 
     * @return the requested metrics
     * 
     * @constructor
     */
    public ChordMetrics reverse() {
        return new ChordMetrics(delay, downstream, upstream);
    }
}
