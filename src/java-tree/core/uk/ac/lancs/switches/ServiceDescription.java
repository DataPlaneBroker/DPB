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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Describes a service by its set of end points and their QoS
 * requirements.
 * 
 * @author simpsons
 */
public interface ServiceDescription {
    /**
     * Get the set of end points of the service, and the maximum traffic
     * at each end point.
     * 
     * @return the traffic flows of each end point of the service
     */
    Map<? extends EndPoint<? extends Terminal>, ? extends TrafficFlow>
        endPointFlows();

    /**
     * Get the set of bandwidth contributions into the service indexed
     * by end point.
     * 
     * @return a mapping from each producer to its contributing
     * bandwidth
     * 
     * @deprecated This method will be removed.
     */
    @Deprecated
    default Map<? extends EndPoint<? extends Terminal>, ? extends Number>
        producers() {
        return new AbstractMap<EndPoint<? extends Terminal>, Number>() {
            @Override
            public Set<Entry<EndPoint<? extends Terminal>, Number>>
                entrySet() {
                return new AbstractSet<Map.Entry<EndPoint<? extends Terminal>, Number>>() {
                    @Override
                    public
                        Iterator<Entry<EndPoint<? extends Terminal>, Number>>
                        iterator() {
                        return new Iterator<Map.Entry<EndPoint<? extends Terminal>, Number>>() {
                            final Iterator<? extends Map.Entry<? extends EndPoint<? extends Terminal>, ? extends TrafficFlow>> iterator =
                                endPointFlows().entrySet().iterator();

                            @Override
                            public boolean hasNext() {
                                return iterator.hasNext();
                            }

                            @Override
                            public
                                Map.Entry<EndPoint<? extends Terminal>, Number>
                                next() {
                                Map.Entry<? extends EndPoint<? extends Terminal>, ? extends TrafficFlow> next =
                                    iterator.next();
                                return new Map.Entry<EndPoint<? extends Terminal>, Number>() {
                                    @Override
                                    public EndPoint<? extends Terminal>
                                        getKey() {
                                        return next.getKey();
                                    }

                                    @Override
                                    public Number getValue() {
                                        return next.getValue().ingress;
                                    }

                                    @Override
                                    public Number setValue(Number value) {
                                        throw new UnsupportedOperationException("unimplemented");
                                    }
                                };
                            }
                        };
                    }

                    @Override
                    public int size() {
                        return endPointFlows().size();
                    }
                };
            }
        };
    }

    /**
     * Get the set of bandwidth tolerances out of the service indexed by
     * end point.
     * 
     * @return a mapping from each consumer to its maximum accepted
     * bandwidth
     * 
     * @deprecated This method will be removed.
     */
    @Deprecated
    default Map<? extends EndPoint<? extends Terminal>, ? extends Number>
        consumers() {
        return new AbstractMap<EndPoint<? extends Terminal>, Number>() {
            @Override
            public Set<Entry<EndPoint<? extends Terminal>, Number>>
                entrySet() {
                return new AbstractSet<Map.Entry<EndPoint<? extends Terminal>, Number>>() {
                    @Override
                    public
                        Iterator<Entry<EndPoint<? extends Terminal>, Number>>
                        iterator() {
                        return new Iterator<Map.Entry<EndPoint<? extends Terminal>, Number>>() {
                            final Iterator<? extends Map.Entry<? extends EndPoint<? extends Terminal>, ? extends TrafficFlow>> iterator =
                                endPointFlows().entrySet().iterator();

                            @Override
                            public boolean hasNext() {
                                return iterator.hasNext();
                            }

                            @Override
                            public
                                Map.Entry<EndPoint<? extends Terminal>, Number>
                                next() {
                                Map.Entry<? extends EndPoint<? extends Terminal>, ? extends TrafficFlow> next =
                                    iterator.next();
                                return new Map.Entry<EndPoint<? extends Terminal>, Number>() {
                                    @Override
                                    public EndPoint<? extends Terminal>
                                        getKey() {
                                        return next.getKey();
                                    }

                                    @Override
                                    public Number getValue() {
                                        return next.getValue().egress;
                                    }

                                    @Override
                                    public Number setValue(Number value) {
                                        throw new UnsupportedOperationException("unimplemented");
                                    }
                                };
                            }
                        };
                    }

                    @Override
                    public int size() {
                        return endPointFlows().size();
                    }
                };
            }
        };
    }

    /**
     * Create a description from a set of producers and consumers. The
     * full set of end points is the union of the two maps' key sets.
     * Anything missing from one key set but present in the other is
     * assumed to be zero. The input data are copied, so changes to them
     * do not affect the resultant object.
     * 
     * @param producers a mapping from each producer to its contributing
     * bandwidth
     * 
     * @param consumers a mapping from each consumer to its maximum
     * accepted bandwidth
     * 
     * @return a description consisting of the provided information
     */
    static ServiceDescription
        of(Map<? extends EndPoint<? extends Terminal>, ? extends Number> producers,
           Map<? extends EndPoint<? extends Terminal>, ? extends Number> consumers) {
        Map<EndPoint<? extends Terminal>, TrafficFlow> result =
            new HashMap<>();
        Collection<EndPoint<? extends Terminal>> keys =
            new HashSet<>(producers.keySet());
        if (consumers != null) keys.addAll(consumers.keySet());
        for (EndPoint<? extends Terminal> ep : keys) {
            Number ingressObj = producers.get(ep);
            double ingress =
                ingressObj == null ? 0.0 : ingressObj.doubleValue();
            Number egressObj =
                consumers == null ? Double.valueOf(0.0) : consumers.get(ep);
            double egress = egressObj == null ? 0.0 : egressObj.doubleValue();
            result.put(ep, TrafficFlow.of(ingress, egress));
        }
        Map<EndPoint<? extends Terminal>, TrafficFlow> finalResult =
            Collections.unmodifiableMap(result);
        return new ServiceDescription() {
            @Override
            public
                Map<? extends EndPoint<? extends Terminal>, ? extends TrafficFlow>
                endPointFlows() {
                return finalResult;
            }
        };
    }

    /**
     * Create a description view of a mapping from end points to pairs
     * of bandwidths. The result is simply a view of the same map, and
     * reflects changes in that map.
     * 
     * @param data the mapping from end points to pairs of bandwidths
     * 
     * @return the description view of the same map
     */
    static ServiceDescription
        create(Map<? extends EndPoint<? extends Terminal>, ? extends TrafficFlow> data) {
        return new ServiceDescription() {
            @Override
            public
                Map<? extends EndPoint<? extends Terminal>, ? extends TrafficFlow>
                endPointFlows() {
                return data;
            }
        };
    }

    /**
     * Create a description from a mapping from end points to pairs of
     * bandwidths.
     * 
     * @param input a mapping from end point to pairs of numbers, the
     * first being ingress and the second ingress
     * 
     * @return a description consisting of the provided information
     */
    static ServiceDescription
        of(Map<? extends EndPoint<? extends Terminal>, ? extends List<? extends Number>> input) {
        Map<EndPoint<? extends Terminal>, Number> producers =
            input.entrySet().stream().collect(Collectors
                .toMap(Map.Entry::getKey, e -> e.getValue().get(0)));
        Map<EndPoint<? extends Terminal>, Number> consumers =
            input.entrySet().stream().collect(Collectors
                .toMap(Map.Entry::getKey, e -> e.getValue().get(1)));
        return of(producers, consumers);
    }

    /**
     * Start accumulating data for a description.
     * 
     * @return an object to accumulate description data
     */
    static Builder start() {
        return new Builder();
    };

    /**
     * Accumulates service description data. Obtain one with
     * {@link ServiceDescription#start()}.
     * 
     * @author simpsons
     */
    class Builder {
        private Map<EndPoint<? extends Terminal>, List<Number>> data =
            new HashMap<>();

        private Builder() {}

        private double defaultProduction = 1.0;

        /**
         * Set the default production bandwidth for
         * {@link #add(EndPoint)} and {@link #add(Terminal, int)}.
         * 
         * @param bandwidth the new default
         * 
         * @return this object
         */
        public Builder produce(double bandwidth) {
            defaultProduction = bandwidth;
            return this;
        }

        /**
         * Add an end point producing the default amount.
         * 
         * @param endPoint the end point to add
         * 
         * @return this object
         */
        public Builder add(EndPoint<? extends Terminal> endPoint) {
            return add(endPoint, defaultProduction);
        }

        /**
         * Add an end point specified by terminal and label, producing
         * the default amount.
         * 
         * @param terminal the terminal containing the end point
         * 
         * @param label the label identifying the end point within the
         * terminal
         * 
         * @return this object
         */
        public Builder add(Terminal terminal, int label) {
            return add(terminal.getEndPoint(label));
        }

        /**
         * Add an end point producing and consuming specific amounts.
         * 
         * @param endPoint the end point to add
         * 
         * @param produced the maximum the end point will produce
         * 
         * @param consumed the maximum the end point will consume
         * 
         * @return this object
         */
        public Builder add(EndPoint<? extends Terminal> endPoint,
                           double produced, double consumed) {
            data.put(endPoint, Arrays.asList(produced, consumed));
            return this;
        }

        /**
         * Add an end point producing a specific amount.
         * 
         * @param endPoint the end point to add
         * 
         * @param bandwidth the maximum the end point will produce
         * 
         * @return this object
         */
        public Builder add(EndPoint<? extends Terminal> endPoint,
                           double bandwidth) {
            return add(endPoint, bandwidth, Double.MAX_VALUE);
        }

        /**
         * Add an end point specified by terminal and label, producing
         * and consuming specific amounts.
         * 
         * @param terminal the terminal containing the end point
         * 
         * @param label the label identifying the end point within the
         * terminal
         * 
         * @param produced the maximum the end point will produce
         * 
         * @param consumed the maximum the end point will consume
         * 
         * @return this object
         */
        public Builder add(Terminal terminal, int label, double produced,
                           double consumed) {
            return add(terminal.getEndPoint(label), produced, consumed);
        }

        /**
         * Add an end point specified by terminal and label, producing a
         * specific amount.
         * 
         * @param terminal the terminal containing the end point
         * 
         * @param label the label identifying the end point within the
         * terminal
         * 
         * @param bandwidth the maximum the end point will produce
         * 
         * @return this object
         */
        public Builder add(Terminal terminal, int label, double bandwidth) {
            return add(terminal, label, bandwidth, Double.MAX_VALUE);
        }

        /**
         * Create a service description from the accumulated data.
         * 
         * @return the corresponding service description
         */
        public ServiceDescription create() {
            return of(data);
        }
    }

    /**
     * Create an immutable sanitized version of a description.
     * Production is clamped to a nominal minimum. Consumption is
     * clamped to a maximum computed as the sum of sanitized productions
     * minus the end point's own production.
     * 
     * @param input the unsanitized description
     * 
     * @param minimumProduction the minimum production
     * 
     * @return the sanitized description
     */
    static ServiceDescription sanitize(ServiceDescription input,
                                       double minimumProduction) {
        /* Provide unspecified producers with the nominal amount, and
         * sum all production. */
        Map<EndPoint<? extends Terminal>, Double> producers = new HashMap<>();
        double producerSum = 0.0;
        for (Map.Entry<? extends EndPoint<? extends Terminal>, ? extends TrafficFlow> entry : input
            .endPointFlows().entrySet()) {
            final double production =
                Math.max(entry.getValue().ingress, minimumProduction);
            producerSum += production;
            producers.put(entry.getKey(), production);
        }

        /* Limit consumption to the production sum minus the end point's
         * production. */
        Map<EndPoint<? extends Terminal>, Double> consumers = new HashMap<>();
        for (Map.Entry<? extends EndPoint<? extends Terminal>, ? extends TrafficFlow> entry : input
            .endPointFlows().entrySet()) {
            TrafficFlow flow = entry.getValue();
            final double maximum = producerSum - flow.ingress;
            final double consumption = Math.min(maximum, flow.egress);
            consumers.put(entry.getKey(), consumption);
        }

        Map<EndPoint<? extends Terminal>, TrafficFlow> result =
            new HashMap<>();
        for (EndPoint<? extends Terminal> key : producers.keySet()) {
            TrafficFlow flow =
                TrafficFlow.of(producers.get(key), consumers.get(key));
            result.put(key, flow);
        }

        Map<EndPoint<? extends Terminal>, TrafficFlow> finalResult =
            Collections.unmodifiableMap(result);
        return new ServiceDescription() {
            @Override
            public
                Map<? extends EndPoint<? extends Terminal>, ? extends TrafficFlow>
                endPointFlows() {
                return finalResult;
            }
        };
    }
}
