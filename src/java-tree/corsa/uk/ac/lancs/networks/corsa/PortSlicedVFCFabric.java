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

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Consumer;

import uk.ac.lancs.networks.TrafficFlow;
import uk.ac.lancs.networks.corsa.rest.CorsaREST;
import uk.ac.lancs.networks.end_points.EndPoint;
import uk.ac.lancs.networks.fabric.Bridge;
import uk.ac.lancs.networks.fabric.BridgeListener;
import uk.ac.lancs.networks.fabric.Fabric;
import uk.ac.lancs.networks.fabric.Interface;

/**
 * Manages a Corsa DP2X000-series switch by creating and maintaining a
 * single VFC whose OF ports are grouped for each logical bridge.
 * Traffic arriving on one port of a group only exits on other ports in
 * the same group. The VFC must connect to a controller that this fabric
 * can notify of new port groups. This fabric handles QoS in the tunnels
 * it attaches to the VFC, so the controller does not handle QoS.
 * 
 * @author simpsons
 */
public final class PortSlicedVFCFabric implements Fabric {
    private final InetSocketAddress controller;
    private final InterfaceManager interfaces;
    private final String netns;
    private final String subtype;
    private final CorsaREST rest;
    private final SliceControllerREST sliceRest;
    private final String partialDesc;
    private final String fullDesc;
    private final String descPrefix;

    /**
     * Create a switching fabric for a Corsa.
     * 
     * @param portCount the number of ports on the switch
     * 
     * @param maxAggregations the maximum number of supported link
     * aggregation groups
     * 
     * @param descPrefix the prefix of the description text for VFCs
     * under the control of this fabric
     * 
     * @param partialDescSuffix the suffix of the description text used
     * for new VFCs before their configuration is complete
     * 
     * @param fullDescSuffix the suffix of the description text used for
     * new VFCs as soon as their configuration is complete
     * 
     * @param subtype the VFC subtype to use when creating VFCs, e.g.,
     * <samp>ls-vpn</samp>, <samp>openflow</samp>, etc.
     * 
     * @param netns the network namespace for the controller port of a
     * new VFC if it needs to be created
     * 
     * @param controller the IP address and port number of the
     * controller used for all created VFCs
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
     * @throws KeyManagementException if there is a problem with the
     * certficate
     */
    public PortSlicedVFCFabric(int portCount, int maxAggregations,
                               String descPrefix, String partialDescSuffix,
                               String fullDescSuffix, String subtype,
                               String netns, InetSocketAddress controller,
                               URI service, X509Certificate cert,
                               String authz, URI ctrlService,
                               X509Certificate ctrlCert, String ctrlAuthz)
        throws KeyManagementException,
            NoSuchAlgorithmException {
        this.interfaces = new InterfaceManager(portCount, maxAggregations);
        this.descPrefix = descPrefix;
        this.partialDesc = descPrefix + partialDescSuffix;
        this.fullDesc = descPrefix + fullDescSuffix;
        this.subtype = subtype;
        this.netns = netns;
        this.controller = controller;
        this.sliceRest =
            new SliceControllerREST(ctrlService, ctrlCert, ctrlAuthz);
        this.rest = new CorsaREST(service, cert, authz);
    }

    @Override
    public Interface<?> getInterface(String desc) {
        return interfaces.getInterface(desc);
    }

    @Override
    public Bridge
        bridge(BridgeListener listener,
               Map<? extends EndPoint<? extends Interface<?>>, ? extends TrafficFlow> details) {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    @Override
    public void retainBridges(Collection<? extends Bridge> bridges) {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    @Override
    public int capacity() {
        return 1000;
    }

    class InternalBridge {
        final Map<EndPoint<? extends Interface<?>>, TrafficFlow> service;

        public InternalBridge(Map<? extends EndPoint<? extends Interface<?>>, ? extends TrafficFlow> service) {
            this.service = new HashMap<>(service);
        }

        private final Collection<BridgeListener> listeners = new HashSet<>();

        void inform(Consumer<BridgeListener> action) {
            assert Thread.holdsLock(PortSlicedVFCFabric.this);
            listeners.forEach(action);
        }

        void addListener(BridgeListener listener) {
            assert Thread.holdsLock(PortSlicedVFCFabric.this);
            listeners.add(listener);
        }

        void removeListener(BridgeListener listener) {
            assert Thread.holdsLock(PortSlicedVFCFabric.this);
            listeners.remove(listener);
        }
    }
}
