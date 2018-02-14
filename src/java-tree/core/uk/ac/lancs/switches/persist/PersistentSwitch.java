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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.routing.span.Edge;
import uk.ac.lancs.switches.EndPoint;
import uk.ac.lancs.switches.Network;
import uk.ac.lancs.switches.NetworkControl;
import uk.ac.lancs.switches.Service;
import uk.ac.lancs.switches.ServiceDescription;
import uk.ac.lancs.switches.ServiceListener;
import uk.ac.lancs.switches.ServiceStatus;
import uk.ac.lancs.switches.Terminal;
import uk.ac.lancs.switches.TrafficFlow;
import uk.ac.lancs.switches.backend.Bridge;
import uk.ac.lancs.switches.backend.BridgeListener;
import uk.ac.lancs.switches.backend.Switch;
import uk.ac.lancs.switches.backend.SwitchFactory;

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
        private final int dbid;

        MyTerminal(String name, String desc, int dbid) {
            this.name = name;
            this.physicalPort = backend.getInterface(desc);
            this.dbid = dbid;
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

    private class MyService implements Service, BridgeListener {
        final int id;
        final Collection<ServiceListener> listeners = new HashSet<>();

        MyService(int id) {
            this.id = id;
        }

        /**
         * Records whether the user has attempted to release us.
         */
        boolean released;

        /**
         * Records the description of the service, purely for diagnostic
         * purposes. When this is set, we are no longer dormant.
         */
        ServiceDescription request;

        /**
         * Records our reference into the backend. When set, we are
         * active or activating. When not set, calling
         * {@link PersistentSwitch#retainBridges()} will ensure that our
         * underlying bridge does not exist.
         */
        Bridge bridge;

        @Override
        public synchronized void initiate(ServiceDescription request) {
            if (released) throw new IllegalStateException("service disused");
            if (this.request != null)
                throw new IllegalStateException("service in use");

            /* Sanitize the request such that every end point mentioned
             * in either set is present in both. A minimum bandwidth is
             * applied to all implicit and explicit consumers. */
            request = ServiceDescription.sanitize(request, 0.01);

            /* Check that all end points belong to us. */
            for (EndPoint ep : request.endPointFlows().keySet()) {
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
            if (bridge != null) return;
            this.bridge =
                backend.bridge(this, mapEndPoints(request.endPointFlows()));
            callOut(ServiceListener::activating);
            /* TODO: This could be a problem if the executor immediately
             * calls its task, instead of queuing it. Maybe have a
             * separate method on Bridge to actually activate the
             * bridge, then we can call it after calling activating(),
             * and ensure it is scheduled before created() calls
             * activated(). */
        }

        @Override
        public synchronized void deactivate() {
            /* We're already deactivated if in the states implied by
             * this condition. */
            if (released || request == null || bridge == null) return;

            /* Make sure that our bridge won't be retained. */
            this.bridge = null;
            callOut(ServiceListener::deactivating);
            retainBridges();
        }

        @Override
        public synchronized ServiceStatus status() {
            if (bridge != null) {
                if (active) return ServiceStatus.ACTIVE;
                return ServiceStatus.ACTIVATING;
            }
            if (active) return ServiceStatus.DEACTIVATING;
            if (request == null) return ServiceStatus.DORMANT;
            if (released) return ServiceStatus.RELEASED;
            return ServiceStatus.INACTIVE;
        }

        @Override
        public synchronized void release() {
            if (released) return;
            request = null;
            released = true;
            if (bridge != null) {
                bridge = null;
                synchronized (PersistentSwitch.this) {
                    services.remove(id);
                    retainBridges();
                }
                callOut(ServiceListener::deactivating);
            } else {
                synchronized (PersistentSwitch.this) {
                    services.remove(id);
                }
                callOut(ServiceListener::released);
            }
        }

        @Override
        public int id() {
            return id;
        }

        void recover(Map<EndPoint, TrafficFlow> details, boolean active) {
            assert this.request == null;

            /* Convert the details into a service request which we store
             * for the user to retrieve. */
            this.request = ServiceDescription.create(details);

            if (active)
                this.bridge = backend.bridge(this, mapEndPoints(details));
        }

        synchronized void dump(PrintWriter out) {
            out.printf("  %3d %-8s", id,
                       released ? "RELEASED" : request == null ? "DORMANT"
                           : active ? "ACTIVE" : "INACTIVE");
            if (request != null) {
                for (Map.Entry<? extends EndPoint, ? extends TrafficFlow> entry : request
                    .endPointFlows().entrySet()) {
                    EndPoint ep = entry.getKey();
                    TrafficFlow flow = entry.getValue();
                    out.printf("%n      %10s %6g %6g", ep, flow.ingress,
                               flow.egress);
                }
            }
            out.println();
        }

        @Override
        public synchronized NetworkControl getSwitch() {
            if (released || request == null) return null;
            return control;
        }

        @Override
        public synchronized ServiceDescription getRequest() {
            return request;
        }

        @Override
        public synchronized void error() {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        /**
         * Records the last event we got from the bridge.
         */
        boolean active;

        @Override
        public synchronized void created() {
            active = true;
            callOut(ServiceListener::activated);
        }

        @Override
        public synchronized void destroyed() {
            active = false;
            callOut(ServiceListener::deactivated);
            if (released) {
                callOut(ServiceListener::released);
                listeners.clear();
            }
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
     * Create a network mapping to a single switch, with state backed up
     * in a database.
     * 
     * <p>
     * Configuration consists of the following fields:
     * 
     * <dl>
     * 
     * <dt><samp>name</samp></dt>
     * 
     * <dd>The name of the switch, used to form the fully qualified
     * names of its terminals
     * 
     * <dt><samp>backend.class</samp></dt>
     * 
     * <dd>The name of the class implementing the back end
     * 
     * <dt><samp>db.service</samp></dt>
     * 
     * <dd>The URI of the database service
     * 
     * <dt><samp>db.slice</samp></dt>
     * 
     * <dd>The name used to distinguish this network's state in the
     * database from others'
     * 
     * <dt><samp>db.<var>misc</var></samp></dt>
     * 
     * <dd>Fields to be passed when connecting to the database service,
     * e.g., <samp>password</samp>
     * 
     * </dl>
     * 
     * @param executor used to invoke {@link ServiceListener}s and
     * {@link BridgeListener}s
     * 
     * @param config the configuration describing the network, the
     * back-end switch, and access to the database
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
    public PersistentSwitch(Executor executor, Configuration config)
        throws InstantiationException,
            IllegalAccessException,
            IllegalArgumentException,
            InvocationTargetException,
            NoSuchMethodException,
            SecurityException,
            ClassNotFoundException {
        this.executor = executor;
        this.name = config.get("name");

        /* Create the backend. */
        Configuration beConfig = config.subview("backend");
        try {
            this.backend = Class.forName(beConfig.get("class"))
                .asSubclass(SwitchFactory.class).getConstructor()
                .newInstance().makeSwitch(executor, beConfig);
        } catch (IllegalArgumentException ex) {
            /* This should not happen, because we ask for a
             * zero-argument constructor, and pass no arguments to
             * it. */
            throw new AssertionError("unreachable");
        }

        /* Record how we talk to the database. */
        Configuration dbConfig = config.subview("db");
        this.dbConnectionAddress = dbConfig.get("service");
        this.dbConnectionConfig = dbConfig.toProperties();
        this.endPointTable = dbConfig.get("end-points.table", "end_points");
        this.terminalTable = dbConfig.get("terminals.table", "terminals");
        this.serviceTable = dbConfig.get("services.table", "services");
        this.dbSlice = dbConfig.get("slice", this.name);
    }

    private final String dbSlice;
    private final String endPointTable, terminalTable, serviceTable;
    private final String dbConnectionAddress;
    private final Properties dbConnectionConfig;

    /**
     * Initialize the switch. Ensure the necessary tables exist in the
     * database. Recreate the internal service records mentioned in the
     * tables. Flush out any other bridges in the back-end.
     * 
     * @throws SQLException if there was an error in accessing the
     * database
     */
    public synchronized void init() throws SQLException {
        if (!services.isEmpty())
            throw new IllegalStateException("services already running in "
                + name);
        if (!terminals.isEmpty())
            throw new IllegalStateException("terminals already established in "
                + name);

        /* Ensure the tables exist. */
        try (Connection conn = database()) {
            try (PreparedStatement stmt =
                conn.prepareStatement("CREATE TABLE IF NOT EXISTS ?"
                    + " (slice VARCHAR(20)," + " terminal_id INT(11) UNSIGNED"
                    + " AUTO_INCREMENT PRIMARY KEY,"
                    + " name VARCHAR(20) NOT NULL,"
                    + " config VARCHAR(40) NOT NULL,"
                    + " CONSTRAINT terminals_unique UNIQUE (slice, name));")) {
                stmt.setString(1, terminalTable);
                stmt.execute();
            }

            try (PreparedStatement stmt =
                conn.prepareStatement("CREATE TABLE IF NOT EXISTS ?"
                    + " (slice VARCHAR(20)," + " service_id INT(11) UNSIGNED"
                    + " AUTO_INCREMENT PRIMARY KEY,"
                    + " intent INT(1) DEFAULT 0);")) {
                stmt.setString(1, serviceTable);
                stmt.execute();
            }

            try (PreparedStatement stmt =
                conn.prepareStatement("CREATE TABLE IF NOT EXISTS ?"
                    + " (service_id INT(11) UNSIGNED,"
                    + " FOREIGN KEY(service_id) REFERENCES ?(service_id),"
                    + " terminal_id INT(11) UNSIGNED,"
                    + " FOREIGN KEY(terminal_id) REFERENCES ?(terminal_id),"
                    + " label INT(5) UNSIGNED, " + " metering DECIMAL(9,3),"
                    + " shaping DECIMAL(9,3));")) {
                stmt.setString(1, endPointTable);
                stmt.setString(2, serviceTable);
                stmt.setString(3, terminalTable);
                stmt.execute();
            }

            /* Recreate terminals from entries in our tables. */
            try (PreparedStatement stmt =
                conn.prepareStatement("SELECT terminal_id, name, config"
                    + " FROM ? WHERE slice = ?;")) {
                stmt.setString(1, terminalTable);
                stmt.setString(2, dbSlice);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        final int id = rs.getInt(1);
                        final String name = rs.getString(2);
                        final String config = rs.getString(3);

                        MyTerminal port = new MyTerminal(name, config, id);
                        terminals.put(name, port);
                    }
                }
            }

            /* Recreate empty services from entries in our tables. */
            BitSet intents = new BitSet();
            try (PreparedStatement stmt =
                conn.prepareStatement("SELECT service_id, intent"
                    + " FROM ? WHERE slice = ?;")) {
                stmt.setString(1, serviceTable);
                stmt.setString(2, dbSlice);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        final int id = rs.getInt(1);
                        final boolean intent = rs.getBoolean(2);
                        MyService service = new MyService(id);
                        services.put(id, service);
                        if (intent) intents.set(id);
                    }
                }
            }

            /* Recover service's details. */
            Map<MyService, Map<EndPoint, TrafficFlow>> enforcements =
                new HashMap<>();
            try (PreparedStatement stmt = conn.prepareStatement("SELECT"
                + " ?.service_id AS service_id," + " ?.name AS terminal_name,"
                + " ?.label AS label," + " ?.metering AS metering,"
                + " ?.shaping AS shaping" + " FROM ?"
                + " LEFT JOIN ? ON ?.terminal_id = ?.terminal_id"
                + " WHERE ?.slice = ?;")) {
                stmt.setString(1, serviceTable);
                stmt.setString(2, terminalTable);
                stmt.setString(3, serviceTable);
                stmt.setString(4, serviceTable);
                stmt.setString(5, serviceTable);
                stmt.setString(6, endPointTable);
                stmt.setString(7, terminalTable);
                stmt.setString(8, serviceTable);
                stmt.setString(9, terminalTable);
                stmt.setString(10, terminalTable);
                stmt.setString(11, dbSlice);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        final MyService service = services.get(rs.getInt(1));
                        final MyTerminal port =
                            terminals.get(rs.getString(2));
                        final int label = rs.getInt(3);
                        final double metering = rs.getDouble(4);
                        final double shaping = rs.getDouble(5);

                        final EndPoint endPoint = port.getEndPoint(label);
                        final TrafficFlow enf =
                            TrafficFlow.of(metering, shaping);
                        enforcements
                            .computeIfAbsent(service, k -> new HashMap<>())
                            .put(endPoint, enf);
                    }
                }
            }

            /* Apply the services' details to the service objects. */
            for (Map.Entry<MyService, Map<EndPoint, TrafficFlow>> entry : enforcements
                .entrySet()) {
                MyService srv = entry.getKey();
                srv.recover(entry.getValue(), intents.get(srv.id));
            }

            /* Flush out all bridges that we didn't create, but which
             * the back-end could have. */
            retainBridges();
        }
    }

    /**
     * Ensure that we have no more bridges running than necessary.
     */
    private void retainBridges() {
        assert Thread.holdsLock(this);
        backend
            .retainBridges(services.values().stream().map(srv -> srv.bridge)
                .filter(b -> b != null).collect(Collectors.toSet()));
    }

    /**
     * Create a port with the given name.
     * 
     * @param name the new port's name
     * 
     * @param desc the back-end description of the port
     * 
     * @return the new port
     * 
     * @throws SQLException
     */
    public synchronized Terminal addPort(String name, String desc)
        throws SQLException {
        if (terminals.containsKey(name))
            throw new IllegalArgumentException("port name in use: " + name);
        try (Connection conn = database();
            PreparedStatement stmt =
                conn.prepareStatement("INSERT INTO ?"
                    + " (slice, name, config)" + " VALUES (?, ?, ?);",
                                      Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, terminalTable);
            stmt.setString(2, dbSlice);
            stmt.setString(3, name);
            stmt.setString(4, desc);
            stmt.execute();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                final int id = rs.getInt(1);
                MyTerminal port = new MyTerminal(name, desc, id);
                terminals.put(name, port);
                return port;
            }
        }
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
            return PersistentSwitch.this.newService();
        }

        @Override
        public Map<Edge<Terminal>, Double> getModel(double minimumBandwidth) {
            return PersistentSwitch.this.getModel(minimumBandwidth);
        }

        @Override
        public Service getService(int id) {
            return PersistentSwitch.this.getService(id);
        }

        @Override
        public Collection<Integer> getServiceIds() {
            return PersistentSwitch.this.getServiceIds();
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

    // Shadowing NetworkControl
    synchronized Service newService() {
        int id = nextConnectionId++;
        MyService conn = new MyService(id);
        services.put(id, conn);
        return conn;
    }

    // Shadowing NetworkControl
    synchronized Service getService(int id) {
        return services.get(id);
    }

    // Shadowing NetworkControl
    synchronized Collection<Integer> getServiceIds() {
        return new HashSet<>(services.keySet());
    }

    // Shadowing NetworkControl
    synchronized Map<Edge<Terminal>, Double>
        getModel(double minimumBandwidth) {
        List<Terminal> list = new ArrayList<>(terminals.values());
        Map<Edge<Terminal>, Double> result = new HashMap<>();
        int size = list.size();
        for (int i = 0; i < size - 1; i++) {
            for (int j = i + 1; j < size; j++) {
                Edge<Terminal> edge = Edge.of(list.get(i), list.get(j));
                result.put(edge, 0.001);
            }
        }
        return result;
    }

    private EndPoint mapEndPoint(EndPoint ep) {
        MyTerminal port = (MyTerminal) ep.getTerminal();
        return port.getInnerEndPoint(ep.getLabel());
    }

    private <V> Map<EndPoint, V>
        mapEndPoints(Map<? extends EndPoint, ? extends V> input) {
        return input.entrySet().stream().collect(Collectors
            .toMap(e -> mapEndPoint(e.getKey()), Map.Entry::getValue));
    }
}
