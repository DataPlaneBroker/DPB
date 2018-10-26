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

import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.Executor;

import javax.json.Json;
import javax.json.JsonObject;

import uk.ac.lancs.networks.NetworkControlException;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.mgmt.Aggregator;
import uk.ac.lancs.networks.mgmt.NetworkManagementException;
import uk.ac.lancs.networks.mgmt.Trunk;
import uk.ac.lancs.networks.mgmt.TrunkManagementException;

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
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    @Override
    public Terminal addTerminal(String name, String subnet, String subterm)
        throws NetworkManagementException {
        JsonObject req = Json.createObjectBuilder().add("type", "add-trunk")
            .add("terminal-name", name).add("subnetwork-name", subnet)
            .add("subterminal-name", subterm).build();
        JsonObject rsp = interact(req);
        try {
            checkErrors(rsp);
        } catch (NetworkManagementException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UndeclaredThrowableException(e);
        }
        return getKnownTerminal(name);
    }

    @Override
    protected void checkErrors(JsonObject rsp)
        throws NetworkManagementException,
            NetworkControlException {
        String type = rsp.getString("error");
        if (type == null) return;
        switch (type) {
        case "trunk-mgmt":
            Trunk trunk = getTrunk(rsp.getString("start-network-name"),
                                   rsp.getString("start-terminal-name"));
            throw new TrunkManagementException(this, trunk,
                                               rsp.getString("msg"));
        }
        super.checkErrors(rsp);
    }

    @Override
    public Trunk addTrunk(String n1, String t1, String n2, String t2)
        throws NetworkManagementException {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    @Override
    public void removeTrunk(String network, String terminal)
        throws NetworkManagementException {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    @Override
    public Trunk findTrunk(String network, String terminal) {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }
}