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
package uk.ac.lancs.networks.fabric.openflow;

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

import org.json.simple.parser.ParseException;

import uk.ac.lancs.rest.JSONEntity;
import uk.ac.lancs.rest.RESTClient;
import uk.ac.lancs.rest.RESTResponse;
import uk.ac.lancs.rest.SecureSingleCertificateHttpProvider;

/**
 * 
 * 
 * @author simpsons
 */
final class VLANCircuitControllerREST extends RESTClient {
    public VLANCircuitControllerREST(URI service, X509Certificate cert,
                                     String authz)
        throws NoSuchAlgorithmException,
            KeyManagementException {
        super(service.resolve("api/v1/"),
              SecureSingleCertificateHttpProvider.forCertificate(cert),
              authz);
    }

    private static Collection<? extends Collection<? extends VLANCircuitId>>
        decodeCircuitSets(JSONEntity ent) {
        Collection<Collection<VLANCircuitId>> result = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<List<List<Long>>> ints = ent.array;
        for (List<List<Long>> ii : ints) {
            Collection<VLANCircuitId> slice = new HashSet<>();
            for (List<Long> iii : ii)
                slice.add(new VLANCircuitId(iii));
            result.add(slice);
        }
        return result;
    }

    public
        RESTResponse<Collection<? extends Collection<? extends VLANCircuitId>>>
        getCircuitSets(long dpid) throws IOException, ParseException {
        return get(String.format("config/%016x", dpid))
            .adapt(VLANCircuitControllerREST::decodeCircuitSets);
    }

    public
        RESTResponse<Collection<? extends Collection<? extends VLANCircuitId>>>
        defineCircuitSets(long dpid,
                          Collection<? extends VLANCircuitId>... sets)
            throws IOException,
                ParseException {
        return defineCircuitSets(dpid, Arrays.asList(sets));
    }

    public
        RESTResponse<Collection<? extends Collection<? extends VLANCircuitId>>>

        defineCircuitSets(long dpid,
                          Collection<? extends Collection<? extends VLANCircuitId>> sets)
            throws IOException,
                ParseException {
        Map<String, List<List<List<Integer>>>> params = new HashMap<>();
        List<List<List<Integer>>> lists = new ArrayList<>();
        params.put("slices", lists);
        for (Collection<? extends VLANCircuitId> set : sets) {
            List<List<Integer>> list = new ArrayList<>();
            for (VLANCircuitId id : set)
                list.add(id.asList());
            lists.add(list);
        }
        return post(String.format("config/%016x", dpid), params)
            .adapt(VLANCircuitControllerREST::decodeCircuitSets);
    }
}
