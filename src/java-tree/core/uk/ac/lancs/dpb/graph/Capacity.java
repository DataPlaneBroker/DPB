/*
 * Copyright (c) 2021, Regents of the University of Lancaster
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the
 *   distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *
 *  Author: Steven Simpson <s.simpson@lancaster.ac.uk>
 */

package uk.ac.lancs.dpb.graph;

/**
 * Specifies a bandwidth requirement as a guaranteed minimum rate and a
 * maximum permitted rate.
 * 
 * @author simpsons
 */
public final class Capacity {
    private final double min, max;

    /**
     * Get the minimum guaranteed bandwidth.
     * 
     * @return the minimum bandwidth
     */
    public double min() {
        return min;
    }

    /**
     * Get the maximum permitted bandwidth. This is
     * {@link Double#POSITIVE_INFINITY} for unlimited bandwidth.
     * 
     * @return the maximum bandwidth
     */
    public double max() {
        return max;
    }

    /**
     * Get a string representation of this bandwidth range. This is the
     * minimum value, a comma and the maximum value, all in square
     * brackets.
     * 
     * @return the string representation
     */
    @Override
    public String toString() {
        return String.format("[ %g, %g ]", min, max);
    }

    /**
     * Get the excess bandwidth. This is the maximum minus the minimum.
     * 
     * @return the excess
     */
    public double excess() {
        return max - min;
    }

    private Capacity(double min, double max) {
        if (min < 0) throw new IllegalArgumentException("-ve min: " + min);
        assert max >= min;
        this.min = min;
        this.max = max;
    }

    /**
     * Express a bandwidth requirement between two rates. If they are
     * provided out of order, they are re-ordered.
     * 
     * @param min the minimum guaranteed rate
     * 
     * @param max the maximum permitted rate
     * 
     * @return the requested range
     * 
     * @constructor
     */
    public static Capacity between(double min, double max) {
        if (max < min)
            return new Capacity(max, min);
        else
            return new Capacity(min, max);
    }

    /**
     * Express a bandwidth requirement with the same minimum and maximum
     * rates.
     * 
     * @param fixed the common rate
     * 
     * @return the requested range
     * 
     * @constructor
     */
    public static Capacity at(double fixed) {
        return new Capacity(fixed, fixed);
    }

    /**
     * Express a bandwidth requirement with a minimum rate but no
     * maximum.
     * 
     * @param min the minimum guaranteed rate
     * 
     * @return the requested range
     * 
     * @constructor
     */
    public static Capacity from(double min) {
        return new Capacity(min, Double.POSITIVE_INFINITY);
    }

    /**
     * Express a bandwidth requirement as a minimum rate and an excess.
     * 
     * @param guarantee the minimum guaranteed rate
     * 
     * @param excess the excess rate
     * 
     * @return the requested range
     * 
     * @constructor
     */
    public static Capacity base(double guarantee, double excess) {
        if (excess < 0.0)
            throw new IllegalArgumentException("-ve excess: " + excess);
        return new Capacity(guarantee, guarantee + excess);
    }

    /**
     * Create a range which is this range plus another.
     * 
     * @see #add(Capacity,Capacity)
     * 
     * @param other the other range
     * 
     * @return the sum range
     */
    public Capacity add(Capacity other) {
        return add(this, other);
    }

    /**
     * Express a range which is the least demanding of two requirements.
     * The resultant minimum rate is the minimum of the two arguments'
     * minimum rates. If either of the arguments' maximum rates are
     * infinite, the other argument's maximum rate is the resultant
     * maximum rate. Otherwise, it is the minimum of the two maximum
     * rates.
     * 
     * @param a one of the requirements
     * 
     * @param b the other requirement
     * 
     * @return the minimum requirement
     * 
     * @constructor
     */
    public static Capacity min(Capacity a, Capacity b) {
        final double min = Math.min(a.min, b.min);
        final double max = Math.min(a.max, b.max);
        return new Capacity(min, max);
    }

    /**
     * Express a range which is the most demanding of two requirements.
     * The resultant minimum rate is the maximum of the two arguments'
     * minimum rates. The resultant maximum rate is the maximum of the
     * two maximum rates.
     * 
     * @param a one of the requirements
     * 
     * @param b the other requirement
     * 
     * @return the minimum requirement
     * 
     * @constructor
     */
    public static Capacity max(Capacity a, Capacity b) {
        final double min = Math.max(a.min, b.min);
        final double max = Math.max(a.max, b.max);
        return new Capacity(min, max);
    }

    /**
     * Add two ranges together. If either argument is {@code null}, the
     * other is returned. Otherwise, the minimum rate of the result is
     * the sum of the arguments' minimum ranges, and the maximum rate of
     * the result is the sum of the arguments' maximum ranges. Unset
     * maximum ranges are considered to be infinite.
     * 
     * @param a the first range; or {@code null} if not specified
     * 
     * @param b the second range; or {@code null} if not specified
     * 
     * @return the sum of the two ranges
     * 
     * @constructor
     */
    public static Capacity add(Capacity a, Capacity b) {
        if (a == null) return b;
        if (b == null) return a;
        final double min = a.min + b.min;
        final double max = a.max + b.max;
        return new Capacity(min, max);
    }
}
