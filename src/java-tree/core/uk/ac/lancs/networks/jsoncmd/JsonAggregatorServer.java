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
                String subNetName = req.getString("subnetwork-name");
                String subTermName = req.getString("subterminal-name");
                TerminalId subterm = TerminalId.of(subNetName, subTermName);
                Trunk trunk = network.findTrunk(subterm);
                return one(Json.createObjectBuilder()
                    .add("exists", trunk != null).build());
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

            default:
                return null;
            }
        } catch (Throwable e) {
            return handle(e);
        }
    }
}
