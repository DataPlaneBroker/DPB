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

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Lists basic bridge details and supported bridge subtypes.
 * 
 * @author simpsons
 */
public class BridgesDesc {
    /**
     * A set of supported bridge subtypes
     * 
     * @see BridgeDesc#subtype
     */
    public final Collection<String> supportedSubtypes = new HashSet<>();

    /**
     * A set of bridges and their REST URIs
     */
    public final Map<String, URI> bridges = new HashMap<>();

    /**
     * Create a list of bridge details from a JSON object.
     */
    public BridgesDesc(JSONObject root) {
        JSONArray brList = (JSONArray) root.get("supported-subtypes");
        if (brList != null) {
            for (@SuppressWarnings("unchecked")
            Iterator<String> iter = brList.iterator(); iter.hasNext();) {
                supportedSubtypes.add(iter.next());
            }
        }

        JSONObject links = (JSONObject) root.get("links");
        @SuppressWarnings("unchecked")
        Collection<Map.Entry<String, JSONObject>> entries = links.entrySet();
        for (Map.Entry<String, JSONObject> entry : entries) {
            String key = entry.getKey();
            String value = (String) entry.getValue().get("href");
            URI href = URI.create(value);
            bridges.put(key, href);
        }
    }
}
