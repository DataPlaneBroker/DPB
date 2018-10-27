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
import java.sql.SQLIntegrityConstraintViolationException;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.networks.Circuit;
import uk.ac.lancs.networks.InvalidServiceException;
import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.Segment;
import uk.ac.lancs.networks.Service;
import uk.ac.lancs.networks.ServiceListener;
import uk.ac.lancs.networks.ServiceResourceException;
import uk.ac.lancs.networks.ServiceStatus;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.TrafficFlow;
import uk.ac.lancs.networks.mgmt.Aggregator;
import uk.ac.lancs.networks.mgmt.LabelsInUseException;
import uk.ac.lancs.networks.mgmt.NetworkResourceException;
import uk.ac.lancs.networks.mgmt.TerminalExistsException;
import uk.ac.lancs.networks.mgmt.TerminalId;
import uk.ac.lancs.networks.mgmt.Trunk;
import uk.ac.lancs.networks.mgmt.UnknownSubnetworkException;
import uk.ac.lancs.networks.mgmt.UnknownSubterminalException;
import uk.ac.lancs.networks.mgmt.UnknownTerminalException;
import uk.ac.lancs.networks.mgmt.UnknownTrunkException;
import uk.ac.lancs.networks.util.ReferenceWatcher;
import uk.ac.lancs.routing.span.DistanceVectorComputer;
import uk.ac.lancs.routing.span.Edge;
import uk.ac.lancs.routing.span.FIBSpanGuide;
import uk.ac.lancs.routing.span.Graphs;
import uk.ac.lancs.routing.span.SpanningTreeComputer;
import uk.ac.lancs.routing.span.Way;

/**
 * Implements a network aggregator that retains its state in a database.
 * 
 * @author simpsons
 */
public class PersistentAggregator implements Aggregator {
    private class MyService implements Service {
        /**
         * Ensure all subservices are not providing us with
         * notifications. Called by
         * {@link PersistentAggregator#serviceWatcher} when all
         * references to this service have gone.
         */
        void cleanUp() {
            clients.forEach(Client::term);
        }

        /**
         * Accesses a subservice, receives notifications from it, and
         * records recent state about it.
         * 
         * <p>
         * No locks are held on this object. Instead, the container (our
         * service) is used. Calls from the container may check that the
         * calling thread holds the lock on that container. Calls from
         * subservices are passed on to synchronized methods on the
         * container.
         * 
         * @author simpsons
         */
        private class Client implements ServiceListener {
            final Service subservice;

            private ServiceListener protectedSelf;

            @SuppressWarnings("unused")
            ServiceStatus lastStableStatus = ServiceStatus.DORMANT;
            ServiceStatus lastStatus = ServiceStatus.DORMANT;

            Client(Service subservice) {
                if (subservice == null)
                    throw new NullPointerException("subservice");
                this.subservice = subservice;
            }

            /**
             * Ensure the subservice can talk back to this client.
             */
            void init() {
                protectedSelf = protect(ServiceListener.class, this);
                this.subservice.addListener(protectedSelf);
            }

            /**
             * Ensure that the subservice does not talk back to this
             * client.
             */
            void term() {
                this.subservice.removeListener(protectedSelf);
            }

            void activate() {
                subservice.activate();
            }

            void deactivate() {
                subservice.deactivate();
            }

            void release() {
                subservice.release();
            }

            void dump(PrintWriter out) {
                assert Thread.holdsLock(MyService.this);

                out.printf("%n      inferior %s:", subservice.status());
                Segment request = subservice.getRequest();
                if (request != null) {
                    for (Map.Entry<? extends Circuit, ? extends TrafficFlow> entry : request
                        .circuitFlows().entrySet()) {
                        Circuit ep = entry.getKey();
                        TrafficFlow flow = entry.getValue();
                        out.printf("%n        %10s %6g %6g", ep, flow.ingress,
                                   flow.egress);
                    }
                }
            }

            @Override
            public void newStatus(ServiceStatus newStatus) {
                newClientStatus(this, newStatus);
            }
        }

        /***
         * The following methods are to be called by inner Client
         * objects.
         ***/

        synchronized void newClientStatus(Client cli,
                                          ServiceStatus newStatus) {
            /* Ignore some non-sensical reports. */
            if (newStatus == ServiceStatus.DORMANT) return;

            /* Do nothing if the status hasn't changed. */
            if (newStatus == cli.lastStatus) return;

            /* Keep track of which counters have changed. */
            boolean activeChanged = false, inactiveChanged = false,
                failedChanged = false, releasedChanged = false;
            @SuppressWarnings("unused")
            boolean dormantChanged = false;

            /* Decrement counters for the previous status of this
             * subservice. */
            switch (cli.lastStatus) {
            case DORMANT:
                dormantCount--;
                dormantChanged = true;
                break;

            case INACTIVE:
                inactiveCount--;
                inactiveChanged = true;
                break;

            case ACTIVE:
                activeCount--;
                activeChanged = true;
                break;

            case FAILED:
                /* After failure, we can only get RELEASED/RELEASING. We
                 * don't decrement this counter. */
                break;

            default:
                /* Nothing else makes sense. */
                return;
            }

            switch (newStatus) {
            case INACTIVE:
                inactiveCount++;
                inactiveChanged = true;
                break;

            case ACTIVE:
                activeCount++;
                activeChanged = true;
                break;

            case FAILED:
                failedCount++;
                failedChanged = true;
                break;

            case RELEASED:
                releasedCount++;
                releasedChanged = true;
                break;

            default:
                /* Nothing else makes sense. TODO: Log a problem. */
                break;
            }

            /* Record the last status and last stable status for this
             * subservice. */
            cli.lastStatus = newStatus;
            if (newStatus.isStable()) cli.lastStableStatus = newStatus;

            try (Connection conn = openDatabase()) {
                conn.setAutoCommit(false);
                boolean okayToCommit = true;

                try {
                    final Intent intent = getIntent(conn, id);

                    /* If any subservice failed, ensure all subservices
                     * are deactivated, release tunnels, and inform the
                     * users. */
                    if (failedChanged) {
                        /* Make sure we have all the errors from the
                         * failing subservice. */
                        errors.addAll(cli.subservice.errors());

                        switch (intent) {
                        case ABORT:
                            /* If we've already recorded that we're
                             * aborting, we have nothing else to do. */
                            return;

                        case RELEASE:
                            /* If the user has already tried to release
                             * us, we have nothing else to do. */
                            return;

                        default:
                            break;
                        }

                        /* Record that we are aborting this service. */
                        setIntent(conn, id, Intent.ABORT);

                        /* Ensure that all subservices are
                         * deactivated. */
                        clients.forEach(Client::deactivate);

                        /* Release all trunk resources now. We
                         * definitely don't need them any more. */
                        releaseTunnels(conn, this.id);

                        /* Notify the user that we have failed, and the
                         * only remaining events are RELEASING and
                         * RELEASED. */
                        callOut(ServiceStatus.FAILED);
                        return;
                    }

                    if (inactiveChanged && inactiveCount == clients.size()) {
                        /* All clients have become inactive. */
                        callOut(ServiceStatus.INACTIVE);

                        switch (intent) {
                        case ACTIVE:
                            /* The clients must have been DORMANT, but
                             * the user prematurely activated us. */
                            callOut(ServiceStatus.ACTIVATING);
                            clients.forEach(Client::activate);
                            break;

                        case RELEASE:
                            /* The user released the service while it
                             * was (trying to be) active. Initiate the
                             * release process. */
                            startRelease(conn);
                            break;

                        default:
                            break;
                        }

                        return;
                    }

                    if (intent == Intent.RELEASE) {
                        if (releasedChanged
                            && releasedCount == clients.size()) {
                            /* All subservices have been released, so we
                             * can regard ourselves as fully released
                             * now. */
                            completeRelease(conn);
                            return;
                        }
                        return;
                    }

                    if (activeChanged && activeCount == clients.size()) {
                        if (intent == Intent.ACTIVE)
                            callOut(ServiceStatus.ACTIVE);
                        return;
                    }
                } catch (Throwable t) {
                    okayToCommit = false;
                    throw t;
                } finally {
                    if (okayToCommit) conn.commit();
                }
            } catch (SQLException e) {
                throw new RuntimeException("DB failure on"
                    + " receiving status update", e);
            }
        }

        private void completeRelease(Connection conn) throws SQLException {
            assert Thread.holdsLock(this);

            /* Lose the references to all the subservices, and make sure
             * they've lost our callbacks. */
            clients.forEach(Client::term);
            clients.clear();

            /* Ensure this service can't be found again by users. */
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM "
                + subserviceTable + " WHERE service_id = ?;")) {
                stmt.setInt(1, id);
                stmt.execute();
            }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM "
                + circuitTable + " WHERE service_id = ?;")) {
                stmt.setInt(1, id);
                stmt.execute();
            }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM "
                + serviceTable + " WHERE service_id = ?;")) {
                stmt.setInt(1, id);
                stmt.execute();
            }

            /* Send our last report to all users. */
            callOut(ServiceStatus.RELEASED);
            listeners.clear();
        }

        final int id;
        final Collection<ServiceListener> listeners = new HashSet<>();
        final List<Client> clients = new ArrayList<>();

        private void callOut(ServiceStatus status) {
            listeners.forEach(l -> l.newStatus(status));
        }

        /**
         * This is just a cache of what we got from the user
         * (sanitized), or what we recovered from the database.
         */
        Segment request;

        /**
         * Holds errors not attached to circuits of subservices.
         */
        final Collection<Throwable> errors = new HashSet<>();

        int dormantCount, inactiveCount, activeCount, failedCount,
            releasedCount;

        MyService(int id) {
            this.id = id;
        }

        @Override
        public synchronized void define(Segment request)
            throws InvalidServiceException {

            /* Make sure the request is sane. */
            request = Segment.sanitize(request, 0.01);
            if (request.circuitFlows().size() < 2)
                throw new IllegalArgumentException("invalid service"
                    + " description (fewer than" + " two circuits)");

            Collection<Service> redundantServices = new HashSet<>();
            boolean failed = true;
            try (Connection conn = openDatabase()) {
                conn.setAutoCommit(false);

                final Intent intent = getIntent(conn, id);
                if (intent == Intent.RELEASE) {
                    if (clients.isEmpty())
                        throw new IllegalStateException("service released");
                    else
                        throw new IllegalStateException("service releasing");
                }

                /* Check that we are not already in use. */
                try (PreparedStatement stmt =
                    conn.prepareStatement("SELECT * FROM " + circuitTable
                        + " WHERE service_id = ?" + " LIMIT 1;")) {
                    stmt.setInt(1, id);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next())
                            throw new IllegalStateException("service in use");
                    }
                }

                /* Plot a spanning tree across this network, allocating
                 * tunnels. */
                Collection<Segment> subrequests = new HashSet<>();
                plotAsymmetricTree(conn, this, request, subrequests);

                /* Create subservices for each inferior network, and a
                 * distinct reference of our own for each one. */
                Map<Service, Segment> subcons =
                    subrequests.stream()
                        .collect(Collectors.toMap(r -> r.circuitFlows()
                            .keySet().iterator().next().getTerminal()
                            .getNetwork().newService(), r -> r));

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
                        stmt.setString(3, subsrv.getNetwork().name());
                        stmt.execute();
                    }
                }

                /* Create a client for each subservice. */
                clients.addAll(subcons.keySet().stream().map(Client::new)
                    .collect(Collectors.toList()));
                clients.forEach(Client::init);

                /* Tell each of the subconnections to initiate spanning
                 * trees with their respective circuits. */
                for (Map.Entry<Service, Segment> entry : subcons.entrySet()) {
                    System.err.printf("Initiating subservice on %s%n",
                                      entry.getValue().circuitFlows());
                    entry.getKey().define(entry.getValue());
                }

                /* Record our service's circuits. */
                try (PreparedStatement stmt =
                    conn.prepareStatement("INSERT INTO " + circuitTable
                        + " (service_id, terminal_id, label, metering, shaping)"
                        + " VALUES (?, ?, ?, ?, ?);")) {
                    stmt.setInt(1, this.id);
                    for (Map.Entry<? extends Circuit, ? extends TrafficFlow> entry : request
                        .circuitFlows().entrySet()) {
                        Circuit circuit = entry.getKey();
                        SuperiorTerminal term =
                            (SuperiorTerminal) circuit.getTerminal();
                        TrafficFlow flow = entry.getValue();
                        stmt.setInt(2, term.id());
                        stmt.setInt(3, circuit.getLabel());
                        stmt.setDouble(4, flow.ingress);
                        stmt.setDouble(5, flow.egress);
                        stmt.execute();
                    }
                }

                this.request = request;
                conn.commit();
                System.err.printf("Initiated service %d%n", this.id);
                redundantServices.clear();
                failed = false;
            } catch (SQLException e) {
                throw new ServiceResourceException("could not plot"
                    + " tree across network", e);
            } finally {
                redundantServices.forEach(Service::release);
                if (failed) release();
            }
        }

        ServiceStatus internalStatus(Connection conn) throws SQLException {
            final int intent;
            try (PreparedStatement stmt =
                conn.prepareStatement("SELECT intent FROM " + serviceTable
                    + " WHERE service_id = ?;")) {
                stmt.setInt(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next())
                        throw new RuntimeException("service id missing: "
                            + id);
                    intent = rs.getInt(1);
                }
            }
            if (intent == Intent.RELEASE.ordinal()) {
                if (clients.isEmpty())
                    return ServiceStatus.RELEASED;
                else
                    return ServiceStatus.RELEASING;
            }
            if (failedCount > 0) return ServiceStatus.FAILED;
            final boolean initiated = testInitiated(conn, id);
            if (!initiated) return ServiceStatus.DORMANT;
            assert failedCount == 0;
            if (dormantCount > 0) return ServiceStatus.ESTABLISHING;
            if (intent == Intent.ACTIVE.ordinal())
                return activeCount < clients.size() ? ServiceStatus.ACTIVATING
                    : ServiceStatus.ACTIVE;
            return activeCount > 0 ? ServiceStatus.DEACTIVATING
                : ServiceStatus.INACTIVE;
        }

        @Override
        public synchronized ServiceStatus status() {
            try (Connection conn = openDatabase()) {
                conn.setAutoCommit(false);
                return internalStatus(conn);
            } catch (SQLException e) {
                throw new RuntimeException("DB failure getting"
                    + " status of service " + id, e);
            }
        }

        @Override
        public synchronized void activate() {
            /* If anything has already gone wrong, we can do nothing
             * more. */
            if (failedCount > 0)
                throw new IllegalStateException("inferior error(s)");

            final boolean initiated;
            try (Connection conn = openDatabase()) {
                conn.setAutoCommit(false);

                final Intent intent = getIntent(conn, id);

                /* If the user has released us, we can do nothing
                 * more. */
                if (intent == Intent.RELEASE)
                    throw new IllegalStateException("released");

                /* Do nothing if we've already recorded the user's
                 * intent, as we must also have activated inferior
                 * services. */
                if (intent == Intent.ACTIVE) return;
                setIntent(conn, id, Intent.ACTIVE);
                initiated = testInitiated(conn, id);
                conn.commit();
            } catch (SQLException ex) {
                throw new ServiceResourceException("failed to store intent",
                                                   ex);
            }

            /* Do nothing but record the user's intent, if they haven't
             * yet provided circuit details. */
            if (!initiated) return;

            callOut(ServiceStatus.ACTIVATING);
            clients.forEach(Client::activate);
        }

        @Override
        public synchronized void deactivate() {
            try (Connection conn = openDatabase()) {
                conn.setAutoCommit(false);
                final Intent intent = getIntent(conn, id);
                if (intent != Intent.ACTIVE) return;
                setIntent(conn, id, Intent.INACTIVE);
                conn.commit();
            } catch (SQLException ex) {
                throw new ServiceResourceException("failed to store intent",
                                                   ex);
            }
            callOut(ServiceStatus.DEACTIVATING);
            if (inactiveCount + failedCount == clients.size()) {
                callOut(ServiceStatus.INACTIVE);
            } else {
                clients.forEach(Client::deactivate);
            }
        }

        @Override
        public synchronized void release() {
            try (Connection conn = openDatabase()) {
                conn.setAutoCommit(false);

                final Intent intent = getIntent(conn, id);
                /* There's nothing to do if we've already recorded the
                 * user's intent to release the service. */
                if (intent == Intent.RELEASE) return;

                /* If the current intent is to be active, trigger
                 * deactivation first. When it completes, and discovers
                 * the release intent, it will start the release
                 * process. Otherwise, we start it now. */
                if (intent == Intent.ACTIVE) {
                    setIntent(conn, id, Intent.RELEASE);
                    if (activeCount > 0) {
                        /* Report start of deactivation. */
                        callOut(ServiceStatus.DEACTIVATING);

                        /* Deactivate inferiors. */
                        clients.forEach(Client::deactivate);
                    }
                } else {
                    /* Record the new intent and initiate the release
                     * process. */
                    setIntent(conn, id, Intent.RELEASE);
                }
                startRelease(conn);
                conn.commit();
            } catch (SQLException e) {
                throw new RuntimeException("DB error" + " releasing service "
                    + id, e);
            }
        }

        private void startRelease(Connection conn) throws SQLException {
            assert Thread.holdsLock(this);

            /* Inform users that the release process has started. */
            callOut(ServiceStatus.RELEASING);

            /* Release subservice resources. */
            clients.forEach(Client::release);

            /* Release tunnel resources. */
            releaseTunnels(conn, this.id);

            if (releasedCount == clients.size()) {
                /* All subservices are already released. This only
                 * happens if we were still dormant when released. */
                completeRelease(conn);
            }
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

        synchronized void dump(Connection conn, PrintWriter out)
            throws SQLException {
            ServiceStatus status = status();
            out.printf("  %3d %-8s", id, status);
            switch (status) {
            case DORMANT:
            case RELEASED:
                break;

            default:
                Collection<Trunk> refs = new ArrayList<>();
                for (Map.Entry<MyTrunk, Circuit> tunnel : getTunnels(conn, id,
                                                                     refs)
                                                                         .entrySet()) {
                    Circuit ep1 = tunnel.getValue();
                    Circuit ep2 = tunnel.getKey().getPeer(ep1);
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
        public synchronized NetworkControl getNetwork() {
            return control;
        }

        @Override
        public synchronized Segment getRequest() {
            return request;
        }

        synchronized void
            recover(Connection conn,
                    Map<Circuit, ? extends TrafficFlow> circuits,
                    Collection<? extends Service> subservices)
                throws SQLException {
            request = Segment.create(circuits);

            clients.clear();
            for (Service srv : subservices) {
                Client cli = new Client(srv);
                clients.add(cli);
            }

            /* Ensure we receive status updates. We don't receive any
             * until this synchronized call terminates. */
            clients.forEach(Client::init);

            for (Client cli : clients) {
                switch (cli.subservice.status()) {
                case DORMANT:
                    dormantCount++;
                    break;
                case INACTIVE:
                    inactiveCount++;
                    break;
                case ACTIVE:
                    activeCount++;
                    break;
                case FAILED:
                    failedCount++;
                    errors.addAll(cli.subservice.errors());
                    break;
                case RELEASED:
                    releasedCount++;
                    break;
                default:
                    break;
                }
            }

            final Intent intent = getIntent(conn, id);
            if (intent == Intent.RELEASE) {
                if (releasedCount == clients.size()) {
                    completeRelease(conn);
                }
            }
        }

        @Override
        public synchronized Collection<Throwable> errors() {
            return new HashSet<>(errors);
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
        private volatile boolean disabled;

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

        void disable() {
            disabled = true;
        }

        double getBandwidth(Connection conn, String field)
            throws SQLException {
            try (PreparedStatement stmt =
                conn.prepareStatement("SELECT " + field + " FROM "
                    + trunkTable + " WHERE trunk_id = ? LIMIT 1;")) {
                stmt.setInt(1, dbid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next())
                        throw new RuntimeException("missing trunk id: "
                            + dbid);
                    return rs.getDouble(1);
                }
            }
        }

        double getUpstreamBandwidth(Connection conn) throws SQLException {
            return getBandwidth(conn, "up_cap");
        }

        /**
         * Get the upstream bandwidth remaining available on this trunk.
         * 
         * @return the remaining available bandwidth from start terminal
         * to end
         */
        double getUpstreamBandwidth() {
            if (disabled) throw new IllegalStateException("trunk removed");
            try (Connection conn = openDatabase()) {
                return getUpstreamBandwidth(conn);
            } catch (SQLException e) {
                throw new RuntimeException("DB error getting trunk b/w: "
                    + dbid);
            }
        }

        double getDownstreamBandwidth(Connection conn) throws SQLException {
            return getBandwidth(conn, "down_cap");
        }

        /**
         * Get the downstream bandwidth remaining available on this
         * trunk.
         * 
         * @return the remaining available bandwidth from end terminal
         * to start
         */
        double getDownstreamBandwidth() {
            if (disabled) throw new IllegalStateException("trunk removed");
            try (Connection conn = openDatabase()) {
                return getDownstreamBandwidth(conn);
            } catch (SQLException e) {
                throw new RuntimeException("DB error getting trunk b/w: "
                    + dbid);
            }
        }

        /**
         * Get the maximum of the upstream and downstream bandwidths.
         * 
         * @return the best bandwidth available on this trunk
         */
        double getMaximumBandwidth() {
            if (disabled) throw new IllegalStateException("trunk removed");
            try (Connection conn = openDatabase()) {
                return getBandwidth(conn, "MAX(up_cap, down_cap)");
            } catch (SQLException e) {
                throw new RuntimeException("DB error getting trunk b/w: "
                    + dbid);
            }
        }

        /**
         * Get the peer of a circuit.
         * 
         * @param p the circuit whose peer is requested
         * 
         * @return the peer of the supplied circuit, or {@code null} if
         * it has no peer
         * 
         * @throws IllegalArgumentException if the circuit does not
         * belong to either terminal of this trunk
         */
        Circuit getPeer(Circuit p) {
            if (p == null) throw new NullPointerException("circuit");
            if (disabled) throw new IllegalStateException("trunk removed");

            final String indexKey, resultKey;
            final Terminal base;
            if (p.getTerminal().equals(start)) {
                indexKey = "start_label";
                resultKey = "end_label";
                base = end;
            } else if (p.getTerminal().equals(end)) {
                indexKey = "end_label";
                resultKey = "start_label";
                base = start;
            } else {
                throw new IllegalArgumentException("circuit does not"
                    + " belong to trunk");
            }
            try (Connection conn = openDatabase();
                PreparedStatement stmt = conn.prepareStatement("SELECT "
                    + resultKey + " FROM " + labelTable
                    + " WHERE trunk_id = ?" + " AND " + indexKey + " = ?;")) {
                stmt.setInt(1, dbid);
                stmt.setInt(2, p.getLabel());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) return null;
                    return base.circuit(rs.getInt(1));
                }
            } catch (SQLException ex) {
                throw new RuntimeException("database inaccessible", ex);
            }
        }

        /**
         * Allocate a tunnel through this trunk. If successful, only one
         * end of the tunnel is returned. The other can be obtained with
         * {@link #getPeer(Circuit)}.
         * 
         * @param upstreamBandwidth the bandwidth to allocate to the
         * tunnel in the direction from the start terminal to the end
         * 
         * @param downstreamBandwidth the bandwidth to allocate to the
         * tunnel in the direction from the end terminal to the start
         * 
         * @return the circuit at the start of the tunnel, or
         * {@code null} if no further resource remains
         * @throws SQLException
         */
        Circuit allocateTunnel(Connection conn, MyService service,
                               double upstreamBandwidth,
                               double downstreamBandwidth)
            throws SQLException {
            if (disabled) throw new IllegalStateException("trunk removed");

            /* Sanity-check bandwidth. */
            if (upstreamBandwidth < 0)
                throw new IllegalArgumentException("negative upstream: "
                    + upstreamBandwidth);
            if (downstreamBandwidth < 0)
                throw new IllegalArgumentException("negative downstream: "
                    + downstreamBandwidth);
            try (PreparedStatement stmt =
                conn.prepareStatement("SELECT up_cap, down_cap" + " FROM "
                    + trunkTable + " WHERE trunk_id = ? LIMIT 1;")) {
                stmt.setInt(1, dbid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next())
                        throw new RuntimeException("missing trunk id: "
                            + dbid);
                    if (upstreamBandwidth > rs.getDouble(1)) return null;
                    if (downstreamBandwidth > rs.getDouble(2)) return null;
                }
            }

            /* Find a free label. */
            final int startLabel;
            try (PreparedStatement stmt =
                conn.prepareStatement("SELECT start_label FROM " + labelTable
                    + " WHERE trunk_id = ?" + " AND service_id IS NULL"
                    + " LIMIT 1;")) {
                stmt.setInt(1, dbid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) return null;
                    startLabel = rs.getInt(1);
                }
            }

            /* Store the allocation for the label, marking it as being
             * used by the specified service. */
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
            return start.circuit(startLabel);
        }

        /**
         * Release a tunnel through this trunk.
         * 
         * @param circuit either of the tunnel circuits
         * 
         * @throws SQLException
         */
        void releaseTunnel(Connection conn, Circuit circuit)
            throws SQLException {
            if (disabled) throw new IllegalStateException("trunk removed");

            /* Identify whether we're looking at the start or end of
             * this tunnel. */
            final int label = circuit.getLabel();
            final String key;
            if (circuit.getTerminal().equals(start)) {
                key = "start_label";
            } else if (circuit.getTerminal().equals(end)) {
                key = "end_label";
            } else {
                throw new IllegalArgumentException("not our circuit: "
                    + circuit);
            }

            /* Find out how much has been allocated. */
            final double upAlloc, downAlloc;
            try (PreparedStatement stmt =
                conn.prepareStatement("SELECT up_alloc," + " down_alloc"
                    + " FROM " + labelTable + " WHERE trunk_id = ?" + " AND "
                    + key + " = ?;")) {
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
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE "
                + labelTable + " SET service_id = NULL," + " up_alloc = NULL,"
                + " down_alloc = NULL" + " WHERE trunk_id = ?" + " AND " + key
                + " = ?")) {
                stmt.setInt(1, dbid);
                stmt.setInt(2, label);
                stmt.execute();
            }

            conn.commit();
        }

        int getAvailableTunnelCount(Connection conn) throws SQLException {
            try (PreparedStatement stmt = conn
                .prepareStatement("SELECT COUNT(*)" + " FROM " + labelTable
                    + " WHERE trunk_id = ?" + " AND service_id IS NULL;")) {
                stmt.setInt(1, dbid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) throw new AssertionError("unreachable");
                    return rs.getInt(1);
                }
            }
        }

        double getDelay(Connection conn) throws SQLException {
            try (PreparedStatement stmt =
                conn.prepareStatement("SELECT metric FROM " + trunkTable
                    + " WHERE trunk_id = ? LIMIT 1;")) {
                stmt.setInt(1, dbid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next())
                        throw new ServiceResourceException("missing trunk id: "
                            + dbid);
                    return rs.getDouble(1);
                }
            }
        }

        /**
         * Get the fixed delay of this trunk.
         * 
         * @return the trunk's fixed delay
         */
        @Override
        public double getDelay() {
            if (disabled) throw new IllegalStateException("trunk removed");
            try (Connection conn = openDatabase()) {
                return getDelay(conn);
            } catch (SQLException e) {
                throw new ServiceResourceException("unexpected DB failure",
                                                   e);
            }
        }

        /**
         * Get the terminals at either end of this trunk.
         * 
         * @return the terminals of the trunk
         */
        List<Terminal> getTerminals() {
            if (disabled) throw new IllegalStateException("trunk removed");
            return Arrays.asList(start, end);
        }

        @Override
        public void withdrawBandwidth(double upstream, double downstream) {
            if (disabled) throw new IllegalStateException("trunk removed");

            if (upstream < 0)
                throw new IllegalArgumentException("negative upstream: "
                    + upstream);
            if (downstream < 0)
                throw new IllegalArgumentException("negative downstream: "
                    + downstream);

            try (Connection conn = openDatabase()) {
                conn.setAutoCommit(false);
                try (PreparedStatement stmt =
                    conn.prepareStatement("SELECT up_cap, down_cap" + " FROM "
                        + trunkTable + " WHERE trunk_id = ? LIMIT 1;")) {
                    stmt.setInt(1, dbid);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next())
                            throw new NetworkResourceException(PersistentAggregator.this,
                                                               "missing"
                                                                   + " trunk id: "
                                                                   + dbid);
                        final double upstreamCapacity = rs.getDouble(1);
                        final double downstreamCapacity = rs.getDouble(2);
                        if (upstream > upstreamCapacity)
                            throw new IllegalArgumentException("request"
                                + " upstream " + upstream + " exceeds "
                                + upstreamCapacity);
                        if (downstream > downstreamCapacity)
                            throw new IllegalArgumentException("request"
                                + " downstream " + downstream + " exceeds "
                                + downstreamCapacity);
                    }
                }

                updateTrunkCapacity(conn, this.dbid, -upstream, -downstream);
                conn.commit();
            } catch (SQLException e) {
                throw new NetworkResourceException(PersistentAggregator.this,
                                                   "unexpected DB failure",
                                                   e);
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

            try (Connection conn = openDatabase()) {
                updateTrunkCapacity(conn, this.dbid, +upstream, +downstream);
            } catch (SQLException e) {
                throw new NetworkResourceException(PersistentAggregator.this,
                                                   "unexpected DB failure",
                                                   e);
            }
        }

        @Override
        public void setDelay(double delay) {
            try (Connection conn = openDatabase();
                PreparedStatement stmt =
                    conn.prepareStatement("UPDATE " + trunkTable
                        + " SET metric = ?" + " WHERE trunk_id = ?;")) {
                stmt.setDouble(1, delay);
                stmt.setInt(2, dbid);
                stmt.execute();
            } catch (SQLException e) {
                throw new NetworkResourceException(PersistentAggregator.this,
                                                   "unexpected DB failure",
                                                   e);
            }
        }

        private void revokeInternalRange(String key, int base, int amount)
            throws SQLException,
                LabelsInUseException {
            try (Connection conn = openDatabase()) {
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
                        throw new LabelsInUseException(PersistentAggregator.this,
                                                       this, inUse);
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
        public void revokeStartLabelRange(int startBase, int amount)
            throws LabelsInUseException {
            try {
                revokeInternalRange("start_label", startBase, amount);
            } catch (SQLException e) {
                throw new NetworkResourceException(PersistentAggregator.this,
                                                   "unexpected DB failure",
                                                   e);
            }
        }

        @Override
        public void revokeEndLabelRange(int endBase, int amount)
            throws LabelsInUseException {
            try {
                revokeInternalRange("end_label", endBase, amount);
            } catch (SQLException e) {
                throw new NetworkResourceException(PersistentAggregator.this,
                                                   "unexpected DB failure",
                                                   e);
            }
        }

        @Override
        public void defineLabelRange(int startBase, int amount, int endBase)
            throws LabelsInUseException {
            /* TODO: WTF is this? If amount is negative...? */
            if (startBase + amount < startBase)
                throw new IllegalArgumentException("illegal start range "
                    + startBase + " plus " + amount);
            if (endBase + amount < endBase)
                throw new IllegalArgumentException("illegal end range "
                    + endBase + " plus " + amount);

            try (Connection conn = openDatabase()) {
                conn.setAutoCommit(false);

                /* Check that all numbers are available. */
                try (PreparedStatement stmt =
                    conn.prepareStatement("SELECT start_label" + " FROM "
                        + labelTable + " WHERE trunk_id = ?"
                        + " AND ((start_label >= ?" + " AND start_label <= ?)"
                        + " OR (end_label >= ?" + " AND end_label <= ?));")) {
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
                        throw new LabelsInUseException(PersistentAggregator.this,
                                                       this, startInUse);
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
                throw new NetworkResourceException(PersistentAggregator.this,
                                                   "unexpected DB failure",
                                                   e);
            }
        }

        int position(Terminal term) {
            return getTerminals().indexOf(term);
        }

        @Override
        public String toString() {
            return start + "+" + end;
        }

        @Override
        public void decommission() {
            try (Connection conn = openDatabase()) {
                commissionTrunk(conn, dbid, false);
            } catch (SQLException e) {
                throw new NetworkResourceException(PersistentAggregator.this,
                                                   "DB failure "
                                                       + "setting trunk "
                                                       + dbid
                                                       + " commissioned status",
                                                   e);
            }
        }

        @Override
        public void recommission() {
            try (Connection conn = openDatabase()) {
                commissionTrunk(conn, dbid, true);
            } catch (SQLException e) {
                throw new NetworkResourceException(PersistentAggregator.this,
                                                   "DB failure "
                                                       + "setting trunk "
                                                       + dbid
                                                       + " commissioned status",
                                                   e);
            }
        }

        @Override
        public boolean isCommissioned() {
            try (Connection conn = openDatabase()) {
                conn.setAutoCommit(false);
                return getTrunkCommissioned(conn, dbid);
            } catch (SQLException e) {
                throw new NetworkResourceException(PersistentAggregator.this,
                                                   "DB failure "
                                                       + "fetching trunk "
                                                       + dbid
                                                       + " commissioned status",
                                                   e);
            }
        }

        Terminal getTerminal(int pos) {
            switch (pos) {
            case 0:
                return start;
            case 1:
                return end;
            default:
                throw new IllegalArgumentException("position meaningless: "
                    + pos);
            }
        }

        @Override
        public TerminalId getStartTerminal() {
            return TerminalId.of(start.getNetwork().name(), start.name());
        }

        @Override
        public TerminalId getEndTerminal() {
            return TerminalId.of(end.getNetwork().name(), end.name());
        }
    }

    private final Function<? super String, ? extends NetworkControl> inferiors;

    private final String circuitTable, terminalTable, serviceTable,
        subserviceTable, trunkTable, labelTable;
    private final String dbConnectionAddress;
    private final Properties dbConnectionConfig;

    private final Executor executor;
    private final String name;

    // private final Map<String, SuperiorTerminal> terminals = new
    // HashMap<>();

    /**
     * Print out the status of all connections and trunks of this
     * aggregator.
     * 
     * @param out the destination for the status report
     */
    @Override
    public void dumpStatus(PrintWriter out) {
        try (Connection conn = newDatabaseContext(false)) {
            out.printf("aggregate %s:%n", name);
            for (int id : getServiceIds(conn)) {
                /* Ensure the service is non-collectable. We don't use
                 * the reference, but we need to retain it to prevent
                 * GC. */
                @SuppressWarnings("unused")
                Service srvIface = serviceWatcher.get(id);

                MyService srv = serviceWatcher.getBase(id);
                srv.dump(conn, out);
            }
            Collection<Trunk> refs = new ArrayList<>();
            for (MyTrunk trunk : getAllTrunks(conn, refs)) {
                out.printf("  %s%s=(%gMbps, %gMbps, %gs) [%d]%n",
                           trunk.getTerminals(),
                           getTrunkCommissioned(conn, trunk.dbid) ? " " : "!",
                           trunk.getUpstreamBandwidth(conn),
                           trunk.getDownstreamBandwidth(conn),
                           trunk.getDelay(conn),
                           trunk.getAvailableTunnelCount(conn));
            }
            out.flush();
            conn.commit();
        } catch (SQLException e) {
            throw new NetworkResourceException(PersistentAggregator.this,
                                               "unable to dump network "
                                                   + name,
                                               e);
        }
    }

    /**
     * Create an aggregator with state backed up in a database. Ensure
     * the necessary tables exist in the database. Recreate the internal
     * service records mentioned in the tables. Obtain subservices used
     * to build these services.
     * 
     * <p>
     * Configuration consists of the following fields:
     * 
     * <dl>
     * 
     * <dt><samp>name</samp></dt>
     * 
     * <dd>The name of the aggregator network, used to form the fully
     * qualified names of its terminals
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
     * @param executor used to invoke call-backs created by this
     * aggregator and passed to inferior networks
     * 
     * @param inferiors a mapping from names to networks, so that this
     * aggregator can find inferior networks and the terminals and ports
     * on them
     * 
     * @param config the configuration describing the network and access
     * to the database
     * 
     * @throws SQLException if there was an error in accessing the
     * database
     */
    public PersistentAggregator(Executor executor,
                                Function<? super String, ? extends NetworkControl> inferiors,
                                Configuration config)
        throws SQLException {
        this.executor = executor;
        this.inferiors = inferiors;
        this.name = config.get("name");

        /* Record how we talk to the database. */
        Configuration dbConfig = config.subview("db");
        this.dbConnectionAddress = dbConfig.get("service");
        this.dbConnectionConfig = dbConfig.toProperties();
        this.circuitTable = dbConfig.get("end-points.table", "end_points");
        this.terminalTable = dbConfig.get("terminals.table", "terminal_map");
        this.serviceTable = dbConfig.get("services.table", "services");
        this.subserviceTable = dbConfig.get("services.table", "subservices");
        this.trunkTable = dbConfig.get("trunks.table", "trunks");
        this.labelTable = dbConfig.get("labels.table", "label_map");

        serviceWatcher.start();
        trunkWatcher.start();

        try (Connection conn = openDatabase()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                /* The terminal table maps this network's terminals
                 * (which have a name and an internal numeric id) to
                 * inferior networks' terminals (identified by the name
                 * of the inferior network and the local name of the
                 * port). */
                stmt.execute("CREATE TABLE IF NOT EXISTS " + terminalTable
                    + " (terminal_id INTEGER PRIMARY KEY,"
                    + " name VARCHAR(20) NOT NULL UNIQUE,"
                    + " subnetwork VARCHAR(40) NOT NULL,"
                    + " subname VARCHAR(40) NOT NULL);");

                /* The trunk table allocates internal numeric ids to our
                 * trunks, identifies the subnetwork and subterminal at
                 * each end of the trunk, records the trunk's metric
                 * (delay), whether the trunk is commissioned, and what
                 * bandwidth is available in each direction over it. */
                stmt.execute("CREATE TABLE IF NOT EXISTS " + trunkTable
                    + " (trunk_id INTEGER PRIMARY KEY,"
                    + " start_network VARCHAR(20),"
                    + " start_terminal VARCHAR(20),"
                    + " end_network VARCHAR(20),"
                    + " end_terminal VARCHAR(20),"
                    + " up_cap DECIMAL(9,3) DEFAULT 0.0,"
                    + " down_cap DECIMAL(9,3) DEFAULT 0.0,"
                    + " metric DECIMAL(9,3) DEFAULT 0.1,"
                    + " commissioned INTEGER DEFAULT 1);");

                /* The service table records in-use service ids, and
                 * records the user's intent (active, inactive,
                 * release). */
                stmt.execute("CREATE TABLE IF NOT EXISTS " + serviceTable
                    + " (service_id INTEGER PRIMARY KEY,"
                    + " intent INT UNSIGNED DEFAULT 0);");

                /* The end-point table records which superior circuits
                 * are associated with each initiated service. When no
                 * entries refer to a particular service id, that
                 * service is uninitiated (dormant). */
                stmt.execute("CREATE TABLE IF NOT EXISTS " + circuitTable
                    + " (service_id INTEGER," + " terminal_id INTEGER,"
                    + " label INTEGER UNSIGNED," + " metering DECIMAL(9,3),"
                    + " shaping DECIMAL(9,3),"
                    + " PRIMARY KEY(service_id, terminal_id, label),"
                    + " FOREIGN KEY(service_id) REFERENCES " + serviceTable
                    + "(service_id),"
                    + " FOREIGN KEY(terminal_id) REFERENCES " + terminalTable
                    + "(terminal_id));");

                /* The subservice table relates each of our services to
                 * those in other networks. */
                stmt.execute("CREATE TABLE IF NOT EXISTS " + subserviceTable
                    + " (service_id INTEGER," + " subservice_id INTEGER,"
                    + " subnetwork_name VARCHAR(20),"
                    + " PRIMARY KEY(service_id, subnetwork_name, subservice_id),"
                    + " FOREIGN KEY(service_id) REFERENCES " + serviceTable
                    + "(service_id));");

                /* The label table records labels available on each
                 * trunk, and the mapping between labels at either end
                 * (forming tunnels). It also associates each tunnel
                 * with a service if it is in use by that service, and
                 * includes the bandwidth allocated in each direction to
                 * that tunnel. The bandwidth is allocated from the
                 * trunk's bandwidth, so when service_id is set, some
                 * bandwidth must be subtracted from
                 * trunkTable.{up,down}_cap, and added to
                 * labelTable.{up,down}_alloc. Correspondingly, when a
                 * tunnel is released, the process must be reversed. */
                stmt.execute("CREATE TABLE IF NOT EXISTS " + labelTable
                    + " (trunk_id INTEGER," + " start_label INTEGER,"
                    + " end_label INTEGER,"
                    + " up_alloc DECIMAL(9,3) DEFAULT NULL,"
                    + " down_alloc DECIMAL(9,3) DEFAULT NULL,"
                    + " service_id INTEGER DEFAULT NULL,"
                    + " PRIMARY KEY(trunk_id, start_label, end_label),"
                    + " UNIQUE(trunk_id, start_label),"
                    + " UNIQUE(trunk_id, end_label),"
                    + " FOREIGN KEY(trunk_id) REFERENCES " + trunkTable
                    + "(trunk_id)," + " FOREIGN KEY(service_id) REFERENCES "
                    + serviceTable + "(service_id));");
            }

            conn.commit();
        }
    }

    private Terminal getSubterminal(TerminalId id)
        throws UnknownSubnetworkException,
            UnknownSubterminalException {
        NetworkControl nc = inferiors.apply(id.network);
        if (nc == null)
            throw new UnknownSubnetworkException(this, id.network);
        Terminal result = nc.getTerminal(id.terminal);
        if (result == null) throw new UnknownSubterminalException(this, id);
        return result;
    }

    @Override
    public Trunk addTrunk(TerminalId t1, TerminalId t2)
        throws UnknownSubterminalException,
            UnknownSubnetworkException {
        Terminal p1 = getSubterminal(t1);
        Terminal p2 = getSubterminal(t2);
        if (p1 == null || p2 == null)
            throw new NullPointerException("null terminal(s)");
        try (Connection conn = newDatabaseContext(false);
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
                if (!rs.next())
                    throw new NetworkResourceException(PersistentAggregator.this,
                                                       "failed to generate"
                                                           + " id for new trunk "
                                                           + p1 + " to "
                                                           + p2);
                final int id = rs.getInt(1);
                conn.commit();
                return trunkWatcher.get(id);
            }
        } catch (SQLException ex) {
            throw new NetworkResourceException(PersistentAggregator.this,
                                               "DB failure creating"
                                                   + " trunk from " + p1
                                                   + " to " + p2,
                                               ex);
        }
    }

    @Override
    public void removeTrunk(TerminalId subterm)
        throws UnknownSubterminalException,
            UnknownSubnetworkException,
            UnknownTrunkException {
        Terminal p = getSubterminal(subterm);
        if (p == null) throw new UnknownSubterminalException(this, subterm);
        try (Connection conn = newDatabaseContext(false)) {
            final int id = findTrunkId(conn, p);
            if (id < 0)
                throw new UnknownTrunkException(PersistentAggregator.this,
                                                subterm);
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM "
                + trunkTable + " WHERE trunk_id = ?;")) {
                stmt.setInt(1, id);
                stmt.execute();
            }
            conn.commit();
            MyTrunk trunk = trunkWatcher.getBase(id);
            trunk.disable();
        } catch (SQLException e) {
            throw new NetworkResourceException(PersistentAggregator.this,
                                               "removing trunk for " + p, e);
        }
    }

    @Override
    public Trunk findTrunk(TerminalId subterm)
        throws UnknownSubterminalException,
            UnknownSubnetworkException {
        Terminal p = getSubterminal(subterm);
        if (p == null) return null;
        try (Connection conn = newDatabaseContext(false)) {
            final int id = findTrunkId(conn, p);
            if (id < 0) return null;
            Trunk result = trunkWatcher.get(id);
            MyTrunk base = trunkWatcher.getBase(id);
            if (base.position(p) == 1) return result.reverse();
            return result;
        } catch (SQLException e) {
            throw new NetworkResourceException(PersistentAggregator.this,
                                               "finding trunk for " + p, e);
        }
    }

    @Override
    public Terminal addTerminal(String name, TerminalId subterm)
        throws UnknownSubterminalException,
            UnknownSubnetworkException,
            TerminalExistsException {
        Terminal inner = getSubterminal(subterm);
        try (Connection conn = openDatabase();
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
                SuperiorTerminal result =
                    new SuperiorTerminal(getControl(), name, inner, id);
                return result;

            }
        } catch (SQLIntegrityConstraintViolationException ex) {
            throw new TerminalExistsException(PersistentAggregator.this, name,
                                              ex);
        } catch (SQLException ex) {
            throw new NetworkResourceException(PersistentAggregator.this,
                                               "DB failure"
                                                   + " creating terminal "
                                                   + name + " on " + inner
                                                   + " in database",
                                               ex);
        }
    }

    /**
     * Remove a terminal from this aggregator.
     * 
     * @param name the terminal's local name
     */
    @Override
    public void removeTerminal(String name) throws UnknownTerminalException {
        try (Connection conn = openDatabase();
            PreparedStatement stmt = conn.prepareStatement("DELETE FROM "
                + terminalTable + " WHERE name = ?;")) {
            stmt.setString(1, name);
            int done = stmt.executeUpdate();
            if (done == 0)
                throw new UnknownTerminalException(PersistentAggregator.this,
                                                   name);
        } catch (SQLException ex) {
            throw new NetworkResourceException(PersistentAggregator.this,
                                               "could not remove terminal "
                                                   + name + " from database",
                                               ex);
        }
    }

    @Override
    public NetworkControl getControl() {
        return control;
    }

    /**
     * Plot a spanning tree with asymmetric bandwidth requirements
     * across this aggregator, allocation tunnels on trunks.
     * 
     * @param service the service which will own the tunnels formed
     * across trunks
     * 
     * @param request the request specifying bandwidth at each concerned
     * circuit of this aggregator
     * 
     * @param tunnels a place to store the set of allocated tunnels,
     * indexed by trunk
     * 
     * @param subrequests a place to store the connection requests to be
     * submitted to each inferior network
     * 
     * @throws SQLException if there was an error accessing the database
     */
    void plotAsymmetricTree(Connection conn, MyService service,
                            Segment request,
                            Collection<? super Segment> subrequests)
        throws SQLException {
        /* Sanity-check the circuits, map them to internal terminals,
         * and record bandwidth requirements. */
        Map<Terminal, List<Double>> bandwidths = new HashMap<>();
        Map<Circuit, List<Double>> innerCircuits = new HashMap<>();
        double smallestBandwidthSoFar = Double.MAX_VALUE;
        for (Map.Entry<? extends Circuit, ? extends TrafficFlow> entry : request
            .circuitFlows().entrySet()) {
            Circuit ep = entry.getKey();
            TrafficFlow flow = entry.getValue();

            /* Map this circuit to an inferior network's terminal. */
            Terminal outerPort = ep.getTerminal();
            if (!(outerPort instanceof SuperiorTerminal))
                throw new IllegalArgumentException("circuit " + ep
                    + " not part of " + name);
            SuperiorTerminal myPort = (SuperiorTerminal) outerPort;
            if (myPort.getNetwork() != getControl())
                throw new IllegalArgumentException("circuit " + ep
                    + " not part of " + name);

            /* Record the bandwidth produced and consumed on the
             * inferior network's terminal. Make sure we aggregate
             * contributions when two or more circuits belong to the
             * same terminal. */
            double produced = flow.ingress;
            double consumed = flow.egress;
            Terminal innerPort = myPort.subterminal();
            List<Double> tuple = bandwidths
                .computeIfAbsent(innerPort, k -> Arrays.asList(0.0, 0.0));
            tuple.set(0, tuple.get(0) + produced);
            tuple.set(1, tuple.get(1) + consumed);

            /* Map the outer circuit to an inner one by copying the
             * label. */
            innerCircuits.put(innerPort.circuit(ep.getLabel()),
                              Arrays.asList(produced, consumed));

            /* Get the smallest production. We use it to filter out
             * trunks that no longer have the required bandwidth in
             * either direction. */
            if (produced < smallestBandwidthSoFar)
                smallestBandwidthSoFar = produced;
        }
        double smallestBandwidth = smallestBandwidthSoFar;

        /* Get the set of terminals to connect. */
        Collection<Terminal> innerTerminals = bandwidths.keySet();

        /* Get a subset of all trunks, those with sufficent bandwidth
         * and free tunnels. */
        Collection<Trunk> refs = new ArrayList<>();
        Collection<MyTrunk> adequateTrunks =
            getAdequateTrunks(conn, smallestBandwidth, refs);

        /* Get the set of all networks connected to our selected
         * trunks. */
        Collection<NetworkControl> subnetworks =
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
            trunkEdgeWeights.put(edge, trunk.getDelay(conn));
            trunkEdges.put(edge, trunk);
        }
        fibGraph.addEdges(trunkEdgeWeights);

        /* The edges include virtual ones constituting models of
         * inferior networks. Also make a note of connected terminals
         * within an inferior network, in case it is a fragmented
         * aggregate. */
        Map<Edge<Terminal>, Double> subnetworkEdgeWeights = subnetworks
            .stream()
            .flatMap(sw -> sw.getModel(smallestBandwidth).entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey,
                                      Map.Entry::getValue));
        fibGraph.addEdges(subnetworkEdgeWeights);
        System.err.printf("Subnetwork count: %d%n", subnetworks.size());
        System.err.printf("Subnetwork edges: %s%n",
                          subnetworkEdgeWeights.keySet());
        Map<Terminal, Collection<Terminal>> terminalGroups = Graphs
            .getGroups(Graphs.getAdjacencies(subnetworkEdgeWeights.keySet()));
        System.err.printf("Terminal groups: %s%n", terminalGroups);

        /* Keep track of the weights of all edges, whether they come
         * from trunks or inferior networks. */
        Map<Edge<Terminal>, Double> edgeWeights =
            new HashMap<>(trunkEdgeWeights);
        edgeWeights.putAll(subnetworkEdgeWeights);

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
                    System.err.printf("Reached %s; groups: %s%n", p,
                                      terminalGroups.get(p));
                    reached.addAll(terminalGroups.get(p));
                }).withEdgePreference(guide::select).eliminating(e -> {
                    /* Permit edges within the same network. */
                    NetworkControl first = e.first().getNetwork();
                    NetworkControl second = e.second().getNetwork();
                    if (first == second) return false;

                    /* Allow this edge if at least one terminal hasn't
                     * been reached. */
                    return reached.containsAll(e);
                }).create().getSpanningTree(guide.first());
            if (tree == null)
                throw new ServiceResourceException("no tree found");

            /* Work out how much bandwidth each trunk edge requires in
             * each direction. Find trunk edges in the spanning tree
             * that don't have enough bandwidth for what is going over
             * them. Identify the worst case. */
            Map<MyTrunk, List<Double>> edgeBandwidths = new HashMap<>();
            DistanceVectorComputer<Terminal> routes =
                new DistanceVectorComputer<>(innerTerminals, tree.stream()
                    .collect(Collectors.toMap(e -> e, edgeWeights::get)));
            routes.update();
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

            /* Remove the worst edge from the graph, and start again. */
            if (worstEdge != null) {
                fibGraph.removeEdge(worstEdge);
                continue;
            }
            /* As there is no worst case, we have a result. */

            /* Allocate tunnels along identified trunks. Also gather end
             * points per terminal group, and bandwidth required on
             * each. */
            Map<Collection<Terminal>, Map<Circuit, List<Double>>> subterminals =
                new HashMap<>();
            for (Map.Entry<MyTrunk, List<Double>> trunkReq : edgeBandwidths
                .entrySet()) {
                MyTrunk trunk = trunkReq.getKey();
                double upstream = trunkReq.getValue().get(0);
                double downstream = trunkReq.getValue().get(1);
                Circuit ep1 =
                    trunk.allocateTunnel(conn, service, upstream, downstream);
                // tunnels.put(trunk, ep1);
                Circuit ep2 = trunk.getPeer(ep1);
                subterminals
                    .computeIfAbsent(terminalGroups.get(ep1.getTerminal()),
                                     k -> new HashMap<>())
                    .put(ep1, Arrays.asList(downstream, upstream));
                subterminals
                    .computeIfAbsent(terminalGroups.get(ep2.getTerminal()),
                                     k -> new HashMap<>())
                    .put(ep2, Arrays.asList(upstream, downstream));
            }

            /* Ensure the caller's circuits are included in the requests
             * to inferior networks. */
            for (Map.Entry<Circuit, List<Double>> entry : innerCircuits
                .entrySet()) {
                Circuit ep = entry.getKey();
                subterminals
                    .computeIfAbsent(terminalGroups.get(ep.getTerminal()),
                                     k -> new HashMap<>())
                    .put(ep, entry.getValue());
            }

            /* For each terminal group, create a new connection
             * request. */
            for (Map<Circuit, List<Double>> reqs : subterminals.values()) {
                subrequests.add(Segment.of(reqs));
            }
            return;
        } while (true);
    }

    /**
     * Given a subset of our internal terminals to connect and a
     * bandwidth requirement, create FIBs for each terminal.
     * 
     * @param bandwidth the required bandwidth
     * 
     * @param innerTerminals the set of terminals to connect
     * 
     * @return a FIB for each terminal
     * @throws SQLException
     */
    Map<Terminal, Map<Terminal, Way<Terminal>>>
        getFibs(Connection conn, double bandwidth,
                Collection<Terminal> innerTerminals)
            throws SQLException {
        /* Get a subset of all trunks, those with sufficent bandwidth
         * and free tunnels. */
        Collection<Trunk> refs = new ArrayList<>();
        Collection<MyTrunk> adequateTrunks =
            getAdequateTrunks(conn, bandwidth, refs);

        /* Get edges representing all suitable trunks. */
        Map<Edge<Terminal>, Double> edges = new HashMap<>();
        for (MyTrunk trunk : adequateTrunks) {
            Edge<Terminal> edge = Edge.of(trunk.getTerminals());
            double delay = trunk.getDelay(conn);
            edges.put(edge, delay);
        }

        /* Get a set of all networks for our trunks. */
        Collection<NetworkControl> subnetworks = adequateTrunks.stream()
            .flatMap(trunk -> trunk.getTerminals().stream()
                .map(Terminal::getNetwork))
            .collect(Collectors.toSet());

        /* Get models of all networks connected to the selected trunks,
         * and combine their edges with the trunks. */
        edges.putAll(subnetworks.stream()
            .flatMap(sw -> sw.getModel(bandwidth).entrySet().stream())
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())));

        /* Get rid of spurs as a small optimization. */
        Graphs.prune(innerTerminals, edges.keySet());

        /* Create routing tables for each terminal. */
        return Graphs.route(innerTerminals, edges);
    }

    Map<Edge<Terminal>, Double> getModel(double bandwidth) {
        try (Connection conn = openDatabase()) {
            conn.setAutoCommit(false);

            Collection<SuperiorTerminal> terminals =
                getAllTerminals(conn).values();

            /* Map the set of our circuits to the corresponding inner
             * terminals that our topology consists of. */
            Collection<Terminal> innerTerminalPorts =
                terminals.stream().map(SuperiorTerminal::subterminal)
                    .collect(Collectors.toSet());

            /* Create routing tables for each terminal. */
            Map<Terminal, Map<Terminal, Way<Terminal>>> fibs =
                getFibs(conn, bandwidth, innerTerminalPorts);

            /* Convert our exposed terminals to a sequence so we can
             * form every combination of two terminals. */
            final List<SuperiorTerminal> termSeq = new ArrayList<>(terminals);
            final int size = termSeq.size();

            /* For every combination of our exposed terminals, store the
             * total distance as part of the result. */
            Map<Edge<Terminal>, Double> result = new HashMap<>();
            for (int i = 0; i + 1 < size; i++) {
                final SuperiorTerminal start = termSeq.get(i);
                final Terminal innerStart = start.subterminal();
                final Map<Terminal, Way<Terminal>> startFib =
                    fibs.get(innerStart);
                if (startFib == null) continue;
                for (int j = i + 1; j < size; j++) {
                    final SuperiorTerminal end = termSeq.get(j);
                    final Terminal innerEnd = end.subterminal();
                    final Way<Terminal> way = startFib.get(innerEnd);
                    if (way == null) continue;
                    final Edge<Terminal> edge = Edge.of(start, end);
                    result.put(edge, way.distance);
                }
            }

            return result;
        } catch (SQLException e) {
            throw new RuntimeException("DB error getting model", e);
        }
    }

    private final NetworkControl control = new NetworkControl() {
        @Override
        public Map<Edge<Terminal>, Double> getModel(double bandwidth) {
            return PersistentAggregator.this.getModel(bandwidth);
        }

        @Override
        public Terminal getTerminal(String id) {
            try (Connection conn = openDatabase()) {
                conn.setAutoCommit(false);
                return PersistentAggregator.this.getTerminal(conn, id);
            } catch (SQLException e) {
                throw new NetworkResourceException(PersistentAggregator.this,
                                                   "DB failure"
                                                       + " looking up terminal "
                                                       + id,
                                                   e);
            }
        }

        @Override
        public Collection<String> getTerminals() {
            try (Connection conn = openDatabase()) {
                conn.setAutoCommit(false);
                return getAllTerminals(conn).keySet();
            } catch (SQLException e) {
                throw new NetworkResourceException(PersistentAggregator.this,
                                                   "DB failure"
                                                       + " listing terminals",
                                                   e);
            }
        }

        @Override
        public Service getService(int id) {
            try (Connection conn = newDatabaseContext(false)) {
                Service result = serviceWatcher.get(id);
                conn.commit();
                return result;
            } catch (SQLException e) {
                throw new ServiceResourceException("unable to"
                    + " recover service " + id, e);
            }
        }

        @Override
        public Service newService() {
            try (Connection conn = newDatabaseContext(false)) {
                final int id;
                try (PreparedStatement stmt =
                    conn.prepareStatement("INSERT INTO " + serviceTable
                        + " (intent) VALUES (?);",
                                          Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setInt(1, Intent.INACTIVE.ordinal());
                    stmt.execute();
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (!rs.next())
                            throw new ServiceResourceException("could not"
                                + " generate new service id");
                        id = rs.getInt(1);
                    }
                }
                Service result = serviceWatcher.get(id);
                conn.commit();
                return result;
            } catch (SQLException e) {
                throw new ServiceResourceException("unable to"
                    + " create new service", e);
            }
        }

        @Override
        public Collection<Integer> getServiceIds() {
            try (Connection conn = openDatabase()) {
                return PersistentAggregator.this.getServiceIds(conn);
            } catch (SQLException e) {
                throw new RuntimeException("unable to list"
                    + " service ids in database", e);
            }
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

    private MyService recoverService(Integer id) {
        try {
            Connection conn = contextConnection.get();
            return recoverService(conn, id);
        } catch (SQLException e) {
            throw new ServiceResourceException("database failure"
                + " recovering service " + id, e);
        }
    }

    private final ReferenceWatcher<Service, MyService, Integer> serviceWatcher =
        ReferenceWatcher.on(Service.class, getClass().getClassLoader(),
                            this::recoverService, MyService::cleanUp);

    private MyTrunk recoverTrunk(Connection conn, int id)
        throws SQLException {
        try (PreparedStatement stmt =
            conn.prepareStatement("SELECT" + " start_network,"
                + " start_terminal," + " end_network," + " end_terminal"
                + " FROM " + trunkTable + " WHERE trunk_id = ?;")) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                final String startNetworkName = rs.getString(1);
                final String startTerminalName = rs.getString(2);
                final String endNetworkName = rs.getString(3);
                final String endTerminalName = rs.getString(4);

                Terminal start = inferiors.apply(startNetworkName)
                    .getTerminal(startTerminalName);
                Terminal end = inferiors.apply(endNetworkName)
                    .getTerminal(endTerminalName);

                return new MyTrunk(start, end, id);
            }
        }
    }

    private MyTrunk recoverTrunk(Integer id) {
        try {
            Connection conn = contextConnection.get();
            return recoverTrunk(conn, id);
        } catch (SQLException e) {
            throw new RuntimeException("database failure"
                + " recovering trunk " + id);
        }
    }

    private final ReferenceWatcher<Trunk, MyTrunk, Integer> trunkWatcher =
        ReferenceWatcher.on(Trunk.class, getClass().getClassLoader(),
                            this::recoverTrunk, (t) -> {});

    private static enum Intent {
        INACTIVE, ACTIVE, ABORT, RELEASE;
    }

    /**
     * Get a fresh connection to the database. Use this in a
     * try-with-resources block.
     * 
     * @return the new connection
     * 
     * @throws SQLException if there was an error accessing the database
     */
    private Connection openDatabase() throws SQLException {
        return DriverManager.getConnection(dbConnectionAddress,
                                           dbConnectionConfig);
    }

    private static ThreadLocal<Connection> contextConnection =
        new ThreadLocal<>();

    private static ConnectionContext setContext(Connection newContext) {
        return new ConnectionContext(newContext);
    }

    private static class ConnectionContext implements AutoCloseable {
        private final Connection oldContext;

        private ConnectionContext(Connection newContext) {
            oldContext = contextConnection.get();
            contextConnection.set(newContext);
        }

        @Override
        public void close() {
            contextConnection.set(oldContext);
        }
    }

    /**
     * Get a fresh connection to the database, and make it the current
     * context for the calling thread. Any previous context is
     * preserved, and restored when the new context is closed.
     * 
     * <p>
     * This method should be called from the head of a
     * try-with-resources block.
     * 
     * @param autoCommit the auto-commit status for the new connection
     * 
     * @return the new connection
     * 
     * @throws SQLException if there was an error accessing the database
     */
    private Connection newDatabaseContext(boolean autoCommit)
        throws SQLException {
        Connection result = openDatabase();
        result.setAutoCommit(false);
        Connection oldValue = contextConnection.get();

        /* No method that gets this connection through its thread
         * context should try to close it. We prevent any attempt to
         * close from doing anything. */
        contextConnection.set(new UnclosingConnection(result));

        return new FilterConnection(result) {
            @Override
            public void close() throws SQLException {
                contextConnection.set(oldValue);
                base.close();
            }
        };
    }

    /**** Raw database methods ****/

    private Collection<Integer> getServiceIds(Connection conn)
        throws SQLException {
        Collection<Integer> result = new HashSet<>();
        try (Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT service_id FROM "
                + serviceTable + ";")) {
            while (rs.next()) {
                result.add(rs.getInt(1));
            }
            return result;
        }
    }

    /**
     * Recover a service object from the database.
     * 
     * @param conn the connection to the database
     * 
     * @param id the service id
     * 
     * @return the requested service
     * 
     * @throws IllegalArgumentException if there is no such service
     * 
     * @throws SQLException if there was an error accessing the database
     */
    private MyService recoverService(Connection conn, Integer id)
        throws SQLException {
        try (PreparedStatement stmt =
            conn.prepareStatement("SELECT" + " intent" + " FROM "
                + serviceTable + " WHERE service_id = ?;")) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    System.err.printf("WARNING: no service %d%n", id);
                    return null;
                }
            }
        }

        final Map<Circuit, TrafficFlow> circuits = new HashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT"
            + " tt.name AS terminal_name," + " tt.terminal_id AS terminal_id,"
            + " tt.subnetwork AS subnetwork," + " tt.subname AS subname,"
            + " et.label AS label," + " et.metering AS metering,"
            + " et.shaping AS shaping" + " FROM " + circuitTable + " AS et"
            + " LEFT JOIN " + terminalTable + " AS tt"
            + " ON tt.terminal_id = et.terminal_id"
            + " WHERE et.service_id = ?;")) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final String name = rs.getString(1);
                    final int tid = rs.getInt(2);
                    final String subnetworkName = rs.getString(3);
                    final String subname = rs.getString(4);
                    final int label = rs.getInt(5);
                    final double metering = rs.getDouble(6);
                    final double shaping = rs.getDouble(7);

                    NetworkControl subnetwork =
                        inferiors.apply(subnetworkName);
                    Terminal innerPort = subnetwork.getTerminal(subname);
                    SuperiorTerminal port =
                        new SuperiorTerminal(getControl(), name, innerPort,
                                             tid);

                    final Circuit circuit = port.circuit(label);
                    final TrafficFlow enf = TrafficFlow.of(metering, shaping);
                    circuits.put(circuit, enf);
                }
            }
        }

        final Collection<Service> subservices = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT"
            + " subservice_id, " + " subnetwork_name" + " FROM "
            + subserviceTable + " WHERE service_id = ?;")) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final int subsrvid = rs.getInt(1);
                    final String subnwname = rs.getString(2);
                    NetworkControl subnw = inferiors.apply(subnwname);
                    Service subsrv = subnw.getService(subsrvid);
                    if (subsrv == null) continue;
                    subservices.add(subsrv);
                }
            }
        }

        MyService result = new MyService(id);
        result.recover(conn, circuits, subservices);
        return result;
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
            + " down_cap = down_cap + ?" + " WHERE trunk_id = ?;")) {
            stmt.setInt(3, tid);
            stmt.setDouble(1, up);
            stmt.setDouble(2, down);
            stmt.execute();
        }
    }

    /**
     * Get the internal numeric id of a trunk connecting an inferior
     * terminal.
     * 
     * @param conn the connection to the database
     * 
     * @param p the terminal
     * 
     * @return the id of the trunk connecting the specified terminal, or
     * {@code -1} if there is no matching trunk
     * 
     * @throws SQLException if there was an error accessing the database
     */
    private int findTrunkId(Connection conn, Terminal p) throws SQLException {
        try (PreparedStatement stmt =
            conn.prepareStatement("SELECT trunk_id" + " FROM " + trunkTable
                + " WHERE (start_network = ? AND start_terminal = ?)"
                + " OR (end_network = ? AND end_terminal = ?);")) {
            stmt.setString(1, p.getNetwork().name());
            stmt.setString(2, p.name());
            stmt.setString(3, p.getNetwork().name());
            stmt.setString(4, p.name());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return -1;
                return rs.getInt(1);
            }
        }
    }

    /**
     * Release all tunnels for this service. The service id for each
     * label is nulled to mark is a available, and the up- and
     * downstream allocations are added to the trunk's capacities.
     * 
     * @param conn the connection to the database
     * 
     * @param sid the service id
     * 
     * @throws SQLException if there was an error accessing the database
     */
    private void releaseTunnels(Connection conn, int sid)
        throws SQLException {
        /* Restore available bandwidth from tunnels to the containing
         * trunks. */
        try (
            PreparedStatement trunkUpdateStmt =
                conn.prepareStatement("UPDATE " + trunkTable
                    + " SET up_cap = up_cap + ?," + " down_cap = down_cap + ?"
                    + " WHERE trunk_id = ?;");
            PreparedStatement readStmt = conn
                .prepareStatement("SELECT" + " trunk_id, up_alloc, down_alloc"
                    + " FROM " + labelTable + " WHERE service_id = ?;")) {
            readStmt.setInt(1, sid);
            try (ResultSet rs = readStmt.executeQuery()) {
                while (rs.next()) {
                    final int tid = rs.getInt(1);
                    final double upAlloc = rs.getDouble(2);
                    final double downAlloc = rs.getDouble(3);

                    trunkUpdateStmt.setDouble(1, upAlloc);
                    trunkUpdateStmt.setDouble(2, downAlloc);
                    trunkUpdateStmt.setInt(3, tid);
                    trunkUpdateStmt.execute();
                }
            }
        }

        /* Cancel all allocations for this service. */
        try (PreparedStatement stmt =
            conn.prepareStatement("UPDATE " + labelTable
                + " SET up_alloc = 0.0, down_alloc = 0.0, service_id = NULL"
                + " WHERE service_id = ?")) {
            stmt.setInt(1, sid);
            stmt.execute();
        }
    }

    /**
     * Get the intent of a service.
     * 
     * @param conn the connection to the databse
     * 
     * @param id the service id
     * 
     * @return the current intent of the service
     * 
     * @throws SQLException if there was an error accessing the database
     */
    private Intent getIntent(Connection conn, int id) throws SQLException {
        try (PreparedStatement stmt =
            conn.prepareStatement("SELECT intent FROM " + serviceTable
                + " WHERE service_id = ?" + " LIMIT 1;")) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next())
                    throw new RuntimeException("service id missing: " + id);
                return Intent.values()[rs.getInt(1)];
            }
        }
    }

    /**
     * Set the intent of a service.
     * 
     * @param conn the connection to the databse
     * 
     * @param id the service id
     * 
     * @param intent the new intent of the service
     * 
     * @throws SQLException if there was an error accessing the database
     */
    private void setIntent(Connection conn, int id, Intent intent)
        throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE "
            + serviceTable + " SET intent = ?" + " WHERE service_id = ?;")) {
            stmt.setInt(1, intent.ordinal());
            stmt.setInt(2, id);
            stmt.execute();
        }
    }

    /**
     * Test whether the user has initiated a service.
     * 
     * @param conn the connection to the databse
     * 
     * @param id the service id
     * 
     * @return {@code true} iff the service has some circuits defined
     * 
     * @throws SQLException if there was an error accessing the database
     */
    private boolean testInitiated(Connection conn, int id)
        throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM "
            + circuitTable + " WHERE service_id = ?" + " LIMIT 1;")) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Get details of all tunnels set up for a given service.
     * 
     * @param conn the connection to the databse
     * 
     * @param id the service id
     * 
     * @param refCache a place to retain corresponding external
     * references, so that the internal references won't be discarded
     * 
     * @return a map from all trunks which contain a tunnel for the
     * service to a circuit of that tunnel
     * 
     * @throws SQLException if there was an error accessing the database
     */
    private Map<MyTrunk, Circuit>
        getTunnels(Connection conn, int id,
                   Collection<? super Trunk> refCache)
            throws SQLException {
        Map<MyTrunk, Circuit> tunnels = new HashMap<>();
        try (ConnectionContext ctxt = setContext(conn);
            PreparedStatement stmt =
                conn.prepareStatement("SELECT" + " lt.trunk_id AS trunk_id,"
                    + " lt.start_label AS start_label,"
                    + " tt.start_network AS start_network,"
                    + " tt.start_terminal AS start_terminal" + " FROM "
                    + labelTable + " AS lt" + " LEFT JOIN " + trunkTable
                    + " AS tt" + " ON tt.trunk_id = lt.trunk_id"
                    + " WHERE lt.service_id = ?;")) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final int trid = rs.getInt(1);
                    final int label = rs.getInt(2);
                    final String nwname = rs.getString(3);
                    final String tname = rs.getString(4);
                    NetworkControl subnw = inferiors.apply(nwname);
                    Terminal term = subnw.getTerminal(tname);
                    refCache.add(trunkWatcher.get(trid));
                    MyTrunk trunk = trunkWatcher.getBase(trid);
                    Circuit ep = term.circuit(label);
                    tunnels.put(trunk, ep);
                }
            }
        }
        return tunnels;
    }

    /**
     * Get internal references to all trunks defined in this aggregator.
     * 
     * @param conn the connection to the database
     * 
     * @param refCache a place to retain corresponding external
     * references, so that the internal references won't be discarded
     * 
     * @return the set of all trunks in this aggregator
     * 
     * @throws SQLException if there was an error accessing the database
     */
    private Collection<MyTrunk>
        getAllTrunks(Connection conn, Collection<? super Trunk> refCache)
            throws SQLException {
        Collection<MyTrunk> result = new HashSet<>();
        try (ConnectionContext ctxt = setContext(conn);
            Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT trunk_id" + " FROM "
                + trunkTable + ";")) {
                while (rs.next()) {
                    final int tid = rs.getInt(1);
                    Trunk trunk = trunkWatcher.get(tid);
                    refCache.add(trunk);
                    result.add(trunkWatcher.getBase(tid));
                }
            }
        }
        return result;
    }

    /**
     * Get internal references to all trunks with sufficient bandwidth
     * in at least one direction.
     * 
     * @param conn the connection to the database
     * 
     * @param bandwidth the minimum bandwidth required
     * 
     * @param refCache a place to retain corresponding external
     * references, so that the internal references won't be discarded
     * 
     * @return the set of all trunks with adequate bandiwdth
     * 
     * @throws SQLException if there was an error accessing the database
     */
    private Collection<MyTrunk>
        getAdequateTrunks(Connection conn, double bandwidth,
                          Collection<? super Trunk> refCache)
            throws SQLException {
        Collection<MyTrunk> result = new HashSet<>();
        try (ConnectionContext ctxt = setContext(conn);
            PreparedStatement stmt =
                conn.prepareStatement("SELECT DISTINCT tt.trunk_id" + " FROM "
                    + trunkTable + " AS tt" + " LEFT JOIN " + labelTable
                    + " AS lt" + " ON lt.trunk_id = tt.trunk_id"
                    + " WHERE (tt.up_cap >= ? AND tt.down_cap >= ?"
                    + " AND tt.commissioned > 0)"
                    + " AND lt.service_id IS NULL;")) {
            stmt.setDouble(1, bandwidth);
            stmt.setDouble(2, bandwidth);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final int tid = rs.getInt(1);
                    Trunk trunk = trunkWatcher.get(tid);
                    refCache.add(trunk);
                    result.add(trunkWatcher.getBase(tid));
                }
            }
        }
        return result;
    }

    private SuperiorTerminal getTerminal(Connection conn, String name)
        throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT"
            + " terminal_id," + " subnetwork," + " subname" + " FROM "
            + terminalTable + " WHERE name = ?;")) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                final int id = rs.getInt(1);
                final String subnetworkName = rs.getString(2);
                final String subname = rs.getString(3);

                NetworkControl subnetwork = inferiors.apply(subnetworkName);
                Terminal innerPort = subnetwork.getTerminal(subname);
                SuperiorTerminal port =
                    new SuperiorTerminal(getControl(), name, innerPort, id);
                return port;
            }
        }
    }

    private Map<String, SuperiorTerminal> getAllTerminals(Connection conn)
        throws SQLException {
        Map<String, SuperiorTerminal> terminals = new HashMap<>();
        try (Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT terminal_id, name,"
                + " subnetwork, subname" + " FROM " + terminalTable + ";")) {
            while (rs.next()) {
                final int id = rs.getInt(1);
                final String name = rs.getString(2);
                final String subnetworkName = rs.getString(3);
                final String subname = rs.getString(4);

                NetworkControl subnetwork = inferiors.apply(subnetworkName);
                Terminal innerPort = subnetwork.getTerminal(subname);
                SuperiorTerminal port =
                    new SuperiorTerminal(getControl(), name, innerPort, id);
                terminals.put(name, port);
            }
        }
        return terminals;
    }

    private boolean getTrunkCommissioned(Connection conn, int tid)
        throws SQLException {
        try (PreparedStatement stmt =
            conn.prepareStatement("SELECT commissioned" + " FROM "
                + trunkTable + " WHERE trunk_id = ?" + " LIMIT 1;")) {
            stmt.setInt(1, tid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next())
                    throw new IllegalArgumentException("no trunk " + tid);
                return rs.getInt(1) != 0;
            }
        }
    }

    private void commissionTrunk(Connection conn, int tid, boolean status)
        throws SQLException {
        try (PreparedStatement stmt =
            conn.prepareStatement("UPDATE " + trunkTable
                + " SET commissioned = ?" + " WHERE trunk_id = ?;")) {
            stmt.setInt(1, tid);
            int done = stmt.executeUpdate();
            if (done == 0)
                throw new IllegalArgumentException("no trunk " + tid);
        }
    }
}
