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
            NetworkControlException {
        String type = rsp.getString("error");
        if (type == null) return;
        switch (type) {
        case "trunk-mgmt":
            TerminalId start =
                TerminalId.of(rsp.getString("start-network-name"),
                              rsp.getString("start-terminal-name"));
            TerminalId end =
                TerminalId.of(rsp.getString("end-network-name"),
                              rsp.getString("end-terminal-name"));
            Trunk trunk = getKnownTrunk(start, end);
            throw new TrunkManagementException(this, trunk,
                                               rsp.getString("msg"));
        }
        super.checkErrors(rsp);
    }

    @Override
    public Terminal addTerminal(String name, TerminalId subterm)
        throws TerminalNameException,
            SubterminalBusyException,
            UnknownSubterminalException,
            UnknownSubnetworkException {
        JsonObject req = Json.createObjectBuilder().add("type", "add-trunk")
            .add("terminal-name", name)
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
    public Trunk addTrunk(TerminalId t1, TerminalId t2)
        throws SubterminalBusyException,
            UnknownSubterminalException,
            UnknownSubnetworkException {
        JsonObject req =
            Json.createObjectBuilder().add("start-network-name", t1.network)
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
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    @Override
    public Trunk findTrunk(TerminalId subterm)
        throws UnknownSubnetworkException,
            UnknownSubterminalException {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    private final ReferenceWatcher<Trunk, RemoteTrunk, TrunkId> trunkWatcher =
        ReferenceWatcher.on(Trunk.class, getClass().getClassLoader(),
                            RemoteTrunk::new, RemoteTrunk::cleanUp);

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
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public void setDelay(double delay) {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public void withdrawBandwidth(double upstream, double downstream)
            throws NetworkManagementException {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public void provideBandwidth(double upstream, double downstream) {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public void defineLabelRange(int startBase, int amount, int endBase)
            throws NetworkManagementException {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public void revokeStartLabelRange(int startBase, int amount)
            throws NetworkManagementException {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public void revokeEndLabelRange(int endBase, int amount)
            throws NetworkManagementException {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public void decommission() {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public void recommission() {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public boolean isCommissioned() {
            throw new UnsupportedOperationException("unimplemented"); // TODO
        }

        @Override
        public TerminalId getStartTerminal() {
            return id.start;
        }

        @Override
        public TerminalId getEndTerminal() {
            return id.end;
        }
    }
}
