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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.json.simple.parser.ParseException;

import uk.ac.lancs.networks.ServiceResourceException;
import uk.ac.lancs.networks.TrafficFlow;
import uk.ac.lancs.networks.corsa.rest.BridgeDesc;
import uk.ac.lancs.networks.corsa.rest.ControllerConfig;
import uk.ac.lancs.networks.corsa.rest.CorsaREST;
import uk.ac.lancs.networks.corsa.rest.Meter;
import uk.ac.lancs.networks.corsa.rest.ReplaceBridgeDescription;
import uk.ac.lancs.networks.corsa.rest.TunnelDesc;
import uk.ac.lancs.networks.end_points.EndPoint;
import uk.ac.lancs.networks.fabric.Bridge;
import uk.ac.lancs.networks.fabric.BridgeListener;
import uk.ac.lancs.networks.fabric.Fabric;
import uk.ac.lancs.networks.fabric.Interface;
import uk.ac.lancs.rest.RESTResponse;

/**
 * Manages a Corsa DP2X00-series switch by creating one VFC per
 * requested service, and hooking it up to a configured learning-switch
 * controller.
 * 
 * @author simpsons
 */
public class VFCPerServiceFabric implements Fabric {
    private final InetSocketAddress controller;

    private final InterfaceManager interfaces;

    private final int maxBridges;

    /**
     * Bridges whose configuration is incomplete should have this
     * description.
     */
    private final String partialDesc;

    /**
     * Bridges whose descriptions have this form should be retained as
     * persistent state.
     */
    private final String fullDesc;

    /**
     * Bridges whose descriptions begin with this string (but not
     * {@link #fullDesc}) should be destroyed at start-up, because they
     * were part of the previous incarnation but were either not
     * completely configured or were used in a different way.
     */
    private final String descPrefix;

    /**
     * The network namespace for new bridges
     */
    private final String netns;

    private final String subtype;

    private final CorsaREST rest;

    /**
     * Create a switching fabric for a Corsa.
     * 
     * @param portCount the number of ports on the switch
     * 
     * @param maxAggregations the maximum number of supported link
     * aggregation groups
     * 
     * @param maxBridges the maximum number of VFCs that this fabric
     * will create and manage at once
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
     * @param netns the network namespace for the controller port of
     * each new VFC
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
    public VFCPerServiceFabric(int portCount, int maxAggregations,
                               int maxBridges, String descPrefix,
                               String partialDescSuffix,
                               String fullDescSuffix, String subtype,
                               String netns, InetSocketAddress controller,
                               URI service, X509Certificate cert,
                               String authz)
        throws KeyManagementException,
            NoSuchAlgorithmException {
        this.interfaces = new InterfaceManager(portCount, maxAggregations);
        this.maxBridges = maxBridges;
        this.descPrefix = descPrefix;
        this.partialDesc = descPrefix + partialDescSuffix;
        this.fullDesc = descPrefix + fullDescSuffix;
        this.subtype = subtype;
        this.netns = netns;
        this.controller = controller;
        this.rest = new CorsaREST(service, cert, authz);
    }

    class InternalBridge {
        public InternalBridge(Map<? extends EndPoint<? extends Interface<?>>, ? extends TrafficFlow> service) {
            this.service = new HashMap<>(service);
        }

        private String bridgeName;

        public InternalBridge(String bridgeName,
                              Collection<? extends EndPoint<? extends Interface<?>>> endPoints) {
            this.bridgeName = bridgeName;

            /* Fake a service description. */
            service = new HashMap<>();
            for (EndPoint<? extends Interface<?>> ep : endPoints)
                service.put(ep, TrafficFlow.of(0.0, 0.0));

            /* Mark the bridge as already existing. */
            started = true;

            System.err.printf("Recovered bridge %s on %s%n", this.bridgeName,
                              this.service);
        }

        private final Collection<BridgeListener> listeners = new HashSet<>();

        void inform(Consumer<BridgeListener> action) {
            assert Thread.holdsLock(VFCPerServiceFabric.this);
            listeners.forEach(action);
        }

        void addListener(BridgeListener listener) {
            assert Thread.holdsLock(VFCPerServiceFabric.this);
            listeners.add(listener);
        }

        void removeListener(BridgeListener listener) {
            assert Thread.holdsLock(VFCPerServiceFabric.this);
            listeners.remove(listener);
        }

        final Map<EndPoint<? extends Interface<?>>, TrafficFlow> service;

        boolean started;

        void start() {
            assert Thread.holdsLock(VFCPerServiceFabric.this);
            if (!started) {
                System.err.printf("Starting a Corsa bridge on %s%n", service);

                /* Contact the switch to build the bridge. */
                boolean destroyBridge = true;
                try {
                    /* Create the bridge, and record the name
                     * allocated. */
                    {
                        RESTResponse<String> creationRsp = rest
                            .createBridge(new BridgeDesc().descr(partialDesc)
                                .resources(2).subtype(subtype).netns(netns));
                        if (creationRsp.code != 201) {
                            System.err.printf(
                                              "Failed to "
                                                  + "created bridge (%d)%n",
                                              creationRsp.code);
                            ServiceResourceException t =
                                new ServiceResourceException("unable to"
                                    + " create bridge");
                            inform(l -> l.error(t));
                            return;
                        }
                        this.bridgeName = creationRsp.message;
                    }

                    System.err.printf("Attaching tunnels...%n");
                    /* Attach each of the tunnels. */
                    int nextOfPort = 1;
                    for (Map.Entry<EndPoint<? extends Interface<?>>, TrafficFlow> entry : service
                        .entrySet()) {
                        EndPoint<? extends Interface<?>> ep = entry.getKey();
                        CorsaInterface iface =
                            (CorsaInterface) ep.getBundle();
                        TrafficFlow flow = entry.getValue();

                        /* Create the tunnel with its shaped (egress)
                         * rate. Shaping can only be set when creating
                         * the tunnel. */
                        final int ofPort = nextOfPort++;
                        TunnelDesc tun = new TunnelDesc().ofport(ofPort)
                            .shapedRate(flow.egress);
                        iface.configureTunnel(tun, ep.getLabel());
                        RESTResponse<Void> tunRsp =
                            rest.attachTunnel(this.bridgeName, tun);
                        if (tunRsp.code != 201) {
                            System.err.printf("failed to "
                                + "attach tunnel (%d)%n" + "  %s->%d%n",
                                              tunRsp.code, ep, ofPort);
                            ServiceResourceException t =
                                new ServiceResourceException("unable to"
                                    + " attach tunnel " + ep + " to ofport "
                                    + ofPort + "; code " + tunRsp.code);
                            inform(l -> l.error(t));
                            return;
                        }

                        /* Apply the ingress rate as metering. */
                        @SuppressWarnings("unused")
                        RESTResponse<Void> tunPatchRsp =
                            rest.patchTunnel(this.bridgeName, ofPort,
                                             Meter.cir(flow.ingress * 1024.0),
                                             Meter.cbs(10));
                    }

                    /* Attach the controller. */
                    {
                        RESTResponse<Void> ctrlRsp =
                            rest.attachController(this.bridgeName,
                                                  new ControllerConfig()
                                                      .id("learner")
                                                      .host(controller
                                                          .getAddress())
                                                      .port(controller
                                                          .getPort()));
                        if (ctrlRsp.code != 201) {
                            System.err.printf("failed to "
                                + "attach controller (%d)%n", ctrlRsp.code);
                            ServiceResourceException t =
                                new ServiceResourceException("unable to"
                                    + " control bridge; code "
                                    + ctrlRsp.code);
                            inform(l -> l.error(t));
                            return;
                        }
                    }

                    /* Record that the bridge is fully configured, so
                     * that it won't get wiped when we start up
                     * again. */
                    RESTResponse<Void> brPatchRsp = rest
                        .patchBridge(this.bridgeName,
                                     ReplaceBridgeDescription.of(fullDesc));
                    if (brPatchRsp.code != 204) {
                        System.err.printf("failed to complete bridge (%d)%n",
                                          brPatchRsp.code);
                        ServiceResourceException t =
                            new ServiceResourceException("unable complete"
                                + " bridge; code " + brPatchRsp.code);
                        inform(l -> l.error(t));
                        return;
                    }

                    destroyBridge = false;
                    started = true;
                } catch (IOException | ParseException e) {
                    ServiceResourceException t =
                        new ServiceResourceException("error talking"
                            + " to switch", e);
                    inform(l -> l.error(t));
                    return;
                } finally {
                    if (destroyBridge && this.bridgeName != null) try {
                        rest.destroyBridge(this.bridgeName);
                    } catch (IOException | ParseException e) {
                        /* These are unlikely to happen at this
                         * stage. */
                    }
                }
            }

            inform(BridgeListener::created);
        }

        void stop() {
            assert Thread.holdsLock(VFCPerServiceFabric.this);

            if (bridgeName != null) {
                try {
                    /* Contact the switch to destroy the bridge, and
                     * record it as destroyed. */
                    RESTResponse<Void> brDestRsp =
                        rest.destroyBridge(bridgeName);
                    switch (brDestRsp.code) {
                    case 204:
                    case 404:
                        break;
                    default:
                        /* We failed to destroy the bridge. */
                        logger.warning("Failed to talk to switch on bridge "
                            + bridgeName + ": RSP=" + brDestRsp.code);
                        return;
                    }
                    bridgeName = null;

                    /* Inform the users, and never talk to them
                     * again. */
                    inform(BridgeListener::destroyed);
                    listeners.clear();
                } catch (IOException | ParseException e) {
                    logger.log(Level.WARNING,
                               "Error talking to switch to delete bridge "
                                   + bridgeName,
                               e);
                }
            }
        }
    }

    /**
     * Indexes bridges by the end points they connect. The number of
     * entries in this map is the number of bridges.
     */
    private final Map<Collection<EndPoint<? extends Interface<?>>>, InternalBridge> bridgesByEndPointSet =
        new HashMap<>();

    /**
     * Indexes bridges by each end point.
     */
    private final Map<EndPoint<? extends Interface<?>>, InternalBridge> bridgesByEndPoint =
        new HashMap<>();

    /**
     * Initialize the fabric. The switch is scanned for bridges. Each
     * bridge described as partially configured is removed. Remaining
     * bridges not described as fully configured are ignored. Other
     * bridges are recognized, and internal records are created for
     * them.
     * 
     * @throws IOException if there was an I/O error in contacting the
     * switch
     * 
     * @throws ParseException if a switch response failed to parse
     */
    public synchronized void init() throws IOException, ParseException {
        /* Contact the switch, and get information on all bridges. */
        RESTResponse<Collection<String>> bridges = rest.getBridgeNames();
        if (bridges.code != 200) throw new RuntimeException("unable to "
            + "get list of bridges; rsp=" + bridges.code);
        for (String bridgeName : bridges.message) {
            RESTResponse<BridgeDesc> bridgeInfo =
                rest.getBridgeDesc(bridgeName);
            if (bridgeInfo.code != 200) {
                logger.warning("Bridge " + bridgeName + ": Getting info: RSP="
                    + bridgeInfo.code);
                continue;
            }

            /* Bridges with no description text are not under our
             * control, and should be ignored. */
            if (bridgeInfo.message.descr == null) continue;

            /* Bridges that are marked as ours and are complete should
             * be analyzed to see what they connect. */
            if (!bridgeInfo.message.descr.equals(fullDesc)) {
                /* Other bridges marked with our prefix are either
                 * leftovers from our previous incarnation or from the
                 * system we've replaced. */
                if (bridgeInfo.message.descr.startsWith(descPrefix))
                    rest.destroyBridge(bridgeName);

                /* Bridges that are not related to us should be
                 * ignored. */
                continue;
            }

            /* Find out how the bridge is connected. Derive a partial
             * service description from it. */
            RESTResponse<Map<Integer, TunnelDesc>> tunnels =
                rest.getTunnels(bridgeName);
            if (tunnels.code != 200) {
                logger.warning("Bridge " + bridgeName
                    + ": Getting tunnels: RSP=" + tunnels.code);
                continue;
            }
            Collection<EndPoint<? extends Interface<?>>> endPoints =
                new HashSet<>();
            for (Map.Entry<Integer, TunnelDesc> entry : tunnels.message
                .entrySet()) {
                @SuppressWarnings("unused")
                int ofport = entry.getKey();
                TunnelDesc tun = entry.getValue();
                EndPoint<? extends Interface<?>> ep = endPointOf(tun);
                endPoints.add(ep);
            }

            /* Create the bridge, and index it. */
            InternalBridge intern = new InternalBridge(bridgeName, endPoints);
            bridgesByEndPointSet.put(endPoints, intern);
            for (EndPoint<? extends Interface<?>> ep : endPoints) {
                bridgesByEndPoint.put(ep, intern);
            }
        }
    }

    /**
     * Given a tunnel description, work out what abstract end point it
     * corresponds to.
     * 
     * @param tun the tunnel description
     * 
     * @return the corresponding end point
     * 
     * @throws IllegalArgumentException if the tunnel isn't a VLAN slice
     * of something
     */
    private EndPoint<? extends Interface<?>> endPointOf(TunnelDesc tun) {
        return interfaces.getEndPoint(tun);
    }

    @SuppressWarnings("unused")
    private static final Pattern INTERFACE_PATTERN =
        Pattern.compile("^(\\d+|lag\\d+|lag|phys)(?::(\\d+))?$");

    /**
     * {@inheritDoc}
     * 
     * <p>
     * This implementation matches descriptors of the form
     * <samp><var>port</var></samp> and
     * <samp><var>port</var>:<var>vlan</var></samp>.
     * <samp><var>port</var></samp> may be an integer for a physical
     * port, or <samp>lag<var>num</var></samp> for a link aggregation
     * group.
     */
    @Override
    public Interface<?> getInterface(String desc) {
        return interfaces.getInterface(desc);
    }

    class BridgeRef implements Bridge {
        final InternalBridge internal;

        BridgeRef(InternalBridge internal) {
            this.internal = internal;
        }

        @Override
        public void start() {
            synchronized (VFCPerServiceFabric.this) {
                internal.start();
            }
        }
    }

    @Override
    public synchronized Bridge
        bridge(BridgeListener listener,
               Map<? extends EndPoint<? extends Interface<?>>, ? extends TrafficFlow> details) {
        InternalBridge intern = bridgesByEndPointSet.get(details.keySet());
        if (intern == null) {
            intern = new InternalBridge(details);
            bridgesByEndPointSet
                .put(new HashSet<EndPoint<? extends Interface<?>>>(details
                    .keySet()), intern);
            for (EndPoint<? extends Interface<?>> ep : details.keySet())
                bridgesByEndPoint.put(ep, intern);
        }
        intern.addListener(listener);
        return new BridgeRef(intern);
    }

    @Override
    public synchronized void
        retainBridges(Collection<? extends Bridge> bridges) {
        /* Map all the bridge references to a set of internal
         * bridges. */
        Collection<InternalBridge> keep = new HashSet<>();
        for (Bridge br : bridges) {
            if (!(br instanceof BridgeRef)) continue;
            BridgeRef ref = (BridgeRef) br;
            InternalBridge ibr = ref.internal;
            keep.add(ibr);
        }

        for (Iterator<Map.Entry<Collection<EndPoint<? extends Interface<?>>>, InternalBridge>> iter =
            bridgesByEndPointSet.entrySet().iterator(); iter.hasNext();) {
            final Map.Entry<Collection<EndPoint<? extends Interface<?>>>, InternalBridge> entry =
                iter.next();
            final InternalBridge cand = entry.getValue();
            if (keep.contains(cand)) continue;

            /* Remove this bridge. */
            cand.stop();
            iter.remove();
            bridgesByEndPoint.keySet().removeAll(entry.getKey());
        }
    }

    @Override
    public synchronized int capacity() {
        int amount = maxBridges - bridgesByEndPointSet.size();
        return amount < 0 ? 0 : amount;
    }

    private static final Logger logger =
        Logger.getLogger("uk.ac.lancs.networks.corsa.dp2000");
}
