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

import java.util.Map;
import java.util.concurrent.Executor;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.mgmt.NetworkManagementException;
import uk.ac.lancs.networks.mgmt.Switch;

/**
 * Translates JSON-formatted requests into invocations on a
 * {@link Switch}, and results of those invocations back to JSON.
 * 
 * @author simpsons
 */
public class JsonSwitchServer extends JsonNetworkServer {
    private final Switch network;

    /**
     * Create a JSON adaptor around a switch.
     * 
     * @param network the switch network to be invoked
     */
    public JsonSwitchServer(Switch network) {
        super(network, true);
        this.network = network;
    }

    /**
     * {@inheritDoc}
     * 
     * @default This implementation recognizes the following commands:
     * 
     * <dl>
     * 
     * <dt><samp>add-terminal <var>terminal-name</var>
     * <var>terminal-config</var></samp>
     * 
     * <dd>Invoke {@link Switch#addTerminal(String, String)} with the
     * specified name.
     * 
     * <dt><samp>get-switch-terminals</samp>
     * 
     * <dd>Invoke {@link Switch#getTerminals()}.
     * 
     * <dt><samp>provide-terminal-bandwidth <var>terminal-name</var>
     * <var>ingress-bandwidth</var> <var>egress-bandwidth</var></samp>
     * 
     * <dd>Invoke
     * {@link Switch#provideBandwidth(String, double, double)}.
     * 
     * <dt><samp>withdraw-terminal-bandwidth <var>terminal-name</var>
     * <var>ingress-bandwidth</var> <var>egress-bandwidth</var></samp>
     * 
     * <dd>Invoke
     * {@link Switch#withdrawBandwidth(String, double, double)}.
     * 
     * </dl>
     */
    @Override
    public Iterable<JsonObject> interact(JsonObject req, Executor onClose) {
        Iterable<JsonObject> superResult = super.interact(req, onClose);
        if (superResult != null) return superResult;
        try {
            switch (req.getString("type")) {
            case "add-terminal": {
                String name = req.getString("terminal-name");
                String config = req.getString("terminal-config");
                network.addTerminal(name, config);
                return empty();
            }

            case "modify-terminal-bandwidth": {
                String name = req.getString("terminal-name");

                JsonObject ingressObject = req.getJsonObject("ingress");
                boolean setIngress =
                    ingressObject.getString("action").equals("set");
                Double ingress = ingressObject.isNull("amount") ? null
                    : ingressObject.getJsonNumber("amount").doubleValue();

                JsonObject egressObject = req.getJsonObject("egress");
                boolean setEgress =
                    egressObject.getString("action").equals("set");
                Double egress = egressObject.isNull("amount") ? null
                    : egressObject.getJsonNumber("amount").doubleValue();

                network.modifyBandwidth(name, setIngress, ingress, setEgress,
                                        egress);
                return empty();
            }

            case "get-switch-terminals": {
                Map<Terminal, String> result = network.getTerminals();
                JsonObjectBuilder terms = Json.createObjectBuilder();
                for (Map.Entry<Terminal, String> entry : result.entrySet()) {
                    Terminal t = entry.getKey();
                    String i = entry.getValue();
                    terms.add(t.name(), i);
                }
                return one(Json.createObjectBuilder().add("terminals", terms)
                    .build());
            }

            default:
                return null;
            }
        } catch (NetworkManagementException e) {
            return handle(e);
        }
    }
}
