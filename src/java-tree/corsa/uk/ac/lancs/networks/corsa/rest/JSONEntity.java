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
package uk.ac.lancs.networks.corsa.rest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Holds a JSON entity, either an array or a map.
 * 
 * @author simpsons
 */
public final class JSONEntity {
    /**
     * The entity as an array, or {@code null} if it is a map
     */
    public final JSONArray array;

    /**
     * The entity as a map, or {@code null} if it is an array
     */
    public final JSONObject map;

    /**
     * Create a JSON entity from a map.
     * 
     * @param map the map
     * 
     * @throws NullPointerException if the argument is {@code null}
     */
    public JSONEntity(JSONObject map) {
        if (map == null) throw new NullPointerException();
        this.map = map;
        this.array = null;
    }

    /**
     * Create a JSON entity from an array
     * 
     * @param array the array
     * 
     * @throws NullPointerException if the argument is {@code null}
     */
    public JSONEntity(JSONArray array) {
        if (array == null) throw new NullPointerException();
        this.array = array;
        this.map = null;
    }

    /**
     * Create a JSON entity from an array or map as appropriate.
     * 
     * @param obj either a {@link JSONArray} or a {@link JSONObject}
     * 
     * @throws IllegalArgumentException if the object is not of a
     * suitable type
     */
    public JSONEntity(Object obj) {
        if (obj instanceof JSONArray) {
            this.array = (JSONArray) obj;
            this.map = null;
        } else if (obj instanceof JSONObject) {
            this.map = (JSONObject) obj;
            this.array = null;
        } else {
            throw new IllegalArgumentException();
        }
    }
}
