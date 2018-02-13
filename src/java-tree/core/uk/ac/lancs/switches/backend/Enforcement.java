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
package uk.ac.lancs.switches.backend;

/**
 * Specifies bidirectional bandwidth requirements.
 * 
 * @author simpsons
 */
public final class Enforcement {
    /**
     * Packets exceeding this rate will be queued.
     * 
     * @summary The rate at which to shape traffic leaving by the
     * associated end point
     */
    public final double shaping;

    /**
     * Packets exceeding this rate will be dropped.
     * 
     * @summary The rate at which to meter traffic entering by the
     * associated end point
     */
    public final double metering;

    private Enforcement(double shaping, double metering) {
        this.shaping = shaping;
        this.metering = metering;
    }

    /**
     * Create an enforcement.
     * 
     * @param shaping the rate at which to shape traffic leaving by the
     * associated end point
     * 
     * @param metering the rate at which to meter traffic entering by
     * the associated end point
     * 
     * @return the requested enforcement
     */
    public static Enforcement of(double shaping, double metering) {
        return new Enforcement(shaping, metering);
    }

    /**
     * Create a symmetric enforcement.
     * 
     * @param rate the rate at which to shape/meter traffic
     * leaving/entering by the associated end point
     * 
     * @return the requested enforcement
     */
    public static Enforcement of(double rate) {
        return of(rate, rate);
    }
}
