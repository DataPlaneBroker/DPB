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
package uk.ac.lancs.networks.transients;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.Service;
import uk.ac.lancs.networks.ServiceDescription;
import uk.ac.lancs.networks.ServiceListener;
import uk.ac.lancs.networks.ServiceStatus;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.TrafficFlow;
import uk.ac.lancs.networks.end_points.EndPoint;
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.routing.span.Edge;

/**
 * Implements an entirely virtual switch that does nothing.
 * 
 * @author simpsons
 */
public class DummySwitch implements Network {
    private class MyTerminal implements Terminal {
        private final String name;

        MyTerminal(String name) {
            this.name = name;
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

    private class MyService implements Service {
        final int id;
        final Collection<ServiceListener> listeners = new HashSet<>();

        MyService(int id) {
            this.id = id;
        }

        boolean active, released;
        ServiceDescription request;

        @Override
        public synchronized void initiate(ServiceDescription request) {
            if (released)
                throw new IllegalStateException("connection disused");
            if (this.request != null)
                throw new IllegalStateException("connection in use");

            /* Sanitize the request such that every end point mentioned
             * in either set is present in both. A minimum bandwidth is
             * applied to all implicit and explicit consumers. */
            request = ServiceDescription.sanitize(request, 0.01);

            /* Check that all end points belong to us. */
            for (EndPoint<? extends Terminal> ep : request.endPointFlows()
                .keySet()) {
                Terminal p = ep.getBundle();
                if (!(p instanceof MyTerminal))
                    throw new IllegalArgumentException("not my end point: "
                        + ep);
                MyTerminal mp = (MyTerminal) p;
                if (mp.owner() != DummySwitch.this)
                    throw new IllegalArgumentException("not my end point: "
                        + ep);
            }
            this.request = request;
            callOut(ServiceListener::ready);
        }

        @Override
        public synchronized void addListener(ServiceListener events) {
            listeners.add(events);
        }

        @Override
        public synchronized void removeListener(ServiceListener events) {
            listeners.remove(events);
        }

        private void callOut(Consumer<? super ServiceListener> action) {
            listeners.stream().forEach(action);
        }

        @Override
        public synchronized void activate() {
            if (released || request == null)
                throw new IllegalStateException("connection uninitiated");
            if (active) return;
            active = true;
            callOut(ServiceListener::activating);
            callOut(ServiceListener::activated);
        }

        @Override
        public synchronized void deactivate() {
            if (released || request == null || !active) return;
            active = false;
            callOut(ServiceListener::deactivating);
            callOut(ServiceListener::deactivated);
        }

        @Override
        public synchronized ServiceStatus status() {
            if (released) return ServiceStatus.RELEASED;
            if (request == null) return ServiceStatus.DORMANT;
            return active ? ServiceStatus.ACTIVE : ServiceStatus.INACTIVE;
        }

        @Override
        public synchronized void release() {
            if (released) return;
            connections.remove(id);
            request = null;
            released = true;
            if (active) {
                active = false;
                callOut(ServiceListener::deactivating);
                callOut(ServiceListener::deactivated);
            }
            callOut(ServiceListener::released);
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
                for (Map.Entry<? extends EndPoint<? extends Terminal>, ? extends TrafficFlow> entry : request
                    .endPointFlows().entrySet()) {
                    EndPoint<? extends Terminal> ep = entry.getKey();
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
        public synchronized ServiceDescription getRequest() {
            return request;
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

    /**
     * Create a terminal with the given name.
     * 
     * @param name the new terminal's name
     * 
     * @return the new terminal
     */
    public synchronized Terminal addTerminal(String name) {
        if (terminals.containsKey(name))
            throw new IllegalArgumentException("terminal name in use: "
                + name);
        MyTerminal terminal = new MyTerminal(name);
        terminals.put(name, terminal);
        return terminal;
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
        public Service newService() {
            synchronized (DummySwitch.this) {
                int id = nextConnectionId++;
                MyService conn = new MyService(id);
                connections.put(id, conn);
                return conn;
            }
        }

        @Override
        public Map<Edge<Terminal>, Double> getModel(double minimumBandwidth) {
            synchronized (DummySwitch.this) {
                List<Terminal> list = new ArrayList<>(terminals.values());
                Map<Edge<Terminal>, Double> result = new HashMap<>();
                int size = list.size();
                for (int i = 0; i < size - 1; i++) {
                    for (int j = i + 1; j < size; j++) {
                        Edge<Terminal> edge =
                            Edge.of(list.get(i), list.get(j));
                        result.put(edge, 0.001);
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
            return new HashSet<>(connections.keySet());
        }

        @Override
        public String toString() {
            return "ctrl:" + name;
        }

        @Override
        public String name() {
            return name;
        }
    };
}
