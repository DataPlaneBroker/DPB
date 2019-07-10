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
package uk.ac.lancs.networks.openflow;

import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonStructure;

import uk.ac.lancs.networks.TrafficFlow;
import uk.ac.lancs.rest.client.RESTClient;
import uk.ac.lancs.rest.client.RESTResponse;
import uk.ac.lancs.rest.client.SecureSingleCertificateHttpProvider;

/**
 * Wraps a REST interface to an OpenFlow controller implementing a
 * VLAN-circuit sliced learning switch.
 * 
 * <p>
 * The controller runs in Python under Ryu, and is called
 * <samp>tupleslicer.py</samp>. It maintains a collection of internal
 * circuit sets per datapath, each circuit set implying that the
 * identified circuit should be connected at Layer 2. No circuit belongs
 * to more than one set.
 * 
 * <p>
 * A circuit id ({@link VLANCircuitId}) identifies a port, and zero, one
 * or two VLAN ids. When one VLAN id is specified (e.g., (6,300)), the
 * circuit id identifies single-tagged traffic (using CTAG (with the
 * value 300)) on the given port (6). When two VLAN ids are specified
 * (e.g., (6,300,20), the traffic is double-tagged with an outer STAG
 * (300) and an inner CTAG (20), on the given port (6). When no VLAN ids
 * are specified, the traffic is untagged.
 * 
 * <p>
 * Some circuits overlap. It is not possible to distinguish untagged
 * traffic from traffic whose payload contains a tag, so an untagged
 * circuit id like (6) overlaps any tagged circuit on the same port,
 * such as (6,300) and (6,300,20). While these latter circuit ids are
 * distinguishable from each other (because one uses CTAG(300) while the
 * other uses STAG(300)), OpenFlow is currently unable to distinguish
 * them, so they also overlap each other. As a result, if the controller
 * is asked to implement a circuit set that includes (say) (6,300), it
 * will first remove all circuits matching (6) and (6,300,*).
 * 
 * <p>
 * The circuits of a set with exactly two elements are connected using
 * simple ELINE rules, i.e., traffic from one circuit is simply passed
 * to the other after appropriate retagging, and vice versa, with no
 * further interaction with the controller.
 * 
 * <p>
 * The circuits of a set with more than two elements are connected using
 * learning-switch rules, where the controller is consulted when a MAC
 * address is seen on a circuit that it was not most recently seen on,
 * resulting in the removal of rules matching that address and connected
 * with the same circuit set. The same MAC address may be learned on two
 * circuits simultaneously, if the circuits belong to different sets.
 * 
 * <p>
 * No circuit set contains only one element. Any set left in a state
 * with only one element is deleted, and will have no rules pertaining
 * to it.
 * 
 * <p>
 * Metering actions/instructions are applied. Each circuit has an
 * associated ingress meter, applied as its traffic enters the switch,
 * and an egress meter, applied as it exits. A meter's rate is
 * optionally supplied with the corresponding circuit id. If none is
 * specified, the meter's rate is set to an unattainably high value,
 * effectively disabling metering.
 * 
 * <p>
 * The REST interface is under the virtual path
 * <samp>/slicer/api/v1/config</samp>. The next path element identifies
 * the DPID of the switch to be configured, as a 16-nibble hex code.
 * Circuit ids are represented as arrays of 1-3 integers.
 * 
 * <p>
 * A <code>GET</code> or <code>POST</code> on (say)
 * <samp>/slicer/api/v1/config/0003fe289c27e8ba</samp> will yield the
 * current circuit sets of the switch with DPID 0003fe289c27e8ba. The
 * response takes the form of a JSON array with each element being an
 * array of circuits corresponding to a circuit set.
 * 
 * <p>
 * A <code>POST</code> on
 * <samp>/slicer/api/v1/config/0003fe289c27e8ba</samp> with a request
 * body consisting of a JSON object changes the circuit-set
 * configuration.
 * 
 * <p>
 * If the JSON object contains an array called <samp>slices</samp>,
 * whose elements are arrays of objects of <samp>circuit</samp> (a
 * circuit id as an array), <samp>egress-bw</samp> (the optional egress
 * bandwidth as a real number in Mbps), and <samp>ingress-bw</samp> (the
 * optional ingress bandwidth as a real number in Mbps), these are
 * interpreted as circuit sets, and are created in the switch.
 * 
 * <p>
 * An optional array called <samp>disused</samp> may contain circuits to
 * be removed from their sets. Circuits belonging to existing sets are
 * first removed from them, and the OpenFlow rules of any resulting
 * circuit sets with only one element are removed altogether.
 * 
 * <p>
 * An optional object <samp>learn</samp> allows a MAC address
 * <samp>mac</samp> to be artificially learned on a specific circuit
 * <samp>tuple</samp>. An optional <samp>timeout</samp> sets the idle
 * timeout (in seconds) for the rule that prevents appearance of the MAC
 * address on its circuit from triggering contact with the controller.
 * This feature is intended only for debugging.
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
 *   "slices": [
 *     [
 *       &#123;
 *         "circuit": [6,60,600],
 *         "ingress-bw": 30.3,
 *         "egress-bw": 19
 *       &#125;,
 *       &#123;
 *         "circuit": [3,30],
 *         "egress-bw": 14.2
 *       &#125;,
 *       &#123;
 *         "circuit": [2],
 *         "ingress-bw": 18.7,
 *       &#125;
 *     ],
 *     [
 *       &#123;
 *         "circuit": [3,40],
 *         "ingress-bw": 30.3,
 *         "egress-bw": 19
 *       &#125;,
 *       &#123;
 *         "circuit": [4,40],
 *         "egress-bw": 14.2
 *       &#125;
 *     ],
 *   ],
 *   "disused": [
 *     [6,60,601],
 *     [3,50],
 *     [1],
 *   ],
 *   "learn": &#123;
 *     "mac": "00:11:22:33:44:55",
 *     "tuple": [3,30],
 *     "timeout": 30
 *   &#125;,
 *   "dhcp": False
 * &#125;
 * </pre>
 * 
 * <p>
 * This class only generates requests containing <samp>slices</samp> or
 * <samp>disused</samp>.
 * 
 * @author simpsons
 */
public final class VLANCircuitControllerREST extends RESTClient {
    /**
     * Create a REST client to talk to a controller.
     * 
     * @param service the URI of the service; <samp>api/v1/</samp> is
     * appended internally.
     * 
     * @param cert the certificate for verifying the identity of the
     * service, or {@code null} if not required
     * 
     * @param authz an authorization string to include with each
     * request, or {@code null} if not required
     * 
     * @throws KeyManagementException if there is a problem with the key
     * 
     * @throws NoSuchAlgorithmException if SSL is an unknown context
     * type
     */
    public VLANCircuitControllerREST(URI service, X509Certificate cert,
                                     String authz)
        throws NoSuchAlgorithmException,
            KeyManagementException {
        super(service.resolve("api/v1/"),
              SecureSingleCertificateHttpProvider.forCertificate(cert),
              authz);
    }

    private static Collection<? extends Collection<? extends VLANCircuitId>>
        decodeCircuitSets(JsonStructure ent) {
        Collection<Collection<VLANCircuitId>> result = new ArrayList<>();
        JsonArray array = (JsonArray) ent;
        for (JsonArray val1 : array.getValuesAs(JsonArray.class)) {
            Collection<VLANCircuitId> slice = new HashSet<>();
            for (JsonArray val2 : val1.getValuesAs(JsonArray.class)) {
                List<Integer> ints = val2.getValuesAs(JsonNumber.class)
                    .stream().map(JsonNumber::intValue)
                    .collect(Collectors.toList());
                slice.add(new VLANCircuitId(ints));
            }
            result.add(slice);
        }
        return result;
    }

    /**
     * Get the current set of circuit id sets that identify slices of
     * the switch.
     * 
     * @param dpid the datapath id of the switch being controlled
     * 
     * @return the REST response, including a set of sets of circuit ids
     * 
     * @throws IOException if there was an I/O in talking with the
     * controller
     */
    public final
        RESTResponse<Collection<? extends Collection<? extends VLANCircuitId>>>
        getCircuitSets(long dpid) throws IOException {
        return get(String.format("config/%016x", dpid))
            .adapt(VLANCircuitControllerREST::decodeCircuitSets);
    }

    /**
     * Add a new set of circuit ids to form a new slice. Overlaps or
     * conflicts with circuits of other slices will cause those circuits
     * to be removed from those slices.
     * 
     * @param dpid the datapath id of the switch being controlled
     * 
     * @param set the additional set of circuit ids to apply, with their
     * bandwidth mappings
     * 
     * @return the REST response, including a set of sets of circuit ids
     * 
     * @throws IOException if there was an I/O in talking with the
     * controller
     */
    public final
        RESTResponse<Collection<? extends Collection<? extends VLANCircuitId>>>
        defineCircuitSet(long dpid,
                         Map<? extends VLANCircuitId, ? extends TrafficFlow> set)
            throws IOException {
        return defineCircuitSets(dpid, Arrays.asList(set));
    }

    /**
     * Add new sets of circuit ids to form a new slice. Overlaps or
     * conflicts with circuits of other slices will cause those circuits
     * to be removed from those slices.
     * 
     * @param dpid the datapath id of the switch being controlled
     * 
     * @param sets the additional sets of circuit ids to apply, with
     * their bandwidth mappings
     * 
     * @return the REST response, including a set of sets of circuit ids
     * 
     * @throws IOException if there was an I/O in talking with the
     * controller
     */
    public final
        RESTResponse<Collection<? extends Collection<? extends VLANCircuitId>>>
        defineCircuitSets(long dpid,
                          Collection<? extends Map<? extends VLANCircuitId, ? extends TrafficFlow>> sets)
            throws IOException {
        Map<String, List<Map<String, Object>>> params = new HashMap<>();
        List<Map<String, Object>> lists = new ArrayList<>();
        params.put("slices", lists);
        for (Map<? extends VLANCircuitId, ? extends TrafficFlow> slice : sets) {
            Map<String, Object> oof = new HashMap<>();
            for (Map.Entry<? extends VLANCircuitId, ? extends TrafficFlow> entry : slice
                .entrySet()) {
                oof.put("ingress-bw", entry.getValue().ingress);
                oof.put("egress-bw", entry.getValue().egress);
                oof.put("circuit", entry.getKey().asList());
            }
            lists.add(oof);
        }
        return post(String.format("config/%016x", dpid), params)
            .adapt(VLANCircuitControllerREST::decodeCircuitSets);
    }

    /**
     * Discard circuit ids.
     * 
     * @param dpid the datapath id of the switch being controlled
     * 
     * @param circuits the circuits to be removed
     * 
     * @return the REST response
     * 
     * @throws IOException if there was an I/O in talking with the
     * controller
     */
    public final RESTResponse<Void>
        discardCircuits(long dpid,
                        Collection<? extends VLANCircuitId> circuits)
            throws IOException {
        List<List<Integer>> list = new ArrayList<>();
        for (VLANCircuitId c : circuits)
            list.add(c.asList());
        Map<String, List<List<Integer>>> params = new HashMap<>();
        params.put("disused", list);
        return post(String.format("config/%016x", dpid), params)
            .adapt(s -> null);
    }
}
