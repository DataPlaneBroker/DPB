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
package uk.ac.lancs.networks.corsa;

import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonStructure;

import uk.ac.lancs.rest.client.RESTClient;
import uk.ac.lancs.rest.client.RESTResponse;
import uk.ac.lancs.rest.client.SecureSingleCertificateHttpProvider;

/**
 * Wraps a REST interface to an OpenFlow controller implementing a
 * port-sliced learning switch.
 * 
 * <p>
 * The controller runs in Python under Ryu, and is called
 * <samp>portslicer.py</samp>. It maintains a collection of internal
 * port sets per datapath, each port set implying that the identified
 * internal ports should be connected at Layer 2. No port belongs to
 * more than one set.
 * 
 * <p>
 * The ports of a set with exactly two elements are connected using
 * simple ELINE rules, i.e., traffic from one port is simply passed to
 * the other, and vice versa, with no further interaction with the
 * controller.
 * 
 * <p>
 * The ports of a set with more than two elements are connected using
 * learning-switch rules, where the controller is consulted when a MAC
 * address is seen on a port that it was not most recently seen on,
 * resulting in the removal of rules matching that address and connected
 * with the same port set. The same MAC address may be learned on two
 * ports simultaneously, if the ports belong to different sets.
 * 
 * <p>
 * No port set contains only one element. Any set left in a state with
 * only one element is deleted, and will have no rules pertaining to it.
 * 
 * <p>
 * No OpenFlow rules are created to enforce any kind of QoS on traffic
 * to or from a port. It is assumed that this is handled by an external
 * mechanism, if at all. For example, on a Corsa DP2000-series switch,
 * QoS can be enforced in the tunnel attachments that associate the
 * virtual switches' (internal) ports with the physical switch's
 * (external) ports.
 * 
 * <p>
 * The REST interface is under the virtual path
 * <samp>/slicer/api/v1/config</samp>. The next path element identifies
 * the DPID of the switch to be configured, as a 16-nibble hex code.
 * 
 * <p>
 * A <code>GET</code> or <code>POST</code> on (say)
 * <samp>/slicer/api/v1/config/0003fe289c27e8ba</samp> will yield the
 * current port sets of the switch with DPID 0003fe289c27e8ba. The
 * response takes the form of a JSON array with each element being an
 * integer array corresponding to a port set.
 * 
 * <p>
 * A <code>POST</code> on
 * <samp>/slicer/api/v1/config/0003fe289c27e8ba</samp> with a request
 * body consisting of a JSON object changes the port-set configuration.
 * 
 * <p>
 * If the JSON object contains an array called <samp>slices</samp>,
 * whose elements are arrays of integers, these are interpreted as port
 * sets, and are created in the switch.
 * 
 * <p>
 * An optional array called <samp>disused</samp> may contain ports to be
 * removed from their sets. Ports belonging to existing sets are first
 * removed from them, and the OpenFlow rules of any resulting port sets
 * with only one element are removed altogether.
 * 
 * <p>
 * An optional object <samp>learn</samp> allows a MAC address
 * <samp>mac</samp> to be artificially learned on a specific port
 * <samp>port</samp>. An optional <samp>timeout</samp> sets the idle
 * timeout (in seconds) for the rule that prevents appearance of the MAC
 * address on its port from triggering contact with the controller. This
 * feature is intended only for debugging.
 * 
 * <p>
 * An optional boolean <samp>dhcp</samp> injects (false) or removes
 * (true) rules applied to all traffic that prevent DHCP from traversing
 * the switch. This is intended as a work-around for use with OSM which
 * does not yet have the logic to prevent two sites from using the same
 * IP address pool.
 * 
 * <p>
 * A complete example:
 * 
 * <pre>
 * &#123;
 *   "slices": [ [1,2,3], [4,5], [6,7,8,9] ],
 *   "disused": [ 10, 11, 12, 13 ],
 *   "learn": &#123;
 *     "mac": "00:11:22:33:44:55",
 *     "port": 7,
 *     "timeout": 30
 *   &#125;,
 *   "dhcp": False
 * &#125;
 * </pre>
 * 
 * <p>
 * This class only generates requests containing <samp>slices</samp>.
 * For its current use case, it does not need to send
 * <samp>disused</samp>, as the corresponding rules will be removed when
 * the internal ports are detached.
 * 
 * @author simpsons
 */
public final class SliceControllerREST extends RESTClient {
    /**
     * Wrap a REST interface for the <samp>portslicer.py</samp>
     * controller.
     * 
     * @param service the prefix of the interface's URL, including a
     * trailing slash; The string <samp>api/v1/</samp> will be resolved
     * against it.
     * 
     * @param cert a certificate used to verify the controller over
     * HTTPS, or {@code null} if only HTTP is to be used
     * 
     * @param authz a string to pass as the value of the
     * <samp>Authorization</samp> header, or {@code null} if no such
     * header should be sent
     * 
     * @throws NoSuchAlgorithmException if SSL is an unknown context
     * type
     * 
     * @throws KeyManagementException if there's is a problem with the
     * key
     */
    public SliceControllerREST(URI service, X509Certificate cert,
                               String authz)
        throws NoSuchAlgorithmException,
            KeyManagementException {
        super(service.resolve("api/v1/"),
              SecureSingleCertificateHttpProvider.forCertificate(cert),
              authz);
    }

    private static Collection<? extends BitSet>
        decodePortSets(JsonStructure ent) {
        Collection<BitSet> result = new ArrayList<>();
        for (JsonArray val1 : ((JsonArray) ent)
            .getValuesAs(JsonArray.class)) {
            BitSet bs = new BitSet();
            for (JsonNumber val2 : val1.getValuesAs(JsonNumber.class))
                bs.set(val2.intValue());
            result.add(bs);
        }
        return result;
    }

    /**
     * Get the current configuration of a switch.
     * 
     * @param dpid the datapath identifier
     * 
     * @return a collection of sets of internal port numbers identifying
     * which ports are connected to each other
     * 
     * @throws IOException if there was an error in communicating with
     * the controller
     */
    public RESTResponse<Collection<? extends BitSet>> getPortSets(long dpid)
        throws IOException {
        return get(String.format("config/%016x", dpid))
            .adapt(SliceControllerREST::decodePortSets);
    }

    /**
     * Add new port sets to the configuration. If any of the mentioned
     * ports are in use, they are removed from their current sets first.
     * 
     * @default This method calls
     * {@link #definePortSets(long, Collection)}, having converted its
     * array of {@link BitSet}s to a {@link Collection}.
     * 
     * @param dpid the datapath identifier
     * 
     * @param sets multiple internal port sets that should be connected
     * together at layer 2
     * 
     * @return a collection of sets of internal port numbers identifying
     * which ports are connected to each other after the changes are
     * applied
     * 
     * @throws IOException if there was an error in communicating with
     * the controller
     */
    public RESTResponse<Collection<? extends BitSet>>
        definePortSets(long dpid, BitSet... sets) throws IOException {
        return definePortSets(dpid, Arrays.asList(sets));
    }

    /**
     * Add new port sets to the configuration. If any of the mentioned
     * ports are in use, they are removed from their current sets first.
     * 
     * @param dpid the datapath identifier
     * 
     * @param sets multiple internal port sets that should be connected
     * together at layer 2
     * 
     * @return a collection of sets of internal port numbers identifying
     * which ports are connected to each other after the changes are
     * applied
     * 
     * @throws IOException if there was an error in communicating with
     * the controller
     */
    public RESTResponse<Collection<? extends BitSet>>
        definePortSets(long dpid, Collection<? extends BitSet> sets)
            throws IOException {
        Map<String, List<List<Integer>>> params = new HashMap<>();
        List<List<Integer>> lists = new ArrayList<>();
        params.put("slices", lists);
        for (BitSet set : sets) {
            List<Integer> list = new ArrayList<>();
            for (int i : BitSetIterable.of(set))
                list.add(i);
            lists.add(list);
        }
        return post(String.format("config/%016x", dpid), params)
            .adapt(SliceControllerREST::decodePortSets);
    }
}
