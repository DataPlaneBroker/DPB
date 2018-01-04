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
package uk.ac.lancs.routing.hier;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Represents a physical link with no persistent state.
 * 
 * @author simpsons
 */
public final class TransientTrunk implements Trunk {
    private final Port start, end;
    private final double delay;
    private double bandwidth;

    /**
     * Create a trunk between two ports with an initial bandwidth
     * allocation.
     * 
     * @param start one of the ends of the trunk
     * 
     * @param end the other end
     * 
     * @param delay the fixed delay of the trunk
     * 
     * @param bandwidth the initial available bandwidth
     */
    public TransientTrunk(Port start, Port end, double delay,
                          double bandwidth) {
        this.start = start;
        this.end = end;
        this.delay = delay;
        this.bandwidth = bandwidth;
    }

    /**
     * Create a trunk between two ports with initially no bandwidth
     * allocation.
     * 
     * @param start one of the ends of the trunk
     * 
     * @param end the other end
     * 
     * @param delay the fixed delay of the trunk
     */
    public TransientTrunk(Port start, Port end, double delay) {
        this(start, end, delay, 0.0);
    }

    @Override
    public double getBandwidth() {
        return bandwidth;
    }

    @Override
    public void allocateBandwidth(double amount) {
        if (amount < 0)
            throw new IllegalArgumentException("negative: " + amount);
        if (amount > bandwidth) throw new IllegalArgumentException("request "
            + amount + " exceeds " + bandwidth);
        bandwidth -= amount;
    }

    @Override
    public void releaseBandwidth(double amount) {
        if (amount < 0)
            throw new IllegalArgumentException("negative: " + amount);
        bandwidth += amount;
    }

    private final NavigableMap<Short, Short> startToEndMap = new TreeMap<>();
    private final NavigableMap<Short, Short> endToStartMap = new TreeMap<>();
    private final BitSet availableTunnels = new BitSet();

    @Override
    public void defineLabelRange(short startBase, short amount,
                                 short endBase) {
        if (startBase + amount < startBase)
            throw new IllegalArgumentException("illegal start range "
                + startBase + " plus " + amount);
        if (endBase + amount < endBase)
            throw new IllegalArgumentException("illegal end range " + endBase
                + " plus " + amount);

        /* Check that all numbers are available. */
        Map<Short, Short> startExisting =
            startToEndMap.subMap(startBase, (short) (startBase + amount));
        if (!startExisting.isEmpty())
            throw new IllegalArgumentException("start range in use "
                + startExisting.keySet());
        Map<Short, Short> endExisting =
            endToStartMap.subMap(endBase, (short) (endBase + amount));
        if (!endExisting.isEmpty())
            throw new IllegalArgumentException("end range in use "
                + endExisting.keySet());

        /* Add all the labels. */
        startToEndMap
            .putAll(IntStream.range(startBase, amount).boxed()
                .collect(Collectors
                    .<Integer, Short, Short>toMap(Integer::shortValue,
                                                  k -> (short) (k.shortValue()
                                                      + endBase
                                                      - startBase))));
        endToStartMap
            .putAll(IntStream.range(startBase, amount).boxed()
                .collect(Collectors
                    .<Integer, Short, Short>toMap(Integer::shortValue,
                                                  k -> (short) (k.shortValue()
                                                      + endBase
                                                      - startBase))));
    }

    @Override
    public Terminus getPeer(Terminus p) {
        if (p.getPort().equals(start)) {
            Short other = startToEndMap.get(p.getLabel());
            if (other == null) return null;
            return end.getEndPoint(other);
        }
        if (p.getPort().equals(end)) {
            Short other = endToStartMap.get(p.getLabel());
            if (other == null) return null;
            return start.getEndPoint(other);
        }
        throw new IllegalArgumentException("end point does not"
            + " belong to trunk");
    }

    @Override
    public Terminus allocateTunnel() {
        if (availableTunnels.isEmpty()) return null;
        short startLabel = (short) availableTunnels.nextSetBit(0);
        availableTunnels.clear(startLabel);
        return start.getEndPoint(startLabel);
    }

    @Override
    public void releaseTunnel(Terminus endPoint) {
        final short startLabel;
        if (endPoint.getPort().equals(start)) {
            startLabel = endPoint.getLabel();
            if (!startToEndMap.containsKey(startLabel))
                throw new IllegalArgumentException("unallocated " + endPoint);
        } else if (endPoint.getPort().equals(end)) {
            short endLabel = endPoint.getLabel();
            Short rv = endToStartMap.get(endLabel);
            if (rv == null)
                throw new IllegalArgumentException("unallocated " + endPoint);
            startLabel = rv;
        } else {
            throw new IllegalArgumentException("end point " + endPoint
                + " does not belong to " + start + " or " + end);
        }
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
}
