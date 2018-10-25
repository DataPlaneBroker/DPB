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
import java.util.function.Function;

import javax.json.JsonObject;

import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.mgmt.Aggregator;
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.networks.mgmt.NetworkManagementException;

/**
 * Translates JSON-formatted requests into invocations on a
 * {@link Aggregator}, and results of those invocations back to JSON.
 * 
 * @author simpsons
 */
public class JsonAggregatorServer extends JsonNetworkServer {
    private final Aggregator network;
    private final Function<? super String, ? extends Network> subnets;

    public JsonAggregatorServer(Aggregator network,
                                Function<? super String, ? extends Network> subnets) {
        super(network, true);
        this.network = network;
        this.subnets = subnets;
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
                Network subnet = subnets.apply(subNetName);
                Terminal subterm = subnet.getTerminal(subTermName);
                network.addTerminal(name, subterm);
                return empty();
            }

            /* TODO: Add trunks, etc. */

            default:
                return null;
            }
        } catch (NetworkManagementException e) {
            return handle(e);
        }
    }
}
