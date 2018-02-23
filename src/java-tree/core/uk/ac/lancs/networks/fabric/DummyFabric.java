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
package uk.ac.lancs.networks.fabric;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import uk.ac.lancs.networks.EndPoint;
import uk.ac.lancs.networks.TrafficFlow;

/**
 * Does nothing but report on requested changes.
 * 
 * @author simpsons
 */
public final class DummyFabric implements Fabric {
    private static class MyInterface implements Interface {
        final String config;

        MyInterface(String config) {
            if (config == null)
                throw new NullPointerException("interface identification");
            this.config = config;
        }

        @Override
        public String toString() {
            return config;
        }

        @Override
        public int hashCode() {
            return config.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) return false;
            if (!(o instanceof MyInterface)) return false;
            MyInterface other = (MyInterface) o;
            return other.config.equals(config);
        }
    }

    @Override
    public Interface getInterface(String desc) {
        return new MyInterface(desc);
    }

    private class BridgeInstance {
        final String name;
        final Map<? extends EndPoint<? extends Interface>, ? extends TrafficFlow> details;

        BridgeInstance(String name,
                       Map<? extends EndPoint<? extends Interface>, ? extends TrafficFlow> details) {
            this.name = name;
            this.details = details;
        }

        final Collection<BridgeListener> listeners = new HashSet<>();

        private void callOut(Consumer<? super BridgeListener> action) {
            listeners.forEach(l -> action.accept(l));
        }

        void start() {
            DummyFabric.this.start(this);
        }

        void stop() {
            assert Thread.holdsLock(DummyFabric.this);

            System.out.printf("Bridge starting: %s%n%s%n", name, details);

            callOut(BridgeListener::destroyed);
        }

        boolean started;
    }

    synchronized void start(BridgeInstance instance) {
        if (bridges.get(instance.name) != instance) return;
        if (instance.started) return;
        instance.started = true;

        System.out.printf("Bridge starting: %s%n%s%n", instance.name,
                          instance.details);

        instance.callOut(BridgeListener::created);
    }

    private class BridgeReference implements Bridge {
        final BridgeInstance instance;

        BridgeReference(BridgeInstance instance) {
            this.instance = instance;
        }

        @Override
        public void start() {
            instance.start();
        }

        DummyFabric owner() {
            return DummyFabric.this;
        }
    }

    private final Map<String, BridgeInstance> bridges = new HashMap<>();
    private Map<EndPoint<Interface>, BridgeInstance> index = new HashMap<>();

    private int nextBridgeId;

    @Override
    public synchronized Bridge
        bridge(BridgeListener listener,
               Map<? extends EndPoint<Interface>, ? extends TrafficFlow> details) {
        /* Look up all end points to find out if they already belong to
         * a bridge. */
        Collection<BridgeInstance> existingBridges =
            details.keySet().stream().map(index::get).filter(br -> br != null)
                .collect(Collectors.toSet());

        /* Among the intersecting bridges, find one matching the new
         * details. */
        Optional<BridgeInstance> match = existingBridges.stream()
            .filter(br -> br.details.equals(details)).findAny();
        if (match.isPresent()) {
            assert existingBridges.size() == 1;
            BridgeInstance br = match.get();
            br.listeners.add(listener);
            return new BridgeReference(br);
        }

        /* If any non-matching bridges have requested end points, the
         * action was a failure. TODO: Maybe throw an exception? */
        if (!existingBridges.isEmpty()) return null;

        /* Create a brand-new bridge. */
        String brName = "br" + nextBridgeId++;
        Map<EndPoint<Interface>, TrafficFlow> copy = new HashMap<>(details);
        BridgeInstance br = new BridgeInstance(brName, copy);
        br.listeners.add(listener);
        bridges.put(brName, br);
        for (EndPoint<Interface> ep : copy.keySet())
            index.put(ep, br);
        return new BridgeReference(br);
    }

    @Override
    public synchronized void
        retainBridges(Collection<? extends Bridge> bridges) {
        /* Get the set of instances that the supplied references refer
         * to. */
        Collection<BridgeInstance> instances =
            bridges.stream().filter(b -> b instanceof BridgeReference)
                .map(b -> (BridgeReference) b).filter(b -> b.owner() == this)
                .map(b -> b.instance).filter(b -> b != null)
                .collect(Collectors.toSet());

        /* Remove anything that isn't in this set. */
        for (Iterator<BridgeInstance> iter =
            this.bridges.values().iterator(); iter.hasNext();) {
            BridgeInstance instance = iter.next();
            if (!instances.contains(instance)) {
                index.keySet().removeAll(instance.details.keySet());
                instance.stop();
                iter.remove();
            }
        }
    }
}
