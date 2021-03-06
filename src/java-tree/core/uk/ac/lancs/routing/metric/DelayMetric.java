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
package uk.ac.lancs.routing.metric;

/**
 * Represents a metric that accumulates by addition, with lower values
 * being better.
 * 
 * @author simpsons
 */
public final class DelayMetric extends UnitNamedMetric {
    /**
     * Create a delay-like metric with a given name and units.
     * 
     * @param name the metric's name
     * 
     * @param units the metric's units
     */
    public DelayMetric(String name, String units) {
        super(name, units);
    }

    /**
     * {@inheritDoc}
     * 
     * @param v1 {@inheritDoc}
     * 
     * @param v2 {@inheritDoc}
     * 
     * @return the sum of the two arguments
     */
    @Override
    public double accumulate(double v1, double v2) {
        return v1 + v2;

    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Smaller values are considered better.
     * 
     * @param v1 {@inheritDoc}
     * 
     * @param v2 {@inheritDoc}
     * 
     * @return {@inheritDoc}
     */
    @Override
    public int compare(double v1, double v2) {
        return Double.compare(v2, v1);
    }
}
