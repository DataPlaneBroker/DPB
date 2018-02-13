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
package uk.ac.lancs.switches.persist;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.routing.span.Edge;
import uk.ac.lancs.switches.ServiceListener;
import uk.ac.lancs.switches.ServiceRequest;
import uk.ac.lancs.switches.ServiceStatus;
import uk.ac.lancs.switches.EndPoint;
import uk.ac.lancs.switches.Terminal;
import uk.ac.lancs.switches.Service;
import uk.ac.lancs.switches.Network;
import uk.ac.lancs.switches.NetworkControl;
import uk.ac.lancs.switches.backend.Switch;
import uk.ac.lancs.switches.backend.SwitchFactory;
import uk.ac.lancs.switches.backend.Bridge;
import uk.ac.lancs.switches.backend.BridgeListener;

/**
 * Implements a switch that retains its state in a database.
 * 
 * @author simpsons
 */
public class PersistentSwitch implements Network {
    private final Switch backend;

    private class MyTerminal implements Terminal {
        private final String name;
        private final Terminal physicalPort;

        MyTerminal(String name, String desc) {
            this.name = name;
            this.physicalPort = backend.getInterface(desc);
        }

        EndPoint getInnerEndPoint(int label) {
            return physicalPort.getEndPoint(label);
        }

        @Override
        public NetworkControl getNetwork() {
            return control;
        }

        @Override
        public String toString() {
            return PersistentSwitch.this.name + ":" + this.name;
        }

        PersistentSwitch owner() {
            return PersistentSwitch.this;
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
        Bridge bridge;

        @Override
        public synchronized void initiate(ServiceRequest request) {
            if (released) throw new IllegalStateException("service disused");
            if (this.request != null)
                throw new IllegalStateException("service in use");

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
                if (mp.owner() != PersistentSwitch.this)
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
                throw new IllegalStateException("service uninitiated");
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
            services.remove(id);
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
    private final Map<String, MyTerminal> terminals = new HashMap<>();
    private final Map<Integer, MyService> services = new HashMap<>();
    private int nextConnectionId;

    /**
     * Print out the status of all services and trunks of this switch.
     * 
     * @param out the destination for the status report
     */
    public void dump(PrintWriter out) {
        Collection<MyService> services;
        synchronized (this) {
            services = new ArrayList<>(this.services.values());
        }
        out.printf("Switch %s:%n", name);
        for (MyService conn : services)
            conn.dump(out);
        out.flush();
    }

    /**
     * Create a dummy switch.
     * 
     * @param executor used to invoke {@link ServiceListener}s and
     * {@link BridgeListener}s
     * 
     * @param name the new switch's name
     * 
     * @throws ClassNotFoundException if the back-end factory class was
     * not found
     * 
     * @throws SecurityException if the back-end factory class or its
     * constructor are inaccessible because of security restrictions
     * 
     * @throws NoSuchMethodException if the back-end factory class has
     * no default constructor
     * 
     * @throws InvocationTargetException if the back-end factory
     * constructor throws an exception
     * 
     * @throws IllegalAccessException if the back-end factory
     * constructor is inaccessible
     * 
     * @throws InstantiationException if the back-end factory class is
     * abstract
     */
    public PersistentSwitch(Executor executor, Configuration config,
                            String name)
        throws InstantiationException,
            IllegalAccessException,
            IllegalArgumentException,
            InvocationTargetException,
            NoSuchMethodException,
            SecurityException,
            ClassNotFoundException {
        this.executor = executor;
        this.name = name;

        /* Create the backend. */
        Configuration beConfig = config.subview("backend");
        try {
            this.backend = Class.forName(beConfig.get("class"))
                .asSubclass(SwitchFactory.class).getConstructor()
                .newInstance().makeSwitch(executor, beConfig);
        } catch (IllegalArgumentException ex) {
            throw new AssertionError("unreachable");
        }

        /* Record how we talk to the database. */
        Configuration dbConfig = config.subview("db");
        this.dbConnectionAddress = dbConfig.get("service");
        this.dbConnectionConfig = dbConfig.toProperties();
    }

    /**
     * Initialize the switch. Ensure the necessary tables exist in the
     * database. Recreate the internal service records mentioned in the
     * tables. Flush out any other bridges in the back-end.
     */
    public void init() {
        /* TODO: Ensure the tables exist. */

        // TODO
        throw new UnsupportedOperationException("unimplemented");
    }

    private final String dbConnectionAddress;
    private final Properties dbConnectionConfig;

    /**
     * Create a port with the given name.
     * 
     * @param name the new port's name
     * 
     * @param desc the back-end description of the port
     * 
     * @return the new port
     */
    public synchronized Terminal addPort(String name, String desc) {
        if (terminals.containsKey(name))
            throw new IllegalArgumentException("port name in use: " + name);
        MyTerminal port = new MyTerminal(name, desc);
        terminals.put(name, port);
        return port;
    }

    @Override
    public synchronized Terminal getTerminal(String id) {
        return terminals.get(id);
    }

    @Override
    public NetworkControl getControl() {
        return control;
    }

    private final NetworkControl control = new NetworkControl() {
        @Override
        public Service newService() {
            synchronized (PersistentSwitch.this) {
                int id = nextConnectionId++;
                MyService conn = new MyService(id);
                services.put(id, conn);
                return conn;
            }
        }

        @Override
        public Map<Edge<Terminal>, Double> getModel(double minimumBandwidth) {
            synchronized (PersistentSwitch.this) {
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
            synchronized (PersistentSwitch.this) {
                return services.get(id);
            }
        }

        @Override
        public Collection<Integer> getServiceIds() {
            return new HashSet<>(services.keySet());
        }

        @Override
        public String toString() {
            return "ctrl:" + name;
        }
    };

    @Override
    public Collection<String> getTerminals() {
        return new HashSet<>(terminals.keySet());
    }

    /**
     * Get a fresh connection to the database. Use this in a
     * try-with-resources block.
     * 
     * @return the new connection
     * 
     * @throws SQLException if there was an error accessing the database
     */
    private Connection database() throws SQLException {
        return DriverManager.getConnection(dbConnectionAddress,
                                           dbConnectionConfig);
    }
}
