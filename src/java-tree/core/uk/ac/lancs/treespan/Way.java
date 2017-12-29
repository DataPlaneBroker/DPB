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
package uk.ac.lancs.treespan;

/**
 * A distance-vector tuple
 * 
 * @param <N> the node type
 * 
 * @author simpsons
 */
public final class Way<N> {
    /**
     * The next hop to some given destination
     */
    public final N nextHop;

    /**
     * This distance to the destination
     */
    public final double distance;

    /**
     * Create a tuple.
     * 
     * @param nextHop the next hop to the implicit destination
     * 
     * @param distance the distance to the implicit destination
     */
    public Way(N nextHop, double distance) {
        this.nextHop = nextHop;
        this.distance = distance;
    }

    @Override
    public String toString() {
        return distance + " via " + nextHop;
    }

    /**
     * Get the hash code for this distance-vector.
     * 
     * @return the hash code of this object
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        long temp;
        temp = Double.doubleToLongBits(distance);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result =
            prime * result + ((nextHop == null) ? 0 : nextHop.hashCode());
        return result;
    }

    /**
     * Determine whether this distance-vector equals another object.
     * 
     * @param obj the other object
     * 
     * @return {@code true} iff the other object is also a
     * distance-vector, and has the same distance and next hop
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Way<?> other = (Way<?>) obj;
        if (Double.doubleToLongBits(distance) != Double
            .doubleToLongBits(other.distance)) return false;
        if (nextHop == null) {
            if (other.nextHop != null) return false;
        } else if (!nextHop.equals(other.nextHop)) return false;
        return true;
    }
}
