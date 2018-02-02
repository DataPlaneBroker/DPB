/*
 * Copyright 2017, Regents of the Univ(ersity of Lancaster
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Describes a required connection in terms of terminal end points and
 * minimum bandwidth allocation.
 * 
 * @author simpsons
 */
public interface ConnectionRequest {
    /**
     * Get the set of producers and their bandwidth contributions. End
     * points not present in the producer keys but present in
     * {@link #consumers()} are assumed to produce a small nominal
     * bandwidth (e.g., enough to respond to ARPs).
     * 
     * @return a mapping from each producer to its contributing
     * bandwidth
     */
    Map<? extends EndPoint, ? extends Number> producers();

    /**
     * Get the set of consumers and the maximum bandwidth each is
     * prepared to accept. End points not present in the consumer keys
     * but present in {@link #producers()} are assumed to accept the sum
     * of all other producers.
     * 
     * <p>
     * By default, this method returns an immutable empty map.
     * 
     * @return a mapping from each consumer to its maximum accepted
     * bandwidth
     */
    default Map<? extends EndPoint, ? extends Number> consumers() {
        return Collections.emptyMap();
    }

    /**
     * Create a request from a set of producers and consumers.
     * 
     * @param producers a mapping from each producer to its contributing
     * bandwidth
     * 
     * @param consumers a mapping from each consumer to its maximum
     * accepted bandwidth
     * 
     * @return a request consisting of the provided information
     */
    static ConnectionRequest
        of(Map<? extends EndPoint, ? extends Number> producers,
           Map<? extends EndPoint, ? extends Number> consumers) {
        return new ConnectionRequest() {
            @Override
            public Map<? extends EndPoint, ? extends Number> consumers() {
                return consumers;
            }

            @Override
            public Map<? extends EndPoint, ? extends Number> producers() {
                return producers;
            }
        };
    }

    /**
     * Create a request from a mapping from end points to pairs of
     * bandwidths.
     * 
     * @param input the input map, yeah
     * 
     * @return a request consisting of the provided information
     */
    static ConnectionRequest
        of(Map<? extends EndPoint, ? extends List<? extends Number>> input) {
        Map<EndPoint, Number> producers =
            input.entrySet().stream().collect(Collectors
                .toMap(Map.Entry::getKey, e -> e.getValue().get(0)));
        Map<EndPoint, Number> consumers =
            input.entrySet().stream().collect(Collectors
                .toMap(Map.Entry::getKey, e -> e.getValue().get(1)));
        return of(producers, consumers);
    }

    /**
     * Start accumulating data for a request.
     * 
     * @return an object to accumulate request data
     */
    static Builder start() {
        return new Builder();
    };

    /**
     * Accumulates connection request data. Obtain one with
     * {@link ConnectionRequest#start()}.
     * 
     * @author simpsons
     */
    class Builder {
        private Map<EndPoint, List<Number>> data = new HashMap<>();

        private Builder() {}

        private double defaultProduction = 1.0;

        /**
         * Set the default production bandwidth for
         * {@link #add(EndPoint)} and {@link #add(Port, int)}.
         * 
         * @param bw the new default
         * 
         * @return this object
         */
        public Builder produce(double bw) {
            defaultProduction = bw;
            return this;
        }

        /**
         * Add an end point producing the default amount.
         * 
         * @param ep the end point to add
         * 
         * @return this object
         */
        public Builder add(EndPoint ep) {
            return add(ep, defaultProduction);
        }

        /**
         * Add an end point specified by port and label, producing the
         * default amount.
         * 
         * @param p the port containing the end point
         * 
         * @param label the label identifying the end point within the
         * port
         * 
         * @return this object
         */
        public Builder add(Port p, int label) {
            return add(p.getEndPoint(label));
        }

        /**
         * Add an end point producing and consuming specific amounts.
         * 
         * @param ep the end point to add
         * 
         * @param produced the maximum the end point will produce
         * 
         * @param consumed the maximum the end point will consume
         * 
         * @return this object
         */
        public Builder add(EndPoint ep, double produced, double consumed) {
            data.put(ep, Arrays.asList(produced, consumed));
            return this;
        }

        /**
         * Add an end point producing a specific amount.
         * 
         * @param ep the end point to add
         * 
         * @param bw the maximum the end point will produce
         * 
         * @return this object
         */
        public Builder add(EndPoint ep, double bw) {
            return add(ep, bw, Double.MAX_VALUE);
        }

        /**
         * Add an end point specified by port and label, producing and
         * consuming specific amounts.
         * 
         * @param p the port containing the end point
         * 
         * @param label the label identifying the end point within the
         * port
         * 
         * @param produced the maximum the end point will produce
         * 
         * @param consumed the maximum the end point will consume
         * 
         * @return this object
         */
        public Builder add(Port p, int label, double produced,
                           double consumed) {
            return add(p.getEndPoint(label), produced, consumed);
        }

        /**
         * Add an end point specified by port and label, producing a
         * specific amount.
         * 
         * @param p the port containing the end point
         * 
         * @param label the label identifying the end point within the
         * port
         * 
         * @param bw the maximum the end point will produce
         * 
         * @return this object
         */
        public Builder add(Port p, int label, double bw) {
            return add(p, label, bw, Double.MAX_VALUE);
        }

        /**
         * Create a connection request from the accumulated data.
         * 
         * @return the corresponding connection request
         */
        public ConnectionRequest create() {
            return of(data);
        }
    }

    /**
     * Create an immutable sanitized version of a request. The key sets
     * of both maps are made identical. Unspecified production is
     * replaced with a nominal bandwidth. Unspecified consumption is
     * replace with the sum of all production minus the key's own
     * production.
     * 
     * @param input the unsanitized request
     * 
     * @param nominalProduction the production for unspecified keys, and
     * the minimum allowed
     * 
     * @return the sanitized request
     */
    static ConnectionRequest sanitize(ConnectionRequest input,
                                      double nominalProduction) {
        /* Get the full set of end points. */
        Collection<EndPoint> keys = new HashSet<>(input.producers().keySet());
        keys.addAll(input.consumers().keySet());

        /* Provide unspecified producers with the nominal amount, and
         * sum all production. */
        Map<EndPoint, Double> producers = new HashMap<>();
        double producerSum = 0.0;
        for (EndPoint key : keys) {
            double production = input.producers().containsKey(key)
                ? Math.max(input.producers().get(key).doubleValue(),
                           nominalProduction)
                : nominalProduction;
            producers.put(key, production);
            producerSum += production;
        }

        /* Compute missing consumptions, and clamp excessive ones. */
        Map<EndPoint, Double> consumers = new HashMap<>();
        for (EndPoint key : keys) {
            double limit = producerSum - producers.get(key);
            double consumption = input.consumers().containsKey(key)
                ? Math.min(input.consumers().get(key).doubleValue(), limit)
                : limit;
            consumers.put(key, consumption);
        }

        producers = Collections.unmodifiableMap(producers);
        consumers = Collections.unmodifiableMap(consumers);
        return of(producers, consumers);
    }
}
