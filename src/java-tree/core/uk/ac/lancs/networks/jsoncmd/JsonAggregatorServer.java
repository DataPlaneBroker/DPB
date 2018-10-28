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

import java.util.concurrent.Executor;

import javax.json.Json;
import javax.json.JsonObject;

import uk.ac.lancs.networks.mgmt.Aggregator;
import uk.ac.lancs.networks.mgmt.TerminalId;
import uk.ac.lancs.networks.mgmt.Trunk;

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
                String n1 = req.getString("start-network-name");
                String t1 = req.getString("start-terminal-name");
                TerminalId i1 = TerminalId.of(n1, t1);
                String n2 = req.getString("end-network-name");
                String t2 = req.getString("end-terminal-name");
                TerminalId i2 = TerminalId.of(n2, t2);
                network.addTrunk(i1, i2);
                return empty();
            }

            case "get-trunk-delay": {
                TerminalId subterm =
                    TerminalId.of(req.getString("subnetwork-name"),
                                  req.getString("subterminal-name"));
                Trunk trunk = network.findTrunk(subterm);
                return one(Json.createObjectBuilder()
                    .add("delay", trunk == null ? -1.0 : trunk.getDelay())
                    .build());
            }

            case "set-trunk-delay": {
                TerminalId subterm =
                    TerminalId.of(req.getString("subnetwork-name"),
                                  req.getString("subterminal-name"));
                Trunk trunk = network.findTrunk(subterm);
                trunk.setDelay(req.getJsonNumber("delay").doubleValue());
                return empty();
            }

            case "define-trunk-label-range": {
                TerminalId subterm =
                    TerminalId.of(req.getString("subnetwork-name"),
                                  req.getString("subterminal-name"));
                Trunk trunk = network.findTrunk(subterm);
                trunk.defineLabelRange(req.getInt("start-label"),
                                       req.getInt("label-count"),
                                       req.getInt("end-label"));
                return empty();
            }

            case "revoke-trunk-start-label-range": {
                TerminalId subterm =
                    TerminalId.of(req.getString("subnetwork-name"),
                                  req.getString("subterminal-name"));
                Trunk trunk = network.findTrunk(subterm);
                trunk.revokeStartLabelRange(req.getInt("start-label"),
                                            req.getInt("label-count"));
                return empty();
            }

            case "revoke-trunk-end-label-range": {
                TerminalId subterm =
                    TerminalId.of(req.getString("subnetwork-name"),
                                  req.getString("subterminal-name"));
                Trunk trunk = network.findTrunk(subterm);
                trunk.revokeEndLabelRange(req.getInt("end-label"),
                                          req.getInt("label-count"));
                return empty();
            }

            case "decommission-trunk": {
                TerminalId subterm =
                    TerminalId.of(req.getString("subnetwork-name"),
                                  req.getString("subterminal-name"));
                Trunk trunk = network.findTrunk(subterm);
                trunk.decommission();
                return empty();
            }

            case "recommission-trunk": {
                TerminalId subterm =
                    TerminalId.of(req.getString("subnetwork-name"),
                                  req.getString("subterminal-name"));
                Trunk trunk = network.findTrunk(subterm);
                trunk.recommission();
                return empty();
            }

            case "is-trunk-commissioned": {
                TerminalId subterm =
                    TerminalId.of(req.getString("subnetwork-name"),
                                  req.getString("subterminal-name"));
                Trunk trunk = network.findTrunk(subterm);
                return one(Json.createObjectBuilder()
                    .add("commissioned",
                         trunk == null ? false : trunk.isCommissioned())
                    .build());
            }

            case "increase-trunk-bw": {
                TerminalId subterm =
                    TerminalId.of(req.getString("subnetwork-name"),
                                  req.getString("subterminal-name"));
                Trunk trunk = network.findTrunk(subterm);
                double upstream =
                    req.getJsonNumber("upstream-bw").doubleValue();
                double downstream =
                    req.getJsonNumber("downstream-bw").doubleValue();
                trunk.provideBandwidth(upstream, downstream);
                return empty();
            }

            case "decrease-trunk-bw": {
                TerminalId subterm =
                    TerminalId.of(req.getString("subnetwork-name"),
                                  req.getString("subterminal-name"));
                Trunk trunk = network.findTrunk(subterm);
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
