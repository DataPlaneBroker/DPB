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
import java.util.Map;

import org.json.simple.JSONObject;

/**
 * Lists tunnels attached to a bridge. This is used for the results of
 * {@link CorsaREST#attachTunnel(String, TunnelDesc, ResponseHandler)}
 * and {@link CorsaREST#getTunnels(String, ResponseHandler)}.
 * 
 * @author simpsons
 */
public class TunnelsDesc {
    /**
     * The ofport that a single tunnel is attached to
     */
    public int ofport = -1;

    /**
     * Maps tunnel numbers (whatever they are) to their URIs
     */
    public Map<Integer, URI> tunnels = new HashMap<>();

    /**
     * Create a description of tunnels from a JSON object. The object is
     * expected to have a <samp>links</samp> component with keys of the
     * form <samp>tunnel <var>N</var></samp>, mapping to a map with the
     * entry <samp>href</samp> giving the URI for the tunnel and
     * <samp>tunnel</samp> giving the tunnel number, whatever that is.
     * The root object may contain an <samp>ofport</samp> integer,
     * usually as a result of creating a tunnel.
     */
    public TunnelsDesc(JSONObject root) {
        Integer ofport = (Integer) root.get("ofport");
        if (ofport != null) this.ofport = ofport;

        JSONObject links = (JSONObject) root.get("links");
        @SuppressWarnings("unchecked")
        Collection<Map.Entry<String, JSONObject>> entries = links.entrySet();
        for (Map.Entry<String, JSONObject> entry : entries) {
            String key = entry.getKey();
            Integer tk = (Integer) entry.getValue().get("tunnel");
            if (tk == null) tk = Integer.parseInt(key.substring(7));

            String value = (String) entry.getValue().get("href");
            URI href = URI.create(value);
            tunnels.put(tk, href);
        }
    }
}
