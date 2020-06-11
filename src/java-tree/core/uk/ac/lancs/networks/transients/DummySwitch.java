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
package uk.ac.lancs.networks.transients;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import uk.ac.lancs.networks.ChordMetrics;
import uk.ac.lancs.networks.Circuit;
import uk.ac.lancs.networks.InvalidServiceException;
import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.Segment;
import uk.ac.lancs.networks.Service;
import uk.ac.lancs.networks.ServiceListener;
import uk.ac.lancs.networks.ServiceStatus;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.TrafficFlow;
import uk.ac.lancs.networks.mgmt.Switch;
import uk.ac.lancs.networks.mgmt.TerminalExistsException;
import uk.ac.lancs.networks.mgmt.UnknownTerminalException;
import uk.ac.lancs.routing.span.Edge;

/**
 * Implements an entirely virtual switch that does nothing.
 * 
 * @author simpsons
 */
public class DummySwitch implements Switch {
    private class MyTerminal implements Terminal {
        private final String name;
        private final String iface;
        Double ingressCapacity = null, egressCapacity = null;

        MyTerminal(String name, String ifconfig) {
            this.name = name;
            this.iface = ifconfig;
        }

        public String ifconfig() {
            return iface;
        }

        @Override
        public NetworkControl getNetwork() {
            return control;
        }

        @Override
        public String toString() {
            return DummySwitch.this.name + ":" + this.name;
        }

        DummySwitch owner() {
            return DummySwitch.this;
        }

        @Override
        public String name() {
            return name;
        }
    }

    private Map<MyTerminal, TrafficFlow> sumTerminalFlows() {
        assert Thread.holdsLock(this);
        Map<MyTerminal, TrafficFlow> result = new HashMap<>();
        for (MyTerminal t : terminals.values())
            result.put(t, TrafficFlow.of(0.0, 0.0));
        for (MyService srv : connections.values()) {
            for (Map.Entry<? extends Circuit, ? extends TrafficFlow> entry : srv.request
                .circuitFlows().entrySet()) {
                Terminal t = entry.getKey().getTerminal();
                TrafficFlow old = result.get(t);
                if (old == null) continue;
                TrafficFlow adj = entry.getValue();
                result.put((MyTerminal) t, old.add(adj));
            }
        }
        return result;
    }

    private final Map<String, MyService> connsByHandle = new HashMap<>();

    private class MyService implements Service {
        final int id;
        final String handle;
        final Collection<ServiceListener> listeners = new HashSet<>();

        MyService(int id, String handle) {
            this.id = id;
            this.handle = handle;
        }

        boolean active, released;
        Segment request;

        @Override
        public synchronized void define(Segment request)
            throws InvalidServiceException {
            if (released) throw new IllegalStateException("service released");
            if (this.request != null)
                throw new IllegalStateException("service in use");

            /* Sanitize the request such that every circuit mentioned in
             * either set is present in both. A minimum bandwidth is
             * applied to all implicit and explicit consumers. */
            request = Segment.sanitize(request, 0.01);

            /* Check that all circuits belong to us. Also track the
             * bandwidth usage on each terminal. */
            final Map<MyTerminal, TrafficFlow> additional = new HashMap<>();
            for (Map.Entry<? extends Circuit, ? extends TrafficFlow> entry : request
                .circuitFlows().entrySet()) {
                final Circuit ep = entry.getKey();
                Terminal p = ep.getTerminal();
                if (!(p instanceof MyTerminal))
                    throw new InvalidServiceException(control
                        .name(), id, "not my circuit: " + ep);
                MyTerminal mp = (MyTerminal) p;
                if (mp.owner() != DummySwitch.this)
                    throw new InvalidServiceException(control
                        .name(), id, "not my circuit: " + ep);
                additional.put(mp,
                               additional
                                   .computeIfAbsent(mp,
                                                    k -> TrafficFlow.of(0.0,
                                                                        0.0))
                                   .add(entry.getValue()));
            }

            /* Check terminal capacities. */
            synchronized (DummySwitch.this) {
                final Map<MyTerminal, TrafficFlow> inUse = sumTerminalFlows();
                for (Map.Entry<MyTerminal, TrafficFlow> entry : additional
                    .entrySet()) {
                    MyTerminal mp = entry.getKey();
                    TrafficFlow next = entry.getValue();
                    TrafficFlow tiu = inUse.get(mp);
                    if (mp.ingressCapacity != null
                        && mp.ingressCapacity >= 0.0
                        && tiu.ingress + next.ingress > mp.ingressCapacity) {
                        String msg = "ingress capacity: " + mp.ingressCapacity
                            + "; use: " + tiu.ingress + "; requested: "
                            + next.ingress;
                        throw new InvalidServiceException(control.name(), id,
                                                          msg);
                    }
                    if (mp.egressCapacity != null && mp.egressCapacity >= 0.0
                        && tiu.egress + next.egress > mp.egressCapacity) {
                        String msg = "egress capacity: " + mp.egressCapacity
                            + "; use: " + tiu.egress + "; requested: "
                            + next.egress;
                        throw new InvalidServiceException(control.name(), id,
                                                          msg);
                    }
                }
            }

            this.request = request;
            callOut(ServiceStatus.ESTABLISHING);
            callOut(ServiceStatus.INACTIVE);
            if (active) {
                callOut(ServiceStatus.ACTIVATING);
                callOut(ServiceStatus.ACTIVE);
            }
        }

        @Override
        public synchronized void addListener(ServiceListener events) {
            listeners.add(events);
        }

        @Override
        public synchronized void removeListener(ServiceListener events) {
            listeners.remove(events);
        }

        private void callOut(ServiceStatus newStatus) {
            listeners.forEach(l -> l.newStatus(newStatus));
        }

        @Override
        public synchronized void activate() {
            if (released) throw new IllegalStateException("service released");
            if (active) return;
            active = true;
            if (request == null) return;
            callOut(ServiceStatus.ACTIVATING);
            callOut(ServiceStatus.ACTIVE);
        }

        @Override
        public synchronized void deactivate() {
            if (released || !active) return;
            active = false;
            if (request == null) return;
            callOut(ServiceStatus.DEACTIVATING);
            callOut(ServiceStatus.INACTIVE);
        }

        @Override
        public synchronized ServiceStatus status() {
            if (released) return ServiceStatus.RELEASED;
            if (request == null) return ServiceStatus.DORMANT;
            return active ? ServiceStatus.ACTIVE : ServiceStatus.INACTIVE;
        }
        
        @Override
        public synchronized void reset() {
            if (released) return;
            request = null;
            if (active) {
                active = false;
                callOut(ServiceStatus.DEACTIVATING);
                callOut(ServiceStatus.INACTIVE);
            }
            callOut(ServiceStatus.RELEASING);
            callOut(ServiceStatus.DORMANT);
        }

        @Override
        public void release() {
            synchronized (this) {
                if (released) return;
                // connections.remove(id);
                request = null;
                released = true;
                if (active) {
                    active = false;
                    callOut(ServiceStatus.DEACTIVATING);
                    callOut(ServiceStatus.INACTIVE);
                }
                callOut(ServiceStatus.RELEASING);
                callOut(ServiceStatus.RELEASED);
            }

            synchronized (DummySwitch.this) {
                connections.remove(id);
                if (handle != null) connsByHandle.remove(handle);
            }
        }

        @Override
        public int id() {
            return id;
        }

        synchronized void dump(PrintWriter out) {
            out.printf("  %3d %-8s", id,
                       released ? "RELEASED" : request == null ? "DORMANT"
                           : active ? "ACTIVE" : "INACTIVE");
            if (request != null) {
                for (Map.Entry<? extends Circuit, ? extends TrafficFlow> entry : request
                    .circuitFlows().entrySet()) {
                    Circuit ep = entry.getKey();
                    TrafficFlow flow = entry.getValue();
                    out.printf("%n      %10s %6g %6g", ep, flow.ingress,
                               flow.egress);
                }
            }
            out.println();
        }

        @Override
        public synchronized NetworkControl getNetwork() {
            if (released || request == null) return null;
            return control;
        }

        @Override
        public synchronized Segment getRequest() {
            return request;
        }

        @Override
        public Collection<Throwable> errors() {
            return Collections.emptySet();
        }
    }

    private final String name;
    private final Map<String, MyTerminal> terminals = new HashMap<>();
    private final Map<Integer, MyService> connections = new HashMap<>();
    private int nextConnectionId;

    /**
     * Print out the status of all connections and trunks of this
     * switch.
     * 
     * @param out the destination for the status report
     */
    @Override
    public void dumpStatus(PrintWriter out) {
        Collection<MyService> connections;
        synchronized (this) {
            connections = new ArrayList<>(this.connections.values());
        }
        out.printf("dummy %s:%n", name);
        for (MyService conn : connections)
            conn.dump(out);
        out.flush();
    }

    /**
     * Create a dummy switch.
     * 
     * @param name the new switch's name
     */
    public DummySwitch(String name) {
        this.name = name;
    }

    @Override
    public synchronized Terminal addTerminal(String terminalName,
                                             String interfaceName)
        throws TerminalExistsException {
        if (terminals.containsKey(terminalName))
            throw new TerminalExistsException(this.name, terminalName);
        MyTerminal terminal = new MyTerminal(terminalName, interfaceName);
        terminals.put(terminalName, terminal);
        return terminal;
    }

    @Override
    public synchronized Map<Terminal, String> getTerminals() {
        Map<Terminal, String> result = new HashMap<>();
        for (MyTerminal t : terminals.values())
            result.put(t, t.ifconfig());
        return result;
    }

    @Override
    public synchronized void removeTerminal(String name) {
        terminals.remove(name);
    }

    @Override
    public NetworkControl getControl() {
        return control;
    }

    private final NetworkControl control = new NetworkControl() {
        @Override
        public Terminal getTerminal(String id) {
            synchronized (DummySwitch.this) {
                return terminals.get(id);
            }
        }

        @Override
        public Collection<String> getTerminals() {
            synchronized (DummySwitch.this) {
                return new HashSet<>(terminals.keySet());
            }
        }

        @Override
        public Service newService(String handle) {
            synchronized (DummySwitch.this) {
                if (handle != null && connsByHandle.containsKey(handle))
                    return null;
                int id = nextConnectionId++;
                MyService conn = new MyService(id, handle);
                connections.put(id, conn);
                if (handle != null) connsByHandle.put(handle, conn);
                return conn;
            }
        }

        @Override
        public Map<Edge<Terminal>, ChordMetrics>
            getModel(double minimumBandwidth) {
            synchronized (DummySwitch.this) {
                List<Terminal> list = new ArrayList<>(terminals.values());
                Map<Edge<Terminal>, ChordMetrics> result = new HashMap<>();
                int size = list.size();
                for (int i = 0; i < size - 1; i++) {
                    for (int j = i + 1; j < size; j++) {
                        Edge<Terminal> edge =
                            Edge.of(list.get(i), list.get(j));
                        result.put(edge, ChordMetrics.ofDelay(0.001));
                    }
                }
                return result;
            }
        }

        @Override
        public Service getService(int id) {
            synchronized (DummySwitch.this) {
                return connections.get(id);
            }
        }

        @Override
        public Collection<Integer> getServiceIds() {
            synchronized (DummySwitch.this) {
                return new HashSet<>(connections.keySet());
            }
        }

        @Override
        public String toString() {
            return "ctrl:" + name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Service getService(String handle) {
            synchronized (DummySwitch.this) {
                return connsByHandle.get(handle);
            }
        }
    };

    @Override
    public void modifyBandwidth(String terminalName, boolean setIngress,
                                Double ingress, boolean setEgress,
                                Double egress)
        throws UnknownTerminalException {
        synchronized (this) {
            MyTerminal t = terminals.get(terminalName);
            if (t == null)
                throw new UnknownTerminalException(name, terminalName);
            final Double newIngress;
            if (setIngress) {
                newIngress = ingress;
            } else {
                newIngress = t.ingressCapacity == null ? 0.0
                    : t.ingressCapacity + ingress;
            }
            if (newIngress != null && newIngress < 0.0)
                throw new IllegalArgumentException("negative new"
                    + " ingress capacity " + newIngress + " at terminal "
                    + terminalName + " on " + name);

            final Double newEgress;
            if (setEgress) {
                newEgress = egress;
            } else {
                newEgress = t.egressCapacity == null ? 0.0
                    : t.egressCapacity + egress;
            }
            if (newEgress != null && newEgress < 0.0)
                throw new IllegalArgumentException("negative new"
                    + " egress capacity " + newEgress + " at terminal "
                    + terminalName + " on " + name);

            t.ingressCapacity = newIngress;
            t.egressCapacity = newEgress;
        }
    }
}
