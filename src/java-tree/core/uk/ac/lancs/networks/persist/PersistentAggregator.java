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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
import uk.ac.lancs.networks.mgmt.ManagedAggregator;
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
public class PersistentAggregator implements ManagedAggregator {
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

        PersistentAggregator owner() {
            return PersistentAggregator.this;
        }

        @Override
        public String toString() {
            return PersistentAggregator.this.name + ":" + name;
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

            /***
             * These methods are only to be called while synchronized on
             * the containing connection.
             ***/

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

            /* Plot a spanning tree across this switch, allocating
             * tunnels. */
            Collection<ServiceDescription> subrequests = new HashSet<>();
            plotAsymmetricTree(request, tunnels, subrequests);

            /* Create connections for each inferior switch, and a
             * distinct reference of our own for each one. */
            Map<Service, ServiceDescription> subcons = subrequests.stream()
                .collect(Collectors.toMap(r -> r.endPointFlows().keySet()
                    .iterator().next().getBundle().getNetwork().newService(),
                                          r -> r));

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
        }

        @Override
        public synchronized ServiceStatus status() {
            if (intent == Intent.RELEASE) if (clients.isEmpty())
                return ServiceStatus.RELEASED;
            else
                return ServiceStatus.RELEASING;
            if (errorCount > 0 || !globalErrors.isEmpty())
                return ServiceStatus.FAILED;
            if (tunnels == null) return ServiceStatus.DORMANT;
            assert errorCount == 0;
            if (readyCount < clients.size())
                return ServiceStatus.ESTABLISHING;
            if (intent == Intent.ACTIVE) return activeCount < clients.size()
                ? ServiceStatus.ACTIVATING : ServiceStatus.ACTIVE;
            return activeCount > 0 ? ServiceStatus.DEACTIVATING
                : ServiceStatus.INACTIVE;
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
    }

    /**
     * Represents a physical link with no persistent state.
     * 
     * @author simpsons
     */
    final class MyTrunk implements Trunk {
        private final Terminal start, end;
        private double delay = 0.0;
        private double upstreamBandwidth = 0.0, downstreamBandwidth = 0.0;

        /**
         * Create a trunk between two terminals.
         * 
         * @param start one of the ends of the trunk
         * 
         * @param end the other end
         */
        MyTrunk(Terminal start, Terminal end) {
            this.start = start;
            this.end = end;
        }

        /**
         * Get the upstream bandwidth remaining available on this trunk.
         * 
         * @return the remaining available bandwidth from start terminal
         * to end
         */
        double getUpstreamBandwidth() {
            return upstreamBandwidth;
        }

        /**
         * Get the downstream bandwidth remaining available on this
         * trunk.
         * 
         * @return the remaining available bandwidth from end terminal
         * to start
         */
        double getDownstreamBandwidth() {
            return downstreamBandwidth;
        }

        /**
         * Get the maximum of the upstream and downstream bandwidths.
         * 
         * @return the best bandwidth available on this trunk
         */
        double getMaximumBandwidth() {
            return Math.max(getUpstreamBandwidth(), getDownstreamBandwidth());
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
            synchronized (PersistentAggregator.this) {
                if (p.getBundle().equals(start)) {
                    Integer other = startToEndMap.get(p.getLabel());
                    if (other == null) return null;
                    return end.getEndPoint(other);
                }
                if (p.getBundle().equals(end)) {
                    Integer other = endToStartMap.get(p.getLabel());
                    if (other == null) return null;
                    return start.getEndPoint(other);
                }
                throw new IllegalArgumentException("end point does not"
                    + " belong to trunk");
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
         */
        EndPoint<? extends Terminal>
            allocateTunnel(double upstreamBandwidth,
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

        /**
         * Release a tunnel through this trunk.
         * 
         * @param endPoint either of the tunnel end points
         */
        void releaseTunnel(EndPoint<? extends Terminal> endPoint) {
            final int startLabel;
            if (endPoint.getBundle().equals(start)) {
                startLabel = endPoint.getLabel();
                if (!startToEndMap.containsKey(startLabel))
                    throw new IllegalArgumentException("unmapped "
                        + endPoint);
            } else if (endPoint.getBundle().equals(end)) {
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

        /**
         * Get the number of tunnels available through this trunk.
         * 
         * @return the number of available tunnels
         */
        int getAvailableTunnelCount() {
            return availableTunnels.cardinality();
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
            synchronized (PersistentAggregator.this) {
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
            synchronized (PersistentAggregator.this) {
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
            synchronized (PersistentAggregator.this) {
                MyTrunk.this.delay = delay;
            }
        }

        @Override
        public void revokeStartLabelRange(int startBase, int amount) {
            synchronized (PersistentAggregator.this) {
                for (int i = startBase; i < startBase + amount; i++) {
                    Integer o = startToEndMap.remove(i);
                    if (o == null) continue;
                    endToStartMap.remove(o);
                }
            }
        }

        @Override
        public void revokeEndLabelRange(int endBase, int amount) {
            synchronized (PersistentAggregator.this) {
                for (int i = endBase; i < endBase + amount; i++) {
                    Integer o = endToStartMap.remove(i);
                    if (o == null) continue;
                    startToEndMap.remove(o);
                }
            }
        }

        @Override
        public void defineLabelRange(int startBase, int amount, int endBase) {
            synchronized (PersistentAggregator.this) {
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
                                                          k -> k.intValue()
                                                              + endBase
                                                              - startBase)));
                availableTunnels.set(startBase, startBase + amount);
                endToStartMap.putAll(IntStream
                    .range(endBase, endBase + amount).boxed()
                    .collect(Collectors
                        .<Integer, Integer, Integer>toMap(Integer::intValue,
                                                          k -> k.intValue()
                                                              + endBase
                                                              - startBase)));
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
    private final Map<Integer, MyService> services = new HashMap<>();
    private int nextServiceId;

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
        this.name = config.get("name");
    }

    @Override
    public synchronized Trunk addTrunk(Terminal p1, Terminal p2) {
        if (p1 == null || p2 == null)
            throw new NullPointerException("null terminal(s)");
        if (trunks.containsKey(p1))
            throw new IllegalArgumentException("terminal in use: " + p1);
        if (trunks.containsKey(p2))
            throw new IllegalArgumentException("terminal in use: " + p2);
        MyTrunk trunk = new MyTrunk(p1, p2);
        trunks.put(p1, trunk);
        trunks.put(p2, trunk);
        return trunk;
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
        MyTerminal result = new MyTerminal(name, inner);
        terminals.put(name, result);
        return result;
    }

    /**
     * Remove a terminal from this switch.
     * 
     * @param name the terminal's local name
     */
    @Override
    public synchronized void removeTerminal(String name) {
        terminals.remove(name);
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
                           Map<? super MyTrunk, ? super EndPoint<? extends Terminal>> tunnels,
                           Collection<? super ServiceDescription> subrequests) {
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
                    trunk.allocateTunnel(upstream, downstream);
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
        plotTree(Collection<? extends EndPoint<? extends Terminal>> terminals,
                 double bandwidth,
                 Map<? super MyTrunk, ? super EndPoint<? extends Terminal>> tunnels,
                 Map<? super NetworkControl, Collection<EndPoint<? extends Terminal>>> subterminals) {
        // System.err.println("outer terminal end points: " +
        // terminals);

        /* Map the set of caller's end points to the corresponding inner
         * end points that our topology consists of. */
        Collection<EndPoint<? extends Terminal>> innerEndPoints =
            terminals.stream().map(t -> {
                Terminal p = t.getBundle();
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

        /* Get the set of terminals that will be used as destinations in
         * routing. */
        Collection<Terminal> innerTerminals = innerEndPoints.stream()
            .map(EndPoint::getBundle).collect(Collectors.toSet());
        // System.err.println("inner terminal terminals: " +
        // innerTerminalPorts);

        /* Create routing tables for each terminal. */
        Map<Terminal, Map<Terminal, Way<Terminal>>> fibs =
            getFibs(bandwidth, innerTerminals);
        // System.err.println("FIBs: " + fibs);

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
            MyTrunk firstTrunk = trunks.get(edge.first());
            EndPoint<? extends Terminal> ep1 =
                firstTrunk.allocateTunnel(bandwidth, bandwidth);
            tunnels.put(firstTrunk, ep1);

            /* Get both end points, find out what switches they
             * correspond to, and add each end point to its switch's
             * respective set of end points. */
            EndPoint<? extends Terminal> ep2 = firstTrunk.getPeer(ep1);
            subterminals.computeIfAbsent(ep1.getBundle().getNetwork(),
                                         k -> new HashSet<>())
                .add(ep1);
            subterminals.computeIfAbsent(ep2.getBundle().getNetwork(),
                                         k -> new HashSet<>())
                .add(ep2);
        }

        /* Make sure the caller's end points are included in their
         * switches' corresponding sets. */
        for (EndPoint<? extends Terminal> t : innerEndPoints)
            subterminals.computeIfAbsent(t.getBundle().getNetwork(),
                                         k -> new HashSet<>())
                .add(t);

        return;
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
        public Service getService(int id) {
            synchronized (PersistentAggregator.this) {
                return services.get(id);
            }
        }

        @Override
        public Service newService() {
            synchronized (PersistentAggregator.this) {
                int id = nextServiceId++;
                MyService conn = new MyService(id);
                services.put(id, conn);
                return conn;
            }
        }

        @Override
        public Collection<Integer> getServiceIds() {
            return new HashSet<>(services.keySet());
        }
    };

    @Override
    public Collection<String> getTerminals() {
        return new HashSet<>(terminals.keySet());
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

    private static enum Intent {
        RELEASE, INACTIVE, ACTIVE;
    }
}
