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
package uk.ac.lancs.networks.corsa.rest;

import java.net.InetAddress;

import org.json.simple.JSONObject;

/**
 * Describes the configuration of a new controller.
 * 
 * @author simpsons
 */
public class ControllerConfig {
    /**
     * The controller's identifier
     */
    public String id;

    /**
     * The controller's port
     */
    public int port;

    /**
     * The controller's IP address
     */
    public InetAddress host;

    /**
     * Whether to use TLS to contact the controller
     */
    public boolean tls;

    /**
     * Set the controller's IP address.
     * 
     * @param host the controller's IP address
     * 
     * @return this object
     */
    public ControllerConfig host(InetAddress host) {
        this.host = host;
        return this;
    }

    /**
     * Set the controller's port.
     * 
     * @param port the controller's port
     * 
     * @return this object
     */
    public ControllerConfig port(int port) {
        this.port = port;
        return this;
    }

    /**
     * Set the controller's identifier.
     * 
     * @param id the controller's identifier
     * 
     * @return this object
     */
    public ControllerConfig id(String id) {
        this.id = id;
        return this;
    }

    /**
     * Enable or disable TLS communication to the controller.
     * 
     * @param tls whether TLS is enabled
     * 
     * @return this object
     */
    public ControllerConfig tls(boolean tls) {
        this.tls = tls;
        return this;
    }

    /**
     * Convert this controller configuration to JSON.
     * 
     * @return a JSON object representing this configuration
     */
    @SuppressWarnings("unchecked")
    public JSONObject toJSON() {
        if (id == null) throw new IllegalStateException("id must be set");
        if (host == null) throw new IllegalStateException("host must be set");
        if (port <= 0 || port > 65535)
            throw new IllegalStateException("port must be set");
        JSONObject result = new JSONObject();
        result.put("controller", id);
        result.put("host", host.getHostAddress());
        result.put("port", port);
        result.put("tls", tls);
        return result;
    }
}
