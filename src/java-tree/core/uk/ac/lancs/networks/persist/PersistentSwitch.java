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
package uk.ac.lancs.networks.persist;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.networks.InvalidServiceException;
import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.Service;
import uk.ac.lancs.networks.ServiceDescription;
import uk.ac.lancs.networks.ServiceListener;
import uk.ac.lancs.networks.ServiceResourceException;
import uk.ac.lancs.networks.ServiceStatus;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.TrafficFlow;
import uk.ac.lancs.networks.end_points.EndPoint;
import uk.ac.lancs.networks.fabric.Bridge;
import uk.ac.lancs.networks.fabric.BridgeListener;
import uk.ac.lancs.networks.fabric.Fabric;
import uk.ac.lancs.networks.fabric.FabricContext;
import uk.ac.lancs.networks.fabric.FabricFactory;
import uk.ac.lancs.networks.fabric.Interface;
import uk.ac.lancs.networks.mgmt.ManagedSwitch;
import uk.ac.lancs.routing.span.Edge;

/**
 * Implements a network that retains its state in a database. The
 * network relies on a {@link Fabric} back end to establish bridges on a
 * physical switch in a vendor-specific way.
 * 
 * @author simpsons
 */
public class PersistentSwitch implements ManagedSwitch {
    private final Fabric backend;

    private class MyTerminal implements Terminal {
        private final String name;
        private final Interface physicalPort;
        private final int dbid;

        MyTerminal(String name, String desc, int dbid) {
            this.name = name;
            this.physicalPort = backend.getInterface(desc);
            this.dbid = dbid;
        }

        EndPoint<Interface> getInnerEndPoint(int label) {
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

        @Override
        public String toString() {
            return PersistentSwitch.this.name + ":" + id;
        }

        MyService(int id) {
            this.id = id;
            self = protect(BridgeListener.class, this);
        }

        final BridgeListener self;

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
         * underlying bridge does not exist. When set, we are either
         * activating or activated.
         */
        Bridge bridge;

        @Override
        public synchronized void initiate(ServiceDescription request)
            throws InvalidServiceException {
            if (released) throw new IllegalStateException("service disused");
            if (this.request != null)
                throw new IllegalStateException("service in use");

            /* Check that all end points belong to us. */
            for (EndPoint<? extends Terminal> ep : request.endPointFlows()
                .keySet()) {
                Terminal p = ep.getBundle();
                if (!(p instanceof MyTerminal))
                    throw new InvalidServiceException("end point " + ep
                        + " not part of " + name);
                MyTerminal mp = (MyTerminal) p;
                if (mp.owner() != PersistentSwitch.this)
                    throw new InvalidServiceException("end point " + ep
                        + " not part of " + name);
            }

            /* Sanitize the request such that no end point produces less
             * than a token amount, and that no end point's egress rate
             * is greater than the sum of the others' ingress rates. */
            this.request = ServiceDescription.sanitize(request, 0.01);

            /* Add the details to the database. */
            try (Connection conn = database()) {
                conn.setAutoCommit(false);
                try (PreparedStatement stmt =
                    conn.prepareStatement("INSERT INTO " + endPointTable
                        + " (service_id, terminal_id,"
                        + " label, metering, shaping)"
                        + " VALUES (?, ?, ?, ?, ?);")) {
                    stmt.setInt(1, this.id);
                    for (Map.Entry<? extends EndPoint<? extends Terminal>, ? extends TrafficFlow> entry : this.request
                        .endPointFlows().entrySet()) {
                        EndPoint<? extends Terminal> endPoint =
                            entry.getKey();
                        MyTerminal terminal =
                            (MyTerminal) endPoint.getBundle();
                        TrafficFlow flow = entry.getValue();
                        stmt.setInt(2, terminal.dbid);
                        stmt.setInt(3, endPoint.getLabel());
                        stmt.setDouble(4, flow.ingress);
                        stmt.setDouble(5, flow.egress);
                        stmt.execute();
                    }
                }
                conn.commit();
            } catch (SQLException ex) {
                throw new ServiceResourceException("persistence failure", ex);
            }

            /* We are ready as soon as we have the information. */
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
                throw new IllegalStateException("service uninitiated");
            if (bridge != null) return;

            /* Record the user's intent for this service. */
            try (Connection conn = database()) {
                updateIntent(conn, id, true);
            } catch (SQLException ex) {
                throw new ServiceResourceException("failed to store intent",
                                                   ex);
            }

            this.bridge =
                backend.bridge(self, mapEndPoints(request.endPointFlows()));
            callOut(ServiceListener::activating);
            this.bridge.start();
        }

        @Override
        public synchronized void deactivate() {
            /* We're already deactivated/deactivating if in the states
             * implied by this condition. */
            if (released || request == null || bridge == null) return;

            /* Record the user's intent for this service. */
            try (Connection conn = database()) {
                updateIntent(conn, id, false);
            } catch (SQLException ex) {
                throw new ServiceResourceException("failed to store intent",
                                                   ex);
            }

            /* Make sure that our bridge won't be retained. */
            this.bridge = null;
            callOut(ServiceListener::deactivating);
            synchronized (PersistentSwitch.this) {
                retainBridges();
            }
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
            Bridge oldBridge = bridge;
            bridge = null;

            /* Delete entries from the database. */
            try (Connection conn = database()) {
                conn.setAutoCommit(false);
                try (PreparedStatement stmt =
                    conn.prepareStatement("DELETE FROM " + endPointTable
                        + " WHERE service_id = ?;")) {
                    stmt.setInt(1, id);
                    stmt.execute();
                }
                try (PreparedStatement stmt =
                    conn.prepareStatement("DELETE FROM " + serviceTable
                        + " WHERE service_id = ?;")) {
                    stmt.setInt(1, id);
                    stmt.execute();
                }
                conn.commit();
            } catch (SQLException ex) {
                throw new ServiceResourceException("releasing", ex);
            }

            if (oldBridge != null) {
                /* We must notify the user before destroying the bridge,
                 * so that the 'deactivating' event arrives before the
                 * 'deactivating' one. */
                callOut(ServiceListener::deactivating);
                synchronized (PersistentSwitch.this) {
                    services.remove(id);
                    retainBridges();
                }
            } else {
                synchronized (PersistentSwitch.this) {
                    services.remove(id);
                }
                callOut(ServiceListener::released);
            }
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
            if (active) return;
            active = true;
            callOut(ServiceListener::activated);
        }

        @Override
        public synchronized void destroyed() {
            if (!active) return;
            active = false;
            callOut(ServiceListener::deactivated);
            if (released) {
                callOut(ServiceListener::released);
                listeners.clear();
            }
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
        public synchronized NetworkControl getSwitch() {
            if (released || request == null) return null;
            return control;
        }

        @Override
        public synchronized ServiceDescription getRequest() {
            return request;
        }

        @Override
        public int id() {
            return id;
        }

        void recover(Map<EndPoint<? extends Terminal>, TrafficFlow> details,
                     boolean active) {
            assert this.request == null;

            /* Convert the details into a service request which we store
             * for the user to retrieve. */
            this.request = ServiceDescription.create(details);

            if (active) {
                this.active = true;
                this.bridge = backend.bridge(self, mapEndPoints(details));
                this.bridge.start();
            }
        }
    }

    private final Executor executor;
    private final String name;
    private final Map<String, MyTerminal> terminals = new HashMap<>();
    private final Map<Integer, MyService> services = new HashMap<>();

    /**
     * Print out the status of all services and trunks of this switch.
     * 
     * @param out the destination for the status report
     */
    @Override
    public void dumpStatus(PrintWriter out) {
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
     * <dt><samp>backend.type</samp></dt>
     * 
     * <dd>The back-end type to be recognized by
     * {@link FabricFactory#recognize(String)}
     * 
     * <dt><samp>backend.<var>misc</var></samp></dt>
     * 
     * <dd>Other parameters used to configure the backend
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
     * @param executor used to invoke call-backs created by this network
     * and passed to the backend
     * 
     * @param config the configuration describing the network, the
     * back-end switch, and access to the database
     * 
     * @throws IllegalArgumentException if no factory recognizes the
     * back-end type
     */
    public PersistentSwitch(Executor executor, Configuration config) {
        this.executor = executor;
        this.name = config.get("name");

        /* Create the backend. */
        Configuration beConfig = config.subview("backend");
        String type = beConfig.get("type");
        Fabric zwitch = null;
        for (FabricFactory factory : ServiceLoader
            .load(FabricFactory.class)) {
            if (!factory.recognize(type)) continue;
            FabricContext ctxt = new FabricContext() {
                @Override
                public Executor executor() {
                    return executor;
                }
            };
            zwitch = factory.makeSwitch(ctxt, beConfig);
            break;
        }
        if (zwitch == null) throw new IllegalArgumentException();
        this.backend = zwitch;

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
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS " + terminalTable
                    + " (slice VARCHAR(20),"
                    + " terminal_id INTEGER PRIMARY KEY,"
                    + " name VARCHAR(20) NOT NULL,"
                    + " config VARCHAR(40) NOT NULL,"
                    + " CONSTRAINT terminals_unique UNIQUE (slice, name));");
                stmt.execute("CREATE TABLE IF NOT EXISTS " + serviceTable
                    + " (slice VARCHAR(20),"
                    + " service_id INTEGER PRIMARY KEY,"
                    + " intent INT UNSIGNED DEFAULT 0);");
                stmt.execute("CREATE TABLE IF NOT EXISTS " + endPointTable
                    + " (service_id INTEGER," + " terminal_id INTEGER,"
                    + " label INTEGER UNSIGNED," + " metering DECIMAL(9,3),"
                    + " shaping DECIMAL(9,3)," + " FOREIGN KEY(service_id)"
                    + " REFERENCES services(service_id),"
                    + " FOREIGN KEY(terminal_id)"
                    + " REFERENCES terminals(terminal_id));");
            }

            /* Recreate terminals from entries in our tables. */
            try (PreparedStatement stmt =
                conn.prepareStatement("SELECT terminal_id, name, config"
                    + " FROM " + terminalTable + " WHERE slice = ?;")) {
                stmt.setString(1, dbSlice);
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
                conn.prepareStatement("SELECT service_id, intent" + " FROM "
                    + serviceTable + " WHERE slice = ?;")) {
                stmt.setString(1, dbSlice);
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
            Map<MyService, Map<EndPoint<? extends Terminal>, TrafficFlow>> enforcements =
                new HashMap<>();
            try (PreparedStatement stmt = conn.prepareStatement("SELECT "
                + endPointTable + ".service_id AS service_id, "
                + terminalTable + ".name AS terminal_name, " + endPointTable
                + ".label AS label, " + endPointTable
                + ".metering AS metering, " + endPointTable
                + ".shaping AS shaping" + " FROM " + endPointTable
                + " LEFT JOIN " + terminalTable + " ON " + terminalTable
                + ".terminal_id = " + endPointTable + ".terminal_id"
                + " WHERE " + terminalTable + ".slice = ?;")) {
                stmt.setString(1, dbSlice);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        final MyService service = services.get(rs.getInt(1));
                        final MyTerminal port =
                            terminals.get(rs.getString(2));
                        final int label = rs.getInt(3);
                        final double metering = rs.getDouble(4);
                        final double shaping = rs.getDouble(5);

                        final EndPoint<? extends Terminal> endPoint =
                            port.getEndPoint(label);
                        final TrafficFlow enf =
                            TrafficFlow.of(metering, shaping);
                        enforcements
                            .computeIfAbsent(service, k -> new HashMap<>())
                            .put(endPoint, enf);
                    }
                }
            }

            conn.commit();

            /* Apply the services' details to the service objects. */
            for (Map.Entry<MyService, Map<EndPoint<? extends Terminal>, TrafficFlow>> entry : enforcements
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

    @Override
    public synchronized Terminal addTerminal(String name, String desc) {
        if (terminals.containsKey(name))
            throw new IllegalArgumentException("terminal name in use: "
                + name);
        try (Connection conn = database();
            PreparedStatement stmt =
                conn.prepareStatement("INSERT INTO " + terminalTable
                    + " (slice, name, config)" + " VALUES (?, ?, ?);",
                                      Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, dbSlice);
            stmt.setString(2, name);
            stmt.setString(3, desc);
            stmt.execute();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                final int id = rs.getInt(1);
                MyTerminal terminal = new MyTerminal(name, desc, id);
                terminals.put(name, terminal);
                return terminal;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("could not create terminal " + name
                + " on " + desc + " in database", ex);
        }
    }

    @Override
    public synchronized void removeTerminal(String name) {
        MyTerminal terminal = terminals.get(name);
        if (terminal == null)
            throw new IllegalArgumentException("no such terminal: " + name);
        try (Connection conn = database();
            PreparedStatement stmt = conn.prepareStatement("DELETE FROM "
                + terminalTable + " WHERE terminal_id = ?;")) {
            stmt.setInt(1, terminal.dbid);
            stmt.execute();
        } catch (SQLException ex) {
            throw new RuntimeException("could not remove terminal " + name
                + " from database", ex);
        }
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    @Override
    public synchronized Terminal getTerminal(String id) {
        return terminals.get(id);
    }

    @Override
    public NetworkControl getControl() {
        return control;
    }

    /**
     * Provides a controller view of this switch. Most methods simply
     * call out to a shadow method in the containing class, to reduce
     * lexical nesting and simplify synchronization.
     */
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
    public synchronized Collection<String> getTerminals() {
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
        final int id;
        try (Connection conn = database();
            PreparedStatement stmt =
                conn.prepareStatement("INSERT INTO " + serviceTable
                    + " (slice) VALUES (?);",
                                      Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, dbSlice);
            stmt.execute();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    id = rs.getInt(1);
                } else {
                    throw new RuntimeException("I got no id from "
                        + "creation of service; WTF?");
                }
            }
        } catch (SQLException ex) {
            throw new ServiceResourceException("creating service", ex);
        }
        MyService srv = new MyService(id);
        services.put(id, srv);
        return srv;
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

    private EndPoint<Interface> mapEndPoint(EndPoint<? extends Terminal> ep) {
        MyTerminal terminal = (MyTerminal) ep.getBundle();
        return terminal.getInnerEndPoint(ep.getLabel());
    }

    private <V> Map<EndPoint<Interface>, V>
        mapEndPoints(Map<? extends EndPoint<? extends Terminal>, ? extends V> input) {
        return input.entrySet().stream().collect(Collectors
            .toMap(e -> mapEndPoint(e.getKey()), Map.Entry::getValue));
    }

    @SuppressWarnings("unchecked")
    private <I> I protect(Class<I> type, I base) {
        InvocationHandler h = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
                if (method.getReturnType() != null)
                    return method.invoke(base, args);
                executor.execute(() -> {
                    try {
                        method.invoke(base, args);
                    } catch (IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException e) {
                        throw new AssertionError("unreachable", e);
                    }
                });
                return null;
            }
        };
        return (I) Proxy.newProxyInstance(type.getClassLoader(),
                                          new Class<?>[]
                                          { type }, h);
    }

    private void updateIntent(Connection conn, int srvid, boolean status)
        throws SQLException {
        try (PreparedStatement stmt =
            conn.prepareStatement("UPDATE " + serviceTable
                + " SET intent = ? WHERE slice = ? AND service_id = ?;")) {
            stmt.setInt(1, status ? 1 : 0);
            stmt.setString(2, dbSlice);
            stmt.setInt(3, srvid);
            stmt.execute();
        }
    }

    @SuppressWarnings("unused")
    private static boolean debugStatement(Statement stmt, String text)
        throws SQLException {
        // System.err.printf("Executing %s%n", text);
        return stmt.execute(text);
    }

    @SuppressWarnings("unused")
    private static PreparedStatement
        debugStatement(Connection conn, String text) throws SQLException {
        // System.err.printf("Preparing: %s%n", text);
        return conn.prepareStatement(text);
    }
}
