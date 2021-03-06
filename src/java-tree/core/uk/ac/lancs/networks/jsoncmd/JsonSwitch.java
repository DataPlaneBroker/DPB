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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.mgmt.Switch;
import uk.ac.lancs.networks.mgmt.TerminalConfigurationException;
import uk.ac.lancs.networks.mgmt.TerminalNameException;
import uk.ac.lancs.networks.mgmt.UnknownTerminalException;

/**
 * Accesses a remote switch through a supply of JSON channels.
 * 
 * @author simpsons
 */
public class JsonSwitch extends JsonNetwork implements Switch {
    /**
     * Access a remote switch through a supply of remote JSON channels.
     * 
     * @param name the local network name
     * 
     * @param executor a resource for running background activities on
     * 
     * @param channels a supply of JSON channels
     */
    public JsonSwitch(String name, Executor executor,
                      JsonChannelManager channels) {
        super(name, executor, channels);
    }

    @Override
    public Terminal addTerminal(String terminalName, String interfaceName)
        throws TerminalNameException,
            TerminalConfigurationException {
        JsonObject req =
            startRequest("add-terminal").add("terminal-name", terminalName)
                .add("terminal-config", interfaceName).build();
        JsonObject rsp = interact(req);
        try {
            checkErrors(rsp);
        } catch (TerminalNameException | TerminalConfigurationException
            | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UndeclaredThrowableException(e);
        }
        return getKnownTerminal(terminalName);
    }

    @Override
    public Map<Terminal, String> getTerminals() {
        JsonObject req = startRequest("get-switch-terminals").build();
        JsonObject rsp = interact(req);
        try {
            checkErrors(rsp);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UndeclaredThrowableException(e);
        }
        Map<Terminal, String> result = new HashMap<>();
        JsonObject terms = rsp.getJsonObject("terminals");
        for (String tn : terms.keySet()) {
            String ifc = terms.getString(tn);
            result.put(getKnownTerminal(tn), ifc);
        }
        return result;
    }

    @Override
    public void modifyBandwidth(String terminalName, boolean setIngress,
                                Double ingress, boolean setEgress,
                                Double egress)
        throws UnknownTerminalException {
        JsonObjectBuilder ingressBuilder = Json.createObjectBuilder()
            .add("action", setIngress ? "set" : "adjust");
        if (ingress != null)
            ingressBuilder.add("amount", ingress);
        else
            ingressBuilder.addNull("amount");

        JsonObjectBuilder egressBuilder = Json.createObjectBuilder()
            .add("action", setEgress ? "set" : "adjust");
        if (egress != null)
            egressBuilder.add("amount", egress);
        else
            egressBuilder.addNull("amount");

        JsonObject req = startRequest("modify-terminal-bandwidth")
            .add("terminal-name", terminalName)
            .add("ingress", ingressBuilder.build())
            .add("egress", egressBuilder.build()).build();
        JsonObject rsp = interact(req);
        try {
            checkErrors(rsp);
        } catch (UnknownTerminalException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UndeclaredThrowableException(e);
        }
    }
}
