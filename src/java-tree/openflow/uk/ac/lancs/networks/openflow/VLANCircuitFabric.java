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
package uk.ac.lancs.networks.openflow;

import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.json.JsonException;

import uk.ac.lancs.networks.TrafficFlow;
import uk.ac.lancs.networks.fabric.Bridge;
import uk.ac.lancs.networks.fabric.BridgeListener;
import uk.ac.lancs.networks.fabric.Channel;
import uk.ac.lancs.networks.fabric.Fabric;
import uk.ac.lancs.networks.fabric.Interface;

/**
 * Manages the slicing of an OpenFlow switch by VLAN circuit ids through
 * a REST interface on its controller.
 * 
 * @author simpsons
 */
public final class VLANCircuitFabric implements Fabric {
    private final int portCount;
    private final long dpid;
    private final VLANCircuitControllerREST sliceRest;

    /**
     * Create a fabric to operate a switch controller that accepts sets
     * of VLAN-slicing tuples as slice descriptions.
     * 
     * @param portCount the number of ports on the switch
     * 
     * @param dpid the datapath identifier of the switch
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
     */
    public VLANCircuitFabric(int portCount, long dpid, URI ctrlService,
                             X509Certificate ctrlCert, String ctrlAuthz)
        throws KeyManagementException,
            NoSuchAlgorithmException {
        this.portCount = portCount;
        this.dpid = dpid;
        this.sliceRest =
            new VLANCircuitControllerREST(ctrlService, ctrlCert, ctrlAuthz);
    }

    private static abstract class VLANCircuitInterface implements Interface {
        protected final List<Integer> prefix;

        protected VLANCircuitInterface(List<Integer> prefix) {
            this.prefix = prefix;
        }

        abstract VLANCircuitId getId(int label);
    }

    private class SingleTagger extends VLANCircuitInterface {
        private final int minLabel;
        private final int maxLabel;

        SingleTagger(List<Integer> prefix, int minLabel, int maxLabel) {
            super(prefix);
            this.minLabel = minLabel;
            this.maxLabel = maxLabel;
        }

        @Override
        VLANCircuitId getId(int label) {
            if (label < minLabel || label > maxLabel)
                throw new IllegalArgumentException("label out of range: "
                    + label);
            List<Integer> in = new ArrayList<>(prefix.size() + 1);
            in.addAll(prefix);
            in.add(label);
            return new VLANCircuitId(in);
        }
    }

    private class DoubleTagger extends VLANCircuitInterface {
        protected DoubleTagger(List<Integer> prefix) {
            super(prefix);
        }

        @Override
        VLANCircuitId getId(int label) {
            if (label < 0 || label > 0xffffff)
                throw new IllegalArgumentException("label out of range: "
                    + label);
            List<Integer> in = new ArrayList<>(prefix.size() + 2);
            in.addAll(prefix);
            in.add(label >> 12);
            in.add(label & 0xfff);
            return new VLANCircuitId(in);
        }
    }

    private static final Pattern INTERFACE_PATTERN =
        Pattern.compile("^(?<phys>phys)|"
            + "(?:(?<port>[0-9]+)(?:(?<x2>x2)|(?:\\.(?<outer>[0-9]+))))$");

    @Override
    public Interface getInterface(String desc) {
        Matcher m = INTERFACE_PATTERN.matcher(desc);
        if (!m.matches())
            throw new IllegalArgumentException("bad interface: " + desc);
        if (m.group("phys") != null)
            return new SingleTagger(Collections.emptyList(), 1, portCount);
        int port = Integer.parseInt(m.group("port"));
        if (m.group("x2") != null)
            return new DoubleTagger(Collections.singletonList(port));
        if (m.group("outer") == null)
            return new SingleTagger(Collections.singletonList(port), 0,
                                    0xfff);
        int outer = Integer.parseInt(m.group("outer"));
        return new SingleTagger(Arrays.asList(port, outer), 0, 0xfff);
    }

    private class Slice {
        private final Map<VLANCircuitId, TrafficFlow> circuits;

        Slice(Map<? extends VLANCircuitId, ? extends TrafficFlow> circuits) {
            this.circuits = new HashMap<>(circuits);
        }

        boolean started = false;

        void start() {
            assert Thread.holdsLock(VLANCircuitFabric.this);
            if (!started) {
                try {
                    sliceRest.defineCircuitSet(dpid, circuits);
                } catch (IOException | JsonException ex) {
                    RuntimeException t =
                        new RuntimeException("could not connect " + circuits,
                                             ex);
                    inform(l -> l.error(t));
                    return;
                }
                started = true;
            }
            inform(BridgeListener::created);
        }

        void stop() {
            assert Thread.holdsLock(VLANCircuitFabric.this);

            try {
                sliceRest.discardCircuits(dpid, circuits.keySet());
            } catch (IOException | JsonException e) {
                /* Don't care at this point. */
            }
            inform(BridgeListener::destroyed);
            listeners.clear();
        }

        private final Collection<BridgeListener> listeners = new HashSet<>();

        void inform(Consumer<BridgeListener> action) {
            assert Thread.holdsLock(VLANCircuitFabric.this);
            listeners.forEach(action);
        }

        void addListener(BridgeListener listener) {
            assert Thread.holdsLock(VLANCircuitFabric.this);
            listeners.add(listener);
        }
    }

    private class BridgeRef implements Bridge {
        final Slice slice;

        BridgeRef(Slice slice) {
            this.slice = slice;
        }

        @Override
        public void start() {
            synchronized (VLANCircuitFabric.this) {
                slice.start();
            }
        }
    }

    private final Map<Collection<Channel>, Slice> slicesByChannelSet =
        new HashMap<>();

    @Override
    public synchronized void
        retainBridges(Collection<? extends Bridge> bridges) {
        /* Filter the references to consider only our own. */
        Collection<Slice> slices = new HashSet<>();
        for (Bridge br : bridges) {
            if (!(br instanceof BridgeRef)) continue;
            BridgeRef ref = (BridgeRef) br;
            Slice slice = ref.slice;
            slices.add(slice);
        }

        /* Identify what we are not retaining. */
        Collection<Slice> dropped = new ArrayList<>();
        for (Iterator<Slice> iter =
            slicesByChannelSet.values().iterator(); iter.hasNext();) {
            Slice slice = iter.next();
            if (slices.contains(slice)) continue;
            dropped.add(slice);
            iter.remove();
        }

        /* Tell the unretained slices to stop. */
        dropped.forEach(Slice::stop);
    }

    @Override
    public int capacity() {
        return 1000;
    }

    private Map<VLANCircuitId, TrafficFlow>
        map2(Map<? extends Channel, ? extends TrafficFlow> channels) {
        Map<VLANCircuitId, TrafficFlow> result = new HashMap<>();
        for (Map.Entry<? extends Channel, ? extends TrafficFlow> entry : channels
            .entrySet()) {
            Channel c = entry.getKey();
            if (!(c.getInterface() instanceof VLANCircuitInterface)) continue;
            VLANCircuitInterface iface =
                (VLANCircuitInterface) c.getInterface();
            result.put(iface.getId(c.getLabel()), entry.getValue());
        }
        return result;
    }

    @SuppressWarnings("unused")
    private Collection<VLANCircuitId>
        map(Collection<? extends Channel> channels) {
        Collection<VLANCircuitId> result = new HashSet<>();
        for (Channel c : channels) {
            if (!(c.getInterface() instanceof VLANCircuitInterface)) continue;
            VLANCircuitInterface iface =
                (VLANCircuitInterface) c.getInterface();
            result.add(iface.getId(c.getLabel()));
        }
        return result;
    }

    @Override
    public synchronized Bridge
        bridge(BridgeListener listener,
               Map<? extends Channel, ? extends TrafficFlow> details) {
        Collection<Channel> key = new HashSet<>(details.keySet());
        Slice slice = slicesByChannelSet
            .computeIfAbsent(key, k -> new Slice(map2(details)));
        slice.addListener(listener);
        return new BridgeRef(slice);
    }
}
