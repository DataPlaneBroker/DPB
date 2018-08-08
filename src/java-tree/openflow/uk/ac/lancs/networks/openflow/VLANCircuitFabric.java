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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

import javax.json.JsonException;

import uk.ac.lancs.networks.ServiceResourceException;
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
    private final long dpid;
    private final VLANCircuitControllerREST sliceRest;

    /**
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     * 
     */
    public VLANCircuitFabric(int portCount, long dpid, URI ctrlService,
                             X509Certificate ctrlCert, String ctrlAuthz)
        throws KeyManagementException,
            NoSuchAlgorithmException {
        this.dpid = dpid;
        this.sliceRest =
            new VLANCircuitControllerREST(ctrlService, ctrlCert, ctrlAuthz);
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    @Override
    public Interface getInterface(String desc) {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    private class Slice {
        private final Collection<VLANCircuitId> circuits;

        Slice(Collection<? extends VLANCircuitId> circuits) {
            this.circuits = new HashSet<>(circuits);
        }

        boolean started = false;

        void start() {
            assert Thread.holdsLock(VLANCircuitFabric.this);
            if (!started) {
                try {
                    sliceRest.defineCircuitSet(dpid, circuits);
                } catch (IOException | JsonException ex) {
                    ServiceResourceException t =
                        new ServiceResourceException("could not connect "
                            + circuits, ex);
                    inform(l -> l.error(t));
                    return;
                }
                started = true;
            }
            inform(BridgeListener::created);
        }

        void stop() {
            assert Thread.holdsLock(VLANCircuitFabric.this);

            // TODO

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
        return 1;
    }

    private Collection<VLANCircuitId>
        map(Collection<? extends Channel> channels) {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    @Override
    public synchronized Bridge
        bridge(BridgeListener listener,
               Map<? extends Channel, ? extends TrafficFlow> details) {
        Collection<Channel> key = new HashSet<>(details.keySet());
        Slice slice =
            slicesByChannelSet.computeIfAbsent(key, k -> new Slice(map(k)));
        slice.addListener(listener);
        return new BridgeRef(slice);
    }
}
