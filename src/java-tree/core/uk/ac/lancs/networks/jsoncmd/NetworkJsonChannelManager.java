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

import javax.json.Json;
import javax.json.JsonObject;

/**
 * Selects the network on a remote channel.
 * 
 * @author simpsons
 */
public final class NetworkJsonChannelManager implements JsonChannelManager {
    private final JsonChannelManager base;
    private final String name;

    /**
     * Create a channel manager that sends a message to select the
     * remote network before passing the channel to the user.
     * 
     * @param base the base provider of channels
     * 
     * @param name the name of the network to select
     */
    public NetworkJsonChannelManager(JsonChannelManager base, String name) {
        this.base = base;
        this.name = name;
    }

    @Override
    public JsonChannel getChannel() {
        JsonChannel result = base.getChannel();
        /* Select the remote network. */
        JsonObject selNet = Json.createObjectBuilder().add("type", "network")
            .add("network-name", name).build();
        result.write(selNet);
        JsonObject selNetRsp = result.read();
        String error = selNetRsp.getString("error");
        switch (error) {
        case "unauthorized":
            throw new IllegalArgumentException("unauthorized to access "
                + name);

        case "no-network":
            throw new IllegalArgumentException("no network " + name);
        }
        return result;
    }
}
