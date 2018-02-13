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
package uk.ac.lancs.switches.transients;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import uk.ac.lancs.routing.span.Edge;
import uk.ac.lancs.switches.Service;
import uk.ac.lancs.switches.ServiceListener;
import uk.ac.lancs.switches.ServiceRequest;
import uk.ac.lancs.switches.ServiceStatus;
import uk.ac.lancs.switches.EndPoint;
import uk.ac.lancs.switches.Terminal;
import uk.ac.lancs.switches.Network;
import uk.ac.lancs.switches.NetworkControl;

/**
 * Implements an entirely virtual network that does nothing.
 * 
 * @author simpsons
 */
public class DummyNetwork implements Network {
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
            return DummyNetwork.this.name + ":" + this.name;
        }

        DummyNetwork owner() {
            return DummyNetwork.this;
        }
    }

    private class MyService implements Service {
        final int id;
        final Collection<ServiceListener> listeners = new HashSet<>();

        MyService(int id) {
            this.id = id;
        }

        boolean active, released;
        ServiceRequest request;

        @Override
        public synchronized void initiate(ServiceRequest request) {
            if (released)
                throw new IllegalStateException("connection disused");
            if (this.request != null)
                throw new IllegalStateException("connection in use");

            /* Sanitize the request such that every end point mentioned
             * in either set is present in both. A minimum bandwidth is
             * applied to all implicit and explicit consumers. */
            request = ServiceRequest.sanitize(request, 0.01);

            /* Check that all end points belong to us. */
            for (EndPoint ep : request.producers().keySet()) {
                Terminal p = ep.getTerminal();
                if (!(p instanceof MyTerminal))
                    throw new IllegalArgumentException("not my end point: "
                        + ep);
                MyTerminal mp = (MyTerminal) p;
                if (mp.owner() != DummyNetwork.this)
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
            listeners.stream()
                .forEach(l -> executor.execute(() -> action.accept(l)));
        }

        @Override
        public synchronized void activate() {
            if (released || request == null)
                throw new IllegalStateException("connection uninitiated");
            if (active) return;
            active = true;
            callOut(ServiceListener::activated);
        }

        @Override
        public synchronized void deactivate() {
            if (released || request == null || !active) return;
            active = false;
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
            if (request != null)
                for (EndPoint ep : request.producers().keySet())
                out.printf("%n      %10s %6g %6g", ep,
                           request.producers().get(ep),
                           request.consumers().get(ep));
            out.println();
        }

        @Override
        public synchronized NetworkControl getSwitch() {
            if (released || request == null) return null;
            return control;
        }

        @Override
        public synchronized ServiceRequest getRequest() {
            return request;
        }
    }

    private final Executor executor;
    private final String name;
    private final Map<String, MyTerminal> ports = new HashMap<>();
    private final Map<Integer, MyService> connections = new HashMap<>();
    private int nextConnectionId;

    /**
     * Print out the status of all connections and trunks of this
     * switch.
     * 
     * @param out the destination for the status report
     */
    public void dump(PrintWriter out) {
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
     * @param executor used to invoke {@link ServiceListener}s
     * 
     * @param name the new switch's name
     */
    public DummyNetwork(Executor executor, String name) {
        this.executor = executor;
        this.name = name;
    }

    /**
     * Create a port with the given name.
     * 
     * @param name the new port's name
     * 
     * @return the new port
     */
    public synchronized Terminal addPort(String name) {
        if (ports.containsKey(name))
            throw new IllegalArgumentException("port name in use: " + name);
        MyTerminal port = new MyTerminal(name);
        ports.put(name, port);
        return port;
    }

    @Override
    public synchronized Terminal getTerminal(String id) {
        return ports.get(id);
    }

    @Override
    public NetworkControl getControl() {
        return control;
    }

    private final NetworkControl control = new NetworkControl() {
        @Override
        public Service newService() {
            synchronized (DummyNetwork.this) {
                int id = nextConnectionId++;
                MyService conn = new MyService(id);
                connections.put(id, conn);
                return conn;
            }
        }

        @Override
        public Map<Edge<Terminal>, Double> getModel(double minimumBandwidth) {
            synchronized (DummyNetwork.this) {
                List<Terminal> list = new ArrayList<>(ports.values());
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
            synchronized (DummyNetwork.this) {
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
    };

    @Override
    public Collection<String> getTerminals() {
        return new HashSet<>(ports.keySet());
    }
}
