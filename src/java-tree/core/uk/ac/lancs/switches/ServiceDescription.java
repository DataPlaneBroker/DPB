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
 * Describes a service in terms of end points and bandwidth allocations.
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
    Map<? extends EndPoint, ? extends TrafficFlow> endPointFlows();

    /**
     * Get the set of bandwidth contributions into the service indexed
     * by end point.
     * 
     * @return a mapping from each producer to its contributing
     * bandwidth
     */
    default Map<? extends EndPoint, ? extends Number> producers() {
        return new AbstractMap<EndPoint, Number>() {
            @Override
            public Set<Entry<EndPoint, Number>> entrySet() {
                return new AbstractSet<Map.Entry<EndPoint, Number>>() {
                    @Override
                    public Iterator<Entry<EndPoint, Number>> iterator() {
                        return new Iterator<Map.Entry<EndPoint, Number>>() {
                            final Iterator<? extends Map.Entry<? extends EndPoint, ? extends TrafficFlow>> iterator =
                                endPointFlows().entrySet().iterator();

                            @Override
                            public boolean hasNext() {
                                return iterator.hasNext();
                            }

                            @Override
                            public Map.Entry<EndPoint, Number> next() {
                                Map.Entry<? extends EndPoint, ? extends TrafficFlow> next =
                                    iterator.next();
                                return new Map.Entry<EndPoint, Number>() {
                                    @Override
                                    public EndPoint getKey() {
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
     */
    default Map<? extends EndPoint, ? extends Number> consumers() {
        return new AbstractMap<EndPoint, Number>() {
            @Override
            public Set<Entry<EndPoint, Number>> entrySet() {
                return new AbstractSet<Map.Entry<EndPoint, Number>>() {
                    @Override
                    public Iterator<Entry<EndPoint, Number>> iterator() {
                        return new Iterator<Map.Entry<EndPoint, Number>>() {
                            final Iterator<? extends Map.Entry<? extends EndPoint, ? extends TrafficFlow>> iterator =
                                endPointFlows().entrySet().iterator();

                            @Override
                            public boolean hasNext() {
                                return iterator.hasNext();
                            }

                            @Override
                            public Map.Entry<EndPoint, Number> next() {
                                Map.Entry<? extends EndPoint, ? extends TrafficFlow> next =
                                    iterator.next();
                                return new Map.Entry<EndPoint, Number>() {
                                    @Override
                                    public EndPoint getKey() {
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
        of(Map<? extends EndPoint, ? extends Number> producers,
           Map<? extends EndPoint, ? extends Number> consumers) {
        Map<EndPoint, TrafficFlow> result = new HashMap<>();
        Collection<EndPoint> keys = new HashSet<>(producers.keySet());
        if (consumers != null) keys.addAll(consumers.keySet());
        for (EndPoint ep : keys) {
            Number ingressObj = producers.get(ep);
            double ingress =
                ingressObj == null ? 0.0 : ingressObj.doubleValue();
            Number egressObj =
                consumers == null ? Double.valueOf(0.0) : consumers.get(ep);
            double egress = egressObj == null ? 0.0 : egressObj.doubleValue();
            result.put(ep, TrafficFlow.of(ingress, egress));
        }
        Map<EndPoint, TrafficFlow> finalResult =
            Collections.unmodifiableMap(result);
        return new ServiceDescription() {
            @Override
            public Map<? extends EndPoint, ? extends TrafficFlow>
                endPointFlows() {
                return finalResult;
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
        private Map<EndPoint, List<Number>> data = new HashMap<>();

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
        public Builder add(EndPoint endPoint) {
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
        public Builder add(EndPoint endPoint, double produced,
                           double consumed) {
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
        public Builder add(EndPoint endPoint, double bandwidth) {
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
     * Create an immutable sanitized version of a description. The key
     * sets of both maps are made identical. Unspecified production is
     * replaced with a nominal bandwidth. Unspecified consumption is
     * replace with the sum of all production minus the key's own
     * production.
     * 
     * @param input the unsanitized description
     * 
     * @param nominalProduction the production for unspecified keys, and
     * the minimum allowed
     * 
     * @return the sanitized description
     */
    static ServiceDescription sanitize(ServiceDescription input,
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

        return of(producers, consumers);
    }
}