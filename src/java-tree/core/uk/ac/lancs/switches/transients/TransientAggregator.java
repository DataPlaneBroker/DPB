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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import uk.ac.lancs.routing.span.DistanceVectorComputer;
import uk.ac.lancs.routing.span.Edge;
import uk.ac.lancs.routing.span.FIBSpanGuide;
import uk.ac.lancs.routing.span.Graphs;
import uk.ac.lancs.routing.span.SpanningTreeComputer;
import uk.ac.lancs.routing.span.Way;
import uk.ac.lancs.switches.EndPoint;
import uk.ac.lancs.switches.InvalidServiceException;
import uk.ac.lancs.switches.NetworkControl;
import uk.ac.lancs.switches.Service;
import uk.ac.lancs.switches.ServiceListener;
import uk.ac.lancs.switches.ServiceDescription;
import uk.ac.lancs.switches.ServiceStatus;
import uk.ac.lancs.switches.Terminal;
import uk.ac.lancs.switches.TrafficFlow;
import uk.ac.lancs.switches.aggregate.Aggregator;
import uk.ac.lancs.switches.aggregate.Trunk;

/**
 * Implements a network aggregator with no persistent state.
 * 
 * @author simpsons
 */
public class TransientAggregator implements Aggregator {
    private class MyTerminal implements Terminal {
        private final String name;
        private final Terminal innerPort;

        public MyTerminal(String name, Terminal innerPort) {
            this.name = name;
            this.innerPort = innerPort;
        }

        public Terminal innerPort() {
            return innerPort;
        }

        @Override
        public NetworkControl getNetwork() {
            return getControl();
        }

        TransientAggregator owner() {
            return TransientAggregator.this;
        }

        @Override
        public String toString() {
            return TransientAggregator.this.name + ":" + name;
        }
    }

    private class MyService implements Service {
        private class Client implements ServiceListener {
            final Service connection;
            Throwable error;
            boolean activeIntent;

            Client(Service connection) {
                this.connection = connection;
                this.connection
                    .addListener(protect(ServiceListener.class, this));
            }

            @Override
            public void ready() {
                clientReady(this);
            }

            @Override
            public void failed(Collection<? extends EndPoint> locations,
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

            /***
             * These methods are only to be called while synchronized on
             * the containing connection.
             ***/

            void activate() {
                assert Thread.holdsLock(MyService.this);
                if (activeIntent) return;
                activeIntent = true;
                connection.activate();
            }

            void deactivate() {
                assert Thread.holdsLock(MyService.this);
                if (!activeIntent) return;
                activeIntent = false;
                if (connection != null) connection.deactivate();
            }

            void release() {
                assert Thread.holdsLock(MyService.this);
                if (connection != null) {
                    connection.release();
                }
            }

            void dump(PrintWriter out) {
                out.printf("%n      inferior %s:", connection.status());
                ServiceDescription request = connection.getRequest();
                for (Map.Entry<? extends EndPoint, ? extends TrafficFlow> entry : request
                    .endPointFlows().entrySet()) {
                    EndPoint ep = entry.getKey();
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
            unresponded--;
            if (unresponded > 0) return;
            if (errored > 0) return;
            callOut(ServiceListener::ready);
            if (intendedActivity) clients.stream().forEach(Client::activate);
        }

        synchronized void
            clientFailed(Client cli, Collection<? extends EndPoint> locations,
                         Throwable t) {
            if (tunnels == null) return;
            if (cli.error != null) return;
            errored++;
            unresponded--;
            if (errored == 1) {
                /* This is the first error. */
                callOut(l -> l.failed(locations, t));
            }
        }

        synchronized void clientReleased(Client cli) {
            if (tunnels == null) return;
            if (!clients.remove(cli)) return;
            if (!clients.isEmpty()) return;
            clients.clear();
            tunnels = null;
            request = null;
            released = true;
            synchronized (TransientAggregator.this) {
                connections.remove(id);
            }
            callOut(ServiceListener::released);
        }

        synchronized void clientActivated(Client cli) {
            if (tunnels == null) return;
            activeInferiors++;
            if (activeInferiors < clients.size()) return;
            callOut(ServiceListener::activated);
        }

        synchronized void clientDeactivated(Client cli) {
            if (tunnels == null) return;
            activeInferiors--;
            if (activeInferiors > 0) return;
            callOut(ServiceListener::deactivated);
        }

        final int id;
        final Collection<ServiceListener> listeners = new HashSet<>();
        final List<Client> clients = new ArrayList<>();

        private void callOut(Consumer<? super ServiceListener> action) {
            listeners.stream()
                .forEach(l -> executor.execute(() -> action.accept(l)));
        }

        Map<TrunkControl, EndPoint> tunnels;
        ServiceDescription request;

        int unresponded, errored;
        int activeInferiors;
        boolean intendedActivity = false, released = false;

        MyService(int id) {
            this.id = id;
        }

        @Override
        public synchronized void initiate(ServiceDescription request)
            throws InvalidServiceException {
            if (released)
                throw new IllegalStateException("connection released");
            if (tunnels != null)
                throw new IllegalStateException("connection in use");
            request = ServiceDescription.sanitize(request, 0.01);
            tunnels = new HashMap<>();
            clients.clear();
            errored = activeInferiors = 0;
            intendedActivity = false;

            /* Plot a spanning tree across this switch, allocating
             * tunnels. */
            Collection<ServiceDescription> subrequests = new HashSet<>();
            plotAsymmetricTree(request, tunnels, subrequests);

            /* Create connections for each inferior switch, and a
             * distinct reference of our own for each one. */
            Map<Service, ServiceDescription> subcons = subrequests.stream()
                .collect(Collectors
                    .toMap(r -> r.endPointFlows().keySet().iterator().next()
                        .getTerminal().getNetwork().newService(), r -> r));

            clients.addAll(subcons.keySet().stream().map(Client::new)
                .collect(Collectors.toList()));
            unresponded = clients.size();

            /* Tell each of the subconnections to initiate spanning
             * trees with their respective end points. */
            for (Map.Entry<Service, ServiceDescription> entry : subcons
                .entrySet())
                entry.getKey().initiate(entry.getValue());

            this.request = request;
        }

        @Override
        public synchronized ServiceStatus status() {
            if (released) return ServiceStatus.RELEASED;
            if (errored > 0) return ServiceStatus.FAILED;
            if (tunnels == null) return ServiceStatus.DORMANT;
            if (unresponded > 0) return ServiceStatus.ESTABLISHING;
            if (intendedActivity) return activeInferiors < clients.size()
                ? ServiceStatus.ACTIVATING : ServiceStatus.ACTIVE;
            return activeInferiors > 0 ? ServiceStatus.DEACTIVATING
                : ServiceStatus.INACTIVE;
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
                for (Map.Entry<TrunkControl, EndPoint> tunnel : tunnels
                    .entrySet()) {
                    EndPoint ep1 = tunnel.getValue();
                    EndPoint ep2 = tunnel.getKey().getPeer(ep1);
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
            if (released) return null;
            return control;
        }

        @Override
        public synchronized ServiceDescription getRequest() {
            return request;
        }
    }

    /**
     * Represents a physical link with no persistent state.
     * 
     * @author simpsons
     */
    final class MyTrunk implements TrunkControl {
        private final Terminal start, end;
        private double delay = 0.0;
        private double upstreamBandwidth = 0.0, downstreamBandwidth = 0.0;

        /**
         * Create a trunk between two ports.
         * 
         * @param start one of the ends of the trunk
         * 
         * @param end the other end
         */
        public MyTrunk(Terminal start, Terminal end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public double getUpstreamBandwidth() {
            return upstreamBandwidth;
        }

        @Override
        public double getDownstreamBandwidth() {
            return downstreamBandwidth;
        }

        private final NavigableMap<Integer, Integer> startToEndMap =
            new TreeMap<>();
        private final NavigableMap<Integer, Integer> endToStartMap =
            new TreeMap<>();
        private final BitSet availableTunnels = new BitSet();
        private final Map<Integer, Double> upstreamAllocations =
            new HashMap<>();
        private final Map<Integer, Double> downstreamAllocations =
            new HashMap<>();

        @Override
        public EndPoint getPeer(EndPoint p) {
            synchronized (TransientAggregator.this) {
                if (p.getTerminal().equals(start)) {
                    Integer other = startToEndMap.get(p.getLabel());
                    if (other == null) return null;
                    return end.getEndPoint(other);
                }
                if (p.getTerminal().equals(end)) {
                    Integer other = endToStartMap.get(p.getLabel());
                    if (other == null) return null;
                    return start.getEndPoint(other);
                }
                throw new IllegalArgumentException("end point does not"
                    + " belong to trunk");
            }
        }

        @Override
        public EndPoint allocateTunnel(double upstreamBandwidth,
                                       double downstreamBandwidth) {
            /* Sanity-check bandwidth. */
            if (upstreamBandwidth < 0)
                throw new IllegalArgumentException("negative upstream: "
                    + upstreamBandwidth);
            if (downstreamBandwidth < 0)
                throw new IllegalArgumentException("negative downstream: "
                    + downstreamBandwidth);
            if (downstreamBandwidth > this.downstreamBandwidth) return null;
            if (upstreamBandwidth > this.upstreamBandwidth) return null;

            /* Obtain a tunnel. */
            if (availableTunnels.isEmpty()) return null;
            int startLabel = (short) availableTunnels.nextSetBit(0);
            availableTunnels.clear(startLabel);

            /* Allocate bandwidth. */
            this.downstreamBandwidth -= downstreamBandwidth;
            downstreamAllocations.put(startLabel, downstreamBandwidth);
            this.upstreamBandwidth -= upstreamBandwidth;
            upstreamAllocations.put(startLabel, upstreamBandwidth);

            return start.getEndPoint(startLabel);
        }

        @Override
        public void releaseTunnel(EndPoint endPoint) {
            final int startLabel;
            if (endPoint.getTerminal().equals(start)) {
                startLabel = endPoint.getLabel();
                if (!startToEndMap.containsKey(startLabel))
                    throw new IllegalArgumentException("unmapped "
                        + endPoint);
            } else if (endPoint.getTerminal().equals(end)) {
                int endLabel = endPoint.getLabel();
                Integer rv = endToStartMap.get(endLabel);
                if (rv == null) throw new IllegalArgumentException("unmapped "
                    + endPoint);
                startLabel = rv;
            } else {
                throw new IllegalArgumentException("end point " + endPoint
                    + " does not belong to " + start + " or " + end);
            }
            if (availableTunnels.get(startLabel))
                throw new IllegalArgumentException("unallocated " + endPoint);
            if (!upstreamAllocations.containsKey(startLabel))
                throw new IllegalArgumentException("unallocated " + endPoint);

            /* De-allocate resources. */
            this.upstreamBandwidth += upstreamAllocations.remove(startLabel);
            this.downstreamBandwidth +=
                downstreamAllocations.remove(startLabel);
            availableTunnels.set(startLabel);
        }

        @Override
        public int getAvailableTunnelCount() {
            return availableTunnels.cardinality();
        }

        @Override
        public double getDelay() {
            return delay;
        }

        @Override
        public List<Terminal> getPorts() {
            return Arrays.asList(start, end);
        }

        @Override
        public Trunk getManagement() {
            return management;
        }

        private final Trunk management = new Trunk() {
            @Override
            public void withdrawBandwidth(double upstream,
                                          double downstream) {
                synchronized (TransientAggregator.this) {
                    if (upstream < 0)
                        throw new IllegalArgumentException("negative upstream: "
                            + upstream);
                    if (upstream > upstreamBandwidth)
                        throw new IllegalArgumentException("request upstream "
                            + upstream + " exceeds " + upstreamBandwidth);
                    if (downstream < 0)
                        throw new IllegalArgumentException("negative downstream: "
                            + downstream);
                    if (downstream > downstreamBandwidth)
                        throw new IllegalArgumentException("request downstream "
                            + downstream + " exceeds " + downstreamBandwidth);

                    upstreamBandwidth -= upstream;
                    downstreamBandwidth -= downstream;
                }
            }

            @Override
            public void provideBandwidth(double upstream, double downstream) {
                synchronized (TransientAggregator.this) {
                    if (upstream < 0)
                        throw new IllegalArgumentException("negative upstream: "
                            + upstream);
                    if (downstream < 0)
                        throw new IllegalArgumentException("negative downstream: "
                            + downstream);
                    upstreamBandwidth += upstream;
                    downstreamBandwidth += downstream;
                }
            }

            @Override
            public void setDelay(double delay) {
                synchronized (TransientAggregator.this) {
                    MyTrunk.this.delay = delay;
                }
            }

            @Override
            public double getDelay() {
                synchronized (TransientAggregator.this) {
                    return delay;
                }
            }

            @Override
            public void defineLabelRange(int startBase, int amount,
                                         int endBase) {
                synchronized (TransientAggregator.this) {
                    if (startBase + amount < startBase)
                        throw new IllegalArgumentException("illegal start range "
                            + startBase + " plus " + amount);
                    if (endBase + amount < endBase)
                        throw new IllegalArgumentException("illegal end range "
                            + endBase + " plus " + amount);

                    /* Check that all numbers are available. */
                    Map<Integer, Integer> startExisting =
                        startToEndMap.subMap(startBase, startBase + amount);
                    if (!startExisting.isEmpty())
                        throw new IllegalArgumentException("start range in use "
                            + startExisting.keySet());
                    Map<Integer, Integer> endExisting =
                        endToStartMap.subMap(endBase, endBase + amount);
                    if (!endExisting.isEmpty())
                        throw new IllegalArgumentException("end range in use "
                            + endExisting.keySet());

                    /* Add all the labels. */
                    startToEndMap.putAll(IntStream
                        .range(startBase, startBase + amount).boxed()
                        .collect(Collectors
                            .<Integer, Integer, Integer>toMap(Integer::intValue,
                                                              k -> k
                                                                  .shortValue()
                                                                  + endBase
                                                                  - startBase)));
                    availableTunnels.set(startBase, startBase + amount);
                    endToStartMap.putAll(IntStream
                        .range(endBase, endBase + amount).boxed()
                        .collect(Collectors
                            .<Integer, Integer, Integer>toMap(Integer::intValue,
                                                              k -> k
                                                                  .shortValue()
                                                                  + endBase
                                                                  - startBase)));
                }
            }
        };

        @Override
        public String toString() {
            return start + "+" + end;
        }
    }

    /**
     * Print out the status of all connections and trunks of this
     * switch.
     * 
     * @param out the destination for the status report
     */
    public synchronized void dump(PrintWriter out) {
        out.printf("aggregate %s:%n", name);
        for (MyService conn : connections.values())
            conn.dump(out);
        for (MyTrunk trunk : new HashSet<>(trunks.values())) {
            out.printf("  %s=(%gMbps, %gMbps, %gs) [%d]%n", trunk.getPorts(),
                       trunk.getUpstreamBandwidth(),
                       trunk.getDownstreamBandwidth(), trunk.getDelay(),
                       trunk.getAvailableTunnelCount());
        }
        out.flush();
    }

    private final Executor executor;
    private final String name;

    private final Map<String, MyTerminal> ports = new HashMap<>();
    private final Map<Terminal, MyTrunk> trunks = new HashMap<>();
    private final Map<Integer, MyService> connections = new HashMap<>();
    private int nextConnectionId;

    /**
     * Create an aggregator.
     * 
     * @param executor used to invoke call-backs created by this
     * aggregator and passed to inferior networks
     * 
     * @param name the new switch's name
     */
    public TransientAggregator(Executor executor, String name) {
        this.executor = executor;
        this.name = name;
    }

    @Override
    public Trunk addTrunk(Terminal p1, Terminal p2) {
        if (p1 == null || p2 == null)
            throw new NullPointerException("null port(s)");
        if (trunks.containsKey(p1))
            throw new IllegalArgumentException("port in use: " + p1);
        if (trunks.containsKey(p2))
            throw new IllegalArgumentException("port in use: " + p2);
        MyTrunk trunk = new MyTrunk(p1, p2);
        trunks.put(p1, trunk);
        trunks.put(p2, trunk);
        return trunk.getManagement();
    }

    @Override
    public Trunk findTrunk(Terminal p) {
        return trunks.get(p).getManagement();
    }

    /**
     * Add a new external port exposing an inferior switch's port.
     * 
     * @param name the local name of the port
     * 
     * @param inner the port of an inferior switch
     * 
     * @return the newly created port
     */
    public Terminal addPort(String name, Terminal inner) {
        if (ports.containsKey(name))
            throw new IllegalArgumentException("name in use: " + name);
        MyTerminal result = new MyTerminal(name, inner);
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
    public Terminal getTerminal(String id) {
        return ports.get(id);
    }

    @Override
    public NetworkControl getControl() {
        return control;
    }

    /**
     * Plot a spanning tree with asymmetric bandwidth requirements
     * across this switch, allocation tunnels on trunks.
     * 
     * @param request the request specifying bandwidth at each concerned
     * end point of this switch
     * 
     * @param tunnels a place to store the set of allocated tunnels,
     * indexed by trunk
     * 
     * @param subrequests a place to store the connection requests to be
     * submitted to each inferior switch
     */
    synchronized void
        plotAsymmetricTree(ServiceDescription request,
                           Map<? super TrunkControl, ? super EndPoint> tunnels,
                           Collection<? super ServiceDescription> subrequests) {
        // System.err.printf("Request producers: %s%n",
        // request.producers());
        // System.err.printf("Request consumers: %s%n",
        // request.consumers());

        /* Sanity-check the end points, map them to internal ports, and
         * record bandwidth requirements. */
        Map<Terminal, List<Double>> bandwidths = new HashMap<>();
        Map<EndPoint, List<Double>> innerTerminalEndPoints = new HashMap<>();
        double smallestBandwidthSoFar = Double.MAX_VALUE;
        for (Map.Entry<? extends EndPoint, ? extends TrafficFlow> entry : request
            .endPointFlows().entrySet()) {
            EndPoint ep = entry.getKey();
            TrafficFlow flow = entry.getValue();

            /* Map this end point to an inferior switch's port. */
            Terminal outerPort = ep.getTerminal();
            if (!(outerPort instanceof MyTerminal))
                throw new IllegalArgumentException("end point " + ep
                    + " not part of " + name);
            MyTerminal myPort = (MyTerminal) outerPort;
            if (myPort.owner() != this)
                throw new IllegalArgumentException("end point " + ep
                    + " not part of " + name);

            /* Record the bandwidth produced and consumed on the
             * inferior switch's port. Make sure we aggregate
             * contributions when two or more end points belong to the
             * same port. */
            double produced = flow.ingress;
            double consumed = flow.egress;
            Terminal innerPort = myPort.innerPort();
            List<Double> tuple = bandwidths
                .computeIfAbsent(innerPort, k -> Arrays.asList(0.0, 0.0));
            tuple.set(0, tuple.get(0) + produced);
            tuple.set(1, tuple.get(1) + consumed);

            /* Map the outer end point to an inner one by copying the
             * label. */
            innerTerminalEndPoints.put(innerPort.getEndPoint(ep.getLabel()),
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
        // System.err.printf("Bandwidths on ports: %s%n", bandwidths);
        // System.err.printf("Inner end points: %s%n",
        // innerTerminalEndPoints);

        /* Get the set of ports to connect. */
        Collection<Terminal> innerTerminalPorts = bandwidths.keySet();
        // System.err.printf("Inner terminal ports: %s%n",
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
            adequateTrunks.stream().flatMap(tr -> tr.getPorts().stream())
                .map(Terminal::getNetwork).collect(Collectors.toSet());

        /* Create modifiable routing tables across our network, where
         * the vertices are inner (inferior) ports. */
        DistanceVectorComputer<Terminal> fibGraph =
            new DistanceVectorComputer<Terminal>();

        /* The terminals are the inner ports of those identified in the
         * request. */
        fibGraph.addTerminals(innerTerminalPorts);

        /* The edges include all our trunks, using their delays as
         * metrics. Also retain a reverse mapping from edge to trunk, so
         * we can test bandwidth availability whenever we find a
         * spanning tree. */
        Map<Edge<Terminal>, Double> trunkEdgeWeights = new HashMap<>();
        Map<Edge<Terminal>, MyTrunk> trunkEdges = new HashMap<>();
        for (MyTrunk trunk : adequateTrunks) {
            Edge<Terminal> edge = Edge.of(trunk.getPorts());
            trunkEdgeWeights.put(edge, trunk.getDelay());
            trunkEdges.put(edge, trunk);
        }
        fibGraph.addEdges(trunkEdgeWeights);
        // System.err.printf("Trunk weights: %s%n", trunkEdgeWeights);

        /* The edges include virtual ones constituting models of
         * inferior switches. Also make a note of connected ports within
         * an inferior switch, in case it is a fragmented aggregate. */
        Map<Edge<Terminal>, Double> switchEdgeWeights = switches.stream()
            .flatMap(sw -> sw.getModel(smallestBandwidth).entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey,
                                      Map.Entry::getValue));
        fibGraph.addEdges(switchEdgeWeights);
        // System.err.printf("Switch weights: %s%n", switchEdgeWeights);
        Map<Terminal, Collection<Terminal>> portGroups = Graphs
            .getGroups(Graphs.getAdjacencies(switchEdgeWeights.keySet()));
        // System.err.printf("Port groups: %s%n",
        // new HashSet<>(portGroups.values()));

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
            // Map<Edge<Port>, Double> weightedEdges =
            // Graphs.flatten(fibs);
            FIBSpanGuide<Terminal> guide = new FIBSpanGuide<>(fibs);
            Collection<Terminal> reached = new HashSet<>();
            Collection<Edge<Terminal>> tree = SpanningTreeComputer
                .start(Terminal.class).withEdges(edgeWeights.keySet())
                .withTerminals(innerTerminalPorts).notifying(p -> {
                    guide.reached(p);
                    reached.addAll(portGroups.get(p));
                }).withEdgePreference(guide::select).eliminating(e -> {
                    /* Permit edges within the same switch. */
                    NetworkControl first = e.first().getNetwork();
                    NetworkControl second = e.second().getNetwork();
                    if (first == second) return false;

                    /* Allow this edge at least one port hasn't been
                     * reached. */
                    return reached.containsAll(e);
                }).create().getSpanningTree(guide.first());
            // Collection<Edge<Port>> tree2 =
            // Graphs.span(innerTerminalPorts, null, weightedEdges,
            // p -> reached.addAll(portGroups.get(p)), e -> {
            // /* Permit edges within the same
            // * switch. */
            // SwitchControl first = e.first().getSwitch();
            // SwitchControl second = e.second().getSwitch();
            // if (first == second) return true;
            //
            // /* Allow this edge at least one port
            // * hasn't been reached. */
            // return !reached.containsAll(e);
            // });
            if (tree == null)
                throw new IllegalArgumentException("no tree found");
            // System.err.printf("Spanning tree: %s%n", tree);

            /* Work out how much bandwidth each trunk edge requires in
             * each direction. Find trunk edges in the spanning tree
             * that don't have enough bandwidth for what is going over
             * them. Identify the worst case. */
            Map<MyTrunk, List<Double>> edgeBandwidths = new HashMap<>();
            DistanceVectorComputer<Terminal> routes =
                new DistanceVectorComputer<>(innerTerminalPorts, tree.stream()
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
                boolean reverse = !trunk.getPorts().equals(edge);

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
             * points per port group, and bandwidth required on each. */
            Map<Collection<Terminal>, Map<EndPoint, List<Double>>> subterminals =
                new HashMap<>();
            for (Map.Entry<MyTrunk, List<Double>> trunkReq : edgeBandwidths
                .entrySet()) {
                MyTrunk trunk = trunkReq.getKey();
                double upstream = trunkReq.getValue().get(0);
                double downstream = trunkReq.getValue().get(1);
                EndPoint ep1 = trunk.allocateTunnel(upstream, downstream);
                tunnels.put(trunk, ep1);
                EndPoint ep2 = trunk.getPeer(ep1);
                subterminals
                    .computeIfAbsent(portGroups.get(ep1.getTerminal()),
                                     k -> new HashMap<>())
                    .put(ep1, Arrays.asList(downstream, upstream));
                subterminals
                    .computeIfAbsent(portGroups.get(ep2.getTerminal()),
                                     k -> new HashMap<>())
                    .put(ep2, Arrays.asList(upstream, downstream));
            }

            /* Ensure the caller's end points are included in the
             * requests to inferior switches. */
            for (Map.Entry<EndPoint, List<Double>> entry : innerTerminalEndPoints
                .entrySet()) {
                EndPoint ep = entry.getKey();
                subterminals
                    .computeIfAbsent(portGroups.get(ep.getTerminal()),
                                     k -> new HashMap<>())
                    .put(ep, entry.getValue());
            }
            // System.err.printf("Subterminals: %s%n", subterminals);

            /* For each port group, create a new connection request. */
            for (Map<EndPoint, List<Double>> reqs : subterminals.values()) {
                subrequests.add(ServiceDescription.of(reqs));
            }
            return;
        } while (true);
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
                 Map<? super TrunkControl, ? super EndPoint> tunnels,
                 Map<? super NetworkControl, Collection<EndPoint>> subterminals) {
        // System.err.println("outer terminal end points: " +
        // terminals);

        /* Map the set of caller's end points to the corresponding inner
         * end points that our topology consists of. */
        Collection<EndPoint> innerTerminalEndPoints =
            terminals.stream().map(t -> {
                Terminal p = t.getTerminal();
                if (!(p instanceof MyTerminal))
                    throw new IllegalArgumentException("end point " + t
                        + " not part of " + name);
                MyTerminal xp = (MyTerminal) p;
                if (xp.owner() != this)
                    throw new IllegalArgumentException("end point " + t
                        + " not part of " + name);
                Terminal ip = xp.innerPort();
                return ip.getEndPoint(t.getLabel());
            }).collect(Collectors.toSet());
        // System.err
        // .println("inner terminal end points: " +
        // innerTerminalEndPoints);

        /* Get the set of ports that will be used as destinations in
         * routing. */
        Collection<Terminal> innerTerminalPorts = innerTerminalEndPoints
            .stream().map(EndPoint::getTerminal).collect(Collectors.toSet());
        // System.err.println("inner terminal ports: " +
        // innerTerminalPorts);

        /* Create routing tables for each port. */
        Map<Terminal, Map<Terminal, Way<Terminal>>> fibs =
            getFibs(bandwidth, innerTerminalPorts);
        // System.err.println("FIBs: " + fibs);

        /* Create terminal-aware weights for each edge. */
        // Map<Edge<Port>, Double> weightedEdges = Graphs.flatten(fibs);
        // System.err.println("Edges: " + weightedEdges);

        /* To impose additional constraints on the spanning tree, keep a
         * set of switches already reached. Edges that connect two
         * distinct switches that have both been reached shall be
         * excluded. */
        Collection<NetworkControl> reachedSwitches = new HashSet<>();

        /* Create the spanning tree, keeping track of reached switches,
         * and rejecting edges connecting two already reached
         * switches. */
        FIBSpanGuide<Terminal> guide = new FIBSpanGuide<Terminal>(fibs);
        Collection<Edge<Terminal>> tree =
            SpanningTreeComputer.start(Terminal.class)
                .withEdgePreference(guide::select).eliminating(e -> {
                    NetworkControl first = e.first().getNetwork();
                    NetworkControl second = e.second().getNetwork();
                    if (first == second) return false;
                    if (reachedSwitches.contains(first)
                        && reachedSwitches.contains(second)) return true;
                    return false;
                }).notifying(p -> {
                    guide.reached(p);
                    reachedSwitches.add(p.getNetwork());
                }).create().getSpanningTree(guide.first());

        /* Collection<Edge<Port>> tree2 =
         * Graphs.span(innerTerminalPorts, null, weightedEdges, p ->
         * reachedSwitches.add(p.getSwitch()), e -> { SwitchControl
         * first = e.first().getSwitch(); SwitchControl second =
         * e.second().getSwitch(); if (first == second) return true; if
         * (reachedSwitches.contains(first) &&
         * reachedSwitches.contains(second)) return false; return true;
         * }); */

        for (Edge<Terminal> edge : tree) {
            NetworkControl firstSwitch = edge.first().getNetwork();
            NetworkControl secondSwitch = edge.second().getNetwork();
            if (firstSwitch == secondSwitch) {
                /* This is an edge across an inferior switch. We don't
                 * handle it directly, but infer it by the edges that
                 * connect to it. */
                continue;
            }
            /* This is an edge runnning along a trunk. */

            /* Create a tunnel along this trunk, and remember one end of
             * it. */
            TrunkControl firstTrunk = trunks.get(edge.first());
            EndPoint ep1 = firstTrunk.allocateTunnel(bandwidth, bandwidth);
            tunnels.put(firstTrunk, ep1);

            /* Get both end points, find out what switches they
             * correspond to, and add each end point to its switch's
             * respective set of end points. */
            EndPoint ep2 = firstTrunk.getPeer(ep1);
            subterminals.computeIfAbsent(ep1.getTerminal().getNetwork(),
                                         k -> new HashSet<>())
                .add(ep1);
            subterminals.computeIfAbsent(ep2.getTerminal().getNetwork(),
                                         k -> new HashSet<>())
                .add(ep2);
        }

        /* Make sure the caller's end points are included in their
         * switches' corresponding sets. */
        for (EndPoint t : innerTerminalEndPoints)
            subterminals.computeIfAbsent(t.getTerminal().getNetwork(),
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
    Map<Terminal, Map<Terminal, Way<Terminal>>>
        getFibs(double bandwidth, Collection<Terminal> innerTerminalPorts) {
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
                .toMap(t -> Edge.of(t.getPorts()), TrunkControl::getDelay)));
        // System.err.println("Edges of trunks: " + edges);

        /* Get a set of all switches for our trunks. */
        Collection<NetworkControl> switches =
            adequateTrunks.stream().flatMap(trunk -> trunk.getPorts().stream()
                .map(Terminal::getNetwork)).collect(Collectors.toSet());
        // System.err.println("Switches: " + switches);

        /* Get models of all switches connected to the selected trunks,
         * and combine their edges with the trunks. */
        edges.putAll(switches.stream()
            .flatMap(sw -> sw.getModel(bandwidth).entrySet().stream())
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())));
        // System.err.println("Edges of trunks and switches: " + edges);

        /* Get rid of spurs as a small optimization. */
        Graphs.prune(innerTerminalPorts, edges.keySet());
        // System.err.println("Pruned edges of trunks and switches: " +
        // edges);

        /* Create routing tables for each port. */
        return Graphs.route(innerTerminalPorts, edges);
    }

    synchronized Map<Edge<Terminal>, Double> getModel(double bandwidth) {
        /* Map the set of our end points to the corresponding inner
         * ports that our topology consists of. */
        Collection<Terminal> innerTerminalPorts = ports.values().stream()
            .map(MyTerminal::innerPort).collect(Collectors.toSet());

        /* Create routing tables for each port. */
        Map<Terminal, Map<Terminal, Way<Terminal>>> fibs =
            getFibs(bandwidth, innerTerminalPorts);

        /* Convert our exposed ports to a sequence so we can form every
         * combination of two ports. */
        final List<MyTerminal> portSeq = new ArrayList<>(ports.values());
        final int size = portSeq.size();

        /* For every combination of our exposed ports, store the total
         * distance as part of the result. */
        Map<Edge<Terminal>, Double> result = new HashMap<>();
        for (int i = 0; i + 1 < size; i++) {
            final MyTerminal start = portSeq.get(i);
            final Terminal innerStart = start.innerPort();
            final Map<Terminal, Way<Terminal>> startFib =
                fibs.get(innerStart);
            if (startFib == null) continue;
            for (int j = i + 1; j < size; j++) {
                final MyTerminal end = portSeq.get(j);
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
            return TransientAggregator.this.getModel(bandwidth);
        }

        @Override
        public Service getService(int id) {
            synchronized (TransientAggregator.this) {
                return connections.get(id);
            }
        }

        @Override
        public Service newService() {
            synchronized (TransientAggregator.this) {
                int id = nextConnectionId++;
                MyService conn = new MyService(id);
                connections.put(id, conn);
                return conn;
            }
        }

        @Override
        public Collection<Integer> getServiceIds() {
            return new HashSet<>(connections.keySet());
        }
    };

    @Override
    public Collection<String> getTerminals() {
        return new HashSet<>(ports.keySet());
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
}
