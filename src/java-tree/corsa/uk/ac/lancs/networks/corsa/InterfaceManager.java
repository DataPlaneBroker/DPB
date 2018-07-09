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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.ac.lancs.networks.corsa.rest.TunnelDesc;
import uk.ac.lancs.networks.end_points.EndPoint;
import uk.ac.lancs.networks.fabric.Interface;

/**
 * 
 * 
 * @author simpsons
 */
final class InterfaceManager {
    private final AllPortsInterface allPorts;
    private final AllAggregationsInterface allAggregations;

    public InterfaceManager(int portCount, int maxGroups) {
        this.allPorts = new AllPortsInterface(portCount);
        this.allAggregations = new AllAggregationsInterface(maxGroups);
    }

    private static final Pattern INTERFACE_PATTERN =
        Pattern.compile("^(?<type>(?:lag|phys|lag|phys|))"
            + "(?:\\.?(?<num>[0-9]+)(?<dt>x2)?(?:\\.(?<outer>[0-9]+))?)$");

    public CorsaInterface getInterface(String config) {
        Matcher m = INTERFACE_PATTERN.matcher(config);
        if (!m.matches())
            throw new IllegalArgumentException("bad interface: " + config);
        CorsaInterface result;
        switch (m.group("type")) {
        case "lag":
            result = allAggregations;
            break;
        case "":
        case "phys":
            result = allPorts;
            break;
        default:
            throw new AssertionError("unreachable");
        }
        if (m.group("num") != null) {
            TagKind kind = null;
            if ("x2".equals(m.group("dt"))) kind = TagKind.VLAN_STAG_CTAG;
            result = result.tag(kind, Integer.parseInt(m.group("num")));
            if (m.group("outer") != null) {
                result = result.tag(null, Integer.parseInt(m.group("outer")));
            }
        }
        return result;
    }

    public EndPoint<Interface> getEndPoint(TunnelDesc tun) {
        /* Parse the port section. */
        CorsaInterface iface;
        int ifacenum;
        if (tun.port.startsWith("lag")) {
            iface = allAggregations;
            ifacenum = Integer.parseInt(tun.port.substring(3));
        } else {
            iface = allPorts;
            ifacenum = Integer.parseInt(tun.port);
        }

        /* Recognize abstract ports called "phys" or "lag", so that
         * "phys:3" identifies port 3, and "lag:4" identifies link
         * aggregation group 4. In these cases, the end-point label is
         * not a VLAN id. */
        if (tun.vlanId < 0) return iface.getEndPoint(ifacenum);

        /* Recognize single-tagged tunnels. */
        if (tun.innerVlanId < 0) return iface
            .tag(TagKind.ENUMERATION, ifacenum).getEndPoint(tun.vlanId);

        /* Recognize double-tagged tunnels. */
        return iface.tag(TagKind.ENUMERATION, ifacenum)
            .tag(TagKind.VLAN_STAG, tun.vlanId).getEndPoint(tun.innerVlanId);
    }
}
