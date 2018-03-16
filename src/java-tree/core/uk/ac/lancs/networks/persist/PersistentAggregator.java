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
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
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
import uk.ac.lancs.networks.mgmt.Aggregator;
import uk.ac.lancs.networks.mgmt.Trunk;
import uk.ac.lancs.routing.span.DistanceVectorComputer;
import uk.ac.lancs.routing.span.Edge;
import uk.ac.lancs.routing.span.FIBSpanGuide;
import uk.ac.lancs.routing.span.Graphs;
import uk.ac.lancs.routing.span.SpanningTreeComputer;
import uk.ac.lancs.routing.span.Way;

/**
 * Implements a network aggregator that retains its state in a database.
 * 
 * <p>
 * TODO: Modify it so it does what it says!
 * 
 * @author simpsons
 */
public class PersistentAggregator implements Aggregator {
    private class MyTerminal implements Terminal {
        private final String name;
        private final Terminal innerPort;
        private final int dbid;

        MyTerminal(String name, Terminal innerPort, int dbid) {
            this.name = name;
            this.innerPort = innerPort;
            this.dbid = dbid;
        }

        public Terminal innerPort() {
            return innerPort;
        }

        @Override
        public NetworkControl getNetwork() {
            return getControl();
        }

        PersistentAggregator owner() {
            return PersistentAggregator.this;
        }

        @Override
        public String toString() {
            return PersistentAggregator.this.name + ":" + name;
        }

        @Override
        public String name() {
            return name;
        }
    }

    private class MyService implements Service {
        private class Client implements ServiceListener {
            final Service subservice;

            /**
             * Accumulates errors on each end point.
             */
            Map<EndPoint<? extends Terminal>, Collection<Throwable>> endPointErrors =
                new HashMap<>();
            Collection<Throwable> globalErrors = new HashSet<>();
            boolean active;

            boolean inErrorState() {
                return !endPointErrors.isEmpty() || !globalErrors.isEmpty();
            }

            Client(Service subservice) {
                this.subservice = subservice;
            }

            /**
             * Ensure the subservice can talk back to this client.
             */
            void init() {
                this.subservice
                    .addListener(protect(ServiceListener.class, this));
            }

            @Override
            public void ready() {
                clientReady(this);
            }

            @Override
            public void
                failed(Collection<? extends EndPoint<? extends Terminal>> locations,
                       Throwable t) {
                clientFailed(this, locations, t);
            }

            @Override
            public void activated() {
                clientActivated(this);
            }

            @Override
            public void deactivated() {
                clientDeactivated(this);
            }

            @Override
            public void released() {
                clientReleased(this);
            }

            void activate() {
                assert Thread.holdsLock(MyService.this);
                subservice.activate();
            }

            void deactivate() {
                assert Thread.holdsLock(MyService.this);
                if (subservice != null) subservice.deactivate();
            }

            void release() {
                assert Thread.holdsLock(MyService.this);
                if (subservice != null) {
                    subservice.release();
                }
            }

            void dump(PrintWriter out) {
                out.printf("%n      inferior %s:", subservice.status());
                ServiceDescription request = subservice.getRequest();
                for (Map.Entry<? extends EndPoint<? extends Terminal>, ? extends TrafficFlow> entry : request
                    .endPointFlows().entrySet()) {
                    EndPoint<? extends Terminal> ep = entry.getKey();
                    TrafficFlow flow = entry.getValue();
                    out.printf("%n        %10s %6g %6g", ep, flow.ingress,
                               flow.egress);
                }
            }
        }

        /***
         * The following methods are to be called by Client objects.
         ***/

        synchronized void clientReady(Client cli) {
            if (tunnels == null) return;

            /* Determine whether we've got responses from anyone now. */
            readyCount++;
            if (readyCount < clients.size()) return;

            /* If all inferior networks have responded, we can report
             * that we are ready. */
            assert errorCount == 0;
            callOut(ServiceListener::ready);

            /* Try to become active now, if the user has prematurely
             * tried to activate us. */
            if (intent == Intent.ACTIVE) {
                callOut(ServiceListener::activating);
                clients.stream().forEach(Client::activate);
            }
        }

        synchronized void
            clientFailed(Client cli,
                         Collection<? extends EndPoint<? extends Terminal>> locations,
                         Throwable t) {
            forceInactive(cli);

            assert tunnels == null;

            /* Record this failure, and keep track of how many
             * subservices have failed. */
            boolean oldState = cli.inErrorState();
            if (locations.isEmpty()) {
                cli.globalErrors.add(t);
            } else {
                for (EndPoint<? extends Terminal> ep : locations)
                    cli.endPointErrors
                        .computeIfAbsent(ep, k -> new HashSet<>()).add(t);
            }
            boolean newState = cli.inErrorState();
            if (!oldState && newState) errorCount++;

            /* Pass this failure on up. */
            callOut(l -> l.failed(locations, t));
        }

        synchronized void clientReleased(Client cli) {
            forceInactive(cli);

            /* Deplete the set of clients. If they're all gone, we are
             * released. */
            if (!clients.remove(cli)) return;
            if (!clients.isEmpty()) return;
            allClientsReleased();
        }

        private void allClientsReleased() {
            assert clients.isEmpty();
            synchronized (PersistentAggregator.this) {
                services.remove(id);
            }
            callOut(ServiceListener::released);
        }

        synchronized void clientActivated(Client cli) {
            if (cli.active) return;
            cli.active = true;
            if (activeCount++ < clients.size()) return;
            callOut(ServiceListener::activated);
        }

        synchronized void clientDeactivated(Client cli) {
            forceInactive(cli);
        }

        private void forceInactive(Client cli) {
            assert Thread.holdsLock(this);
            if (!cli.active) return;
            cli.active = false;
            if (activeCount-- > 0) return;
            completeDeactivation();
        }

        private void completeDeactivation() {
            callOut(ServiceListener::deactivated);
            if (intent != Intent.RELEASE) return;
            releaseInternal();
        }

        final int id;
        final Collection<ServiceListener> listeners = new HashSet<>();
        final List<Client> clients = new ArrayList<>();

        private void callOut(Consumer<? super ServiceListener> action) {
            listeners.stream()
                .forEach(l -> executor.execute(() -> action.accept(l)));
        }

        /**
         * This holds the set of trunks on which we have allocated
         * bandwidth. If {@code null}, the service is not initiated.
         */
        Map<MyTrunk, EndPoint<? extends Terminal>> tunnels;
        ServiceDescription request;

        /**
         * Holds errors not attached to end points of subservices.
         */
        Collection<Throwable> globalErrors = new HashSet<>();

        int readyCount;
        int errorCount;

        /**
         * Counts the number of active or deactivating
         * (having-been-active) subservices. Subservices that were
         * activating but switched to deactivating before becoming
         * active are not included.
         */
        int activeCount;

        /**
         * Records the user's intent for this service. The default is
         * {@link Intent#INACTIVE}.
         */
        Intent intent = Intent.INACTIVE;

        MyService(int id) {
            this.id = id;
        }

        @Override
        public synchronized void initiate(ServiceDescription request)
            throws InvalidServiceException {

            if (intent == Intent.RELEASE) if (clients.isEmpty())
                throw new IllegalStateException("service released");
            else
                throw new IllegalStateException("service releasing");

            if (tunnels != null)
                throw new IllegalStateException("service in use");
            request = ServiceDescription.sanitize(request, 0.01);
            tunnels = new HashMap<>();

            Collection<Service> redundantServices = new HashSet<>();
            try (Connection conn = database()) {
                conn.setAutoCommit(false);
                /* Plot a spanning tree across this switch, allocating
                 * tunnels. */
                Collection<ServiceDescription> subrequests = new HashSet<>();
                plotAsymmetricTree(conn, this, request, tunnels, subrequests);

                /* Create connections for each inferior switch, and a
                 * distinct reference of our own for each one. */
                Map<Service, ServiceDescription> subcons =
                    subrequests.stream()
                        .collect(Collectors
                            .toMap(r -> r.endPointFlows().keySet().iterator()
                                .next().getBundle().getNetwork().newService(),
                                   r -> r));

                /* If we fail, ensure we release all these resources. */
                redundantServices.addAll(subcons.keySet());

                /* Record the identity of the subservices. */
                try (PreparedStatement stmt =
                    conn.prepareStatement("INSERT INTO " + subserviceTable
                        + " (service_id, subservice_id, subnetwork_name)"
                        + " VALUES (?, ?, ?);")) {
                    stmt.setInt(1, this.id);
                    for (Service subsrv : subcons.keySet()) {
                        stmt.setInt(2, subsrv.id());
                        stmt.setString(3, subsrv.getSwitch().name());
                        stmt.execute();
                    }
                }

                /* Create a client for each subservice. */
                clients.addAll(subcons.keySet().stream().map(Client::new)
                    .collect(Collectors.toList()));
                clients.forEach(Client::init);

                /* Tell each of the subconnections to initiate spanning
                 * trees with their respective end points. */
                try {
                    for (Map.Entry<Service, ServiceDescription> entry : subcons
                        .entrySet())
                        entry.getKey().initiate(entry.getValue());
                } catch (InvalidServiceException ex) {
                    release();
                    throw ex;
                }

                this.request = request;
                conn.commit();
                redundantServices.clear();
            } catch (SQLException e) {
                throw new ServiceResourceException("could not plot"
                    + " tree across network", e);
            } finally {
                redundantServices.forEach(Service::release);
            }
        }

        @Override
        public synchronized ServiceStatus status() {
            if (intent == Intent.RELEASE) {
                if (clients.isEmpty())
                    return ServiceStatus.RELEASED;
                else
                    return ServiceStatus.RELEASING;
            }
            if (errorCount > 0 || !globalErrors.isEmpty())
                return ServiceStatus.FAILED;
            if (tunnels == null) return ServiceStatus.DORMANT;
            assert errorCount == 0;
            if (readyCount < clients.size())
                return ServiceStatus.ESTABLISHING;

            if (intent == Intent.ACTIVE) {
                if (activeCount < clients.size())
                    return ServiceStatus.ACTIVATING;
                else
                    return ServiceStatus.ACTIVE;
            }

            if (activeCount > 0)
                return ServiceStatus.DEACTIVATING;
            else
                return ServiceStatus.INACTIVE;
        }

        @Override
        public synchronized void activate() {
            /* If anything has already gone wrong, we can do nothing
             * more. */
            if (errorCount > 0)
                throw new IllegalStateException("inferior error(s)");

            /* If the user has released us, we can do nothing more. */
            if (intent == Intent.RELEASE)
                throw new IllegalStateException("released");

            /* Do nothing if we've already recorded the user's intent,
             * as we must also have activated inferior services. */
            if (intent == Intent.ACTIVE) return;
            try (Connection conn = database()) {
                updateIntent(conn, id, true);
            } catch (SQLException ex) {
                throw new ServiceResourceException("failed to store intent",
                                                   ex);
            }
            intent = Intent.ACTIVE;

            /* Do nothing but record the user's intent, if they haven't
             * yet provided end-point details. */
            if (tunnels == null) return;

            callOut(ServiceListener::activating);
            clients.stream().forEach(Client::activate);
        }

        @Override
        public synchronized void deactivate() {
            if (intent != Intent.ACTIVE) return;
            try (Connection conn = database()) {
                updateIntent(conn, id, false);
            } catch (SQLException ex) {
                throw new ServiceResourceException("failed to store intent",
                                                   ex);
            }
            intent = Intent.INACTIVE;
            deactivateInternal();
        }

        private void deactivateInternal() {
            assert Thread.holdsLock(this);

            /* Indicate that we are starting to deactivate, and then
             * initiate it if we are not already. */
            callOut(ServiceListener::deactivating);
            clients.forEach(Client::deactivate);
            if (activeCount == 0) completeDeactivation();
        }

        @Override
        public synchronized void release() {
            /* There's nothing to do if we've already recorded the
             * user's intent to release the service. */
            if (intent == Intent.RELEASE) return;

            /* Record the new intent, but remember the old. */
            Intent oldIntent = intent;
            intent = Intent.RELEASE;

            /* If the current intent is to be active, trigger
             * deactivation first. When it completes, and discovers the
             * release intent, it will start the release process.
             * Otherwise, we start it now. */
            if (oldIntent == Intent.ACTIVE) {
                deactivateInternal();
            } else {
                releaseInternal();
            }
        }

        private void releaseInternal() {
            assert Thread.holdsLock(this);

            /* If we have no subservices to release, we're ready to
             * release ourselves. */
            if (clients.isEmpty())
                allClientsReleased();
            else
                /* Release subservice resources. */
                clients.stream().forEach(Client::release);

            /* Release tunnel resources and make ourselves
             * unfindable. */
            synchronized (PersistentAggregator.this) {
                tunnels.forEach((k, v) -> k.releaseTunnel(v));
                try (Connection conn = database()) {
                    try (PreparedStatement stmt =
                        conn.prepareStatement("DELETE FROM " + subserviceTable
                            + " WHERE service_id = ?;")) {
                        stmt.setInt(1, id);
                        stmt.execute();
                    }
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
                } catch (SQLException ex) {
                    // Erm?
                }
                services.remove(id);
            }
            tunnels.clear();
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public synchronized void addListener(ServiceListener events) {
            listeners.add(events);
        }

        @Override
        public synchronized void removeListener(ServiceListener events) {
            listeners.remove(events);
        }

        synchronized void dump(PrintWriter out) {
            ServiceStatus status = status();
            out.printf("  %3d %-8s", id, status);
            switch (status) {
            case DORMANT:
            case RELEASED:
                break;

            default:
                for (Map.Entry<MyTrunk, EndPoint<? extends Terminal>> tunnel : tunnels
                    .entrySet()) {
                    EndPoint<? extends Terminal> ep1 = tunnel.getValue();
                    EndPoint<? extends Terminal> ep2 =
                        tunnel.getKey().getPeer(ep1);
                    out.printf("%n      %20s=%-20s", ep1, ep2);
                }
                for (Client cli : clients) {
                    cli.dump(out);
                }
                break;
            }
            out.println();
        }

        @Override
        public synchronized NetworkControl getSwitch() {
            if (intent == Intent.RELEASE && clients.isEmpty()) return null;
            return control;
        }

        @Override
        public synchronized ServiceDescription getRequest() {
            return request;
        }

        void recover(boolean active,
                     Map<EndPoint<? extends Terminal>, ? extends TrafficFlow> endPoints,
                     Collection<? extends Service> subservices,
                     Map<MyTrunk, EndPoint<? extends Terminal>> tunnels) {
            request = ServiceDescription.create(endPoints);

            this.tunnels = tunnels;

            intent = active ? Intent.ACTIVE : Intent.INACTIVE;
            clients.clear();
            for (Service srv : subservices) {
                Client cli = new Client(srv);
                clients.add(cli);
            }
            errorCount = 0;
            activeCount = 0;
            clients.forEach(Client::init);

            for (Client cli : clients) {
                switch (cli.subservice.status()) {
                case INACTIVE:
                case ACTIVATING:
                case DEACTIVATING:
                    readyCount++;
                    break;
                case ACTIVE:
                    readyCount++;
                    activeCount++;
                    cli.active = true;
                    break;
                case FAILED:
                    errorCount++;
                    break;
                case RELEASED:
                    clients.remove(cli);
                    break;
                default:
                    break;
                }
            }
        }
    }

    /**
     * Represents a physical link with no persistent state.
     * 
     * @author simpsons
     */
    final class MyTrunk implements Trunk {
        private final int dbid;
        private final Terminal start, end;
        private double delay = 0.0;
        private double upstreamCapacity = 0.0, downstreamCapacity = 0.0;

        /**
         * Create a trunk between two terminals.
         * 
         * @param start one of the ends of the trunk
         * 
         * @param end the other end
         */
        MyTrunk(Terminal start, Terminal end, int dbid) {
            this.start = start;
            this.end = end;
            this.dbid = dbid;
        }

        /**
         * Get the upstream bandwidth remaining available on this trunk.
         * 
         * @return the remaining available bandwidth from start terminal
         * to end
         */
        double getUpstreamBandwidth() {
            assert Thread.holdsLock(PersistentAggregator.this);
            return upstreamCapacity;
        }

        /**
         * Get the downstream bandwidth remaining available on this
         * trunk.
         * 
         * @return the remaining available bandwidth from end terminal
         * to start
         */
        double getDownstreamBandwidth() {
            assert Thread.holdsLock(PersistentAggregator.this);
            return downstreamCapacity;
        }

        /**
         * Get the maximum of the upstream and downstream bandwidths.
         * 
         * @return the best bandwidth available on this trunk
         */
        double getMaximumBandwidth() {
            return Math.max(getUpstreamBandwidth(), getDownstreamBandwidth());
        }

        /**
         * Get the peer of an end point.
         * 
         * @param p the end point whose peer is requested
         * 
         * @return the peer of the supplied end point, or {@code null}
         * if it has no peer
         * 
         * @throws IllegalArgumentException if the end point does not
         * belong to either terminal of this trunk
         */
        EndPoint<? extends Terminal> getPeer(EndPoint<? extends Terminal> p) {
            final String indexKey, resultKey;
            final Terminal base;
            if (p.getBundle().equals(start)) {
                indexKey = "start_label";
                resultKey = "end_label";
                base = end;
            } else if (p.getBundle().equals(end)) {
                indexKey = "end_label";
                resultKey = "start_label";
                base = start;
            } else {
                throw new IllegalArgumentException("end point does not"
                    + " belong to trunk");
            }
            try (Connection conn = database();
                PreparedStatement stmt = conn.prepareStatement("SELECT "
                    + resultKey + " FROM " + labelTable
                    + " WHERE trunk_id = ?" + " AND " + indexKey + " = ?;")) {
                stmt.setInt(1, dbid);
                stmt.setInt(2, p.getLabel());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) return null;
                    return base.getEndPoint(rs.getInt(1));
                }
            } catch (SQLException ex) {
                throw new RuntimeException("database inaccessible", ex);
            }
        }

        /**
         * Allocate a tunnel through this trunk. If successful, only one
         * end of the tunnel is returned. The other can be obtained with
         * {@link #getPeer(EndPoint)}.
         * 
         * @param upstreamBandwidth the bandwidth to allocate to the
         * tunnel in the direction from the start terminal to the end
         * 
         * @param downstreamBandwidth the bandwidth to allocate to the
         * tunnel in the direction from the end terminal to the start
         * 
         * @return the end point at the start of the tunnel, or
         * {@code null} if no further resource remains
         * @throws SQLException
         */
        EndPoint<? extends Terminal>
            allocateTunnel(Connection conn, MyService service,
                           double upstreamBandwidth,
                           double downstreamBandwidth)
                throws SQLException {
            assert Thread.holdsLock(PersistentAggregator.this);

            /* Sanity-check bandwidth. */
            if (upstreamBandwidth < 0)
                throw new IllegalArgumentException("negative upstream: "
                    + upstreamBandwidth);
            if (downstreamBandwidth < 0)
                throw new IllegalArgumentException("negative downstream: "
                    + downstreamBandwidth);
            if (upstreamBandwidth > this.upstreamCapacity) return null;
            if (downstreamBandwidth > this.downstreamCapacity) return null;

            conn.setAutoCommit(false);

            /* Find a free label. */
            final int startLabel;
            try (PreparedStatement stmt =
                conn.prepareStatement("SELECT start_label FROM " + labelTable
                    + " WHERE trunk_id = ?" + " AND up_alloc = NULL"
                    + " LIMIT 1;")) {
                stmt.setInt(1, dbid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) return null;
                    startLabel = rs.getInt(1);
                }
            }

            /* Store the allocation for the label, marking it as
             * unavailable/in-use. */
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE "
                + labelTable + " SET up_alloc = ?," + " down_alloc = ?"
                + ", service_id = ? " + " WHERE trunk_id = ?"
                + " AND start_label = ?;")) {
                stmt.setDouble(1, upstreamBandwidth);
                stmt.setDouble(2, downstreamBandwidth);
                stmt.setInt(3, service.id);
                stmt.setInt(4, dbid);
                stmt.setInt(5, startLabel);
                stmt.execute();
            }

            /* Mark our bandwidth as consumed. */
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE "
                + trunkTable + " SET up_cap = up_cap - ?,"
                + " down_cap = down_cap - ?" + " WHERE trunk_id = ?;")) {
                stmt.setDouble(1, upstreamBandwidth);
                stmt.setDouble(2, downstreamBandwidth);
                stmt.setInt(3, dbid);
                stmt.execute();
            }

            conn.commit();
            this.upstreamCapacity -= upstreamBandwidth;
            this.downstreamCapacity -= downstreamBandwidth;
            return start.getEndPoint(startLabel);
        }

        /**
         * Release a tunnel through this trunk.
         * 
         * @param endPoint either of the tunnel end points
         */
        void releaseTunnel(EndPoint<? extends Terminal> endPoint) {
            /* Identify whether we're looking at the start or end of
             * this tunnel. */
            final int label = endPoint.getLabel();
            final String key;
            if (endPoint.getBundle().equals(start)) {
                key = "start_label";
            } else if (endPoint.getBundle().equals(end)) {
                key = "end_label";
            } else {
                throw new IllegalArgumentException("not our end point: "
                    + endPoint);
            }

            try (Connection conn = database()) {
                conn.setAutoCommit(false);

                /* Find out how much has been allocated. */
                final double upAlloc, downAlloc;
                try (PreparedStatement stmt =
                    conn.prepareStatement("SELECT up_alloc," + " down_alloc"
                        + " FROM " + labelTable + " WHERE trunk_id = ?"
                        + " AND " + key + " = ?;")) {
                    stmt.setInt(1, dbid);
                    stmt.setInt(2, label);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) return;
                        Double upObj = (Double) rs.getObject(1);
                        if (upObj == null) return;
                        upAlloc = upObj;
                        downAlloc = rs.getDouble(2);
                    }
                }

                /* Mark the amount now available to the trunk. */
                try (PreparedStatement stmt = conn.prepareStatement("UPDATE "
                    + trunkTable + " SET up_cap = up_cap + ?,"
                    + " down_cap = down_cap + ?" + " WHERE trunk_id = ?;")) {
                    stmt.setDouble(1, upAlloc);
                    stmt.setDouble(2, downAlloc);
                    stmt.setInt(3, dbid);
                    stmt.execute();
                }

                /* Mark the tunnel as out-of-use. */
                try (PreparedStatement stmt =
                    conn.prepareStatement("UPDATE " + labelTable
                        + " SET up_alloc = NULL," + " down_alloc = NULL"
                        + " WHERE trunk_id = ?" + " AND " + key + " = ?")) {
                    stmt.setInt(1, dbid);
                    stmt.setInt(2, label);
                    stmt.execute();
                }

                conn.commit();
                this.upstreamCapacity += upAlloc;
                this.downstreamCapacity += downAlloc;
            } catch (SQLException e) {
                throw new RuntimeException("unexpected database failure", e);
            }
        }

        /**
         * Get the number of tunnels available through this trunk.
         * 
         * @return the number of available tunnels
         */
        int getAvailableTunnelCount() {
            try (Connection conn = database();
                PreparedStatement stmt =
                    conn.prepareStatement("SELECT * FROM " + labelTable
                        + " WHERE trunk_id = ?"
                        + " AND upstream_alloaction IS NULL;")) {
                stmt.setInt(1, dbid);
                try (ResultSet rs = stmt.executeQuery()) {
                    int c = 0;
                    while (rs.next())
                        c++;
                    return c;
                }
            } catch (SQLException e) {
                throw new RuntimeException("unexpected database failure", e);
            }
        }

        /**
         * Get the fixed delay of this trunk.
         * 
         * @return the trunk's fixed delay
         */
        @Override
        public double getDelay() {
            synchronized (PersistentAggregator.this) {
                return delay;
            }
        }

        /**
         * Get the terminals at either end of this trunk.
         * 
         * @return the terminals of the trunk
         */
        List<Terminal> getTerminals() {
            return Arrays.asList(start, end);
        }

        @Override
        public void withdrawBandwidth(double upstream, double downstream) {
            if (upstream < 0)
                throw new IllegalArgumentException("negative upstream: "
                    + upstream);
            if (downstream < 0)
                throw new IllegalArgumentException("negative downstream: "
                    + downstream);

            synchronized (PersistentAggregator.this) {
                if (upstream > upstreamCapacity)
                    throw new IllegalArgumentException("request upstream "
                        + upstream + " exceeds " + upstreamCapacity);
                if (downstream > downstreamCapacity)
                    throw new IllegalArgumentException("request downstream "
                        + downstream + " exceeds " + downstreamCapacity);

                try (Connection conn = database()) {
                    updateTrunkCapacity(conn, this.dbid, -upstream,
                                        -downstream);

                    upstreamCapacity -= upstream;
                    downstreamCapacity -= downstream;
                } catch (SQLException e) {
                    throw new RuntimeException("unexpected database failure",
                                               e);
                }
            }
        }

        @Override
        public void provideBandwidth(double upstream, double downstream) {
            if (upstream < 0)
                throw new IllegalArgumentException("negative upstream: "
                    + upstream);
            if (downstream < 0)
                throw new IllegalArgumentException("negative downstream: "
                    + downstream);

            synchronized (PersistentAggregator.this) {
                try (Connection conn = database()) {
                    updateTrunkCapacity(conn, this.dbid, +upstream,
                                        +downstream);

                    upstreamCapacity += upstream;
                    downstreamCapacity += downstream;
                } catch (SQLException e) {
                    throw new RuntimeException("unexpected database failure",
                                               e);
                }
            }
        }

        @Override
        public void setDelay(double delay) {
            synchronized (PersistentAggregator.this) {
                try (Connection conn = database();
                    PreparedStatement stmt =
                        conn.prepareStatement("UPDATE " + trunkTable
                            + " SET metric = ?" + " WHERE trunk_id = ?;")) {
                    stmt.setDouble(1, delay);
                    stmt.setInt(2, dbid);
                    stmt.execute();
                    this.delay = delay;
                } catch (SQLException e) {
                    throw new RuntimeException("unexpected database failure",
                                               e);
                }
            }
        }

        private void revokeInternalRange(String key, int base, int amount)
            throws SQLException {
            try (Connection conn = database()) {
                conn.setAutoCommit(false);
                /* Detect labels that are in use. Fail completely if any
                 * are found. */
                try (PreparedStatement stmt = conn.prepareStatement("SELECT "
                    + key + " FROM " + labelTable + " WHERE trunk_id = ?"
                    + " AND " + key + " >= ?" + " AND " + key + " <= ?"
                    + " AND up_alloc IS NOT NULL;")) {
                    stmt.setInt(1, dbid);
                    stmt.setInt(2, base);
                    stmt.setInt(3, base + amount - 1);
                    BitSet inUse = new BitSet();
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next())
                            inUse.set(rs.getInt(1));
                    }
                    if (!inUse.isEmpty())
                        throw new RuntimeException("start labels in use: "
                            + inUse);
                }
                try (PreparedStatement stmt =
                    conn.prepareStatement("DELETE FROM " + labelTable
                        + " WHERE trunk_id = ?" + " AND " + key + " >= ?"
                        + " AND " + key + " <= ?;")) {
                    stmt.setInt(1, dbid);
                    stmt.setInt(2, base);
                    stmt.setInt(3, base + amount - 1);
                    stmt.execute();
                }
                conn.commit();
            }
        }

        @Override
        public void revokeStartLabelRange(int startBase, int amount) {
            synchronized (PersistentAggregator.this) {
                try {
                    revokeInternalRange("start_label", startBase, amount);
                } catch (SQLException e) {
                    throw new RuntimeException("unexpected database failure",
                                               e);
                }
            }
        }

        @Override
        public void revokeEndLabelRange(int endBase, int amount) {
            synchronized (PersistentAggregator.this) {
                try {
                    revokeInternalRange("end_label", endBase, amount);
                } catch (SQLException e) {
                    throw new RuntimeException("unexpected database failure",
                                               e);
                }
            }
        }

        @Override
        public void defineLabelRange(int startBase, int amount, int endBase) {
            if (startBase + amount < startBase)
                throw new IllegalArgumentException("illegal start range "
                    + startBase + " plus " + amount);
            if (endBase + amount < endBase)
                throw new IllegalArgumentException("illegal end range "
                    + endBase + " plus " + amount);

            synchronized (PersistentAggregator.this) {
                try (Connection conn = database()) {
                    conn.setAutoCommit(false);

                    /* Check that all numbers are available. */
                    try (PreparedStatement stmt =
                        conn.prepareStatement("SELECT start_label" + " FROM "
                            + labelTable + " WHERE trunk_id = ?"
                            + " (AND start_label >= ?"
                            + " AND start_label <= ?)" + " OR (end_label >="
                            + " AND end_label <= ?);")) {
                        stmt.setInt(1, dbid);
                        stmt.setInt(2, startBase);
                        stmt.setInt(3, startBase + amount - 1);
                        stmt.setInt(4, endBase);
                        stmt.setInt(5, endBase + amount - 1);
                        BitSet startInUse = new BitSet();
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next())
                                startInUse.set(rs.getInt(1));
                        }
                        if (!startInUse.isEmpty())
                            throw new IllegalArgumentException("range in use: "
                                + startInUse);
                    }

                    /* Add all the labels. */
                    try (PreparedStatement stmt =
                        conn.prepareStatement("INSERT INTO " + labelTable
                            + " (trunk_id, start_label, end_label)"
                            + " VALUES (?, ?, ?);")) {
                        stmt.setInt(1, dbid);
                        for (int i = 0; i < amount; i++) {
                            stmt.setInt(2, startBase + i);
                            stmt.setInt(3, endBase + i);
                            stmt.execute();
                        }
                    }

                    conn.commit();
                } catch (SQLException e) {
                    throw new RuntimeException("unexpected database failure",
                                               e);
                }
            }
        }

        @Override
        public int position(Terminal term) {
            return getTerminals().indexOf(term);
        }

        @Override
        public String toString() {
            return start + "+" + end;
        }

        void recoverCapacities(double upCap, double downCap, double delay2) {
            this.upstreamCapacity = upCap;
            this.downstreamCapacity = downCap;
            this.delay = delay2;
        }
    }

    /**
     * Print out the status of all connections and trunks of this
     * switch.
     * 
     * @param out the destination for the status report
     */
    @Override
    public synchronized void dumpStatus(PrintWriter out) {
        out.printf("aggregate %s:%n", name);
        for (MyService conn : services.values())
            conn.dump(out);
        for (MyTrunk trunk : new HashSet<>(trunks.values())) {
            out.printf("  %s=(%gMbps, %gMbps, %gs) [%d]%n",
                       trunk.getTerminals(), trunk.getUpstreamBandwidth(),
                       trunk.getDownstreamBandwidth(), trunk.getDelay(),
                       trunk.getAvailableTunnelCount());
        }
        out.flush();
    }

    private final Executor executor;
    private final String name;

    private final Map<String, MyTerminal> terminals = new HashMap<>();
    private final Map<Terminal, MyTrunk> trunks = new HashMap<>();
    private final Map<Integer, MyTrunk> trunkIndex = new HashMap<>();
    private final Map<Integer, MyService> services = new HashMap<>();

    /**
     * Create an aggregator.
     * 
     * @param executor used to invoke call-backs created by this
     * aggregator and passed to inferior networks
     * 
     * @param inferiors a mapping from names to networks, so that this
     * aggregator can find inferior networks and the terminals and ports
     * on them
     * 
     * @param config the configuration describing the network and access
     * to the database
     */
    public PersistentAggregator(Executor executor,
                                Function<? super String, ? extends NetworkControl> inferiors,
                                Configuration config) {
        this.executor = executor;
        this.inferiors = inferiors;
        this.name = config.get("name");

        /* Record how we talk to the database. */
        Configuration dbConfig = config.subview("db");
        this.dbConnectionAddress = dbConfig.get("service");
        this.dbConnectionConfig = dbConfig.toProperties();
        this.endPointTable = dbConfig.get("end-points.table", "end_points");
        this.terminalTable = dbConfig.get("terminals.table", "terminal_map");
        this.serviceTable = dbConfig.get("services.table", "services");
        this.subserviceTable = dbConfig.get("services.table", "subservices");
        this.trunkTable = dbConfig.get("trunks.table", "trunks");
        this.labelTable = dbConfig.get("labels.table", "label_map");
    }

    private final Function<? super String, ? extends NetworkControl> inferiors;

    /**
     * Initialize the aggregator. Ensure the necessary tables exist in
     * the database. Recreate the internal service records mentioned in
     * the tables. Obtain subservices used to build these services.
     * 
     * @throws SQLException if there was an error in accessing the
     * database
     */
    public synchronized void init() throws SQLException {
        try (Connection conn = database()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS " + terminalTable
                    + " (terminal_id INTEGER PRIMARY KEY,"
                    + " name VARCHAR(20) NOT NULL UNIQUE,"
                    + " subnetwork VARCHAR(40) NOT NULL,"
                    + " subname VARCHAR(40) NOT NULL);");
                stmt.execute("CREATE TABLE IF NOT EXISTS " + serviceTable
                    + " (service_id INTEGER PRIMARY KEY,"
                    + " intent INT UNSIGNED DEFAULT 0);");
                stmt.execute("CREATE TABLE IF NOT EXISTS " + subserviceTable
                    + " (service_id INTEGER," + " subservice_id INTEGER,"
                    + " subnetwork_name VARCHAR(20),"
                    + " FORIEGN KEY(service_id) REFERENCES " + serviceTable
                    + "(service_id));");
                stmt.execute("CREATE TABLE IF NOT EXISTS " + trunkTable
                    + " (trunk_id INTEGER PRIMARY KEY,"
                    + " start_network VARCHAR(20),"
                    + " start_terminal VARCHAR(20),"
                    + " end_network VARCHAR(20),"
                    + " end_terminal VARCHAR(20),"
                    + " up_cap DECIMAL(9,3) DEFAULT 0.0,"
                    + " down_cap DECIMAL(9,3) DEFAULT 0.0,"
                    + " metric DECIMAL(9,3) DEFAULT 0.1,"
                    + " commissioned VARCHAR(1) DEFAULT 1);");
                stmt.execute("CREATE TABLE IF NOT EXISTS " + labelTable
                    + " (trunk_id INTEGER," + " start_label INTEGER,"
                    + " end_label INTEGER,"
                    + " up_alloc DECIMAL(9,3) DEFAULT NULL,"
                    + " down_alloc DECIMAL(9,3) DEFAULT NULL,"
                    + " service_id INTEGER DEFAULT NULL"
                    + " PRIMARY KEY(trunk_id, start_label, end_label),"
                    + " UNIQUE(trunk_id, start_label),"
                    + " UNIQUE(trunk_id, end_label),"
                    + " FOREIGN KEY(trunk_id) REFERENCES " + trunkTable
                    + "(trunk_id))" + " FOREIGN KEY(service_id) REFERENCES "
                    + serviceTable + "(service_id));");
                stmt.execute("CREATE TABLE IF NOT EXISTS " + endPointTable
                    + " (service_id INTEGER," + " terminal_id INTEGER,"
                    + " label INTEGER UNSIGNED," + " metering DECIMAL(9,3),"
                    + " shaping DECIMAL(9,3),"
                    + " FOREIGN KEY(service_id) REFERENCES " + serviceTable
                    + "(service_id),"
                    + " FOREIGN KEY(terminal_id) REFERENCES " + terminalTable
                    + "(terminal_id));");
            }

            /* Recreate terminals from entries in our tables. */
            try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT terminal_id, name,"
                    + " subnetwork, subname" + " FROM " + terminalTable
                    + ";")) {
                while (rs.next()) {
                    final int id = rs.getInt(1);
                    final String name = rs.getString(2);
                    final String subnetworkName = rs.getString(3);
                    final String subname = rs.getString(4);

                    NetworkControl subnetwork =
                        inferiors.apply(subnetworkName);
                    Terminal innerPort = subnetwork.getTerminal(subname);
                    MyTerminal port = new MyTerminal(name, innerPort, id);
                    terminals.put(name, port);
                }
            }

            /* Recover a list of trunks. */
            try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT" + " trunk_id,"
                    + " start_network," + " start_terminal," + " end_network,"
                    + " end_terminal," + " up_cap," + " down_cap,"
                    + " metric FROM " + trunkTable + ";")) {
                while (rs.next()) {
                    final int id = rs.getInt(1);
                    final String startNetworkName = rs.getString(2);
                    final String startTerminalName = rs.getString(3);
                    final String endNetworkName = rs.getString(4);
                    final String endTerminalName = rs.getString(5);
                    final double upCap = rs.getDouble(6);
                    final double downCap = rs.getDouble(7);
                    final double delay = rs.getDouble(8);

                    Terminal start = inferiors.apply(startNetworkName)
                        .getTerminal(startTerminalName);
                    Terminal end = inferiors.apply(endNetworkName)
                        .getTerminal(endTerminalName);
                    MyTrunk trunk = new MyTrunk(start, end, id);
                    trunkIndex.put(id, trunk);
                    trunks.put(start, trunk);
                    trunks.put(end, trunk);
                    trunk.recoverCapacities(upCap, downCap, delay);
                }
            }

            /* Collect details of each services. */
            class ServiceDetails {
                Map<EndPoint<? extends Terminal>, TrafficFlow> endPoints =
                    new HashMap<>();
                boolean active;
                Collection<Service> subservices = new ArrayList<>();
                Map<MyTrunk, EndPoint<? extends Terminal>> tunnels =
                    new HashMap<>();
            }
            Map<MyService, ServiceDetails> details = new HashMap<>();

            /* Recreate empty services from entries in our tables. */
            try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT service_id, intent"
                    + " FROM " + serviceTable + ";")) {
                while (rs.next()) {
                    final int id = rs.getInt(1);
                    final boolean intent = rs.getBoolean(2);
                    MyService service = new MyService(id);
                    services.put(id, service);
                    details
                        .computeIfAbsent(service,
                                         k -> new ServiceDetails()).active =
                                             intent;
                }
            }

            /* Recover services' details. */
            try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt
                    .executeQuery("SELECT" + " et.service_id AS service_id,"
                        + " tt.name AS terminal_name," + " et.label AS label,"
                        + " et.metering AS metering,"
                        + " et.shaping AS shaping" + " FROM " + endPointTable
                        + " AS et" + " LEFT JOIN " + terminalTable + " AS tt"
                        + " ON tt.terminal_id = et.terminal_id" + ";")) {
                while (rs.next()) {
                    final MyService service = services.get(rs.getInt(1));
                    final MyTerminal port = terminals.get(rs.getString(2));
                    final int label = rs.getInt(3);
                    final double metering = rs.getDouble(4);
                    final double shaping = rs.getDouble(5);

                    final EndPoint<? extends Terminal> endPoint =
                        port.getEndPoint(label);
                    final TrafficFlow enf = TrafficFlow.of(metering, shaping);
                    details
                        .computeIfAbsent(service,
                                         k -> new ServiceDetails()).endPoints
                                             .put(endPoint, enf);
                }
            }

            /* Recover subservice details. */
            try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt
                    .executeQuery("SELECT" + " st.service_id AS service_id, "
                        + " st.subservice_id AS subservice_id, "
                        + " sst.subnetwork_name AS subnetwork_name" + " FROM "
                        + subserviceTable + " AS sst" + " LEFT JOIN "
                        + serviceTable + " AS st"
                        + " ON st.service_id = sst.service_id;")) {
                while (rs.next()) {
                    final int srvid = rs.getInt(1);
                    final int subsrvid = rs.getInt(2);
                    final String subnwname = rs.getString(3);

                    NetworkControl subnw = inferiors.apply(subnwname);
                    Service subsrv = subnw.getService(subsrvid);
                    MyService srv = services.get(srvid);
                    details
                        .computeIfAbsent(srv,
                                         k -> new ServiceDetails()).subservices
                                             .add(subsrv);
                }
            }

            /* Recover tunnel details. */
            try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt
                    .executeQuery("SELECT" + " lt.service_id AS service_id,"
                        + " lt.trunk_id AS trunk_id,"
                        + " lt.start_label AS start_label,"
                        + " tt.start_network AS start_network,"
                        + " tt.start_terminal AS start_terminal" + " FROM "
                        + labelTable + " AS lt" + " LEFT JOIN " + trunkTable
                        + " AS tt" + " ON tt.trunk_id = lt.trunk_id;")) {
                while (rs.next()) {
                    final int trid = rs.getInt(1);
                    final int srvid = rs.getInt(2);
                    final int label = rs.getInt(3);
                    final String nwname = rs.getString(4);
                    final String tname = rs.getString(5);
                    MyService srv = services.get(srvid);
                    NetworkControl subnw = inferiors.apply(nwname);
                    Terminal term = subnw.getTerminal(tname);
                    MyTrunk trunk = trunkIndex.get(trid);
                    EndPoint<Terminal> ep = term.getEndPoint(label);
                    details.computeIfAbsent(srv,
                                            k -> new ServiceDetails()).tunnels
                                                .put(trunk, ep);
                }
            }

            /* Pass the recovered data to each service. */
            for (Map.Entry<MyService, ServiceDetails> entry : details
                .entrySet()) {
                ServiceDetails dt = entry.getValue();
                entry.getKey().recover(dt.active, dt.endPoints,
                                       dt.subservices, dt.tunnels);
            }

            conn.commit();
        }
    }

    private final String endPointTable, terminalTable, serviceTable,
        subserviceTable, trunkTable, labelTable;
    private final String dbConnectionAddress;
    private final Properties dbConnectionConfig;

    @Override
    public synchronized Trunk addTrunk(Terminal p1, Terminal p2) {
        if (p1 == null || p2 == null)
            throw new NullPointerException("null terminal(s)");
        if (trunks.containsKey(p1))
            throw new IllegalArgumentException("terminal in use: " + p1);
        if (trunks.containsKey(p2))
            throw new IllegalArgumentException("terminal in use: " + p2);
        try (Connection conn = database();
            PreparedStatement stmt =
                conn.prepareStatement("INSERT INTO " + trunkTable
                    + " (start_network, start_terminal,"
                    + " end_network, end_terminal)" + " VALUES (?, ?, ?, ?);",
                                      Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, p1.getNetwork().name());
            stmt.setString(2, p1.name());
            stmt.setString(3, p2.getNetwork().name());
            stmt.setString(4, p2.name());
            stmt.execute();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    final int id = rs.getInt(1);
                    MyTrunk trunk = new MyTrunk(p1, p2, id);
                    trunks.put(p1, trunk);
                    trunks.put(p2, trunk);
                    return trunk;
                } else {
                    throw new RuntimeException("failed to generate id for new trunk "
                        + p1 + " to " + p2);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("could not create trunk from " + p1
                + " to " + p2, ex);
        }
    }

    @Override
    public synchronized void removeTrunk(Terminal p) {
        MyTrunk t = trunks.get(p);
        if (t == null) return; // TODO: error?
        trunks.keySet().removeAll(t.getTerminals());
    }

    @Override
    public synchronized Trunk findTrunk(Terminal p) {
        return trunks.get(p);
    }

    @Override
    public synchronized Terminal addTerminal(String name, Terminal inner) {
        if (terminals.containsKey(name))
            throw new IllegalArgumentException("name in use: " + name);
        try (Connection conn = database();
            PreparedStatement stmt =
                conn.prepareStatement("INSERT INTO " + terminalTable
                    + " (name, subnetwork, subname)" + " VALUES (?, ?, ?)",
                                      Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, inner.getNetwork().name());
            stmt.setString(3, inner.name());
            stmt.execute();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                final int id = rs.getInt(1);
                MyTerminal result = new MyTerminal(name, inner, id);
                terminals.put(name, result);
                return result;

            }
        } catch (SQLException ex) {
            throw new RuntimeException("could not create terminal " + name
                + " on " + inner + " in database", ex);
        }
    }

    /**
     * Remove a terminal from this switch.
     * 
     * @param name the terminal's local name
     */
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
            terminals.remove(name);
        } catch (SQLException ex) {
            throw new RuntimeException("could not remove terminal " + name
                + " from database", ex);
        }
    }

    @Override
    public NetworkControl getControl() {
        return control;
    }

    /**
     * Plot a spanning tree with asymmetric bandwidth requirements
     * across this switch, allocation tunnels on trunks.
     * 
     * @param service the service which will own the tunnels formed
     * across trunks
     * 
     * @param request the request specifying bandwidth at each concerned
     * end point of this switch
     * 
     * @param tunnels a place to store the set of allocated tunnels,
     * indexed by trunk
     * 
     * @param subrequests a place to store the connection requests to be
     * submitted to each inferior switch
     * @throws SQLException
     */
    synchronized void
        plotAsymmetricTree(Connection conn, MyService service,
                           ServiceDescription request,
                           Map<? super MyTrunk, ? super EndPoint<? extends Terminal>> tunnels,
                           Collection<? super ServiceDescription> subrequests)
            throws SQLException {
        // System.err.printf("Request producers: %s%n",
        // request.producers());
        // System.err.printf("Request consumers: %s%n",
        // request.consumers());

        /* Sanity-check the end points, map them to internal terminals,
         * and record bandwidth requirements. */
        Map<Terminal, List<Double>> bandwidths = new HashMap<>();
        Map<EndPoint<? extends Terminal>, List<Double>> innerEndPoints =
            new HashMap<>();
        double smallestBandwidthSoFar = Double.MAX_VALUE;
        for (Map.Entry<? extends EndPoint<? extends Terminal>, ? extends TrafficFlow> entry : request
            .endPointFlows().entrySet()) {
            EndPoint<? extends Terminal> ep = entry.getKey();
            TrafficFlow flow = entry.getValue();

            /* Map this end point to an inferior switch's terminal. */
            Terminal outerPort = ep.getBundle();
            if (!(outerPort instanceof MyTerminal))
                throw new IllegalArgumentException("end point " + ep
                    + " not part of " + name);
            MyTerminal myPort = (MyTerminal) outerPort;
            if (myPort.owner() != this)
                throw new IllegalArgumentException("end point " + ep
                    + " not part of " + name);

            /* Record the bandwidth produced and consumed on the
             * inferior switch's terminal. Make sure we aggregate
             * contributions when two or more end points belong to the
             * same terminal. */
            double produced = flow.ingress;
            double consumed = flow.egress;
            Terminal innerPort = myPort.innerPort();
            List<Double> tuple = bandwidths
                .computeIfAbsent(innerPort, k -> Arrays.asList(0.0, 0.0));
            tuple.set(0, tuple.get(0) + produced);
            tuple.set(1, tuple.get(1) + consumed);

            /* Map the outer end point to an inner one by copying the
             * label. */
            innerEndPoints.put(innerPort.getEndPoint(ep.getLabel()),
                               Arrays.asList(produced, consumed));

            /* Get the smallest production. We use it to filter out
             * trunks that no longer have the required bandwidth in
             * either direction. */
            if (produced < smallestBandwidthSoFar)
                smallestBandwidthSoFar = produced;

            // System.err.printf("e/p %s is part of %s, mapping to
            // %s%n", ep,
            // outerPort, innerPort);
        }
        double smallestBandwidth = smallestBandwidthSoFar;
        // System.err.printf("Bandwidths on terminals: %s%n",
        // bandwidths);
        // System.err.printf("Inner end points: %s%n",
        // innerTerminalEndPoints);

        /* Get the set of terminals to connect. */
        Collection<Terminal> innerTerminals = bandwidths.keySet();
        // System.err.printf("Inner terminals: %s%n",
        // innerTerminalPorts);

        /* Get a subset of all trunks, those with sufficent bandwidth
         * and free tunnels. */
        Collection<MyTrunk> adequateTrunks = trunks.values().stream()
            .filter(trunk -> trunk.getAvailableTunnelCount() > 0
                && trunk.getMaximumBandwidth() >= smallestBandwidth)
            .collect(Collectors.toSet());
        // System.err.printf("Selected trunks: %s%n",
        // adequateTrunks.stream()
        // .map(t ->
        // Edge.of(t.getPorts())).collect(Collectors.toSet()));

        /* Get the set of all switches connected to our selected
         * trunks. */
        Collection<NetworkControl> switches =
            adequateTrunks.stream().flatMap(tr -> tr.getTerminals().stream())
                .map(Terminal::getNetwork).collect(Collectors.toSet());

        /* Create modifiable routing tables across our network, where
         * the vertices are inner (inferior) terminals. */
        DistanceVectorComputer<Terminal> fibGraph =
            new DistanceVectorComputer<Terminal>();

        /* The terminals for spanning trees are the inner terminals of
         * those identified in the request. */
        fibGraph.addTerminals(innerTerminals);

        /* The edges include all our trunks, using their delays as
         * metrics. Also retain a reverse mapping from edge to trunk, so
         * we can test bandwidth availability whenever we find a
         * spanning tree. */
        Map<Edge<Terminal>, Double> trunkEdgeWeights = new HashMap<>();
        Map<Edge<Terminal>, MyTrunk> trunkEdges = new HashMap<>();
        for (MyTrunk trunk : adequateTrunks) {
            Edge<Terminal> edge = Edge.of(trunk.getTerminals());
            trunkEdgeWeights.put(edge, trunk.getDelay());
            trunkEdges.put(edge, trunk);
        }
        fibGraph.addEdges(trunkEdgeWeights);
        // System.err.printf("Trunk weights: %s%n", trunkEdgeWeights);

        /* The edges include virtual ones constituting models of
         * inferior switches. Also make a note of connected terminals
         * within an inferior switch, in case it is a fragmented
         * aggregate. */
        Map<Edge<Terminal>, Double> switchEdgeWeights = switches.stream()
            .flatMap(sw -> sw.getModel(smallestBandwidth).entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey,
                                      Map.Entry::getValue));
        fibGraph.addEdges(switchEdgeWeights);
        // System.err.printf("Switch weights: %s%n", switchEdgeWeights);
        Map<Terminal, Collection<Terminal>> terminalGroups = Graphs
            .getGroups(Graphs.getAdjacencies(switchEdgeWeights.keySet()));
        // System.err.printf("Port groups: %s%n",
        // new HashSet<>(terminalGroups.values()));

        /* Keep track of the weights of all edges, whether they come
         * from trunks or inferior switches. */
        Map<Edge<Terminal>, Double> edgeWeights =
            new HashMap<>(trunkEdgeWeights);
        edgeWeights.putAll(switchEdgeWeights);

        do {
            /* Ensure we have up-to-date routing tables. */
            fibGraph.update();

            /* Create terminal-aware weights for each edge, and build a
             * spanning tree. */
            Map<Terminal, Map<Terminal, Way<Terminal>>> fibs =
                fibGraph.getFIBs();
            FIBSpanGuide<Terminal> guide = new FIBSpanGuide<>(fibs);
            Collection<Terminal> reached = new HashSet<>();
            Collection<Edge<Terminal>> tree = SpanningTreeComputer
                .start(Terminal.class).withEdges(edgeWeights.keySet())
                .withTerminals(innerTerminals).notifying(p -> {
                    guide.reached(p);
                    reached.addAll(terminalGroups.get(p));
                }).withEdgePreference(guide::select).eliminating(e -> {
                    /* Permit edges within the same switch. */
                    NetworkControl first = e.first().getNetwork();
                    NetworkControl second = e.second().getNetwork();
                    if (first == second) return false;

                    /* Allow this edge if at least one terminal hasn't
                     * been reached. */
                    return reached.containsAll(e);
                }).create().getSpanningTree(guide.first());
            if (tree == null)
                throw new ServiceResourceException("no tree found");
            // System.err.printf("Spanning tree: %s%n", tree);

            /* Work out how much bandwidth each trunk edge requires in
             * each direction. Find trunk edges in the spanning tree
             * that don't have enough bandwidth for what is going over
             * them. Identify the worst case. */
            Map<MyTrunk, List<Double>> edgeBandwidths = new HashMap<>();
            DistanceVectorComputer<Terminal> routes =
                new DistanceVectorComputer<>(innerTerminals, tree.stream()
                    .collect(Collectors.toMap(e -> e, edgeWeights::get)));
            routes.update();
            // System.err.printf("Loads: %s%n", routes.getEdgeLoads());
            Edge<Terminal> worstEdge = null;
            double worstShortfall = 0.0;
            for (Map.Entry<Edge<Terminal>, List<Map<Terminal, Double>>> entry : routes
                .getEdgeLoads().entrySet()) {
                Edge<Terminal> edge = entry.getKey();
                MyTrunk trunk = trunkEdges.get(edge);
                if (trunk == null) continue;
                boolean reverse = !trunk.getTerminals().equals(edge);

                List<Double> thisEdgeBandwidths = Arrays.asList(0.0, 0.0);
                edgeBandwidths.put(trunk, thisEdgeBandwidths);
                for (int i = 0; i < 2; i++) {
                    double consumed =
                        entry.getValue().get(i).keySet().stream()
                            .mapToDouble(p -> bandwidths.get(p).get(1)).sum();
                    double produced =
                        entry.getValue().get(1 - i).keySet().stream()
                            .mapToDouble(p -> bandwidths.get(p).get(0)).sum();
                    thisEdgeBandwidths.set(reverse ? 1 - i : i,
                                           Math.min(consumed, produced));
                }
                double shortfall = 0.0;
                shortfall += Math.max(0.0, thisEdgeBandwidths.get(0)
                    - trunk.getUpstreamBandwidth());
                shortfall += Math.max(0.0, thisEdgeBandwidths.get(1)
                    - trunk.getDownstreamBandwidth());
                if (shortfall > worstShortfall) {
                    worstShortfall = shortfall;
                    worstEdge = edge;
                }
            }
            // System.err.printf("Edge bandwidths: %s%n",
            // edgeBandwidths);

            /* Remove the worst edge from the graph, and start again. */
            if (worstEdge != null) {
                fibGraph.removeEdge(worstEdge);
                continue;
            }
            /* If there is no worst case, we have a result. */

            /* Allocate tunnels along identified trunks. Also gather end
             * points per terminal group, and bandwidth required on
             * each. */
            Map<Collection<Terminal>, Map<EndPoint<? extends Terminal>, List<Double>>> subterminals =
                new HashMap<>();
            for (Map.Entry<MyTrunk, List<Double>> trunkReq : edgeBandwidths
                .entrySet()) {
                MyTrunk trunk = trunkReq.getKey();
                double upstream = trunkReq.getValue().get(0);
                double downstream = trunkReq.getValue().get(1);
                EndPoint<? extends Terminal> ep1 =
                    trunk.allocateTunnel(conn, service, upstream, downstream);
                tunnels.put(trunk, ep1);
                EndPoint<? extends Terminal> ep2 = trunk.getPeer(ep1);
                subterminals
                    .computeIfAbsent(terminalGroups.get(ep1.getBundle()),
                                     k -> new HashMap<>())
                    .put(ep1, Arrays.asList(downstream, upstream));
                subterminals
                    .computeIfAbsent(terminalGroups.get(ep2.getBundle()),
                                     k -> new HashMap<>())
                    .put(ep2, Arrays.asList(upstream, downstream));
            }

            /* Ensure the caller's end points are included in the
             * requests to inferior switches. */
            for (Map.Entry<EndPoint<? extends Terminal>, List<Double>> entry : innerEndPoints
                .entrySet()) {
                EndPoint<? extends Terminal> ep = entry.getKey();
                subterminals
                    .computeIfAbsent(terminalGroups.get(ep.getBundle()),
                                     k -> new HashMap<>())
                    .put(ep, entry.getValue());
            }
            // System.err.printf("Subterminals: %s%n", subterminals);

            /* For each terminal group, create a new connection
             * request. */
            for (Map<EndPoint<? extends Terminal>, List<Double>> reqs : subterminals
                .values()) {
                subrequests.add(ServiceDescription.of(reqs));
            }
            return;
        } while (true);
    }

    /**
     * Given a subset of our internal terminals to connect and a
     * bandwidth requirement, create FIBs for each terminal.
     * 
     * <p>
     * This method does not modify any switch state, but should only be
     * called while synchronized on the switch.
     * 
     * @param bandwidth the required bandwidth
     * 
     * @param innerTerminals the set of terminals to connect
     * 
     * @return a FIB for each terminal
     */
    Map<Terminal, Map<Terminal, Way<Terminal>>>
        getFibs(double bandwidth, Collection<Terminal> innerTerminals) {
        assert Thread.holdsLock(this);

        /* Get a subset of all trunks, those with sufficent bandwidth
         * and free tunnels. */
        Collection<MyTrunk> adequateTrunks = trunks.values().stream()
            .filter(trunk -> trunk.getAvailableTunnelCount() > 0
                && trunk.getMaximumBandwidth() >= bandwidth)
            .collect(Collectors.toSet());
        // System.err.println("Usable trunks: " + adequateTrunks);

        /* Get edges representing all suitable trunks. */
        Map<Edge<Terminal>, Double> edges =
            new HashMap<>(adequateTrunks.stream().collect(Collectors
                .toMap(t -> Edge.of(t.getTerminals()), MyTrunk::getDelay)));
        // System.err.println("Edges of trunks: " + edges);

        /* Get a set of all switches for our trunks. */
        Collection<NetworkControl> switches = adequateTrunks.stream()
            .flatMap(trunk -> trunk.getTerminals().stream()
                .map(Terminal::getNetwork))
            .collect(Collectors.toSet());
        // System.err.println("Switches: " + switches);

        /* Get models of all switches connected to the selected trunks,
         * and combine their edges with the trunks. */
        edges.putAll(switches.stream()
            .flatMap(sw -> sw.getModel(bandwidth).entrySet().stream())
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())));
        // System.err.println("Edges of trunks and switches: " + edges);

        /* Get rid of spurs as a small optimization. */
        Graphs.prune(innerTerminals, edges.keySet());
        // System.err.println("Pruned edges of trunks and switches: " +
        // edges);

        /* Create routing tables for each terminal. */
        return Graphs.route(innerTerminals, edges);
    }

    synchronized Map<Edge<Terminal>, Double> getModel(double bandwidth) {
        /* Map the set of our end points to the corresponding inner
         * terminals that our topology consists of. */
        Collection<Terminal> innerTerminalPorts = terminals.values().stream()
            .map(MyTerminal::innerPort).collect(Collectors.toSet());

        /* Create routing tables for each terminal. */
        Map<Terminal, Map<Terminal, Way<Terminal>>> fibs =
            getFibs(bandwidth, innerTerminalPorts);

        /* Convert our exposed terminals to a sequence so we can form
         * every combination of two terminals. */
        final List<MyTerminal> termSeq = new ArrayList<>(terminals.values());
        final int size = termSeq.size();

        /* For every combination of our exposed terminals, store the
         * total distance as part of the result. */
        Map<Edge<Terminal>, Double> result = new HashMap<>();
        for (int i = 0; i + 1 < size; i++) {
            final MyTerminal start = termSeq.get(i);
            final Terminal innerStart = start.innerPort();
            final Map<Terminal, Way<Terminal>> startFib =
                fibs.get(innerStart);
            if (startFib == null) continue;
            for (int j = i + 1; j < size; j++) {
                final MyTerminal end = termSeq.get(j);
                final Terminal innerEnd = end.innerPort();
                final Way<Terminal> way = startFib.get(innerEnd);
                if (way == null) continue;
                final Edge<Terminal> edge = Edge.of(start, end);
                result.put(edge, way.distance);
            }
        }

        return result;
    }

    private final NetworkControl control = new NetworkControl() {
        @Override
        public Map<Edge<Terminal>, Double> getModel(double bandwidth) {
            return PersistentAggregator.this.getModel(bandwidth);
        }

        @Override
        public Terminal getTerminal(String id) {
            synchronized (PersistentAggregator.this) {
                return terminals.get(id);
            }
        }

        @Override
        public Collection<String> getTerminals() {
            synchronized (PersistentAggregator.this) {
                return new HashSet<>(terminals.keySet());
            }
        }

        @Override
        public Service getService(int id) {
            synchronized (PersistentAggregator.this) {
                return services.get(id);
            }
        }

        @Override
        public Service newService() {
            final int id;
            try (Connection conn = database();
                Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO " + serviceTable
                    + " DEFAULT VALUES;", Statement.RETURN_GENERATED_KEYS);
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (!rs.next())
                        throw new ServiceResourceException("could not"
                            + " generate new service id");
                    id = rs.getInt(1);
                }
            } catch (SQLException e) {
                throw new ServiceResourceException("unable to create new service",
                                                   e);
            }
            MyService conn = new MyService(id);
            synchronized (PersistentAggregator.this) {
                services.put(id, conn);
            }
            return conn;
        }

        @Override
        public Collection<Integer> getServiceIds() {
            return new HashSet<>(services.keySet());
        }

        @Override
        public String name() {
            return name;
        }
    };

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

    private static enum Intent {
        RELEASE, INACTIVE, ACTIVE;
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

    private void updateIntent(Connection conn, int srvid, boolean status)
        throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE "
            + serviceTable + " SET intent = ? WHERE service_id = ?;")) {
            stmt.setInt(1, status ? 1 : 0);
            stmt.setInt(2, srvid);
            stmt.execute();
        }
    }

    /**
     * Record a change to trunk capacity in the database.
     * 
     * @param conn the connection to the database
     * 
     * @param tid the id of the trunk to update
     * 
     * @param up the change to the the upstream capacity
     * 
     * @param down the change to the downstream capacity
     * 
     * @throws SQLException if there was an error accessing the database
     */
    private void updateTrunkCapacity(Connection conn, int tid, double up,
                                     double down)
        throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE "
            + trunkTable + " SET up_cap = up_cap + ?,"
            + " SET down_cap = down_cap + ?" + " WHERE trunk_id = ?;")) {
            stmt.setInt(3, tid);
            stmt.setDouble(1, up);
            stmt.setDouble(2, down);
            stmt.execute();
        }
    }
}
