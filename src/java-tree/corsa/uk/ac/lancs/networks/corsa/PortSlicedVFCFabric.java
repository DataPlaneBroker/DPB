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
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

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
import uk.ac.lancs.rest.client.RESTResponse;

/**
 * Manages a Corsa DP2X000-series switch by creating and maintaining a
 * single VFC whose OF ports are grouped for each logical bridge.
 * Traffic arriving on one port of a group only exits on other ports in
 * the same group. The VFC must connect to a controller that this fabric
 * can notify of new port groups. This fabric handles QoS in the tunnels
 * it attaches to the VFC, so the controller does not handle QoS.
 * 
 * @todo Try to detect when the OF controller has lost its state, and
 * re-issue it. Or simply give it the full set of port sets every time
 * it is invoked.
 * 
 * @author simpsons
 */
public final class PortSlicedVFCFabric implements Fabric {
    private final InetSocketAddress controller;
    private final InterfaceManager interfaces;

    private final String netns;
    private final String subtype;
    private final int resources;
    private final CorsaREST rest;
    private String bridgeId;
    private long dpid;

    private final SliceControllerREST sliceRest;
    private final String partialDesc;
    private final String fullDesc;
    private final String descPrefix;

    private final boolean withMetering;

    private final boolean withShaping;

    private final boolean withDestruction;

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
     * @param resources the percentage of resources to allocate when
     * creating a VFC
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
     * @param withMetering enables metering applied to tunnel
     * attachments
     * 
     * @param withShaping enables shaping applied to tunnel attachments
     * 
     * @param withDestruction {@code true} if existing VFCs with the
     * matching prefix but not the correct suffix should be deleted
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
                               int resources, String netns,
                               InetSocketAddress controller, URI service,
                               X509Certificate cert, String authz,
                               URI ctrlService, X509Certificate ctrlCert,
                               String ctrlAuthz, boolean withMetering,
                               boolean withShaping, boolean withDestruction)
        throws KeyManagementException,
            NoSuchAlgorithmException,
            IOException {
        this.interfaces = new InterfaceManager(portCount, maxAggregations);
        this.descPrefix = descPrefix;
        this.partialDesc = descPrefix + partialDescSuffix;
        this.fullDesc = descPrefix + fullDescSuffix;
        this.withMetering = withMetering;
        this.withShaping = withShaping;
        this.subtype = subtype;
        this.resources = resources;
        this.netns = netns;
        this.controller = controller;
        this.withDestruction = withDestruction;
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

        /* Attempt to contact the controller. Try a few times, as it
         * might not have come up yet, but is on the way. */
        long delay = 5000;
        for (int i = 0; i < 20; i++) {
            try {
                System.err.printf("Attempting contact with controller...%n");
                sliceRest.definePortSets(dpid, Collections.emptySet());
            } catch (IOException ex) {
                try {
                    System.err.printf("No contact; sleeping %gs...%n",
                                      delay / 1000.0);
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    /* Just try again. */
                    continue;
                }
                delay += delay / 2;
                continue;
            }
            break;
        }

        if (false) {
            /* This strategy cannot be relied upon. We're trying to
             * recover after a restart, and we can assume that the Corsa
             * has retained its bridge and tunnel configuration, but not
             * its OpenFlow rules, nor the internal state of its
             * controller (since that could have restarted too). This
             * strategy DOES assume that the OF state is preserved, so
             * it cannot work in the case that the controller has been
             * restarted. */

            /* TODO: A way to recover this strategy is to encode the
             * information in the ifdescr field of each attachment.
             * Ensure that each of our bridges has a unique int id, and
             * set that as the ifdescr for each tunnel. When restarting,
             * group each read tunnel configuration according to its
             * ifdescr, and reform the bridge objects out of those
             * (preserving the id). Then go to the controller and pass
             * it all of the recovered port sets.
             * 
             * The problem will be when the tunnels have not all had
             * their ifdescrs set up; they can't all be set up
             * atomically. It might be better to set each one to the set
             * of ports involved, so any can be used to recover; just
             * use a <=255-nibble hex bitmap. (No need to allocate an id
             * too!) If one tunnel is found to have an invalid ifdescr,
             * but is implied by another to belong together, just fix
             * its ifdescr. Otherwise, just delete the tunnel, and let
             * our own persistent state fully recover the slice.
             * 
             * Finally, make sure to re-issue the controller with all
             * port sets. */

            /* Load the mapping between OF port and circuit. */
            loadMapping();

            /* Get port sets from the controller, and create
             * corresponding bridge entities. */
            collectPortSets();
        } else {
            /* We have no reliable state to work on but that which is in
             * our database. This only make sense if start with a clean
             * VFC, so we must remove all existing tunnels. */
            for (int ofport : rest.getTunnels(bridgeId).message.keySet()) {
                System.err.printf("Detaching ofport %d of bridge %s%n",
                                  ofport, bridgeId);
                rest.detachTunnel(bridgeId, ofport);
            }
        }
    }

    @Override
    public Interface getInterface(String desc) {
        return interfaces.getInterface(desc);
    }

    private class BridgeSlice {
        private final Map<Channel, TrafficFlow> service;
        private final Map<Channel, Integer> statuses = new HashMap<>();

        /**
         * Create a bridge for something not already in the physical
         * switch.
         * 
         * @param details the ports of the bridge
         */
        BridgeSlice(Map<? extends Channel, ? extends TrafficFlow> details) {
            this.service = new HashMap<>(details);
        }

        /**
         * Create a bridge representing something already existing in
         * the physical switch.
         * 
         * @param circuits the existing circuits
         */
        BridgeSlice(Collection<? extends Channel> circuits) {
            this.service = new HashMap<>();
            for (Channel circuit : circuits)
                service.put(circuit, TrafficFlow.of(0.0, 0.0));
            started = true;
        }

        void stop() {
            assert Thread.holdsLock(PortSlicedVFCFabric.this);

            ExecutorService es = Executors.newFixedThreadPool(service.size());
            try {
                CompletionService<Void> cs =
                    new ExecutorCompletionService<>(es);
                int csGot = 0;

                /* Detach tunnels from ports. */
                for (Iterator<Map.Entry<Channel, Integer>> iter =
                    statuses.entrySet().iterator(); iter.hasNext();) {
                    Map.Entry<Channel, Integer> entry = iter.next();
                    iter.remove();

                    /* Skip tunnels that failed. They might have failed
                     * because they already exist, so don't delete
                     * them. */
                    if (entry.getValue() != 201) continue;

                    final Channel circuit = entry.getKey();
                    cs.submit(() -> {
                        /* Find which OF port it is attached to, and
                         * detach it. */
                        int ofport = circuitToPort.getOrDefault(circuit, 0);
                        if (ofport <= 0) return null;
                        try {
                            rest.detachTunnel(bridgeId, ofport);
                        } catch (IOException e) {
                            /* Perhaps we don't care by this stage. */
                        }
                        return null;
                    });
                    csGot++;
                }
                while (csGot > 0) {
                    try {
                        cs.take();
                        csGot--;
                    } catch (InterruptedException e) {
                        // Um?
                    }
                }
            } finally {
                es.shutdown();
            }

            inform(BridgeListener::destroyed);
            listeners.clear();
        }

        boolean started = false;

        void start() {
            assert Thread.holdsLock(PortSlicedVFCFabric.this);
            boolean failed = false;
            if (!started) {
                ExecutorService es =
                    Executors.newFixedThreadPool(service.size());
                try {
                    CompletionService<Map.Entry<Channel, Integer>> cs =
                        new ExecutorCompletionService<>(es);
                    int csGot = 0;

                    /* Allocate each circuit to an OF port, attach them,
                     * and set the QoS. */
                    BitSet ofPorts = new BitSet();
                    for (Map.Entry<? extends Channel, ? extends TrafficFlow> entry : service
                        .entrySet()) {
                        TrafficFlow flow = entry.getValue();
                        final Channel circuit = entry.getKey();

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
                            .ofport(ofport);
                        if (withShaping) {
                            /* Set the outgoing rate of the tunnel. */
                            desc.shapedRate(flow.egress);
                        }

                        cs.submit(() -> {
                            final RESTResponse<Void> rsp;
                            {
                                final long t0 = System.currentTimeMillis();
                                rsp = rest.attachTunnel(bridgeId, desc);
                                final long t1 = System.currentTimeMillis();
                                System.err.printf("Port %d time: %gs%n",
                                                  ofport, (t1 - t0) / 1000.0);
                            }

                            if (rsp.code == 201 && withMetering) {
                                final long t0 = System.currentTimeMillis();
                                /* Set the incoming QoS of the
                                 * tunnel. */
                                rest.patchTunnel(bridgeId, ofport,
                                                 Meter.cir(flow.ingress
                                                     * 1024.0),
                                                 Meter.cbs(10));
                                final long t1 = System.currentTimeMillis();
                                System.err
                                    .printf("Port %d metering time: %gs%n",
                                            ofport, (t1 - t0) / 1000.0);
                            }
                            return new Map.Entry<Channel, Integer>() {
                                @Override
                                public Channel getKey() {
                                    return circuit;
                                }

                                @Override
                                public Integer getValue() {
                                    return rsp.code;
                                }

                                @Override
                                public Integer setValue(Integer value) {
                                    throw new UnsupportedOperationException("immutable");
                                }
                            };
                        });
                        csGot++;
                    }
                    while (csGot > 0) {
                        try {
                            Map.Entry<Channel, Integer> rsp = cs.take().get();
                            csGot--;
                            if (rsp.getValue() != 201) failed = true;
                            statuses.put(rsp.getKey(), rsp.getValue());
                        } catch (InterruptedException
                            | ExecutionException e) {
                            // Shouldn't happen.
                            throw new AssertionError("unreachable",
                                                     e.getCause());
                        }
                    }

                    if (!failed) {
                        final long t0 = System.currentTimeMillis();
                        /* Tell the controller of the new OF port
                         * set. */
                        RESTResponse<Collection<? extends BitSet>> defRsp =
                            sliceRest.definePortSets(dpid, ofPorts);
                        if (defRsp.code != 200) {
                            RuntimeException t =
                                new RuntimeException("failed to set slice port set");
                            for (BridgeSlice slice : bridgesByCircuitSet
                                .values()) {
                                slice.inform(l -> l.error(t));
                                slice.stop();
                            }
                        }
                        final long t1 = System.currentTimeMillis();
                        System.err.printf("New port set %s time: %gs%n",
                                          ofPorts, (t1 - t0) / 1000.0);
                    }
                } catch (IOException ex) {
                    RuntimeException t =
                        new RuntimeException("failed to start slice", ex);
                    for (BridgeSlice slice : bridgesByCircuitSet.values()) {
                        slice.inform(l -> l.error(t));
                        slice.stop();
                    }
                } finally {
                    es.shutdown();
                }

                started = true;
            }
            if (failed) {
                for (Map.Entry<Channel, Integer> entry : statuses
                    .entrySet()) {
                    final int code = entry.getValue();
                    if (code == 201) continue;
                    final Throwable t = new RuntimeException("tunnel "
                        + entry.getKey() + " yielded " + code);
                    inform(bl -> bl.error(t));
                }
            } else {
                inform(BridgeListener::created);
            }
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
            Integer ofportObj = circuitToPort.remove(circuit);
            iter.remove();
            if (ofportObj == null) continue;
            int ofport = ofportObj;

            try {
                rest.detachTunnel(bridgeId, ofport);
            } catch (IOException e) {
                RuntimeException t =
                    new RuntimeException("error talking" + " to switch", e);
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

            if (withDestruction) {
                /* Delete others. */
                rest.destroyBridge(bridgeId);
            }
        }
    }

    private void establishVFC() throws IOException {
        if (bridgeId != null) return;

        /* Create the VFC in a partial state. */
        final String bridgeId;
        {
            RESTResponse<String> creationRsp =
                rest.createBridge(new BridgeDesc().descr(partialDesc)
                    .resources(resources).subtype(subtype).netns(netns));
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
            System.err.printf("Recreating bridge for %s%n", set);
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
