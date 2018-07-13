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

import uk.ac.lancs.networks.circuits.Circuit;
import uk.ac.lancs.networks.corsa.rest.TunnelDesc;
import uk.ac.lancs.networks.fabric.Interface;
import uk.ac.lancs.networks.fabric.TagKind;

/**
 * Maps interface definitions to entities in the Corsa interface
 * hierarchy.
 * 
 * <p>
 * The root of the hierarchy consists of two abstract interfaces
 * representing all physical ports (<samp>phys</samp>) and all
 * link-aggregation groups (LAGs; <samp>lag</samp>). circuits derived
 * from these represent specific ports or LAGs with no encapsulation.
 * These are intended to be used when access to a connected device is
 * not possible with any kind of encapsulation, and so multiple ports or
 * LAGs must be physically connected to it as a last resort.
 * 
 * <p>
 * Subinterfaces of <samp>phys</samp> and <samp>lag</samp>, such as
 * <samp>phys.3</samp> or <samp>lag.4</samp> identify specific ports or
 * LAGs. circuits of these interfaces are ctagged VLANs. Interface
 * definitions such as <samp>4</samp> and <samp>phys4</samp> are aliases
 * for <samp>phys.4</samp>. Similarly, <samp>lag4</samp> is an alias for
 * <samp>lag.4</samp>.
 * 
 * <p>
 * Subinterfaces such as <samp>phys.3x2</samp> or <samp>lag.4x2</samp>
 * also identify specific ports and LAGs. However, their circuits are
 * 24-bit labels, with the top 12 bits used to form the outer stag VLAN
 * id, and the bottom 12 to form the inner ctag. These interfaces have
 * no subinterfaces of their own.
 * 
 * <p>
 * Interfaces such as <samp>phys.3</samp> or <samp>lag.4</samp> can also
 * have subinterfaces such as <samp>phys.3.100</samp> or
 * <samp>lag.4.200</samp>. Their circuits also represent double-tagged
 * tunnels, but keep the stag component hidden from the user. These
 * interfaces have no subinterfaces of their own.
 * 
 * @author simpsons
 */
public final class InterfaceManager {
    private final AllPortsInterface allPorts;
    private final AllAggregationsInterface allAggregations;

    /**
     * Create an interface/circuit mapping.
     * 
     * @param portCount the number of physical ports of the represented
     * switch
     * 
     * @param maxGroups the highest number link-aggregation group
     * possible on the switch
     */
    public InterfaceManager(int portCount, int maxGroups) {
        this.allPorts = new AllPortsInterface(portCount);
        this.allAggregations = new AllAggregationsInterface(maxGroups);
    }

    private static final Pattern INTERFACE_PATTERN =
        Pattern.compile("^(?<type>(?:lag|phys|))"
            + "(?:\\.?(?<num>[0-9]+)(?<dt>x2)?(?:\\.(?<outer>[0-9]+))?)$");

    /**
     * Get the interface with the given definition.
     * 
     * @param config the interface definition
     * 
     * @return the interface matching the definition
     * 
     * @throws IllegalArgumentException if the definition is not
     * recognized
     */
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

    /**
     * Get a fabric circuit from a Corsa tunnel description. This never
     * returns a circuit for a double-tagged interface such as
     * <samp>phys.3x2</samp>. Instead, the circuit's label will be the
     * ctag VLAN id, and the circuit's interface will be return
     * <samp>phys.3.<var>XXX</var></samp>, giving the stag VLAN id,
     * i.e., the top 12 bits of the label if the circuit were to be
     * expressed as a member of <samp>phys.3x2</samp>. This happens
     * because the tunnel description is identical in both cases.
     * 
     * @param tun the tunnel description, as obtained from a REST call
     * to the switch
     * 
     * @return the circuit
     */
    public Circuit<? extends Interface<?>> getCircuit(TunnelDesc tun) {
        /* Parse the port section. */
        final CorsaInterface iface;
        final int ifacenum;
        if (tun.port.startsWith("lag")) {
            iface = allAggregations;
            ifacenum = Integer.parseInt(tun.port.substring(3));
        } else {
            iface = allPorts;
            ifacenum = Integer.parseInt(tun.port);
        }

        /* Recognize abstract ports called "phys" or "lag", so that
         * "phys:3" identifies port 3, and "lag:4" identifies link
         * aggregation group 4. In these cases, the circuit label is not
         * a VLAN id. */
        if (tun.vlanId < 0) return iface.circuit(ifacenum);

        /* Recognize single-tagged tunnels. */
        if (tun.innerVlanId < 0) return iface
            .tag(TagKind.ENUMERATION, ifacenum).circuit(tun.vlanId);

        /* Recognize double-tagged tunnels. */
        return iface.tag(TagKind.ENUMERATION, ifacenum)
            .tag(TagKind.VLAN_STAG, tun.vlanId).circuit(tun.innerVlanId);
    }

    /**
     * Resolve a circuit to its canonical form.
     * 
     * @throws ClassCastException if the interface is of the wrong type
     * 
     * @param circuit the circuit to be resolved
     * 
     * @return the input circuit resolved to its canonical form
     */
    public Circuit<? extends Interface<?>>
        resolve(Circuit<? extends Interface<?>> circuit) {
        CorsaInterface iface = (CorsaInterface) circuit.getBundle();
        return iface.resolve(circuit.getLabel());
    }
}
