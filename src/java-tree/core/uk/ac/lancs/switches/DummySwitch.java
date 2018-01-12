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
package uk.ac.lancs.switches;

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
import uk.ac.lancs.routing.span.HashableEdge;

/**
 * Implements an entirely virtual switch that does nothing.
 * 
 * @author simpsons
 */
public class DummySwitch implements Switch {
    private class MyPort implements Port {
        private final String name;

        MyPort(String name) {
            this.name = name;
        }

        @Override
        public SwitchControl getSwitch() {
            return control;
        }

        @Override
        public String toString() {
            return DummySwitch.this.name + ":" + this.name;
        }

        DummySwitch owner() {
            return DummySwitch.this;
        }
    }

    private class MyConnection implements Connection {
        final int id;
        final Collection<ConnectionListener> listeners = new HashSet<>();

        MyConnection(int id) {
            this.id = id;
        }

        boolean active, released;
        ConnectionRequest request;

        @Override
        public synchronized void initiate(ConnectionRequest request) {
            if (released)
                throw new IllegalStateException("connection disused");
            if (this.request != null)
                throw new IllegalStateException("connection in use");
            /* Check that all end points belong to us. */
            for (EndPoint ep : request.terminals) {
                Port p = ep.getPort();
                if (!(p instanceof MyPort))
                    throw new IllegalArgumentException("not my end point: "
                        + ep);
                MyPort mp = (MyPort) p;
                if (mp.owner() != DummySwitch.this)
                    throw new IllegalArgumentException("not my end point: "
                        + ep);
            }
            this.request = request;
            callOut(ConnectionListener::ready);
        }

        @Override
        public synchronized void addListener(ConnectionListener events) {
            listeners.add(events);
        }

        @Override
        public synchronized void removeListener(ConnectionListener events) {
            listeners.remove(events);
        }

        private void callOut(Consumer<? super ConnectionListener> action) {
            listeners.stream()
                .forEach(l -> executor.execute(() -> action.accept(l)));
        }

        @Override
        public synchronized void activate() {
            if (released || request == null)
                throw new IllegalStateException("connection uninitiated");
            if (active) return;
            active = true;
            callOut(ConnectionListener::activated);
        }

        @Override
        public synchronized void deactivate() {
            if (released || request == null || !active) return;
            active = false;
            callOut(ConnectionListener::deactivated);
        }

        @Override
        public synchronized ConnectionStatus status() {
            if (released) return ConnectionStatus.RELEASED;
            if (request == null) return ConnectionStatus.DORMANT;
            return active ? ConnectionStatus.ACTIVE
                : ConnectionStatus.INACTIVE;
        }

        @Override
        public synchronized void release() {
            if (released) return;
            connections.remove(id);
            request = null;
            released = true;
            callOut(ConnectionListener::released);
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
                out.printf(" %3g", request.bandwidth);
                for (EndPoint ep : request.terminals)
                    out.printf("%n      %s", ep);
            }
            out.println();
        }

        @Override
        public synchronized SwitchControl getSwitch() {
            if (released || request == null) return null;
            return control;
        }

        @Override
        public synchronized ConnectionRequest getRequest() {
            return request;
        }
    }

    private final Executor executor;
    private final String name;
    private final Map<String, MyPort> ports = new HashMap<>();
    private final Map<Integer, MyConnection> connections = new HashMap<>();
    private int nextConnectionId;

    /**
     * Print out the status of all connections and trunks of this
     * switch.
     * 
     * @param out the destination for the status report
     */
    public void dump(PrintWriter out) {
        Collection<MyConnection> connections;
        synchronized (this) {
            connections = new ArrayList<>(this.connections.values());
        }
        out.printf("dummy %s:%n", name);
        for (MyConnection conn : connections)
            conn.dump(out);
        out.flush();
    }

    /**
     * Create a dummy switch.
     * 
     * @param executor used to invoke {@link ConnectionListener}s
     * 
     * @param name the new switch's name
     */
    public DummySwitch(Executor executor, String name) {
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
    public synchronized Port addPort(String name) {
        if (ports.containsKey(name))
            throw new IllegalArgumentException("port name in use: " + name);
        MyPort port = new MyPort(name);
        ports.put(name, port);
        return port;
    }

    @Override
    public synchronized Port getPort(String id) {
        return ports.get(id);
    }

    @Override
    public SwitchControl getControl() {
        return control;
    }

    private final SwitchControl control = new SwitchControl() {
        @Override
        public Connection newConnection() {
            synchronized (DummySwitch.this) {
                int id = nextConnectionId++;
                MyConnection conn = new MyConnection(id);
                connections.put(id, conn);
                return conn;
            }
        }

        @Override
        public Map<Edge<Port>, Double> getModel(double minimumBandwidth) {
            synchronized (DummySwitch.this) {
                List<Port> list = new ArrayList<>(ports.values());
                Map<Edge<Port>, Double> result = new HashMap<>();
                int size = list.size();
                for (int i = 0; i < size - 1; i++) {
                    for (int j = i + 1; j < size; j++) {
                        Edge<Port> edge =
                            HashableEdge.of(list.get(i), list.get(j));
                        result.put(edge, 0.001);
                    }
                }
                return result;
            }
        }

        @Override
        public Connection getConnection(int id) {
            synchronized (DummySwitch.this) {
                return connections.get(id);
            }
        }

        @Override
        public Collection<Integer> getConnectionIds() {
            return new HashSet<>(connections.keySet());
        }

        @Override
        public String toString() {
            return "ctrl:" + name;
        }
    };

    @Override
    public Collection<String> getPorts() {
        return new HashSet<>(ports.keySet());
    }
}
