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
package uk.ac.lancs.switches.aggregate;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import uk.ac.lancs.switches.EndPoint;
import uk.ac.lancs.switches.Port;

/**
 * Represents a physical link with no persistent state.
 * 
 * @author simpsons
 */
final class TransientTrunk implements Trunk {
    private final Port start, end;
    private double delay = 0.0;
    private double bandwidth = 0.0;

    /**
     * Create a trunk between two ports.
     * 
     * @param start one of the ends of the trunk
     * 
     * @param end the other end
     */
    public TransientTrunk(Port start, Port end) {
        this.start = start;
        this.end = end;
    }

    @Override
    public double getBandwidth() {
        return bandwidth;
    }

    private final NavigableMap<Integer, Integer> startToEndMap =
        new TreeMap<>();
    private final NavigableMap<Integer, Integer> endToStartMap =
        new TreeMap<>();
    private final BitSet availableTunnels = new BitSet();
    private final Map<Integer, Double> allocations = new HashMap<>();

    @Override
    public EndPoint getPeer(EndPoint p) {
        if (p.getPort().equals(start)) {
            Integer other = startToEndMap.get(p.getLabel());
            if (other == null) return null;
            return end.getEndPoint(other);
        }
        if (p.getPort().equals(end)) {
            Integer other = endToStartMap.get(p.getLabel());
            if (other == null) return null;
            return start.getEndPoint(other);
        }
        throw new IllegalArgumentException("end point does not"
            + " belong to trunk");
    }

    @Override
    public EndPoint allocateTunnel(double bandwidth) {
        /* Sanity-check bandwidth. */
        if (bandwidth < 0)
            throw new IllegalArgumentException("negative: " + bandwidth);
        if (bandwidth > this.bandwidth) return null;

        /* Obtain a tunnel. */
        if (availableTunnels.isEmpty()) return null;
        int startLabel = (short) availableTunnels.nextSetBit(0);
        availableTunnels.clear(startLabel);

        /* Allocate bandwidth. */
        this.bandwidth -= bandwidth;
        allocations.put(startLabel, bandwidth);

        return start.getEndPoint(startLabel);
    }

    @Override
    public void releaseTunnel(EndPoint endPoint) {
        final int startLabel;
        if (endPoint.getPort().equals(start)) {
            startLabel = endPoint.getLabel();
            if (!startToEndMap.containsKey(startLabel))
                throw new IllegalArgumentException("unmapped " + endPoint);
        } else if (endPoint.getPort().equals(end)) {
            int endLabel = endPoint.getLabel();
            Integer rv = endToStartMap.get(endLabel);
            if (rv == null)
                throw new IllegalArgumentException("unmapped " + endPoint);
            startLabel = rv;
        } else {
            throw new IllegalArgumentException("end point " + endPoint
                + " does not belong to " + start + " or " + end);
        }
        if (availableTunnels.get(startLabel))
            throw new IllegalArgumentException("unallocated " + endPoint);
        if (!allocations.containsKey(startLabel))
            throw new IllegalArgumentException("unallocated " + endPoint);

        /* De-allocate resources. */
        this.bandwidth += allocations.remove(startLabel);
        availableTunnels.set(startLabel);
    }

    @Override
    public int getAvailableTunnelCount() {
        return availableTunnels.cardinality();
    }

    @Override
    public double getDelay() {
        return delay;
    }

    @Override
    public List<Port> getPorts() {
        return Arrays.asList(start, end);
    }

    @Override
    public TrunkManagement getManagement() {
        return management;
    }

    private final TrunkManagement management = new TrunkManagement() {
        @Override
        public void allocateBandwidth(double amount) {
            if (amount < 0)
                throw new IllegalArgumentException("negative: " + amount);
            if (amount > bandwidth)
                throw new IllegalArgumentException("request " + amount
                    + " exceeds " + bandwidth);
            bandwidth -= amount;
        }

        @Override
        public void releaseBandwidth(double amount) {
            if (amount < 0)
                throw new IllegalArgumentException("negative: " + amount);
            bandwidth += amount;
        }

        @Override
        public void setDelay(double delay) {
            TransientTrunk.this.delay = delay;
        }

        @Override
        public double getDelay() {
            return delay;
        }

        @Override
        public void defineLabelRange(int startBase, int amount, int endBase) {
            if (startBase + amount < startBase)
                throw new IllegalArgumentException("illegal start range "
                    + startBase + " plus " + amount);
            if (endBase + amount < endBase)
                throw new IllegalArgumentException("illegal end range "
                    + endBase + " plus " + amount);

            /* Check that all numbers are available. */
            Map<Integer, Integer> startExisting =
                startToEndMap.subMap(startBase, startBase + amount);
            if (!startExisting.isEmpty())
                throw new IllegalArgumentException("start range in use "
                    + startExisting.keySet());
            Map<Integer, Integer> endExisting =
                endToStartMap.subMap(endBase, endBase + amount);
            if (!endExisting.isEmpty())
                throw new IllegalArgumentException("end range in use "
                    + endExisting.keySet());

            /* Add all the labels. */
            startToEndMap
                .putAll(IntStream.range(startBase, startBase + amount).boxed()
                    .collect(Collectors
                        .<Integer, Integer, Integer>toMap(Integer::intValue,
                                                          k -> k.shortValue()
                                                              + endBase
                                                              - startBase)));
            availableTunnels.set(startBase, startBase + amount);
            endToStartMap
                .putAll(IntStream.range(endBase, endBase + amount).boxed()
                    .collect(Collectors
                        .<Integer, Integer, Integer>toMap(Integer::intValue,
                                                          k -> k.shortValue()
                                                              + endBase
                                                              - startBase)));
        }
    };

    @Override
    public String toString() {
        return start + "+" + end;
    }
}
