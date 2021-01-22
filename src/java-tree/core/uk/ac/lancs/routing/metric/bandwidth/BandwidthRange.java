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

package uk.ac.lancs.routing.metric.bandwidth;

/**
 * Specifies a bandwidth requirement as a guaranteed minimum rate and a
 * maximum permitted rate.
 * 
 * @author simpsons
 */
public final class BandwidthRange {
    public final double min;

    public final Double max;

    private BandwidthRange(double min, Double max) {
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
    public static BandwidthRange between(double min, double max) {
        if (max < min)
            return new BandwidthRange(max, min);
        else
            return new BandwidthRange(min, max);
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
    public static BandwidthRange at(double fixed) {
        return new BandwidthRange(fixed, fixed);
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
    public static BandwidthRange from(double min) {
        return new BandwidthRange(min, null);
    }

    /**
     * Create a range which is this range plus another.
     * 
     * @see #add(uk.ac.lancs.routing.metric.bandwidth.BandwidthRange,
     * uk.ac.lancs.routing.metric.bandwidth.BandwidthRange)
     * 
     * @param other the other range
     * 
     * @return the sum range
     */
    public BandwidthRange add(BandwidthRange other) {
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
    public static BandwidthRange min(BandwidthRange a, BandwidthRange b) {
        final double min = Math.min(a.min, b.min);
        final Double max;
        if (b.max == null)
            max = a.max;
        else if (a.max == null)
            max = b.max;
        else
            max = Math.min(a.max, b.max);
        return new BandwidthRange(min, max);
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
    public static BandwidthRange add(BandwidthRange a, BandwidthRange b) {
        if (a == null) return b;
        if (b == null) return a;
        final double min = a.min + b.min;
        final Double max;
        if (b.max == null)
            max = a.max;
        else if (a.max == null)
            max = b.max;
        else
            max = a.max + b.max;
        return new BandwidthRange(min, max);
    }
}
