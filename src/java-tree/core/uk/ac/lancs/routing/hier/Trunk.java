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

import java.util.List;

import uk.ac.lancs.routing.span.Edge;

/**
 * Represents a physical link with a fixed delay and a remaining
 * bandwidth connecting two ports. Bandwidth can be allocated and
 * released. Tunnels within the trunk can be allocated and released.
 * 
 * @author simpsons
 */
public interface Trunk {
    /**
     * Get the ports at either end of this trunk.
     * 
     * @return the ports of the trunk
     */
    List<Port> getPorts();

    /**
     * Get the ports of this trunk as an edge.
     * 
     * @return an edge representing this trunk
     */
    default Edge<Port> getEdge() {
        List<Port> ports = getPorts();
        return Edge.of(ports.get(0), ports.get(1));
    }

    /**
     * Get the bandwidth remaining available on this trunk.
     * 
     * @return the remaining available bandwidth
     */
    double getBandwidth();

    /**
     * Consume bandwidth on this trunk.
     * 
     * @param amount the amount to deduct from the remaining bandwidth
     * 
     * @throws IllegalArgumentException if the amount is negative or
     * exceeds the remaining level
     */
    void allocateBandwidth(double amount);

    /**
     * Release bandwidth on this trunk.
     * 
     * @param amount the amount to add to the remaining bandwidth
     * 
     * @throws IllegalArgumentException if the amount is negative
     */
    void releaseBandwidth(double amount);

    /**
     * Make a range of labels available.
     * 
     * @param startBase the first available label at the start side of
     * the link
     * 
     * @param amount the number of labels from the base to make
     * available
     * 
     * @param endBase the first available label at the end side of the
     * link
     */
    void defineLabelRange(short startBase, short amount, short endBase);

    /**
     * Make a range of labels available.
     * 
     * <p>
     * By default, this method calls
     * {@link #defineLabelRange(short, short, short)}, using the first
     * argument also as the last.
     * 
     * @param startBase the first available label at either side of the
     * link
     * 
     * @param amount the number of labels from the base to make
     * available
     */
    default void defineLabelRange(short startBase, short amount) {
        defineLabelRange(startBase, amount, startBase);
    }

    /**
     * Get the peer of an end point.
     * 
     * @param p the end point whose peer is requested
     * 
     * @return the peer of the supplied end point, or {@code null} if it
     * has no peer
     * 
     * @throws IllegalArgumentException if the end point does not belong
     * to either port of this trunk
     */
    Terminus getPeer(Terminus p);

    /**
     * Get the number of tunnels available through this trunk.
     * 
     * @return the number of available tunnels
     */
    int getAvailableTunnelCount();

    /**
     * Allocate a tunnel through this trunk. If successful, only one end
     * of the tunnel is returned. The other can be obtained with
     * {@link #getPeer(Terminus)}.
     * 
     * @return the end point at the start of the tunnel, or {@code null}
     * if no further resource remains
     */
    Terminus allocateTunnel();

    /**
     * Get the fixed delay of this trunk.
     * 
     * @return the trunk's fixed delay
     */
    double getDelay();

    /**
     * Release a tunnel through this trunk.
     * 
     * @param endPoint either of the tunnel end points
     */
    void releaseTunnel(Terminus endPoint);
}
