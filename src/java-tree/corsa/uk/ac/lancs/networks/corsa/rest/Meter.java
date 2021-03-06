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
package uk.ac.lancs.networks.corsa.rest;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/**
 * Describes an operation to set a tunnel's metering.
 * 
 * @author simpsons
 * 
 * @param <T> the type of the meter field
 */
public class Meter<T extends Number> implements TunnelPatchOp {
    private final String part;
    private final T value;

    private Meter(String part, T value) {
        this.part = part;
        this.value = value;
    }

    /**
     * Create an operation to set the tunnel's CIR (Committed
     * Information Rate). A {@link #cbs(long)} operation must accompany
     * this operation.
     * 
     * @param value the new value in Kbps
     * 
     * @return the requested operation
     */
    public static Meter<Double> cir(double value) {
        return new Meter<Double>("cir", value);
    }

    /**
     * Create an operation to set the tunnel's CBS (Committed Burst
     * Size). A {@link #cir(double)} operation must accompany this
     * operation.
     * 
     * @param value the new value in KB
     * 
     * @return the requested operation
     */
    public static Meter<Long> cbs(long value) {
        return new Meter<Long>("cbs", value);
    }

    /**
     * Create an operation to set the tunnel's EIR (Excess Information
     * Rate). An {@link #ebs(long)} operation must accompany this
     * operation.
     * 
     * @param value the new value in kbps
     * 
     * @return the requested operation
     */
    public static Meter<Double> eir(double value) {
        return new Meter<Double>("eir", value);
    }

    /**
     * Create an operation to set the tunnel's EBS (Excess Burst Size).
     * An {@link #eir(double)} operation must accompany this operation.
     * 
     * @param value the new value in KB
     * 
     * @return the requested operation
     */
    public static Meter<Long> ebs(long value) {
        return new Meter<Long>("ebs", value);
    }

    /**
     * Convert this meter to a JSON object.
     * 
     * @return the JSON representation of this meter
     */
    @Override
    public JsonObject marshal() {
        JsonObjectBuilder result = Json.createObjectBuilder();
        result.add("op", "replace");
        result.add("path", "/meter/" + part);
        result.add("value", value.doubleValue());
        return result.build();
    }
}
