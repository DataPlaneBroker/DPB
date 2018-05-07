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
package uk.ac.lancs.networks.corsa;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Describes a bridge configuration.
 * 
 * @author simpsons
 */
class BridgeDesc {
    /**
     * The bridge id
     */
    public String bridge;

    /**
     * The DPID that the bridge identifies itself with to its
     * controller(s)
     */
    public Long dpid;

    /**
     * The bridge subtype
     */
    public String subtype;

    /**
     * The allocated percentage of resources allocated to this bridge
     */
    public Integer resources;

    /**
     * The multicast traffic class of the bridge, or {@code null} if not
     * specified
     */
    public Integer trafficClass;
    public String netns;
    public String descr;
    public Collection<String> protocols;
    public Map<String, URI> links;

    public BridgeDesc() {}

    public BridgeDesc bridge(String bridge) {
        this.bridge = bridge;
        return this;
    }

    public BridgeDesc dpid(Long dpid) {
        this.dpid = dpid;
        return this;
    }

    public BridgeDesc subtype(String subtype) {
        this.subtype = subtype;
        return this;
    }

    public BridgeDesc resources(Integer resources) {
        this.resources = resources;
        return this;
    }

    public BridgeDesc trafficClass(Integer trafficClass) {
        this.trafficClass = trafficClass;
        return this;
    }

    public BridgeDesc netns(String netns) {
        this.netns = netns;
        return this;
    }

    public BridgeDesc descr(String descr) {
        this.descr = descr;
        return this;
    }

    @SuppressWarnings("unchecked")
    public JSONObject toJSON() {
        if (subtype == null)
            throw new IllegalStateException("subtype must be set");
        if (resources == null)
            throw new IllegalStateException("resources must be set");
        JSONObject result = new JSONObject();
        result.put("bridge", bridge);
        if (dpid != null)
            result.put("dpid", "0x" + Long.toUnsignedString(dpid, 16));
        if (subtype != null) result.put("subtype", subtype);
        if (resources != null) result.put("resources", resources);
        if (trafficClass != null) result.put("traffic-class", trafficClass);
        if (netns != null) result.put("netns", netns);
        if (descr != null) result.put("descr", descr);
        return result;
    }

    /**
     * Create a bridge description from a JSON object.
     * 
     * @param root the JSON object
     */
    public BridgeDesc(JSONObject root) {
        System.err.println(root);
        bridge = (String) root.get("bridge");
        String dpidText = (String) root.get("dpid");
        if (dpidText != null) dpid = Long.parseUnsignedLong(dpidText, 16);
        subtype = (String) root.get("subtype");
        String resourcesText = (String) root.get("resources");
        if (resourcesText != null)
            resources = Integer.parseInt(resourcesText);
        String trafficClassText = (String) root.get("traffic-class");
        if (trafficClassText != null)
            trafficClass = Integer.parseInt(trafficClassText);
        netns = (String) root.get("netns");
        descr = (String) root.get("descr");
        JSONArray brList = (JSONArray) root.get("protocols");
        if (brList != null) {
            protocols = new ArrayList<>();
            for (@SuppressWarnings("unchecked")
            Iterator<String> iter = brList.iterator(); iter.hasNext();) {
                protocols.add(iter.next());
            }
        }
        JSONObject links = (JSONObject) root.get("links");
        if (links != null) {
            this.links = new HashMap<>();
            @SuppressWarnings("unchecked")
            Collection<Map.Entry<String, JSONObject>> entries =
                links.entrySet();
            for (Map.Entry<String, JSONObject> entry : entries) {
                String key = entry.getKey();
                String value = (String) entry.getValue().get("href");
                URI href = URI.create(value);
                this.links.put(key, href);
            }
        }
    }
}
