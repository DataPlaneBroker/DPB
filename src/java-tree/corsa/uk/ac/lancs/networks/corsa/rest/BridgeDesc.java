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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.json.JsonValue;

/**
 * Describes a bridge configuration.
 * 
 * @author simpsons
 */
public class BridgeDesc {
    /**
     * The bridge id
     */
    public String name;

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

    /**
     * The namespace fot the bridge's controller port
     */
    public String netns;

    /**
     * A human-readable description of the bridge
     */
    public String descr;

    /**
     * A set of supported controller protocols
     */
    public Collection<String> protocols;

    /**
     * REST addresses of other components of the bridge
     */
    public Map<String, URI> links;

    /**
     * Create an empty bridge description.
     */
    public BridgeDesc() {}

    /**
     * Set the bridge identifier.
     * 
     * @param bridge an identifier of the form
     * <samp>br<var>N</var></samp>, where <var>N</var> is in [1,63]
     * 
     * @return this object
     */
    public BridgeDesc bridge(String bridge) {
        this.name = bridge;
        return this;
    }

    /**
     * Set the bridge's DPID.
     * 
     * @param dpid the new DPID
     * 
     * @return this object
     */
    public BridgeDesc dpid(long dpid) {
        this.dpid = dpid;
        return this;
    }

    /**
     * Unset the bridge's DPID.
     * 
     * @return this object
     */
    public BridgeDesc noDpid() {
        this.dpid = null;
        return this;
    }

    /**
     * Set the bridge's subtype.
     * 
     * @param subtype the new subtype
     * 
     * @return this object
     */
    public BridgeDesc subtype(String subtype) {
        this.subtype = subtype;
        return this;
    }

    /**
     * Set the bridge's resource percentage.
     * 
     * @param resources the bridge's resource percentage
     * 
     * @return this object
     */
    public BridgeDesc resources(int resources) {
        this.resources = resources;
        return this;
    }

    /**
     * Set the bridge's multcast traffic class.
     * 
     * @param trafficClass the bridge's traffic class
     * 
     * @return this object
     */
    public BridgeDesc trafficClass(int trafficClass) {
        this.trafficClass = trafficClass;
        return this;
    }

    /**
     * Unset the bridge's traffic class.
     * 
     * @return this object
     */
    public BridgeDesc noTrafficClass() {
        this.trafficClass = null;
        return this;
    }

    /**
     * Set the namespace for the bridge's controller port.
     * 
     * @param netns the bridge's controller port's new namespace
     * 
     * @return this object
     */
    public BridgeDesc netns(String netns) {
        this.netns = netns;
        return this;
    }

    /**
     * Set the bridge's description text.
     * 
     * @param descr the new description
     * 
     * @return this object
     */
    public BridgeDesc descr(String descr) {
        this.descr = descr;
        return this;
    }

    /**
     * Convert this bridge description to a JSON object.
     * 
     * @return the requested JSON representation of this bridge
     */
    public JsonObject toJSON() {
        if (subtype == null)
            throw new IllegalStateException("subtype must be set");
        if (resources == null)
            throw new IllegalStateException("resources must be set");
        JsonObjectBuilder result = Json.createObjectBuilder();
        result.add("bridge", name);
        if (dpid != null)
            result.add("dpid", "0x" + Long.toUnsignedString(dpid, 16));
        if (subtype != null) result.add("subtype", subtype);
        if (resources != null) result.add("resources", resources);
        if (trafficClass != null) result.add("traffic-class", trafficClass);
        if (netns != null) result.add("netns", netns);
        if (descr != null) result.add("bridge-descr", descr);
        return result.build();
    }

    /**
     * Create a bridge description from a JSON entity.
     * 
     * @param entity the JSON object
     */
    public BridgeDesc(JsonStructure entity) {
        this((JsonObject) entity);
    }

    /**
     * Create a mapping from bridge name to bridge description from a
     * JSON array.
     * 
     * @param json the source array
     * 
     * @return the requested mapping; never {@code null}
     */
    public static Map<String, BridgeDesc> of(JsonArray json) {
        Map<String, BridgeDesc> bridges = new HashMap<>();
        for (JsonObject entry : json.getValuesAs(JsonObject.class)) {
            BridgeDesc desc = new BridgeDesc(entry);
            String key = desc.name;
            bridges.put(key, desc);
        }
        return bridges;
    }

    /**
     * Create a bridge description from a JSON object.
     * 
     * @param root the JSON object
     */
    public BridgeDesc(JsonObject root) {
        name = root.getString("bridge", null);
        if (name == null) {
            JsonObject links = root.getJsonObject("links");
            JsonObject self = links.getJsonObject("self");
            if (self != null) name = self.get("bridge").toString();
        }
        String dpidText = root.getString("dpid");
        if (dpidText != null) dpid = Long.parseUnsignedLong(dpidText, 16);
        subtype = root.getString("subtype");
        String resourcesText = root.getString("resources", null);
        if (resourcesText != null)
            resources = Integer.parseInt(resourcesText);
        trafficClass = getIntFromString(root.get("traffic-class"));
        netns = root.getString("netns", null);
        descr = root.getString("bridge-descr", null);
        JsonArray brList = root.getJsonArray("protocols");
        if (brList != null) protocols =
            new ArrayList<>(brList.getValuesAs(JsonString.class).stream()
                .map(JsonString::getString).collect(Collectors.toList()));
        JsonObject links = root.getJsonObject("links");
        if (links != null) {
            this.links = new HashMap<>();
            for (Map.Entry<String, JsonValue> entry : links.entrySet()) {
                JsonObject value = (JsonObject) entry.getValue();
                URI href = URI.create(value.getString("href"));
                this.links.put(entry.getKey(), href);
            }
        }
    }

    private static int getIntFromString(Object obj) {
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Long) return (int) (long) (Long) obj;
        if (obj instanceof String) return Integer.parseInt((String) obj);
        return -1;
    }
}
