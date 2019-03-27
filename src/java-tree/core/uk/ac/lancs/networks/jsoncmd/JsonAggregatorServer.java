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
package uk.ac.lancs.networks.jsoncmd;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.mgmt.Aggregator;
import uk.ac.lancs.networks.mgmt.ExpiredTrunkException;
import uk.ac.lancs.networks.mgmt.TerminalId;
import uk.ac.lancs.networks.mgmt.Trunk;
import uk.ac.lancs.networks.mgmt.UnknownSubnetworkException;
import uk.ac.lancs.networks.mgmt.UnknownSubterminalException;

/**
 * Translates JSON-formatted requests into invocations on a
 * {@link Aggregator}, and results of those invocations back to JSON.
 * 
 * @author simpsons
 */
public class JsonAggregatorServer extends JsonNetworkServer {
    private final Aggregator network;

    /**
     * Create a JSON adaptor around an aggregator
     * 
     * @param network the aggregator network to be invoked
     */
    public JsonAggregatorServer(Aggregator network) {
        super(network, true);
        this.network = network;
    }

    private Trunk confirmTrunk(JsonObject req)
        throws UnknownSubterminalException,
            UnknownSubnetworkException {
        TerminalId i1 = TerminalId.of(req.getString("start-network-name"),
                                      req.getString("start-terminal-name"));
        TerminalId i2 = TerminalId.of(req.getString("end-network-name"),
                                      req.getString("end-terminal-name"));
        Trunk trunk = network.findTrunk(i1);
        if (!i2.equals(trunk.getEndTerminal()))
            throw new ExpiredTrunkException(network.getControl().name(), i1,
                                            i2);
        return trunk;
    }

    @Override
    public Iterable<JsonObject> interact(JsonObject req, Executor onClose) {
        Iterable<JsonObject> superResult = super.interact(req, onClose);
        if (superResult != null) return superResult;
        try {
            switch (req.getString("type")) {
            case "map-terminal": {
                String name = req.getString("terminal-name");
                String subNetName = req.getString("subnetwork-name");
                String subTermName = req.getString("subterminal-name");
                TerminalId subterm = TerminalId.of(subNetName, subTermName);
                network.addTerminal(name, subterm);
                return empty();
            }

            case "get-aggregator-terminals": {
                JsonObjectBuilder terms = Json.createObjectBuilder();
                for (Map.Entry<Terminal, TerminalId> entry : network
                    .getTerminals().entrySet()) {
                    TerminalId innerId = entry.getValue();
                    terms.add(entry.getKey().name(),
                              Json.createObjectBuilder()
                                  .add("subnetwork-name", innerId.network)
                                  .add("subterminal-name", innerId.terminal));
                }
                return one(Json.createObjectBuilder().add("terminals", terms)
                    .build());
            }

            case "get-trunks": {
                JsonArrayBuilder trunks = Json.createArrayBuilder();
                for (List<TerminalId> item : network.getTrunks()) {
                    TerminalId s = item.get(0);
                    TerminalId e = item.get(1);
                    trunks.add(Json.createObjectBuilder()
                        .add("start-network-name", s.network)
                        .add("start-terminal-name", s.terminal)
                        .add("end-network-name", e.network)
                        .add("end-terminal-name", e.terminal).build());
                }
                return one(Json.createObjectBuilder().add("trunks", trunks)
                    .build());
            }

            case "remove-trunk": {
                String subNetName = req.getString("subnetwork-name");
                String subTermName = req.getString("subterminal-name");
                TerminalId subterm = TerminalId.of(subNetName, subTermName);
                network.removeTrunk(subterm);
                return empty();
            }

            case "check-trunk": {
                String subNetName = req.getString("start-subnetwork-name");
                String subTermName = req.getString("start-subterminal-name");
                TerminalId subterm = TerminalId.of(subNetName, subTermName);
                Trunk trunk = network.findTrunk(subterm);
                if (trunk == null) return empty();
                return one(Json.createObjectBuilder()
                    .add("end-subnetwork-name",
                         trunk.getEndTerminal().network)
                    .add("end-subterminal-name",
                         trunk.getEndTerminal().terminal)
                    .build());
            }

            case "add-trunk": {
                TerminalId i1 =
                    TerminalId.of(req.getString("start-network-name"),
                                  req.getString("start-terminal-name"));
                TerminalId i2 =
                    TerminalId.of(req.getString("end-network-name"),
                                  req.getString("end-terminal-name"));
                network.addTrunk(i1, i2);
                return empty();
            }

            case "get-trunk-delay": {
                Trunk trunk = confirmTrunk(req);
                return one(Json.createObjectBuilder()
                    .add("delay", trunk == null ? -1.0 : trunk.getDelay())
                    .build());
            }

            case "set-trunk-delay": {
                Trunk trunk = confirmTrunk(req);
                trunk.setDelay(req.getJsonNumber("delay").doubleValue());
                return empty();
            }

            case "define-trunk-label-range": {
                Trunk trunk = confirmTrunk(req);
                trunk.defineLabelRange(req.getInt("start-label"),
                                       req.getInt("label-count"),
                                       req.getInt("end-label"));
                return empty();
            }

            case "revoke-trunk-start-label-range": {
                Trunk trunk = confirmTrunk(req);
                trunk.revokeStartLabelRange(req.getInt("start-label"),
                                            req.getInt("label-count"));
                return empty();
            }

            case "revoke-trunk-end-label-range": {
                Trunk trunk = confirmTrunk(req);
                trunk.revokeEndLabelRange(req.getInt("end-label"),
                                          req.getInt("label-count"));
                return empty();
            }

            case "decommission-trunk": {
                Trunk trunk = confirmTrunk(req);
                trunk.decommission();
                return empty();
            }

            case "recommission-trunk": {
                Trunk trunk = confirmTrunk(req);
                trunk.recommission();
                return empty();
            }

            case "is-trunk-commissioned": {
                Trunk trunk = confirmTrunk(req);
                return one(Json.createObjectBuilder()
                    .add("commissioned",
                         trunk == null ? false : trunk.isCommissioned())
                    .build());
            }

            case "increase-trunk-bw": {
                Trunk trunk = confirmTrunk(req);
                double upstream =
                    req.getJsonNumber("upstream-bw").doubleValue();
                double downstream =
                    req.getJsonNumber("downstream-bw").doubleValue();
                trunk.provideBandwidth(upstream, downstream);
                return empty();
            }

            case "decrease-trunk-bw": {
                Trunk trunk = confirmTrunk(req);
                double upstream =
                    req.getJsonNumber("upstream-bw").doubleValue();
                double downstream =
                    req.getJsonNumber("downstream-bw").doubleValue();
                trunk.withdrawBandwidth(upstream, downstream);
                return empty();
            }

            default:
                return null;
            }
        } catch (Throwable e) {
            return handle(e);
        }
    }
}
