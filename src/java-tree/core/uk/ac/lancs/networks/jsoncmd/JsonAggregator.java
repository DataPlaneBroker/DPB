/*
 * Copyright 2018,2019, Regents of the University of Lancaster
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
package uk.ac.lancs.networks.jsoncmd;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import uk.ac.lancs.networks.NetworkLogicException;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.mgmt.Aggregator;
import uk.ac.lancs.networks.mgmt.BandwidthUnavailableException;
import uk.ac.lancs.networks.mgmt.ExpiredTrunkException;
import uk.ac.lancs.networks.mgmt.LabelManagementException;
import uk.ac.lancs.networks.mgmt.LabelsInUseException;
import uk.ac.lancs.networks.mgmt.LabelsUnavailableException;
import uk.ac.lancs.networks.mgmt.NetworkManagementException;
import uk.ac.lancs.networks.mgmt.SubterminalBusyException;
import uk.ac.lancs.networks.mgmt.TerminalId;
import uk.ac.lancs.networks.mgmt.TerminalNameException;
import uk.ac.lancs.networks.mgmt.Trunk;
import uk.ac.lancs.networks.mgmt.TrunkManagementException;
import uk.ac.lancs.networks.mgmt.UnknownSubnetworkException;
import uk.ac.lancs.networks.mgmt.UnknownSubterminalException;
import uk.ac.lancs.networks.mgmt.UnknownTrunkException;
import uk.ac.lancs.networks.util.ReferenceWatcher;

/**
 * Accesses a remote aggregator through a supply of JSON channels.
 * 
 * @author simpsons
 */
public class JsonAggregator extends JsonNetwork implements Aggregator {
    /**
     * Create an aggregator accessible through a supply of JSON
     * channels.
     * 
     * @param name the local network name
     * 
     * @param executor a resource for running background activities on
     * 
     * @param channels a supply of JSON channels
     */
    public JsonAggregator(String name, Executor executor,
                          JsonChannelManager channels) {
        super(name, executor, channels);

        trunkWatcher.start();
    }

    @Override
    protected void checkErrors(JsonObject rsp)
        throws NetworkManagementException,
            NetworkLogicException {
        String type = rsp.getString("error", null);
        if (type == null) return;
        switch (type) {
        case "trunk-expired": {
            TerminalId start =
                TerminalId.of(rsp.getString("start-network-name"),
                              rsp.getString("start-terminal-name"));
            TerminalId end =
                TerminalId.of(rsp.getString("end-network-name"),
                              rsp.getString("end-terminal-name"));
            @SuppressWarnings("unused")
            Trunk trunk = getKnownTrunk(start, end);
            throw new ExpiredTrunkException(this.getControl().name(), start,
                                            end);
        }

        case "bw-unavailable":
        case "labels-in-use":
        case "label-mgmt":
        case "trunk-mgmt": {
            TerminalId start =
                TerminalId.of(rsp.getString("start-network-name"),
                              rsp.getString("start-terminal-name"));
            TerminalId end =
                TerminalId.of(rsp.getString("end-network-name"),
                              rsp.getString("end-terminal-name"));
            Trunk trunk = getKnownTrunk(start, end);

            switch (type) {
            case "labels-unavailable":
            case "labels-in-use":
            case "label-mgmt": {
                BitSet labels = new BitSet();
                for (JsonValue val : rsp.getJsonArray("labels")) {
                    JsonNumber label = (JsonNumber) val;
                    labels.set(label.intValue());
                }
                switch (type) {
                case "labels-unavailable":
                    throw new LabelsUnavailableException(this.getControl()
                        .name(), trunk.getStartTerminal(),
                                                         trunk
                                                             .getEndTerminal(),
                                                         labels);
                case "labels-in-use":
                    throw new LabelsInUseException(this.getControl().name(),
                                                   trunk.getStartTerminal(),
                                                   trunk.getEndTerminal(),
                                                   labels);
                case "label-mgmt":
                    throw new LabelManagementException(this.getControl()
                        .name(), trunk.getStartTerminal(),
                                                       trunk.getEndTerminal(),
                                                       labels);
                }
            }
            case "bw-unavailable": {
                boolean up = rsp.getString("direction").equals("upstream");
                double amount = rsp.getJsonNumber("amount").doubleValue();
                throw new BandwidthUnavailableException(this.getControl()
                    .name(), trunk.getStartTerminal(), trunk.getEndTerminal(),
                                                        up, amount);
            }
            case "trunk-mgmt":
                throw new TrunkManagementException(this.getControl().name(),
                                                   trunk.getStartTerminal(),
                                                   trunk.getEndTerminal(),
                                                   rsp.getString("msg"));
            }
        }

        default:
            super.checkErrors(rsp);
            break;
        }
    }

    @Override
    public Map<Terminal, TerminalId> getTerminals() {
        JsonObject req = Json.createObjectBuilder()
            .add("type", "get-aggregator-terminals").build();
        JsonObject rsp = interact(req);
        try {
            checkErrors(rsp);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UndeclaredThrowableException(e);
        }
        Map<Terminal, TerminalId> result = new HashMap<>();
        JsonObject terms = rsp.getJsonObject("terminals");
        for (String outerName : terms.keySet()) {
            Terminal outer = getKnownTerminal(outerName);
            JsonObject innerObj = terms.getJsonObject(outerName);
            String innerNetworkName = innerObj.getString("subnetwork-name");
            String innerTerminalName = innerObj.getString("subterminal-name");
            TerminalId innerId =
                TerminalId.of(innerNetworkName, innerTerminalName);
            result.put(outer, innerId);
        }
        return result;
    }

    @Override
    public Terminal addTerminal(String name, TerminalId subterm)
        throws TerminalNameException,
            SubterminalBusyException,
            UnknownSubterminalException,
            UnknownSubnetworkException {
        JsonObject req = Json.createObjectBuilder()
            .add("type", "map-terminal").add("terminal-name", name)
            .add("subnetwork-name", subterm.network)
            .add("subterminal-name", subterm.terminal).build();
        JsonObject rsp = interact(req);
        try {
            checkErrors(rsp);
        } catch (TerminalNameException | SubterminalBusyException
            | UnknownSubterminalException | UnknownSubnetworkException
            | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UndeclaredThrowableException(e);
        }
        return getKnownTerminal(name);
    }

    @Override
    public Collection<List<TerminalId>> getTrunks() {
        JsonObject req =
            Json.createObjectBuilder().add("type", "get-trunks").build();
        JsonObject rsp = interact(req);
        try {
            checkErrors(rsp);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UndeclaredThrowableException(e);
        }
        Collection<List<TerminalId>> result = new HashSet<>();
        for (JsonObject desc : rsp.getJsonArray("trunks")
            .getValuesAs(JsonObject.class)) {
            String sn = desc.getString("start-network-name");
            String st = desc.getString("start-terminal-name");
            String en = desc.getString("end-network-name");
            String et = desc.getString("end-terminal-name");
            TerminalId s = TerminalId.of(sn, st);
            TerminalId e = TerminalId.of(en, et);
            result.add(Arrays.asList(s, e));
        }
        return result;
    }

    @Override
    public Trunk addTrunk(TerminalId t1, TerminalId t2)
        throws SubterminalBusyException,
            UnknownSubterminalException,
            UnknownSubnetworkException {
        JsonObject req = Json.createObjectBuilder().add("type", "add-trunk")
            .add("start-network-name", t1.network)
            .add("start-terminal-name", t1.terminal)
            .add("end-network-name", t2.network)
            .add("end-terminal-name", t2.terminal).build();
        JsonObject rsp = interact(req);
        try {
            checkErrors(rsp);
        } catch (SubterminalBusyException | UnknownSubterminalException
            | UnknownSubnetworkException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UndeclaredThrowableException(e);
        }
        return getKnownTrunk(t1, t2);
    }

    @Override
    public void removeTrunk(TerminalId subterm)
        throws UnknownTrunkException,
            UnknownSubterminalException,
            UnknownSubnetworkException {
        JsonObject req =
            Json.createObjectBuilder().add("type", "remove-trunk")
                .add("subnetwork-name", subterm.network)
                .add("subterminal-name", subterm.terminal).build();
        JsonObject rsp = interact(req);
        try {
            checkErrors(rsp);
        } catch (UnknownSubterminalException | UnknownSubnetworkException
            | UnknownTrunkException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UndeclaredThrowableException(e);
        }
    }

    @Override
    public Trunk findTrunk(TerminalId subterm)
        throws UnknownSubnetworkException,
            UnknownSubterminalException {
        JsonObject req = Json.createObjectBuilder().add("type", "check-trunk")
            .add("start-subnetwork-name", subterm.network)
            .add("start-subterminal-name", subterm.terminal).build();
        JsonObject rsp = interact(req);
        try {
            checkErrors(rsp);
        } catch (UnknownSubnetworkException | UnknownSubterminalException
            | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UndeclaredThrowableException(e);
        }
        TerminalId endTerm =
            TerminalId.of(rsp.getString("end-subnetwork-name"),
                          rsp.getString("end-subterminal-name"));
        if (endTerm == null) return null;
        return getKnownTrunk(subterm, endTerm);
    }

    private final ReferenceWatcher<Trunk, RemoteTrunk, TrunkId> trunkWatcher =
        ReferenceWatcher.on(Trunk.class, getClass().getClassLoader(),
                            RemoteTrunk::new, RemoteTrunk::cleanUp);

    /**
     * Get a trunk that has just been implied to exist by the result of
     * a call. For example, a call to create a new trunk necessarily
     * includes specification of both ends of the trunk, so (if
     * successful) that trunk can be fully identified locally on return
     * from the call. For another example, if a trunk is sought by
     * specifying the start terminal, the remote call's response (if
     * successful) includes the end terminal, so a local representation
     * of the trunk can still be built.
     * 
     * @param start the inferior terminal defining the start of the
     * trunk
     * 
     * @param end the inferior terminal defining the end of the trunk
     * 
     * @return the requested trunk
     */
    protected Trunk getKnownTrunk(TerminalId start, TerminalId end) {
        TrunkId id = new TrunkId(start, end);
        return trunkWatcher.get(id);
    }

    private class RemoteTrunk implements Trunk {
        private TrunkId id;

        void cleanUp() {}

        public RemoteTrunk(TrunkId id) {
            this.id = id;
        }

        @Override
        public double getDelay() {
            JsonObject req = startRequest("get-trunk-delay").build();
            JsonObject rsp = interact(req);
            try {
                checkErrors(rsp);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new UndeclaredThrowableException(e);
            }
            return rsp.getJsonNumber("delay").doubleValue();
        }

        @Override
        public void setDelay(double delay) {
            JsonObject req =
                startRequest("set-trunk-delay").add("delay", delay).build();
            JsonObject rsp = interact(req);
            try {
                checkErrors(rsp);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new UndeclaredThrowableException(e);
            }
        }

        @Override
        public void withdrawBandwidth(double upstream, double downstream)
            throws BandwidthUnavailableException {
            JsonObject req =
                startRequest("decrease-bw").add("upstream-bw", upstream)
                    .add("downstream-bw", downstream).build();
            JsonObject rsp = interact(req);
            try {
                checkErrors(rsp);
            } catch (BandwidthUnavailableException | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new UndeclaredThrowableException(e);
            }
        }

        @Override
        public void provideBandwidth(double upstream, double downstream) {
            JsonObject req =
                startRequest("increase-trunk-bw").add("upstream-bw", upstream)
                    .add("downstream-bw", downstream).build();
            JsonObject rsp = interact(req);
            try {
                checkErrors(rsp);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new UndeclaredThrowableException(e);
            }
        }

        @Override
        public void defineLabelRange(int startBase, int amount, int endBase)
            throws LabelsInUseException,
                LabelsUnavailableException {
            JsonObject req = startRequest("define-trunk-label-range")
                .add("start-label", startBase).add("label-count", amount)
                .add("end-label", endBase).build();
            JsonObject rsp = interact(req);
            try {
                checkErrors(rsp);
            } catch (LabelsInUseException | LabelsUnavailableException
                | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new UndeclaredThrowableException(e);
            }
        }

        @Override
        public void revokeStartLabelRange(int startBase, int amount)
            throws LabelsInUseException {
            JsonObject req = startRequest("revoke-trunk-start-label-range")
                .add("start-label", startBase).add("label-count", amount)
                .build();
            JsonObject rsp = interact(req);
            try {
                checkErrors(rsp);
            } catch (LabelsInUseException | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new UndeclaredThrowableException(e);
            }
        }

        @Override
        public void revokeEndLabelRange(int endBase, int amount)
            throws LabelsInUseException {
            JsonObject req = startRequest("revoke-trunk-end-label-range")
                .add("end-label", endBase).add("label-count", amount).build();
            JsonObject rsp = interact(req);
            try {
                checkErrors(rsp);
            } catch (LabelsInUseException | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new UndeclaredThrowableException(e);
            }
        }

        @Override
        public void decommission() {
            JsonObject req = startRequest("decommission-trunk").build();
            JsonObject rsp = interact(req);
            try {
                checkErrors(rsp);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new UndeclaredThrowableException(e);
            }
        }

        @Override
        public void recommission() {
            JsonObject req = startRequest("recommission-trunk").build();
            JsonObject rsp = interact(req);
            try {
                checkErrors(rsp);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new UndeclaredThrowableException(e);
            }
        }

        @Override
        public boolean isCommissioned() {
            JsonObject req = startRequest("is-trunk-commissioned").build();
            JsonObject rsp = interact(req);
            try {
                checkErrors(rsp);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new UndeclaredThrowableException(e);
            }
            return rsp.getBoolean("commissioned");
        }

        @Override
        public TerminalId getStartTerminal() {
            return id.start;
        }

        @Override
        public TerminalId getEndTerminal() {
            return id.end;
        }

        private JsonObjectBuilder startRequest(String type) {
            return JsonAggregator.this.startRequest(type)
                .add("start-network-name", id.start.network)
                .add("start-terminal-name", id.start.terminal)
                .add("end-network-name", id.end.network)
                .add("end-terminal-name", id.end.terminal);
        }
    }
}
