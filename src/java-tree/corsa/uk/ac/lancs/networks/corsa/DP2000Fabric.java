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

import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Map;

import org.json.simple.parser.ParseException;

import uk.ac.lancs.networks.TrafficFlow;
import uk.ac.lancs.networks.corsa.rest.BridgeDesc;
import uk.ac.lancs.networks.corsa.rest.BridgesDesc;
import uk.ac.lancs.networks.corsa.rest.CorsaREST;
import uk.ac.lancs.networks.corsa.rest.RESTResponse;
import uk.ac.lancs.networks.corsa.rest.TunnelDesc;
import uk.ac.lancs.networks.end_points.EndPoint;
import uk.ac.lancs.networks.fabric.Bridge;
import uk.ac.lancs.networks.fabric.BridgeListener;
import uk.ac.lancs.networks.fabric.Fabric;
import uk.ac.lancs.networks.fabric.Interface;

/**
 * Manages a Corsa DP2X00-series switch as a fabric.
 * 
 * @author simpsons
 */
public class DP2000Fabric implements Fabric {
    /**
     * Bridges whose descriptions do not begin with this prefix are to
     * be considered outside the control of this object.
     */
    private final String descPrefix = "initiate:";

    /**
     * Bridges whose descriptions have this form should be deleted at
     * start-up. Their configuration was not completed.
     */
    private final String partialDesc = descPrefix + "partial";

    /**
     * Bridges whose descriptions have this form should be retained as
     * persistent state.
     */
    private final String fullDesc = descPrefix + "full";

    private final CorsaREST rest;

    /**
     * Create a switching fabric for a Corsa.
     * 
     * @param service the REST API URI for the Corsa
     * 
     * @param cert a certificate to check against the Corsa REST API
     * 
     * @param authz an authorization token obtained from the Corsa
     * 
     * @throws NoSuchAlgorithmException if there is no SSL support in
     * this implementation
     * 
     * @throws NoSuchAlgorithmException if there is a problem with the
     * certficate
     */
    public DP2000Fabric(URI service, X509Certificate cert, String authz)
        throws KeyManagementException,
            NoSuchAlgorithmException {
        this.rest = new CorsaREST(service, cert, authz);
    }

    class InternalBridge {
        private final int number;

        public InternalBridge(int number) {
            this.number = number;
        }

        public String name() {
            return "br" + number;
        }
    }

    public void init() throws IOException, ParseException {
        /* Contact the switch, and get information on all bridges. */
        RESTResponse<Collection<String>> bridges = rest.getBridgeNames();
        for (String bridgeName : bridges.message) {
            RESTResponse<BridgeDesc> bridgeInfo =
                rest.getBridgeDesc(bridgeName);

            /* Bridges not under our control should be ignored. */
            if (bridgeInfo.message.descr == null
                || !bridgeInfo.message.descr.startsWith(descPrefix)) continue;

            /* Destroy bridges that were only partially configured from
             * last time. */
            if (bridgeInfo.message.descr.equals(partialDesc))
                rest.destroyBridge(bridgeName);

            RESTResponse<Map<Integer, TunnelDesc>> tunnels =
                rest.getTunnels(bridgeName);
        }
    }

    @Override
    public Interface getInterface(String desc) {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    @Override
    public Bridge
        bridge(BridgeListener listener,
               Map<? extends EndPoint<Interface>, ? extends TrafficFlow> details) {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    @Override
    public void retainBridges(Collection<? extends Bridge> bridges) {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    @Override
    public int capacity() {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }
}
