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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
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
import uk.ac.lancs.networks.circuits.Circuit;
import uk.ac.lancs.networks.fabric.Bridge;
import uk.ac.lancs.networks.fabric.BridgeListener;
import uk.ac.lancs.networks.fabric.Fabric;
import uk.ac.lancs.networks.fabric.Interface;
import uk.ac.lancs.networks.mgmt.NetworkResourceException;
import uk.ac.lancs.networks.mgmt.Switch;
import uk.ac.lancs.routing.span.Edge;

/**
 * Implements a switch that retains its state in a database. The switch
 * relies on a {@link Fabric} back end to establish bridges on a
 * physical switch in a vendor-specific way.
 * 
 * @author simpsons
 */
public class PersistentSwitch implements Switch {
    private enum Intent {
        INACTIVE, ACTIVE, ABORT, RELEASE;
    }

    private Fabric fabric;

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
         * Becomes true when the RELEASE intent is accomplished. At the
         * same time, this service is removed from the switch's index.
         */
        boolean released;

        Intent intent = Intent.INACTIVE;

        /**
         * Records the description of the service, purely for diagnostic
         * purposes. When this is set, we are no longer dormant.
         */
        ServiceDescription request;

        /**
         * Records our reference into the fabric. When set, we are
         * active or activating. When not set, calling
         * {@link PersistentSwitch#retainBridges()} will ensure that our
         * underlying bridge does not exist. When set, we are either
         * activating or activated.
         */
        Bridge bridge;

        /**
         * Records the last event we got from the bridge.
         */
        boolean active;

        final Collection<Throwable> bridgeErrors = new HashSet<>();

        @Override
        public synchronized void initiate(ServiceDescription request)
            throws InvalidServiceException {
            switch (intent) {
            case RELEASE:
                throw new IllegalStateException("service disused");
            case ABORT:
                throw new IllegalStateException("service aborted");
            default:
                break;
            }
            if (this.request != null)
                throw new IllegalStateException("service in use");

            /* Check that all circuits belong to us. */
            for (Circuit<? extends Terminal> ep : request.circuitFlows()
                .keySet()) {
                Terminal p = ep.getBundle();
                if (!(p instanceof SwitchTerminal))
                    throw new InvalidServiceException("circuit " + ep
                        + " not part of " + name);
                SwitchTerminal mp = (SwitchTerminal) p;
                if (mp.getNetwork() != getControl())
                    throw new InvalidServiceException("circuit " + ep
                        + " not part of " + name);
            }

            /* Sanitize the request such that no circuit produces less
             * than a token amount, and that no circuit's egress rate is
             * greater than the sum of the others' ingress rates. */
            this.request = ServiceDescription.sanitize(request, 0.01);

            callOut(ServiceStatus.ESTABLISHING);

            /* Add the details to the database. */
            try (Connection conn = database()) {
                conn.setAutoCommit(false);
                try (PreparedStatement stmt =
                    conn.prepareStatement("INSERT INTO " + endPointTable
                        + " (service_id, terminal_id,"
                        + " label, metering, shaping)"
                        + " VALUES (?, ?, ?, ?, ?);")) {
                    stmt.setInt(1, this.id);
                    for (Map.Entry<? extends Circuit<? extends Terminal>, ? extends TrafficFlow> entry : this.request
                        .circuitFlows().entrySet()) {
                        Circuit<? extends Terminal> endPoint = entry.getKey();
                        SwitchTerminal terminal =
                            (SwitchTerminal) endPoint.getBundle();
                        TrafficFlow flow = entry.getValue();
                        stmt.setInt(2, terminal.id());
                        stmt.setInt(3, endPoint.getLabel());
                        stmt.setDouble(4, flow.ingress);
                        stmt.setDouble(5, flow.egress);
                        stmt.execute();
                    }
                }
                conn.commit();
            } catch (SQLException ex) {
                throw new ServiceResourceException("DB failure", ex);
            }

            /* We are ready as soon as we have the information. */
            callOut(ServiceStatus.INACTIVE);
            if (intent == Intent.ACTIVE) completeActivation();
        }

        @Override
        public synchronized void addListener(ServiceListener events) {
            listeners.add(events);
        }

        @Override
        public synchronized void removeListener(ServiceListener events) {
            listeners.remove(events);
        }

        private void callOut(ServiceStatus status) {
            listeners.forEach(l -> l.newStatus(status));
        }

        @Override
        public synchronized void activate() {
            /* Check the user's intent. If already activated, do
             * nothing. */
            switch (intent) {
            case RELEASE:
                throw new IllegalStateException("service released");
            case ABORT:
                throw new IllegalStateException("service aborted");
            case ACTIVE:
                return;
            default:
                break;
            }

            try (Connection conn = database()) {
                conn.setAutoCommit(false);

                /* Record the user's intent for this service. */
                updateIntent(conn, id, intent = Intent.ACTIVE);
                conn.commit();
            } catch (SQLException ex) {
                throw new ServiceResourceException("failed to store intent",
                                                   ex);
            }

            completeActivation();
        }

        void completeActivation() {
            assert Thread.holdsLock(this);
            this.bridge =
                fabric.bridge(self, mapEndPoints(request.circuitFlows()));
            callOut(ServiceStatus.ACTIVATING);
            this.bridge.start();
        }

        @Override
        public synchronized void deactivate() {
            /* We're already deactivated/deactivating if in the states
             * implied by this condition. */
            if (request == null || bridge == null) return;
            switch (intent) {
            case RELEASE:
            case ABORT:
            case INACTIVE:
                return;
            default:
                break;
            }

            /* Record the user's intent for this service. */
            try (Connection conn = database()) {
                updateIntent(conn, id, intent = Intent.INACTIVE);
            } catch (SQLException ex) {
                throw new ServiceResourceException("failed to store intent",
                                                   ex);
            }

            /* Make sure that our bridge won't be retained. */
            this.bridge = null;
            callOut(ServiceStatus.DEACTIVATING);
            synchronized (PersistentSwitch.this) {
                retainBridges();
            }
        }

        @Override
        public synchronized ServiceStatus status() {
            if (intent == Intent.RELEASE) return released
                ? ServiceStatus.RELEASED : ServiceStatus.RELEASING;
            if (intent == Intent.ABORT) return ServiceStatus.FAILED;
            if (intent == Intent.INACTIVE) {
                if (request == null) return ServiceStatus.DORMANT;
                if (bridge != null) return ServiceStatus.DEACTIVATING;
                return ServiceStatus.INACTIVE;
            }
            assert intent == Intent.ACTIVE;
            if (bridge != null) {
                if (active) return ServiceStatus.ACTIVE;
                return ServiceStatus.ACTIVATING;
            }
            if (active) return ServiceStatus.DEACTIVATING;
            if (request == null) return ServiceStatus.DORMANT;
            return ServiceStatus.INACTIVE;
        }

        @Override
        public synchronized void release() {
            if (intent == Intent.RELEASE) return;
            request = null;
            Bridge oldBridge = bridge;
            bridge = null;

            /* Delete entries from the database. */
            try (Connection conn = database()) {
                conn.setAutoCommit(false);
                updateIntent(conn, id, intent = Intent.RELEASE);
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
                callOut(ServiceStatus.DEACTIVATING);
                synchronized (PersistentSwitch.this) {
                    retainBridges();
                }
            } else {
                synchronized (PersistentSwitch.this) {
                    services.remove(id);
                }
                released = true;
                callOut(ServiceStatus.RELEASED);
            }
        }

        @Override
        public synchronized void error(Throwable t) {
            bridgeErrors.add(t);
            if (intent == Intent.ABORT || intent == Intent.RELEASE) return;
            active = false;
            if (intent != Intent.RELEASE) {
                try (Connection conn = database()) {
                    updateIntent(conn, id, intent = Intent.ABORT);
                } catch (SQLException ex) {
                    throw new ServiceResourceException("failed to store intent",
                                                       ex);
                }
            }
            callOut(ServiceStatus.FAILED);
        }

        @Override
        public synchronized void created() {
            /* Detect redundant calls. */
            if (active) return;
            active = true;

            callOut(ServiceStatus.ACTIVE);
        }

        @Override
        public synchronized void destroyed() {
            /* Detect redundant calls. */
            if (!active) return;
            active = false;

            if (intent == Intent.RELEASE) {
                callOut(ServiceStatus.RELEASING);
                synchronized (PersistentSwitch.this) {
                    services.remove(id);
                }
                released = true;
                callOut(ServiceStatus.RELEASED);
                listeners.clear();
            } else {
                callOut(ServiceStatus.INACTIVE);
            }
        }

        synchronized void dump(PrintWriter out) {
            out.printf("  %3d %-8s (intent=%-8s)", id, status(), intent);
            if (request != null) {
                for (Map.Entry<? extends Circuit<? extends Terminal>, ? extends TrafficFlow> entry : request
                    .circuitFlows().entrySet()) {
                    Circuit<? extends Terminal> ep = entry.getKey();
                    TrafficFlow flow = entry.getValue();
                    out.printf("%n      %10s %6g %6g", ep, flow.ingress,
                               flow.egress);
                }
            }
            out.println();
        }

        @Override
        public synchronized NetworkControl getNetwork() {
            if (intent == Intent.RELEASE) return null;
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

        void recover(Map<Circuit<? extends Terminal>, TrafficFlow> details,
                     Intent intent) {
            assert this.request == null;

            /* Convert the details into a service request which we store
             * for the user to retrieve. */
            this.request = ServiceDescription.create(details);

            this.intent = intent;
            if (intent == Intent.ACTIVE) {
                this.bridge = fabric.bridge(self, mapEndPoints(details));
                this.bridge.start();
            }
        }

        @Override
        public synchronized Collection<Throwable> errors() {
            return new HashSet<>(bridgeErrors);
        }
    }

    private final Executor executor;
    private final String name;
    private final Map<String, SwitchTerminal> terminals = new HashMap<>();
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
     * <dt><samp>fabric.agent</samp></dt>
     * <dt><samp>fabric.agent.key</samp></dt>
     * 
     * <dd>The name of an agent providing a {@link Fabric} service, and
     * optionally a key within that agent
     * 
     * <dt><samp>fabric.<var>misc</var></samp></dt>
     * 
     * <dd>Other parameters used to configure the fabric
     * 
     * <dt><samp>db.service</samp></dt>
     * 
     * <dd>The URI of the database service
     * 
     * <dt><samp>db.<var>misc</var></samp></dt>
     * 
     * <dd>Fields to be passed when connecting to the database service,
     * e.g., <samp>password</samp>
     * 
     * </dl>
     * 
     * @param executor used to invoke call-backs created by this network
     * and passed to the fabric
     * 
     * @param dbConfig the configuration describing access to the
     * database
     * 
     * @throws IllegalArgumentException if no factory recognizes the
     * back-end type
     */
    public PersistentSwitch(String name, Executor executor,
                            Configuration dbConfig) {
        this.executor = executor;
        this.name = name;

        /* Record how we talk to the database. */
        this.dbConnectionAddress = dbConfig.get("service");
        this.dbConnectionConfig = dbConfig.toProperties();
        this.endPointTable = dbConfig.get("end-points.table", "end_points");
        this.terminalTable = dbConfig.get("terminals.table", "terminals");
        this.serviceTable = dbConfig.get("services.table", "services");
    }

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
    public synchronized void init(Fabric fabric) throws SQLException {
        this.fabric = fabric;
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
                    + " (terminal_id INTEGER PRIMARY KEY,"
                    + " name VARCHAR(20) NOT NULL UNIQUE,"
                    + " config VARCHAR(40) NOT NULL);");
                stmt.execute("CREATE TABLE IF NOT EXISTS " + serviceTable
                    + " (service_id INTEGER PRIMARY KEY,"
                    + " intent INT UNSIGNED DEFAULT 0);");
                stmt.execute("CREATE TABLE IF NOT EXISTS " + endPointTable
                    + " (service_id INTEGER," + " terminal_id INTEGER,"
                    + " label INTEGER UNSIGNED," + " metering DECIMAL(9,3),"
                    + " shaping DECIMAL(9,3)," + " FOREIGN KEY(service_id)"
                    + " REFERENCES " + serviceTable + "(service_id),"
                    + " FOREIGN KEY(terminal_id)" + " REFERENCES "
                    + terminalTable + "(terminal_id));");
            }

            /* Recreate terminals from entries in our tables. */
            try (Statement stmt = conn.createStatement();
                ResultSet rs =
                    stmt.executeQuery("SELECT terminal_id, name, config"
                        + " FROM " + terminalTable + ";")) {
                while (rs.next()) {
                    final int id = rs.getInt(1);
                    final String name = rs.getString(2);
                    final String config = rs.getString(3);

                    SwitchTerminal port =
                        new SwitchTerminal(getControl(), name,
                                           fabric.getInterface(config), id);
                    terminals.put(name, port);
                }
            }

            /* Recreate empty services from entries in our tables. */
            Map<Integer, Intent> intents = new HashMap<>();
            try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT service_id, intent"
                    + " FROM " + serviceTable + ";")) {
                while (rs.next()) {
                    final int id = rs.getInt(1);
                    final Intent intent = Intent.values()[rs.getInt(2)];
                    MyService service = new MyService(id);
                    services.put(id, service);
                    intents.put(id, intent);
                }
            }

            /* Recover service's details. */
            Map<MyService, Map<Circuit<? extends Terminal>, TrafficFlow>> enforcements =
                new HashMap<>();
            try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt
                    .executeQuery("SELECT" + " et.service_id AS service_id,"
                        + " tt.name AS terminal_name," + " et.label AS label,"
                        + " et.metering AS metering,"
                        + " et.shaping AS shaping" + " FROM " + endPointTable
                        + " AS et" + " LEFT JOIN " + terminalTable + " AS tt"
                        + " ON tt.terminal_id = et.terminal_id;")) {
                while (rs.next()) {
                    final MyService service = services.get(rs.getInt(1));
                    final SwitchTerminal port =
                        terminals.get(rs.getString(2));
                    final int label = rs.getInt(3);
                    final double metering = rs.getDouble(4);
                    final double shaping = rs.getDouble(5);

                    final Circuit<? extends Terminal> endPoint =
                        port.circuit(label);
                    final TrafficFlow enf = TrafficFlow.of(metering, shaping);
                    enforcements
                        .computeIfAbsent(service, k -> new HashMap<>())
                        .put(endPoint, enf);
                }
            }

            conn.commit();

            /* Apply the services' details to the service objects. */
            for (Map.Entry<MyService, Map<Circuit<? extends Terminal>, TrafficFlow>> entry : enforcements
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
        fabric.retainBridges(services.values().stream().map(srv -> srv.bridge)
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
                    + " (name, config)" + " VALUES (?, ?);",
                                      Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, desc);
            stmt.execute();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (!rs.next())
                    throw new NetworkResourceException("id unreported"
                        + " for new terminal " + name);
                final int id = rs.getInt(1);
                SwitchTerminal terminal =
                    new SwitchTerminal(getControl(), name,
                                       fabric.getInterface(desc), id);
                terminals.put(name, terminal);
                return terminal;
            }
        } catch (SQLException ex) {
            throw new NetworkResourceException("DB failure"
                + " in creating terminal " + name + " on " + desc
                + " in database", ex);
        }
    }

    @Override
    public synchronized void removeTerminal(String name) {
        SwitchTerminal terminal = terminals.get(name);
        if (terminal == null)
            throw new IllegalArgumentException("no such terminal: " + name);
        try (Connection conn = database();
            PreparedStatement stmt = conn.prepareStatement("DELETE FROM "
                + terminalTable + " WHERE terminal_id = ?;")) {
            stmt.setInt(1, terminal.id());
            stmt.execute();
            terminals.remove(name);
        } catch (SQLException ex) {
            throw new NetworkResourceException("DB failure"
                + " in removing terminal " + name, ex);
        }
    }

    private synchronized Terminal getTerminal(String id) {
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
        public Terminal getTerminal(String id) {
            return PersistentSwitch.this.getTerminal(id);
        }

        @Override
        public Collection<String> getTerminals() {
            return PersistentSwitch.this.getTerminals();
        }

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

        @Override
        public String name() {
            return name;
        }
    };

    private synchronized Collection<String> getTerminals() {
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
                    + " (intent) VALUES (?);",
                                      Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, Intent.INACTIVE.ordinal());
            stmt.execute();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (!rs.next())
                    throw new ServiceResourceException("id unreported"
                        + " for new service");
                id = rs.getInt(1);
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
        final long fabricCapacity = fabric.capacity();
        if (fabricCapacity >= 0) {
            final long bridgeCapacity = fabric.capacity() - services.values()
                .stream().filter(s -> s.bridge == null).count();
            if (bridgeCapacity <= 0) return Collections.emptyMap();
        }

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

    private Circuit<? extends Interface<?>>
        mapEndPoint(Circuit<? extends Terminal> ep) {
        SwitchTerminal terminal = (SwitchTerminal) ep.getBundle();
        return terminal.getInnerEndPoint(ep.getLabel());
    }

    private <V> Map<Circuit<? extends Interface<?>>, V>
        mapEndPoints(Map<? extends Circuit<? extends Terminal>, ? extends V> input) {
        return input.entrySet().stream().collect(Collectors
            .toMap(e -> mapEndPoint(e.getKey()), Map.Entry::getValue));
    }

    @SuppressWarnings("unchecked")
    private <I> I protect(Class<I> type, I base) {
        InvocationHandler h = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
                if (method.getReturnType() != Void.TYPE)
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

    private void updateIntent(Connection conn, int srvid, Intent intent)
        throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE "
            + serviceTable + " SET intent = ? WHERE service_id = ?;")) {
            stmt.setInt(1, intent.ordinal());
            stmt.setInt(2, srvid);
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
