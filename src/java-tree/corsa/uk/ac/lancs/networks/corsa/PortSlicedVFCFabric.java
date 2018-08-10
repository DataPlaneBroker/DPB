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
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

import uk.ac.lancs.networks.ServiceResourceException;
import uk.ac.lancs.networks.TrafficFlow;
import uk.ac.lancs.networks.corsa.rest.BridgeDesc;
import uk.ac.lancs.networks.corsa.rest.ControllerConfig;
import uk.ac.lancs.networks.corsa.rest.CorsaREST;
import uk.ac.lancs.networks.corsa.rest.Meter;
import uk.ac.lancs.networks.corsa.rest.ReplaceBridgeDescription;
import uk.ac.lancs.networks.corsa.rest.TunnelDesc;
import uk.ac.lancs.networks.fabric.Bridge;
import uk.ac.lancs.networks.fabric.BridgeListener;
import uk.ac.lancs.networks.fabric.Channel;
import uk.ac.lancs.networks.fabric.Fabric;
import uk.ac.lancs.networks.fabric.Interface;
import uk.ac.lancs.rest.RESTResponse;

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
    private String bridgeId;
    private long dpid;

    private final SliceControllerREST sliceRest;
    private final String partialDesc;
    private final String fullDesc;
    private final String descPrefix;

    /**
     * Maps the circuit description of each tunnel attachment to the OF
     * port of our VFC. The circuit is in canonical form.
     */
    private final Map<Channel, Integer> circuitToPort = new HashMap<>();

    /**
     * Maps each OF port in use on our VFC to the circuit description of
     * the tunnel attached to it. The circuit is in canonical form.
     */
    private final NavigableMap<Integer, Channel> portToCircuit =
        new TreeMap<>();

    /**
     * Indexes all bridge slices by their circuit sets. The circuits are
     * in canonical form.
     */
    private final Map<Collection<Channel>, BridgeSlice> bridgesByCircuitSet =
        new HashMap<>();

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
     * @param cert a certificate to check against the Corsa REST API, or
     * {@code null} if not required
     * 
     * @param authz an authorization to use with the Corsa REST API, or
     * {@code null} if not required
     * 
     * @param ctrlService the REST API URI for the controller
     * 
     * @param ctrlCert a certificate to check against the controller
     * REST API, or {@code null} if not required
     * 
     * @param ctrlAuthz an authorization token to use with the
     * controller REST API, or {@code null} if not required
     * 
     * @throws NoSuchAlgorithmException if there is no SSL support in
     * this implementation
     * 
     * @throws KeyManagementException if there is a problem with the
     * certficate
     * 
     * @throws IOException if there was an I/O in contacting the
     * controller
     */
    public PortSlicedVFCFabric(int portCount, int maxAggregations,
                               String descPrefix, String partialDescSuffix,
                               String fullDescSuffix, String subtype,
                               String netns, InetSocketAddress controller,
                               URI service, X509Certificate cert,
                               String authz, URI ctrlService,
                               X509Certificate ctrlCert, String ctrlAuthz)
        throws KeyManagementException,
            NoSuchAlgorithmException,
            IOException {
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

        /* Go through all VFCs whose descriptions match our configured
         * prefix. Destroy them unless they match our full name. */
        cleanUpVFCs();

        /* Create a bridge if it was not already established. */
        establishVFC();

        /* Get the VFC's DPID (or set it? TODO). */
        {
            RESTResponse<BridgeDesc> descRsp = rest.getBridgeDesc(bridgeId);
            if (descRsp.code != 200)
                throw new RuntimeException("bridge info failure: "
                    + descRsp.code);
            this.dpid = descRsp.message.dpid;
        }

        /* Load the mapping between OF port and circuit. */
        loadMapping();

        /* Get port sets from the controller, and create corresponding
         * bridge entities. */
        collectPortSets();
    }

    @Override
    public Interface getInterface(String desc) {
        return interfaces.getInterface(desc);
    }

    private class BridgeSlice {
        private final Map<Channel, TrafficFlow> service;

        BridgeSlice(Map<? extends Channel, ? extends TrafficFlow> details) {
            this.service = new HashMap<>(details);
        }

        BridgeSlice(Collection<? extends Channel> circuits) {
            this.service = new HashMap<>();
            for (Channel circuit : circuits)
                service.put(circuit, TrafficFlow.of(0.0, 0.0));
            started = true;
        }

        void stop() {
            assert Thread.holdsLock(PortSlicedVFCFabric.this);

            /* Detach tunnels from ports. */
            for (Channel circuit : service.keySet()) {
                /* Find which OF port it is attached to, and detach
                 * it. */
                int ofport = circuitToPort.getOrDefault(circuit, 0);
                if (ofport <= 0) continue;
                try {
                    rest.detachTunnel(bridgeId, ofport);
                } catch (IOException e) {
                    /* Perhaps we don't care by this stage. */
                }
            }

            inform(BridgeListener::destroyed);
            listeners.clear();
        }

        boolean started = false;

        void start() {
            assert Thread.holdsLock(PortSlicedVFCFabric.this);
            if (!started) {
                try {
                    /* Allocate each circuit to an OF port, attach them,
                     * and set the QoS. */
                    BitSet ofPorts = new BitSet();
                    for (Map.Entry<? extends Channel, ? extends TrafficFlow> entry : service
                        .entrySet()) {
                        TrafficFlow flow = entry.getValue();
                        Channel circuit = entry.getKey();

                        /* Choose the next available OF port, and mark
                         * it as in use. */
                        final int ofport = nextFreePort();
                        ofPorts.set(ofport);
                        assert circuit != null;
                        portToCircuit.put(ofport, circuit);
                        circuitToPort.put(circuit, ofport);

                        /* Attach the circuit to the OF port, setting
                         * the outgoing QoS. */
                        TunnelDesc desc = new TunnelDesc();
                        CorsaInterface iface =
                            (CorsaInterface) circuit.getInterface();
                        iface.configureTunnel(desc, circuit.getLabel())
                            .shapedRate(flow.egress).ofport(ofport);
                        rest.attachTunnel(bridgeId, desc);

                        /* Set the incoming QoS of the tunnel. */
                        rest.patchTunnel(bridgeId, ofport,
                                         Meter.cir(flow.ingress * 1024.0),
                                         Meter.cbs(10));
                    }

                    /* Tell the controller of the new OF port set. */
                    RESTResponse<Collection<? extends BitSet>> defRsp =
                        sliceRest.definePortSets(dpid, ofPorts);
                    if (defRsp.code != 200) {
                        ServiceResourceException t =
                            new ServiceResourceException("failed to set slice port set");
                        for (BridgeSlice slice : bridgesByCircuitSet
                            .values()) {
                            slice.inform(l -> l.error(t));
                            slice.stop();
                        }
                    }
                } catch (IOException ex) {
                    ServiceResourceException t =
                        new ServiceResourceException("failed to start slice",
                                                     ex);
                    for (BridgeSlice slice : bridgesByCircuitSet.values()) {
                        slice.inform(l -> l.error(t));
                        slice.stop();
                    }
                }

                started = true;
            }
            inform(BridgeListener::created);
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
    }

    class BridgeRef implements Bridge {
        final BridgeSlice internal;

        BridgeRef(BridgeSlice internal) {
            this.internal = internal;
        }

        @Override
        public void start() {
            synchronized (PortSlicedVFCFabric.this) {
                internal.start();
            }
        }
    }

    @Override
    public synchronized Bridge
        bridge(BridgeListener listener,
               Map<? extends Channel, ? extends TrafficFlow> details) {
        System.err.printf("%nShared bridge, original circuits: %s%n",
                          details.keySet());
        /* Resolve the circuits to their canonical forms. */
        Map<Channel, TrafficFlow> resolvedDetails = new HashMap<>();
        for (Map.Entry<? extends Channel, ? extends TrafficFlow> entry : details
            .entrySet())
            resolvedDetails.put(interfaces.resolve(entry.getKey()),
                                entry.getValue());
        details = resolvedDetails;
        System.err.printf("%nShared bridge, resolved circuits: %s%n",
                          details.keySet());

        BridgeSlice intern = bridgesByCircuitSet.get(details.keySet());
        if (intern == null) {
            intern = new BridgeSlice(details);
            bridgesByCircuitSet.put(new HashSet<>(details.keySet()), intern);
        }
        intern.addListener(listener);
        return new BridgeRef(intern);
    }

    @Override
    public synchronized void
        retainBridges(Collection<? extends Bridge> bridges) {
        /* Filter the references to consider only our own. */
        Collection<BridgeSlice> slices = new HashSet<>();
        for (Bridge br : bridges) {
            if (!(br instanceof BridgeRef)) continue;
            BridgeRef ref = (BridgeRef) br;
            BridgeSlice slice = ref.internal;
            slices.add(slice);
        }

        /* Gather all the interface circuits of the listed bridges. */
        Collection<Channel> retainedCircuits = new HashSet<>();
        for (BridgeSlice slice : slices)
            retainedCircuits.addAll(slice.service.keySet());

        /* Get information on all the tunnel attachments for our VFC.
         * Identify the OF ports that are no longer needed, and detach
         * them. This will trigger the OF controller to revise its
         * rules. */
        for (Iterator<Channel> iter = portToCircuit.values().iterator(); iter
            .hasNext();) {
            Channel circuit = iter.next();
            assert circuit != null;
            if (retainedCircuits.contains(circuit)) continue;
            int ofport = circuitToPort.remove(circuit);
            iter.remove();

            try {
                rest.detachTunnel(bridgeId, ofport);
            } catch (IOException e) {
                ServiceResourceException t =
                    new ServiceResourceException("error talking"
                        + " to switch", e);
                bridgesByCircuitSet.values()
                    .forEach(s -> s.inform(l -> l.error(t)));
                bridgesByCircuitSet.values().forEach(BridgeSlice::stop);
                bridgesByCircuitSet.clear();
                return;
            }
        }

        /* Remove the bridges that weren't retained. */
        Collection<BridgeSlice> dying =
            new HashSet<>(bridgesByCircuitSet.values());
        dying.removeAll(slices);
        bridgesByCircuitSet.values().retainAll(slices);
        dying.forEach(BridgeSlice::stop);
    }

    @Override
    public int capacity() {
        return 1000;
    }

    /**
     * Refresh the mapping between circuit and port.
     * 
     * @throws IOException if there was an I/O error in fetching the
     * port information
     */
    private void loadMapping() throws IOException {
        circuitToPort.clear();
        portToCircuit.clear();
        RESTResponse<Map<Integer, TunnelDesc>> tinf =
            rest.getTunnels(bridgeId);
        for (Map.Entry<Integer, TunnelDesc> entry : tinf.message.entrySet()) {
            Channel circuit = interfaces.getCircuit(entry.getValue());
            int port = entry.getKey();
            System.err.printf("%s <-> %d%n", circuit, port);
            assert circuit != null;
            circuitToPort.put(circuit, port);
            portToCircuit.put(port, circuit);
        }
    }

    private int nextFreePort() {
        int cand = 1;
        for (int k : portToCircuit.keySet()) {
            if (k != cand) return cand;
            cand++;
        }
        return cand;
    }

    private void cleanUpVFCs() throws IOException {
        RESTResponse<Collection<String>> bridgeNames = rest.getBridgeNames();
        if (bridgeNames.code != 200)
            throw new RuntimeException("unable to list bridge names; rsp="
                + bridgeNames.code);
        for (String bridgeId : bridgeNames.message) {
            RESTResponse<BridgeDesc> info = rest.getBridgeDesc(bridgeId);
            if (info.code != 200) {
                logger.warning("Bridge " + info + ": Getting info: RSP="
                    + info.code);
                continue;
            }

            /* Ignore VFCs with the wrong prefix. */
            String descr = info.message.descr;
            if (descr == null || !descr.startsWith(this.descPrefix)) continue;

            /* Recognize an already established VFC. */
            if (descr.equals(this.fullDesc)) {
                this.bridgeId = bridgeId;
                continue;
            }

            /* Delete others. */
            rest.destroyBridge(bridgeId);
        }
    }

    private void establishVFC() throws IOException {
        if (bridgeId != null) return;

        /* Create the VFC in a partial state. */
        final String bridgeId;
        {
            RESTResponse<String> creationRsp =
                rest.createBridge(new BridgeDesc().descr(partialDesc)
                    .resources(10).subtype(subtype).netns(netns));
            if (creationRsp.code != 201)
                throw new RuntimeException("bridge creation failure: "
                    + creationRsp.code);
            bridgeId = creationRsp.message;
        }

        /* Set the VFC's controller. */
        {
            RESTResponse<Void> ctrlRsp =
                rest.attachController(bridgeId,
                                      new ControllerConfig().id("learner")
                                          .host(controller.getAddress())
                                          .port(controller.getPort()));
            if (ctrlRsp.code != 201)
                throw new RuntimeException("bridge controller failure: "
                    + ctrlRsp.code);
        }

        /* Mark the VFC as in a complete state. */
        {
            RESTResponse<Void> brPatchRsp = rest
                .patchBridge(bridgeId, ReplaceBridgeDescription.of(fullDesc));
            if (brPatchRsp.code != 204)
                throw new RuntimeException("bridge completion failure: "
                    + brPatchRsp.code);
        }

        this.bridgeId = bridgeId;
    }

    private void collectPortSets() throws IOException {
        RESTResponse<Collection<? extends BitSet>> portSets =
            this.sliceRest.getPortSets(dpid);
        for (BitSet set : portSets.message) {
            Collection<Channel> circuits = new HashSet<>();
            for (int i : BitSetIterable.of(set))
                circuits.add(portToCircuit.get(i));
            BridgeSlice slice = new BridgeSlice(circuits);
            bridgesByCircuitSet.put(circuits, slice);
        }
    }

    private static final Logger logger =
        Logger.getLogger("uk.ac.lancs.networks.corsa.dp2000");
}
