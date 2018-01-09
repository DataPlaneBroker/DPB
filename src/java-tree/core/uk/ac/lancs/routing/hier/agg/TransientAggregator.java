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
package uk.ac.lancs.routing.hier.agg;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import uk.ac.lancs.routing.hier.Connection;
import uk.ac.lancs.routing.hier.ConnectionListener;
import uk.ac.lancs.routing.hier.ConnectionRequest;
import uk.ac.lancs.routing.hier.ConnectionStatus;
import uk.ac.lancs.routing.hier.EndPoint;
import uk.ac.lancs.routing.hier.Port;
import uk.ac.lancs.routing.hier.SwitchControl;
import uk.ac.lancs.routing.span.Edge;
import uk.ac.lancs.routing.span.HashableEdge;
import uk.ac.lancs.routing.span.Spans;
import uk.ac.lancs.routing.span.Way;

/**
 * Implements a virtual switch hierarchically configuring inferior
 * switches.
 * 
 * @author simpsons
 */
public class TransientAggregator implements Aggregator {
    private class MyPort implements Port {
        private final String name;
        private final Port innerPort;

        public MyPort(String name, Port innerPort) {
            this.name = name;
            this.innerPort = innerPort;
        }

        public Port innerPort() {
            return innerPort;
        }

        @Override
        public SwitchControl getSwitch() {
            return getControl();
        }

        TransientAggregator owner() {
            return TransientAggregator.this;
        }

        @Override
        public EndPoint getEndPoint(short label) {
            if (label < 0)
                throw new IllegalArgumentException("negative label on " + this
                    + ": " + label);
            return new MyEndPoint(this, label);
        }

        @Override
        public String toString() {
            return TransientAggregator.this.name + ":" + name;
        }
    }

    private static class MyEndPoint implements EndPoint {
        private final Port port;
        private final short label;

        MyEndPoint(Port port, short label) {
            assert port != null;
            this.port = port;
            this.label = label;
        }

        @Override
        public Port getPort() {
            return port;
        }

        @Override
        public short getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return port + ":" + label;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + label;
            result = prime * result + ((port == null) ? 0 : port.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            MyEndPoint other = (MyEndPoint) obj;
            if (label != other.label) return false;
            if (port == null) {
                if (other.port != null) return false;
            } else if (!port.equals(other.port)) return false;
            return true;
        }
    }

    private class MyConnection implements Connection {
        private class Client implements ConnectionListener {
            final Connection connection;
            Throwable error;
            boolean activeIntent;

            Client(Connection connection) {
                this.connection = connection;
                this.connection.addListener(this);
            }

            @Override
            public void ready() {
                clientReady(this);
            }

            @Override
            public void failed(Throwable t) {
                clientFailed(this, t);
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

            /***
             * These methods are only to be called while synchronized on
             * the containing connection.
             ***/

            void activate() {
                assert Thread.holdsLock(MyConnection.this);
                if (activeIntent) return;
                activeIntent = true;
                connection.activate();
            }

            void deactivate() {
                assert Thread.holdsLock(MyConnection.this);
                if (!activeIntent) return;
                activeIntent = false;
                if (connection != null) connection.deactivate();
            }

            void release() {
                assert Thread.holdsLock(MyConnection.this);
                if (connection != null) {
                    connection.release();
                }
            }
        }

        /***
         * The following methods are to be called by Client objects.
         ***/

        synchronized void clientReady(Client cli) {
            if (tunnels == null) return;
            unresponded--;
            if (unresponded > 0) return;
            if (errored > 0) return;
            callOut(ConnectionListener::ready);
            if (intendedActivity) clients.stream().forEach(Client::activate);
        }

        synchronized void clientFailed(Client cli, Throwable t) {
            if (tunnels == null) return;
            if (cli.error != null) return;
            cli.error = t;
            errored++;
            unresponded--;
            if (errored == 1) {
                /* This is the first error. */
                callOut(l -> l.failed(t));
            }
        }

        synchronized void clientReleased(Client cli) {
            if (tunnels == null) return;
            if (!clients.remove(cli)) return;
            if (!clients.isEmpty()) return;
            clients.clear();
            tunnels = null;
            bandwidth = -1.0;
            synchronized (TransientAggregator.this) {
                connections.remove(id);
            }
            callOut(ConnectionListener::released);
        }

        synchronized void clientActivated(Client cli) {
            if (tunnels == null) return;
            activeInferiors++;
            if (activeInferiors < clients.size()) return;
            callOut(ConnectionListener::activated);
        }

        synchronized void clientDeactivated(Client cli) {
            if (tunnels == null) return;
            activeInferiors--;
            if (activeInferiors > 0) return;
            callOut(ConnectionListener::deactivated);
        }

        final int id;
        final Collection<ConnectionListener> listeners = new HashSet<>();
        final List<Client> clients = new ArrayList<>();

        private void callOut(Consumer<? super ConnectionListener> action) {
            listeners.stream()
                .forEach(l -> executor.execute(() -> action.accept(l)));
        }

        double bandwidth;
        Map<Trunk, EndPoint> tunnels;

        int unresponded, errored;
        int activeInferiors;
        boolean intendedActivity = false;

        MyConnection(int id) {
            this.id = id;
        }

        @Override
        public synchronized void initiate(ConnectionRequest request) {
            if (bandwidth < 0.0)
                throw new IllegalStateException("connection released");
            if (tunnels != null)
                throw new IllegalStateException("connection in use");
            bandwidth = request.bandwidth;
            tunnels = new HashMap<>();
            clients.clear();
            errored = activeInferiors = 0;
            intendedActivity = false;

            /* Plot a spanning tree across this switch, allocating
             * tunnels. */
            Map<SwitchControl, Collection<EndPoint>> subterminals =
                new HashMap<>();
            plotTree(request.terminals, bandwidth, tunnels, subterminals);

            /* Create connections for each inferior switch, and a
             * distinct reference of our own for each one. */
            Map<Connection, ConnectionRequest> subcons = subterminals
                .entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().newConnection(),
                                          e -> ConnectionRequest
                                              .of(e.getValue(), bandwidth)));
            clients.addAll(subcons.keySet().stream().map(Client::new)
                .collect(Collectors.toList()));
            unresponded = clients.size();

            /* Tell each of the subconnections to initiate spanning
             * trees with their respective end points. */
            subcons.entrySet().stream()
                .forEach(e -> e.getKey().initiate(e.getValue()));
        }

        @Override
        public synchronized ConnectionStatus status() {
            if (errored > 0) return ConnectionStatus.FAILED;
            if (bandwidth < 0.0) return ConnectionStatus.RELEASED;
            if (tunnels == null) return ConnectionStatus.DORMANT;
            if (unresponded > 0) return ConnectionStatus.ESTABLISHING;
            if (intendedActivity) return activeInferiors < clients.size()
                ? ConnectionStatus.ACTIVATING : ConnectionStatus.ACTIVE;
            return activeInferiors > 0 ? ConnectionStatus.DEACTIVATING
                : ConnectionStatus.INACTIVE;
        }

        /* ConnectionListener user, double bandwidth, Map<SwitchControl,
         * Collection<EndPoint>> subterminals, Map<Trunk, EndPoint>
         * tunnels) { this.id = id; this.user = user; this.bandwidth =
         * bandwidth; this.tunnels = tunnels; this.clients =
         * subterminals.entrySet().stream().map(Client::new)
         * .collect(Collectors.toList()); this.unresponded =
         * clients.size(); } */

        @Override
        public synchronized void activate() {
            if (errored > 0)
                throw new IllegalStateException("inferior error(s)");
            if (intendedActivity) return;
            intendedActivity = true;
            if (unresponded > 0) return;
            clients.stream().forEach(Client::activate);
        }

        @Override
        public synchronized void deactivate() {
            if (!intendedActivity) return;
            intendedActivity = false;
            clients.stream().forEach(Client::deactivate);
        }

        @Override
        public synchronized void release() {
            /* Release switch resources. */
            clients.stream().forEach(Client::release);
            clients.clear();

            synchronized (TransientAggregator.this) {
                /* Release tunnel resources. */
                tunnels.forEach((k, v) -> k.releaseTunnel(v));
                connections.remove(id);
            }

            tunnels.clear();
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public synchronized void addListener(ConnectionListener events) {
            listeners.add(events);
        }

        @Override
        public synchronized void removeListener(ConnectionListener events) {
            listeners.remove(events);
        }

        synchronized void dump(PrintWriter out) {
            // TODO
        }
    }

    public void dump(PrintWriter out) {
        Collection<MyConnection> connections;
        synchronized (this) {
            connections = new ArrayList<>(this.connections.values());
        }
        for (MyConnection conn : connections)
            conn.dump(out);
    }

    private final Executor executor;
    private final String name;

    private final Map<String, MyPort> ports = new HashMap<>();
    private final Map<Port, TransientTrunk> trunks = new HashMap<>();
    private final Map<Integer, MyConnection> connections = new HashMap<>();
    private int nextConnectionId;

    public TransientAggregator(Executor executor, String name) {
        this.executor = executor;
        this.name = name;
    }

    @Override
    public TrunkManagement addTrunk(Port p1, Port p2) {
        if (trunks.containsKey(p1))
            throw new IllegalArgumentException("port in use: " + p1);
        if (trunks.containsKey(p2))
            throw new IllegalArgumentException("port in use: " + p2);
        TransientTrunk trunk = new TransientTrunk(p1, p2);
        trunks.put(p1, trunk);
        trunks.put(p2, trunk);
        return trunk.getManagement();
    }

    @Override
    public TrunkManagement findTrunk(Port p) {
        return trunks.get(p).getManagement();
    }

    /**
     * Add a new port exposing an inferior switch's port.
     * 
     * @param name the local name of the port
     * 
     * @param inner the port of an inferior switch
     * 
     * @return the newly created port
     */
    public Port addPort(String name, Port inner) {
        if (ports.containsKey(name))
            throw new IllegalArgumentException("name in use: " + name);
        MyPort result = new MyPort(name, inner);
        ports.put(name, result);
        return result;
    }

    /**
     * Remove a port from this switch.
     * 
     * @param name the port's local name
     */
    public void removePort(String name) {
        ports.remove(name);
    }

    @Override
    public Port getPort(String id) {
        return ports.get(id);
    }

    @Override
    public SwitchControl getControl() {
        return control;
    }

    /**
     * Plot a spanning tree across this switch, allocating tunnels on
     * trunks.
     * 
     * @param terminals the switch's own visible end points to be
     * connected
     * 
     * @param bandwidth the bandwidth required on all tunnels
     * 
     * @param tunnels a place to store the set of allocated tunnels,
     * indicating which trunk they belong to
     * 
     * @param subterminals a place to store which internal end points of
     * which internal switches should be connected
     * 
     * @throws IllegalArgumentException if any end point does not belong
     * to this switch
     */
    synchronized void
        plotTree(Collection<? extends EndPoint> terminals, double bandwidth,
                 Map<? super Trunk, ? super EndPoint> tunnels,
                 Map<? super SwitchControl, Collection<EndPoint>> subterminals) {
        /* Map the set of caller's end points to the corresponding inner
         * end points that our topology consists of. */
        Collection<EndPoint> innerTerminalEndPoints =
            terminals.stream().map(t -> {
                Port p = t.getPort();
                if (!(p instanceof MyPort))
                    throw new IllegalArgumentException("end point " + t
                        + " not part of " + name);
                MyPort xp = (MyPort) p;
                if (xp.owner() != this)
                    throw new IllegalArgumentException("end point " + t
                        + " not part of " + name);
                Port ip = xp.innerPort();
                return ip.getEndPoint(t.getLabel());
            }).collect(Collectors.toSet());

        /* Get the set of ports that will be used as destinations in
         * routing. */
        Collection<Port> innerTerminalPorts = innerTerminalEndPoints.stream()
            .map(EndPoint::getPort).collect(Collectors.toSet());

        /* Create routing tables for each port. */
        Map<Port, Map<Port, Way<Port>>> fibs =
            getFibs(bandwidth, innerTerminalPorts);

        /* Create terminal-aware weights for each edge. */
        Map<Edge<Port>, Double> weightedEdges =
            Spans.flatten(fibs, (p1, p2) -> HashableEdge.of(p1, p2));

        /* To impose additional constraints on the spanning tree, keep a
         * set of switches already reached. Edges that connect two
         * distinct switches that have both been reached shall be
         * excluded. */
        Collection<SwitchControl> reachedSwitches = new HashSet<>();

        /* Create the spanning tree, keeping track of reached switches,
         * and rejecting edges connecting two already reached
         * switches. */
        Collection<Edge<Port>> tree =
            Spans.span(innerTerminalPorts, weightedEdges,
                       p -> reachedSwitches.add(p.getSwitch()), e -> {
                           SwitchControl first = e.first().getSwitch();
                           SwitchControl second = e.second().getSwitch();
                           if (first == second) return true;
                           if (reachedSwitches.contains(first)
                               && reachedSwitches.contains(second))
                               return false;
                           return true;
                       });

        for (Edge<Port> edge : tree) {
            SwitchControl firstSwitch = edge.first().getSwitch();
            SwitchControl secondSwitch = edge.second().getSwitch();
            if (firstSwitch == secondSwitch) {
                /* This is an edge across an inferior switch. We don't
                 * handle it directly, but infer it by the edges that
                 * connect to it. */
                continue;
            }
            /* This is an edge runnning along a trunk. */

            /* Create a tunnel along this trunk, and remember one end of
             * it. */
            Trunk firstTrunk = trunks.get(edge.first());
            EndPoint ep1 = firstTrunk.allocateTunnel(bandwidth);
            tunnels.put(firstTrunk, ep1);

            /* Get both end points, find out what switches they
             * correspond to, and add each end point to its switch's
             * respective set of end points. */
            EndPoint ep2 = firstTrunk.getPeer(ep1);
            subterminals.computeIfAbsent(ep1.getPort().getSwitch(),
                                         k -> new HashSet<>())
                .add(ep1);
            subterminals.computeIfAbsent(ep2.getPort().getSwitch(),
                                         k -> new HashSet<>())
                .add(ep2);
        }

        /* Make sure the caller's end points are included in their
         * switches' corresponding sets. */
        for (EndPoint t : innerTerminalEndPoints)
            subterminals.computeIfAbsent(t.getPort().getSwitch(),
                                         k -> new HashSet<>())
                .add(t);

        return;
    }

    /**
     * Given a subset of our internal ports to connect and a bandwidth
     * requirement, create FIBs for each port.
     * 
     * <p>
     * This method does not modify any switch state, but should only be
     * called while synchronized on the switch.
     * 
     * @param bandwidth the required bandwidth
     * 
     * @param innerTerminalPorts the set of ports to connect
     * 
     * @return a FIB for each port
     */
    Map<Port, Map<Port, Way<Port>>>
        getFibs(double bandwidth, Collection<Port> innerTerminalPorts) {
        assert Thread.holdsLock(this);

        /* Get a subset of all trunks, those with sufficent bandwidth
         * and free tunnels. */
        Collection<Trunk> adequateTrunks = trunks.values().stream()
            .filter(trunk -> trunk.getAvailableTunnelCount() > 0
                && trunk.getBandwidth() >= bandwidth)
            .collect(Collectors.toSet());

        /* Get edges representing all suitable trunks. */
        Map<Edge<Port>, Double> edges =
            new HashMap<>(adequateTrunks.stream().collect(Collectors
                .toMap(t -> HashableEdge.of(t.getPorts()), Trunk::getDelay)));

        /* Get a set of all switches for our trunks. */
        Collection<SwitchControl> switches = adequateTrunks.stream()
            .flatMap(trunk -> trunk.getPorts().stream().map(Port::getSwitch))
            .collect(Collectors.toSet());

        /* Get models of all switches connected to the selected trunks,
         * and combine their edges with the trunks. */
        edges.entrySet()
            .addAll(switches.stream()
                .flatMap(sw -> sw.getModel(bandwidth).entrySet().stream())
                .collect(Collectors.toSet()));

        /* Get rid of spurs as a small optimization. */
        Spans.prune(innerTerminalPorts, edges.keySet());

        /* Create routing tables for each port. */
        return Spans.route(innerTerminalPorts, edges);
    }

    synchronized Map<Edge<Port>, Double> getModel(double bandwidth) {
        /* Map the set of our end points to the corresponding inner
         * ports that our topology consists of. */
        Collection<Port> innerTerminalPorts = ports.values().stream()
            .map(MyPort::innerPort).collect(Collectors.toSet());

        /* Create routing tables for each port. */
        Map<Port, Map<Port, Way<Port>>> fibs =
            getFibs(bandwidth, innerTerminalPorts);

        /* Convert our exposed ports to a sequence so we can form every
         * combination of two ports. */
        final List<MyPort> portSeq = new ArrayList<>(ports.values());
        final int size = portSeq.size();

        /* For every combination of our exposed ports, store the total
         * distance as part of the result. */
        Map<Edge<Port>, Double> result = new HashMap<>();
        for (int i = 0; i + 1 < size; i++) {
            final MyPort start = portSeq.get(i);
            final Port innerStart = start.innerPort();
            final Map<Port, Way<Port>> startFib = fibs.get(innerStart);
            if (startFib == null) continue;
            for (int j = i + 1; j < size; j++) {
                final MyPort end = portSeq.get(j);
                final Port innerEnd = end.innerPort();
                final Way<Port> way = startFib.get(innerEnd);
                if (way == null) continue;
                final Edge<Port> edge = HashableEdge.of(start, end);
                result.put(edge, way.distance);
            }
        }

        return result;
    }

    private final SwitchControl control = new SwitchControl() {
        @Override
        public Map<Edge<Port>, Double> getModel(double bandwidth) {
            return TransientAggregator.this.getModel(bandwidth);
        }

        @Override
        public Connection getConnection(int id) {
            synchronized (TransientAggregator.this) {
                return connections.get(id);
            }
        }

        @Override
        public Connection newConnection() {
            synchronized (TransientAggregator.this) {
                int id = nextConnectionId++;
                MyConnection conn = new MyConnection(id);
                connections.put(id, conn);
                return conn;
            }
        }
    };
}
