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

import org.json.simple.JSONObject;

/**
 * 
 * 
 * @author simpsons
 */
class TunnelDesc {
    public int ofport = -1;
    public String type;
    public String port;
    public int vlanId = -1;
    public int trafficClass = -1;
    public int tpid = -1;
    public int innerVlanId = -1;
    public boolean isShaped;
    public int shapedRate = -1;
    public int queueProfile = -1;
    public boolean operational;
    public String descr;

    public TunnelDesc() {}

    public TunnelDesc ofport(int ofport) {
        this.ofport = ofport;
        return this;
    }

    public TunnelDesc vlanId(int vlanId) {
        this.vlanId = vlanId;
        return this;
    }

    public TunnelDesc innerVlanId(int innerVlanId) {
        this.innerVlanId = innerVlanId;
        return this;
    }

    public TunnelDesc trafficClass(int trafficClass) {
        this.trafficClass = trafficClass;
        return this;
    }

    public TunnelDesc port(String port) {
        this.port = port;
        return this;
    }

    public TunnelDesc descr(String descr) {
        this.descr = descr;
        return this;
    }

    public TunnelDesc shapedRate(int shapedRate) {
        this.shapedRate = shapedRate;
        return this;
    }

    @SuppressWarnings("unchecked")
    public JSONObject toJSON() {
        if (ofport < 0) throw new IllegalStateException("ofport must be set");
        if (port == null) throw new IllegalStateException("port must be set");
        JSONObject result = new JSONObject();
        result.put("ofport", ofport);
        result.put("port", port);
        if (vlanId >= 0) {
            result.put("vlan-id", vlanId);
            if (innerVlanId >= 0) result.put("inner-vlan-id", innerVlanId);
        }
        if (trafficClass >= 0) result.put("traffic-class", trafficClass);
        if (shapedRate >= 0) result.put("shaped-rate", shapedRate);
        if (descr != null) result.put("ifdescr", descr);
        /* TODO: vlan-range parameter can be provide too. */
        return result;
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
    public TunnelDesc(JSONObject root) {
        this.ofport = (Integer) root.get("ofport");
        this.type = (String) root.get("type");
        this.port = (String) root.get("port");
        innerVlanId = vlanId = -1;
        trafficClass = -1;
        tpid = -1;
        switch (type) {
        case "stag-ctag":
        case "ctag-ctag":
            this.tpid = (Integer) root.get("tpid");
            this.innerVlanId = (Integer) root.get("inner-vlan-id");
            /* Fall-through! */
        case "ctag":
        case "untagged":
            this.vlanId = (Integer) root.get("vlan-id");
            this.trafficClass = (Integer) root.get("traffic-class");
            break;
        default:
            break;
        }
        this.isShaped = (Boolean) root.get("is-shaped");
        shapedRate = -1;
        queueProfile = -1;
        if (isShaped) {
            this.shapedRate = (Integer) root.get("shaped-rate");
            this.queueProfile = (Integer) root.get("queue-profile");
        }
        this.operational = (Boolean) root.get("oper-state");
        this.descr = (String) root.get("ifdescr");
    }
}
