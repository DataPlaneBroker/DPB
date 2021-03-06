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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonStructure;

/**
 * Describes an existing tunnel or one to be created.
 * 
 * @author simpsons
 */
public class TunnelDesc {
    /**
     * The ofport of the VFC that the tunnel connects to, or {@code -1}
     * if not specified
     */
    public int ofport = -1;

    /**
     * The type of the logical port the tunnel connects to
     */
    public String type;

    /**
     * The logical port the tunnel connects to
     */
    public String port;

    /**
     * The VLAN id the tunnel connects to for type <samp>ctag</samp>,
     * the outer tag for <samp>ctag-ctag</samp> or
     * <samp>stag-ctag</samp>, or {@code -1} for other types
     * 
     * @see #type
     */
    public int vlanId = -1;

    /**
     * The committed burst size (CBS) in KB, or {@code -1} if not set
     */
    public long cbs = -1;

    /**
     * The committed information rate (CIR) in Kbps, or {@code -1} if
     * not set
     */
    public long cir = -1;

    /**
     * The excess burst size (EBS) in KB, or {@code -1} if not set
     */
    public long ebs = -1;

    /**
     * The excess information rate (EIR) in Kbps, or {@code -1} if not
     * set
     */
    public long eir = -1;

    /**
     * The traffic class for the tunnel, or {@code -1} if unspecified
     */
    public int trafficClass = -1;

    /**
     * The TPID, or {@code -1} if unspecified
     */
    public int tpid = -1;

    /**
     * The inner VLAN id for type <samp>ctag-ctag</samp> or
     * <samp>stag-ctag</samp>, or {@code -1} for other types
     * 
     * @see #type
     */
    public int innerVlanId = -1;

    /**
     * Whether traffic output over the tunnel is shaped
     */
    public boolean isShaped;

    /**
     * The shaped rate for traffic output over the tunnel, in Mbps, or
     * {@code -1} if unspecified
     */
    public double shapedRate = -1;

    /**
     * The shaped queue profile
     */
    public String queueProfile;

    /**
     * Whether the tunnel is operational
     */
    public String operationalState;

    /**
     * A description text for the tunnel, or {@code null}
     */
    public String descr;

    /**
     * Create an empty tunnel description in preparation for creating a
     * new tunnel.
     */
    public TunnelDesc() {}

    /**
     * Set the ofport that the tunnel will attach to.
     * 
     * @param ofport the ofport
     * 
     * @return this object
     */
    public TunnelDesc ofport(int ofport) {
        this.ofport = ofport;
        return this;
    }

    /**
     * Set the (outer) VLAN id for external traffic.
     * 
     * @param vlanId the VLAN id
     * 
     * @return this object
     */
    public TunnelDesc vlanId(int vlanId) {
        this.vlanId = vlanId;
        return this;
    }

    /**
     * Unset the (outer) VLAN id for external traffic
     * 
     * @return this object
     */
    public TunnelDesc noVlanId() {
        return vlanId(-1);
    }

    /**
     * Set the inner VLAN id for external traffic.
     * 
     * @param innerVlanId the VLAN id
     * 
     * @return this object
     */
    public TunnelDesc innerVlanId(int innerVlanId) {
        this.innerVlanId = innerVlanId;
        return this;
    }

    /**
     * Unset the inner VLAN id for external traffic.
     * 
     * @return this object
     */
    public TunnelDesc noInnerVlanId() {
        return innerVlanId(-1);
    }

    /**
     * Set the traffic class.
     * 
     * @param trafficClass the traffic class
     * 
     * @return this object
     */
    public TunnelDesc trafficClass(int trafficClass) {
        this.trafficClass = trafficClass;
        return this;
    }

    /**
     * Set the logical port.
     * 
     * @param port the logical port
     * 
     * @return this object
     */
    public TunnelDesc port(String port) {
        this.port = port;
        return this;
    }

    /**
     * Set the description text.
     * 
     * @param descr the description text
     * 
     * @return this object
     */
    public TunnelDesc descr(String descr) {
        this.descr = descr;
        return this;
    }

    /**
     * Set the shaped rate.
     * 
     * @param shapedRate the shaped rate in Mbps
     * 
     * @return this object
     */
    public TunnelDesc shapedRate(double shapedRate) {
        this.shapedRate = shapedRate;
        return this;
    }

    /**
     * Convert this description into JSON.
     * 
     * @return the JSON representation of this tunnel
     */
    public JsonObject toJSON() {
        if (ofport < 0) throw new IllegalStateException("ofport must be set");
        if (port == null) throw new IllegalStateException("port must be set");
        JsonObjectBuilder result = Json.createObjectBuilder();
        result.add("ofport", ofport);
        result.add("port", port);
        if (vlanId >= 0) {
            result.add("vlan-id", vlanId);
            if (innerVlanId >= 0) result.add("inner-vlan-id", innerVlanId);
        }
        if (trafficClass >= 0) result.add("traffic-class", trafficClass);
        if (shapedRate >= 0) result.add("shaped-rate", shapedRate);
        if (descr != null) result.add("ifdescr", descr);
        /* TODO: vlan-range parameter can be provided too. */
        return result.build();
    }

    /**
     * Create a mapping from ofport to tunnel description from a JSON
     * entity. If an array is provided, the result will have as many
     * entries as the array. If a map is provided, the result will have
     * one entry.
     * 
     * @param entity the source entity
     * 
     * @return the requested mapping; never {@code null}
     */
    public static Map<Integer, TunnelDesc> of(JsonStructure entity) {
        if (entity instanceof JsonArray)
            return of((JsonArray) entity);
        else
            return of((JsonObject) entity);
    }

    /**
     * Create a singleton mapping from ofport to tunnel description from
     * a JSON map.
     * 
     * @param json the source map
     * 
     * @return the requested mapping; never {@code null}
     */
    public static Map<Integer, TunnelDesc> of(JsonObject json) {
        TunnelDesc desc = new TunnelDesc(json);
        return Collections.singletonMap(desc.ofport, desc);
    }

    /**
     * Create a mapping from ofport to tunnel description from a JSON
     * array.
     * 
     * @param json the source array
     * 
     * @return the requested mapping; never {@code null}
     */
    public static Map<Integer, TunnelDesc> of(JsonArray json) {
        Map<Integer, TunnelDesc> tunnels = new HashMap<>();
        for (JsonObject entry : json.getValuesAs(JsonObject.class)) {
            TunnelDesc desc = new TunnelDesc(entry);
            int key = desc.ofport;
            tunnels.put(key, desc);
        }
        return tunnels;
    }

    /**
     * Create a tunnel description from a JSON entity.
     * 
     * <p>
     * The fields {@link #innerVlanId} and {@link #tpid} are set only if
     * {@link #type} is <samp>stag-ctag</samp> or
     * <samp>ctag-ctag</samp>, and are {@code -1} otherwise.
     * 
     * <p>
     * The fields {@link #vlanId} and {@link #trafficClass} are set only
     * if {@link #type} is <samp>ctag</samp>, <samp>untagged</samp>,
     * <samp>stag-ctag</samp> or <samp>ctag-ctag</samp>, and are
     * {@code -1} otherwise.
     * 
     * <p>
     * The fields {@link #shapedRate} and {@link #queueProfile} are set
     * only if {@link #isShaped} is {@code true}, and are {@code -1}
     * otherwise.
     * 
     * @param entity the JSON entity
     */
    public TunnelDesc(JsonStructure entity) {
        this((JsonObject) entity);
    }

    /**
     * Create a tunnel description from a JSON object.
     * 
     * <p>
     * The fields {@link #innerVlanId} and {@link #tpid} are set only if
     * {@link #type} is <samp>stag-ctag</samp> or
     * <samp>ctag-ctag</samp>, and are {@code -1} otherwise.
     * 
     * <p>
     * The fields {@link #vlanId} and {@link #trafficClass} are set only
     * if {@link #type} is <samp>ctag</samp>, <samp>untagged</samp>,
     * <samp>stag-ctag</samp> or <samp>ctag-ctag</samp>, and are
     * {@code -1} otherwise.
     * 
     * <p>
     * The fields {@link #shapedRate} and {@link #queueProfile} are set
     * only if {@link #isShaped} is {@code true}, and are {@code -1}
     * otherwise.
     * 
     * @param root the JSON object
     */
    public TunnelDesc(JsonObject root) {
        this.ofport = root.getJsonNumber("ofport").intValue();
        this.type = root.getString("type");
        this.port = root.getString("port");
        innerVlanId = vlanId = -1;
        trafficClass = -1;
        tpid = -1;
        switch (type) {
        case "stag-ctag":
        case "ctag-ctag":
            this.tpid =
                Integer.parseInt(root.getString("tpid").substring(2), 16);
            JsonNumber ivi = root.getJsonNumber("inner-vlan-id");
            this.innerVlanId = ivi == null ? -1 : ivi.intValue();
            /* Fall-through! */
        case "ctag":
        case "untagged":
            this.vlanId = root.getJsonNumber("vlan-id").intValue();
            this.trafficClass =
                root.getJsonNumber("traffic-class").intValue();
            break;
        default:
            break;
        }

        this.isShaped = root.getBoolean("is-shaped");
        shapedRate = -1;
        queueProfile = null;
        if (isShaped) {
            this.shapedRate = getDouble(root, "shaped-rate", -1);
            this.queueProfile = root.get("queue-profile").toString();
        }

        this.operationalState = root.getString("oper-state");
        this.descr = root.getString("ifdescr");

        JsonObject meter = root.getJsonObject("meter");
        cir = cbs = eir = ebs = -1;
        if (meter != null) {
            this.cir = getLong(meter, "cir", -1l);
            this.cbs = getLong(meter, "cbs", -1l);
            this.eir = getLong(meter, "eir", -1l);
            this.ebs = getLong(meter, "ebs", -1l);
        }
    }

    private static long getLong(JsonObject obj, String key, long def) {
        JsonNumber num = obj.getJsonNumber(key);
        if (num == null) return def;
        return num.longValue();
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        JsonNumber num = obj.getJsonNumber(key);
        if (num == null) return def;
        return num.longValue();
    }
}
