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
package uk.ac.lancs.networks.transients;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
import uk.ac.lancs.networks.mgmt.Trunk;
import uk.ac.lancs.routing.span.DistanceVectorComputer;
import uk.ac.lancs.routing.span.Edge;
import uk.ac.lancs.routing.span.FIBSpanGuide;
import uk.ac.lancs.routing.span.Graphs;
import uk.ac.lancs.routing.span.SpanningTreeComputer;
import uk.ac.lancs.routing.span.Way;

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

        @Override
        public String name() {
            return name;
        }
    }

    private class MyService implements Service {
        private class Client implements ServiceListener {
            final Service subservice;

            ServiceListener protectedSelf;

            /**
             * The last stable status reported by this subservice
             */
            @SuppressWarnings("unused")
            ServiceStatus lastStableStatus = ServiceStatus.DORMANT;

            /**
             * The last status reported by this subservice
             */
            ServiceStatus lastStatus = ServiceStatus.DORMANT;

            Client(Service subservice) {
                this.subservice = subservice;
            }

            /**
             * Ensure the subservice can talk back to this client.
             */
            void init() {
                protectedSelf = protect(ServiceListener.class, this);
                this.subservice.addListener(protectedSelf);
            }

            void term() {
                this.subservice.removeListener(protectedSelf);
            }

            @Override
            public void newStatus(ServiceStatus newStatus) {
                newClientStatus(this, newStatus);
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
                Segment request = subservice.getRequest();
                for (Map.Entry<? extends Circuit, ? extends TrafficFlow> entry : request
                    .circuitFlows().entrySet()) {
                    Circuit ep = entry.getKey();
                    TrafficFlow flow = entry.getValue();
                    out.printf("%n        %10s %6g %6g", ep, flow.ingress,
                               flow.egress);
                }
            }
        }

        /***
         * The following methods are to be called by Client objects.
         ***/

        final int id;
        final Collection<ServiceListener> listeners = new HashSet<>();
        final List<Client> clients = new ArrayList<>();

        private void callOut(ServiceStatus status) {
            listeners.forEach(l -> l.newStatus(status));
        }

        /**
         * This holds the set of trunks on which we have allocated
         * bandwidth. If {@code null}, the service is not initiated.
         */
        Map<MyTrunk, Circuit> tunnels;
        Segment request;

        int dormantCount, inactiveCount, activeCount, failedCount,
            releasedCount;

        /**
         * Records the user's intent for this service. The default is
         * {@link Intent#INACTIVE}.
         */
        Intent intent = Intent.INACTIVE;

        MyService(int id) {
            this.id = id;
        }

        @Override
        public synchronized void define(Segment request)
            throws InvalidServiceException {

            if (intent == Intent.RELEASE) if (clients.isEmpty())
                throw new IllegalStateException("service released");
            else
                throw new IllegalStateException("service releasing");

            if (tunnels != null)
                throw new IllegalStateException("service in use");
            request = Segment.sanitize(request, 0.01);
            tunnels = new HashMap<>();

            /* Plot a spanning tree across this switch, allocating
             * tunnels. */
            Collection<Segment> subrequests = new HashSet<>();
            plotAsymmetricTree(request, tunnels, subrequests);

            /* Create connections for each inferior switch, and a
             * distinct reference of our own for each one. */
            Map<Service, Segment> subcons = subrequests.stream()
                .collect(Collectors.toMap(r -> r.circuitFlows().keySet()
                    .iterator().next().getTerminal().getNetwork().newService(),
                                          r -> r));

            /* Create a client for each subservice. */
            clients.addAll(subcons.keySet().stream().map(Client::new)
                .collect(Collectors.toList()));
            clients.forEach(Client::init);
            dormantCount = clients.size();

            /* Tell each of the subconnections to initiate spanning
             * trees with their respective circuits. */
            try {
                for (Map.Entry<Service, Segment> entry : subcons.entrySet())
                    entry.getKey().define(entry.getValue());
            } catch (InvalidServiceException ex) {
                release();
                throw ex;
            }

            this.request = request;
        }

        @Override
        public synchronized ServiceStatus status() {
            if (intent == Intent.RELEASE) {
                if (clients.isEmpty())
                    return ServiceStatus.RELEASED;
                else
                    return ServiceStatus.RELEASING;
            }
            if (failedCount > 0) return ServiceStatus.FAILED;
            if (tunnels == null) return ServiceStatus.DORMANT;
            assert failedCount == 0;
            if (dormantCount > 0) return ServiceStatus.ESTABLISHING;
            if (intent == Intent.ACTIVE) return activeCount < clients.size()
                ? ServiceStatus.ACTIVATING : ServiceStatus.ACTIVE;
            return activeCount > 0 ? ServiceStatus.DEACTIVATING
                : ServiceStatus.INACTIVE;
        }

        @Override
        public synchronized void activate() {
            /* If anything has already gone wrong, we can do nothing
             * more. */
            if (failedCount > 0)
                throw new IllegalStateException("inferior error(s)");

            /* If the user has released us, we can do nothing more. */
            if (intent == Intent.RELEASE)
                throw new IllegalStateException("released");

            /* Do nothing if we've already recorded the user's intent,
             * as we must also have activated inferior services. */
            if (intent == Intent.ACTIVE) return;
            intent = Intent.ACTIVE;

            /* Do nothing but record the user's intent, if they haven't
             * yet provided circuit details. */
            if (tunnels == null) return;

            callOut(ServiceStatus.ACTIVATING);
            clients.forEach(Client::activate);
        }

        @Override
        public synchronized void deactivate() {
            if (intent != Intent.ACTIVE) return;
            intent = Intent.INACTIVE;
            callOut(ServiceStatus.DEACTIVATING);
            if (inactiveCount + failedCount == clients.size()) {
                callOut(ServiceStatus.INACTIVE);
            } else {
                clients.forEach(Client::deactivate);
            }
        }

        @Override
        public synchronized void release() {
            /* There's nothing to do if we've already recorded the
             * user's intent to release the service. */
            if (intent == Intent.RELEASE) return;

            /* If the current intent is to be active, trigger
             * deactivation first. When it completes, and discovers the
             * release intent, it will start the release process.
             * Otherwise, we start it now. */
            if (intent == Intent.ACTIVE) {
                intent = Intent.RELEASE;
                if (activeCount > 0) {
                    if (activeCount == clients.size())
                        callOut(ServiceStatus.DEACTIVATING);
                    clients.forEach(Client::deactivate);
                }
                return;
            }

            /* Record the new intent and initiate the release
             * process. */
            intent = Intent.RELEASE;
            startRelease();
        }

        private void startRelease() {
            assert Thread.holdsLock(this);

            /* Inform users that the release process has started. */
            callOut(ServiceStatus.RELEASING);

            /* Release subservice resources. */
            clients.forEach(Client::release);

            /* Release tunnel resources. */
            synchronized (TransientAggregator.this) {
                tunnels.forEach((k, v) -> k.releaseTunnel(v));
            }
            tunnels.clear();

            if (releasedCount == clients.size()) {
                /* All subservices are already released. This only
                 * happens if we were still dormant when released. */
                completeRelease();
            }
        }

        synchronized void newClientStatus(Client cli,
                                          ServiceStatus newStatus) {
            if (tunnels == null) {
                /* We haven't initiated anything yet, so we shouldn't be
                 * called back by subservices we haven't set up. */
                return;
            }

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

            /* If any subservice failed, ensure all subservices are
             * deactivated, release tunnels, and inform the users. */
            if (failedChanged) {
                /* Make sure we have all the errors from the failing
                 * subservice. */
                errors.addAll(cli.subservice.errors());

                switch (intent) {
                case ABORT:
                    /* If we've already recorded that we're aborting, we
                     * have nothing else to do. */
                    return;

                case RELEASE:
                    /* If the user has already tried to release us, we
                     * have nothing else to do. */
                    return;

                default:
                    break;
                }

                /* Record that we are aborting this service. */
                intent = Intent.ABORT;

                /* Ensure that all subservices are deactivated. */
                clients.forEach(Client::deactivate);

                /* Release all trunk resources now. We definitely don't
                 * need them any more. */
                synchronized (TransientAggregator.this) {
                    tunnels.forEach((trunk, circuit) -> {
                        trunk.releaseTunnel(circuit);
                    });
                }
                tunnels.clear();

                /* Notify the user that we have failed, and the only
                 * remaining events are RELEASING and RELEASED. */
                callOut(ServiceStatus.FAILED);
                return;
            }

            if (inactiveChanged && inactiveCount == clients.size()) {
                /* All clients have become inactive. */
                callOut(ServiceStatus.INACTIVE);

                switch (intent) {
                case ACTIVE:
                    /* The clients must have been DORMANT, but the user
                     * prematurely activated us. */
                    callOut(ServiceStatus.ACTIVATING);
                    clients.forEach(Client::activate);
                    break;

                case RELEASE:
                    /* The user released the service while it was
                     * (trying to be) active. Initiate the release
                     * process. */
                    startRelease();
                    break;

                default:
                    break;
                }

                return;
            }

            if (intent == Intent.RELEASE) {
                if (releasedChanged && releasedCount == clients.size()) {
                    /* All subservices have been released, so we can
                     * regard ourselves as fully released now. */
                    completeRelease();
                    return;
                }
                return;
            }

            if (activeChanged && activeCount == clients.size()) {
                if (intent == Intent.ACTIVE) callOut(ServiceStatus.ACTIVE);
                return;
            }
        }

        private void completeRelease() {
            assert Thread.holdsLock(this);

            /* We should have already dealt with the tunnels, but let's
             * be thorough. */
            assert tunnels == null || tunnels.isEmpty();
            tunnels = null;

            /* Lose the references to all the subservices, and make sure
             * they've lost our callbacks. */
            clients.forEach(Client::term);
            clients.clear();

            /* Ensure this service can't be found again by users. */
            synchronized (TransientAggregator.this) {
                services.remove(id);
            }

            /* Send our last report to all users. */
            callOut(ServiceStatus.RELEASED);
            listeners.clear();
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
                for (Map.Entry<MyTrunk, Circuit> tunnel : tunnels
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
            if (intent == Intent.RELEASE && clients.isEmpty()) return null;
            return control;
        }

        @Override
        public synchronized Segment getRequest() {
            return request;
        }

        private Collection<Throwable> errors = new HashSet<>();
        private Collection<Throwable> immutableErrors =
            Collections.unmodifiableCollection(errors);

        @Override
        public Collection<Throwable> errors() {
            return immutableErrors;
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
            synchronized (TransientAggregator.this) {
                if (p.getTerminal().equals(start)) {
                    Integer other = startToEndMap.get(p.getLabel());
                    if (other == null) return null;
                    return end.circuit(other);
                }
                if (p.getTerminal().equals(end)) {
                    Integer other = endToStartMap.get(p.getLabel());
                    if (other == null) return null;
                    return start.circuit(other);
                }
                throw new IllegalArgumentException("circuit does not"
                    + " belong to trunk");
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
         */
        Circuit allocateTunnel(double upstreamBandwidth,
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

            return start.circuit(startLabel);
        }

        /**
         * Release a tunnel through this trunk.
         * 
         * @param circuit either of the tunnel circuits
         */
        void releaseTunnel(Circuit circuit) {
            final int startLabel;
            if (circuit.getTerminal().equals(start)) {
                startLabel = circuit.getLabel();
                if (!startToEndMap.containsKey(startLabel))
                    throw new IllegalArgumentException("unmapped " + circuit);
            } else if (circuit.getTerminal().equals(end)) {
                int endLabel = circuit.getLabel();
                Integer rv = endToStartMap.get(endLabel);
                if (rv == null)
                    throw new IllegalArgumentException("unmapped " + circuit);
                startLabel = rv;
            } else {
                throw new IllegalArgumentException("circuit " + circuit
                    + " does not belong to " + start + " or " + end);
            }
            if (availableTunnels.get(startLabel))
                throw new IllegalArgumentException("unallocated " + circuit);
            if (!upstreamAllocations.containsKey(startLabel))
                throw new IllegalArgumentException("unallocated " + circuit);

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
            synchronized (TransientAggregator.this) {
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
        public void revokeStartLabelRange(int startBase, int amount) {
            synchronized (TransientAggregator.this) {
                for (int i = startBase; i < startBase + amount; i++) {
                    Integer o = startToEndMap.remove(i);
                    if (o == null) continue;
                    endToStartMap.remove(o);
                }
            }
        }

        @Override
        public void revokeEndLabelRange(int endBase, int amount) {
            synchronized (TransientAggregator.this) {
                for (int i = endBase; i < endBase + amount; i++) {
                    Integer o = endToStartMap.remove(i);
                    if (o == null) continue;
                    startToEndMap.remove(o);
                }
            }
        }

        @Override
        public void defineLabelRange(int startBase, int amount, int endBase) {
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

        boolean commissioned = true;

        @Override
        public void decommission() {
            synchronized (TransientAggregator.this) {
                commissioned = false;
            }
        }

        @Override
        public void recommission() {
            synchronized (TransientAggregator.this) {
                commissioned = true;
            }
        }

        @Override
        public boolean isCommissioned() {
            synchronized (TransientAggregator.this) {
                return commissioned;
            }
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
            out.printf("  %s%s=(%gMbps, %gMbps, %gs) [%d]%n",
                       trunk.getTerminals(),
                       trunk.isCommissioned() ? " " : "!",
                       trunk.getUpstreamBandwidth(),
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
     * @param name the new switch's name
     */
    public TransientAggregator(Executor executor, String name) {
        this.executor = executor;
        this.name = name;
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
        Trunk result = trunks.get(p);
        if (result == null) return null;
        if (result.position(p) == 1) return result.reverse();
        return result;
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
    public NetworkControl getControl() {
        return control;
    }

    /**
     * Plot a spanning tree with asymmetric bandwidth requirements
     * across this switch, allocation tunnels on trunks.
     * 
     * @param request the request specifying bandwidth at each concerned
     * circuit of this switch
     * 
     * @param tunnels a place to store the set of allocated tunnels,
     * indexed by trunk
     * 
     * @param subrequests a place to store the connection requests to be
     * submitted to each inferior switch
     */
    synchronized void
        plotAsymmetricTree(Segment request,
                           Map<? super MyTrunk, ? super Circuit> tunnels,
                           Collection<? super Segment> subrequests) {
        // System.err.printf("Request producers: %s%n",
        // request.producers());
        // System.err.printf("Request consumers: %s%n",
        // request.consumers());

        /* Sanity-check the circuits, map them to internal terminals,
         * and record bandwidth requirements. */
        Map<Terminal, List<Double>> bandwidths = new HashMap<>();
        Map<Circuit, List<Double>> innerCircuits = new HashMap<>();
        double smallestBandwidthSoFar = Double.MAX_VALUE;
        for (Map.Entry<? extends Circuit, ? extends TrafficFlow> entry : request
            .circuitFlows().entrySet()) {
            Circuit ep = entry.getKey();
            TrafficFlow flow = entry.getValue();

            /* Map this circuit to an inferior switch's terminal. */
            Terminal outerPort = ep.getTerminal();
            if (!(outerPort instanceof MyTerminal))
                throw new IllegalArgumentException("circuit " + ep
                    + " not part of " + name);
            MyTerminal myPort = (MyTerminal) outerPort;
            if (myPort.owner() != this)
                throw new IllegalArgumentException("circuit " + ep
                    + " not part of " + name);

            /* Record the bandwidth produced and consumed on the
             * inferior switch's terminal. Make sure we aggregate
             * contributions when two or more circuits belong to the
             * same terminal. */
            double produced = flow.ingress;
            double consumed = flow.egress;
            Terminal innerPort = myPort.innerPort();
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

            // System.err.printf("e/p %s is part of %s, mapping to
            // %s%n", ep,
            // outerPort, innerPort);
        }
        double smallestBandwidth = smallestBandwidthSoFar;
        // System.err.printf("Bandwidths on terminals: %s%n",
        // bandwidths);
        // System.err.printf("Inner circuits: %s%n",
        // innerTerminalCircuits);

        /* Get the set of terminals to connect. */
        Collection<Terminal> innerTerminals = bandwidths.keySet();
        // System.err.printf("Inner terminals: %s%n",
        // innerTerminalPorts);

        /* Get a subset of all trunks, those with sufficent bandwidth
         * and free tunnels. */
        Collection<MyTrunk> adequateTrunks = trunks.values().stream()
            .filter(trunk -> trunk.isCommissioned()
                && trunk.getAvailableTunnelCount() > 0
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
            Map<Collection<Terminal>, Map<Circuit, List<Double>>> subterminals =
                new HashMap<>();
            for (Map.Entry<MyTrunk, List<Double>> trunkReq : edgeBandwidths
                .entrySet()) {
                MyTrunk trunk = trunkReq.getKey();
                double upstream = trunkReq.getValue().get(0);
                double downstream = trunkReq.getValue().get(1);
                Circuit ep1 = trunk.allocateTunnel(upstream, downstream);
                tunnels.put(trunk, ep1);
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
             * to inferior switches. */
            for (Map.Entry<Circuit, List<Double>> entry : innerCircuits
                .entrySet()) {
                Circuit ep = entry.getKey();
                subterminals
                    .computeIfAbsent(terminalGroups.get(ep.getTerminal()),
                                     k -> new HashMap<>())
                    .put(ep, entry.getValue());
            }
            // System.err.printf("Subterminals: %s%n", subterminals);

            /* For each terminal group, create a new connection
             * request. */
            for (Map<Circuit, List<Double>> reqs : subterminals.values()) {
                subrequests.add(Segment.of(reqs));
            }
            return;
        } while (true);
    }

    /**
     * Plot a spanning tree across this switch, allocating tunnels on
     * trunks.
     * 
     * @param terminals the switch's own visible circuits to be
     * connected
     * 
     * @param bandwidth the bandwidth required on all tunnels
     * 
     * @param tunnels a place to store the set of allocated tunnels,
     * indicating which trunk they belong to
     * 
     * @param subterminals a place to store which internal circuits of
     * which internal switches should be connected
     * 
     * @throws IllegalArgumentException if any circuit does not belong
     * to this switch
     */
    synchronized void
        plotTree(Collection<? extends Circuit> terminals, double bandwidth,
                 Map<? super MyTrunk, ? super Circuit> tunnels,
                 Map<? super NetworkControl, Collection<Circuit>> subterminals) {
        // System.err.println("outer terminal circuits: " +
        // terminals);

        /* Map the set of caller's circuits to the corresponding inner
         * circuits that our topology consists of. */
        Collection<Circuit> innerCircuits = terminals.stream().map(t -> {
            Terminal p = t.getTerminal();
            if (!(p instanceof MyTerminal))
                throw new IllegalArgumentException("circuit " + t
                    + " not part of " + name);
            MyTerminal xp = (MyTerminal) p;
            if (xp.owner() != this)
                throw new IllegalArgumentException("circuit " + t
                    + " not part of " + name);
            Terminal ip = xp.innerPort();
            return ip.circuit(t.getLabel());
        }).collect(Collectors.toSet());
        // System.err
        // .println("inner terminal circuits: " +
        // innerTerminalCircuits);

        /* Get the set of terminals that will be used as destinations in
         * routing. */
        Collection<Terminal> innerTerminals = innerCircuits.stream()
            .map(Circuit::getTerminal).collect(Collectors.toSet());
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
            Circuit ep1 = firstTrunk.allocateTunnel(bandwidth, bandwidth);
            tunnels.put(firstTrunk, ep1);

            /* Get both circuits, find out what switches they correspond
             * to, and add each circuit to its switch's respective set
             * of circuits. */
            Circuit ep2 = firstTrunk.getPeer(ep1);
            subterminals.computeIfAbsent(ep1.getTerminal().getNetwork(),
                                         k -> new HashSet<>())
                .add(ep1);
            subterminals.computeIfAbsent(ep2.getTerminal().getNetwork(),
                                         k -> new HashSet<>())
                .add(ep2);
        }

        /* Make sure the caller's circuits are included in their
         * switches' corresponding sets. */
        for (Circuit t : innerCircuits)
            subterminals.computeIfAbsent(t.getTerminal().getNetwork(),
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
            .filter(trunk -> trunk.isCommissioned()
                && trunk.getAvailableTunnelCount() > 0
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
        /* Map the set of our circuits to the corresponding inner
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
        public Terminal getTerminal(String id) {
            synchronized (TransientAggregator.this) {
                return terminals.get(id);
            }
        }

        @Override
        public Collection<String> getTerminals() {
            synchronized (TransientAggregator.this) {
                return new HashSet<>(terminals.keySet());
            }
        }

        @Override
        public Map<Edge<Terminal>, Double> getModel(double bandwidth) {
            return TransientAggregator.this.getModel(bandwidth);
        }

        @Override
        public Service getService(int id) {
            synchronized (TransientAggregator.this) {
                return services.get(id);
            }
        }

        @Override
        public Service newService() {
            synchronized (TransientAggregator.this) {
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

    private static enum Intent {
        RELEASE, INACTIVE, ACTIVE, ABORT;
    }
}
