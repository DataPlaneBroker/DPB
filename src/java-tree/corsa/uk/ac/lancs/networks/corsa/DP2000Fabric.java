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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.parser.ParseException;

import uk.ac.lancs.networks.ServiceResourceException;
import uk.ac.lancs.networks.TrafficFlow;
import uk.ac.lancs.networks.corsa.rest.BridgeDesc;
import uk.ac.lancs.networks.corsa.rest.ControllerConfig;
import uk.ac.lancs.networks.corsa.rest.CorsaREST;
import uk.ac.lancs.networks.corsa.rest.Meter;
import uk.ac.lancs.networks.corsa.rest.RESTResponse;
import uk.ac.lancs.networks.corsa.rest.ReplaceBridgeDescription;
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
    private final InetSocketAddress controller;

    private final int maxBridges;

    /**
     * Bridges whose descriptions have this form should be deleted at
     * start-up. Their configuration was not completed.
     */
    private final String partialDesc;

    /**
     * Bridges whose descriptions have this form should be retained as
     * persistent state.
     */
    private final String fullDesc;

    /**
     * The network namespace for new bridges
     */
    private final String netns;

    private final String subtype;

    private final CorsaREST rest;

    /**
     * Create a switching fabric for a Corsa.
     * 
     * @param maxBridges the maximum number of bridges that this fabric
     * will create and manage at once
     * 
     * @param partialDesc the description text used for new bridges
     * before their configuration is complete
     * 
     * @param fullDesc the description text used for new bridges as soon
     * as their configuration is complete
     * 
     * @param subtype the VFC subtype to use when creating bridges,
     * e.g., <samp>ls-vpn</samp>, <samp>openflow</samp>, etc.
     * 
     * @param netns the network namespace for the controller port of
     * each new bridge
     * 
     * @param controller the IP address and port number of the
     * controller used for all created bridges
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
    public DP2000Fabric(int maxBridges, String partialDesc, String fullDesc,
                        String subtype, String netns,
                        InetSocketAddress controller, URI service,
                        X509Certificate cert, String authz)
        throws KeyManagementException,
            NoSuchAlgorithmException {
        this.maxBridges = maxBridges;
        this.partialDesc = partialDesc;
        this.fullDesc = fullDesc;
        this.subtype = subtype;
        this.netns = netns;
        this.controller = controller;
        this.rest = new CorsaREST(service, cert, authz);
    }

    class InternalBridge {
        public InternalBridge(Map<? extends EndPoint<Interface>, ? extends TrafficFlow> service) {
            this.service = new HashMap<>(service);
        }

        private String bridgeName;

        public InternalBridge(String bridgeName,
                              Collection<? extends EndPoint<Interface>> endPoints) {
            this.bridgeName = bridgeName;

            /* Fake a service description. */
            service = new HashMap<>();
            for (EndPoint<Interface> ep : endPoints)
                service.put(ep, TrafficFlow.of(0.0, 0.0));

            /* Mark the bridge as already existing. */
            started = true;

            System.err.printf("Recovered bridge %s on %s%n", this.bridgeName,
                              this.service);
        }

        private final Collection<BridgeListener> listeners = new HashSet<>();

        void inform(Consumer<BridgeListener> action) {
            assert Thread.holdsLock(DP2000Fabric.this);
            listeners.forEach(action);
        }

        void addListener(BridgeListener listener) {
            assert Thread.holdsLock(DP2000Fabric.this);
            listeners.add(listener);
        }

        void removeListener(BridgeListener listener) {
            assert Thread.holdsLock(DP2000Fabric.this);
            listeners.remove(listener);
        }

        final Map<EndPoint<Interface>, TrafficFlow> service;

        boolean started;

        void start() {
            assert Thread.holdsLock(DP2000Fabric.this);
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
                    for (Map.Entry<EndPoint<Interface>, TrafficFlow> entry : service
                        .entrySet()) {
                        EndPoint<Interface> ep = entry.getKey();
                        CorsaInterface iface =
                            (CorsaInterface) ep.getBundle();
                        TrafficFlow flow = entry.getValue();

                        /* Create the tunnel with its shaped (egress)
                         * rate. Shaping can only be set when creating
                         * the tunnel. */
                        final int ofPort = nextOfPort++;
                        TunnelDesc tun = new TunnelDesc().ofport(ofPort)
                            .shapedRate(flow.egress);
                        if (iface.port.equals("phys")) {
                            tun.port("" + ep.getLabel());
                        } else if (iface.port.equals("lag")) {
                            tun.port("lag" + ep.getLabel());
                        } else if (iface.vlanId < 0) {
                            tun.port(iface.port).vlanId(ep.getLabel());
                        } else {
                            tun.port(iface.port).vlanId(iface.vlanId)
                                .innerVlanId(ep.getLabel());
                        }
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
            assert Thread.holdsLock(DP2000Fabric.this);

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
    private final Map<Collection<EndPoint<Interface>>, InternalBridge> bridgesByEndPointSet =
        new HashMap<>();

    /**
     * Indexes bridges by each end point.
     */
    private final Map<EndPoint<Interface>, InternalBridge> bridgesByEndPoint =
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

            /* Destroy bridges that were only partially configured from
             * last time. */
            if (bridgeInfo.message.descr.equals(partialDesc))
                rest.destroyBridge(bridgeName);

            /* Bridges not under our control should be ignored. */
            if (!bridgeInfo.message.descr.equals(fullDesc)) continue;

            /* Find out how the bridge is connected. Derive a partial
             * service description from it. */
            RESTResponse<Map<Integer, TunnelDesc>> tunnels =
                rest.getTunnels(bridgeName);
            if (tunnels.code != 200) {
                logger.warning("Bridge " + bridgeName
                    + ": Getting tunnels: RSP=" + tunnels.code);
                continue;
            }
            Collection<EndPoint<Interface>> endPoints = new HashSet<>();
            for (Map.Entry<Integer, TunnelDesc> entry : tunnels.message
                .entrySet()) {
                @SuppressWarnings("unused")
                int ofport = entry.getKey();
                TunnelDesc tun = entry.getValue();
                EndPoint<Interface> ep = endPointOf(tun);
                endPoints.add(ep);
            }

            /* Create the bridge, and index it. */
            InternalBridge intern = new InternalBridge(bridgeName, endPoints);
            bridgesByEndPointSet.put(endPoints, intern);
            for (EndPoint<Interface> ep : endPoints) {
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
    private EndPoint<Interface> endPointOf(TunnelDesc tun) {
        if (tun.vlanId < 0) {
            /* Recognize abstract ports called "phys" or "lag", so that
             * "phys:3" identifies port 3, and "lag:4" identifies link
             * aggregation group 4. In these cases, the end-point label
             * is not a VLAN id. */
            if (tun.port.startsWith("lag")) {
                CorsaInterface iface = new CorsaInterface("lag", -1);
                return iface
                    .getEndPoint(Integer.parseInt(tun.port.substring(3)));
            } else {
                CorsaInterface iface = new CorsaInterface("phys", -1);
                return iface.getEndPoint(Integer.parseInt(tun.port));
            }
        }
        if (tun.innerVlanId < 0) {
            CorsaInterface iface = new CorsaInterface(tun.port, -1);
            return iface.getEndPoint(tun.vlanId);
        }
        CorsaInterface iface = new CorsaInterface(tun.port, tun.vlanId);
        return iface.getEndPoint(tun.innerVlanId);
    }

    private static final Pattern INTERFACE_PATTERN =
        Pattern.compile("^(\\d+|lag\\d+|lag|phys)(?::(\\d+))?$");

    /**
     * {@inheritDoc}
     * 
     * This implementation matches descriptors of the form
     * <samp><var>port</var></samp> and
     * <samp><var>port</var>:<var>vlan</var></samp>.
     * <samp><var>port</var></samp> may be an integer for a physical
     * port, or <samp>lag<var>num</var></samp> for a link aggregation
     * group.
     */
    @Override
    public Interface getInterface(String desc) {
        Matcher m = INTERFACE_PATTERN.matcher(desc);
        if (!m.matches())
            throw new IllegalArgumentException("invalid interface: " + desc);
        String port = m.group(1);
        int vlanId = -1;
        if (m.group(2) != null) vlanId = Integer.parseInt(m.group(2));
        return new CorsaInterface(port, vlanId);
    }

    class BridgeRef implements Bridge {
        final InternalBridge internal;

        BridgeRef(InternalBridge internal) {
            this.internal = internal;
        }

        @Override
        public void start() {
            synchronized (DP2000Fabric.this) {
                internal.start();
            }
        }
    }

    @Override
    public synchronized Bridge
        bridge(BridgeListener listener,
               Map<? extends EndPoint<Interface>, ? extends TrafficFlow> details) {
        InternalBridge intern = bridgesByEndPointSet.get(details.keySet());
        if (intern == null) {
            intern = new InternalBridge(details);
            bridgesByEndPointSet
                .put(new HashSet<EndPoint<Interface>>(details.keySet()),
                     intern);
            for (EndPoint<Interface> ep : details.keySet())
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

        for (Iterator<Map.Entry<Collection<EndPoint<Interface>>, InternalBridge>> iter =
            bridgesByEndPointSet.entrySet().iterator(); iter.hasNext();) {
            final Map.Entry<Collection<EndPoint<Interface>>, InternalBridge> entry =
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
