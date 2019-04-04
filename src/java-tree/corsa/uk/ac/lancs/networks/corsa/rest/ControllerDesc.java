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
package uk.ac.lancs.networks.corsa.rest;

import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.json.JsonObject;
import javax.json.JsonStructure;

/**
 * Describes an existing controller's configuration and status.
 * 
 * @author simpsons
 */
public class ControllerDesc {
    /**
     * The controller identifier
     */
    public String id;

    /**
     * The IP address of the controller
     */
    public InetAddress host;

    /**
     * The TCP port of the controller
     */
    public int port;

    /**
     * Whether TLS is enabled to contact the controller
     */
    public boolean tls;

    /**
     * Whether the VFC is connected to the controller
     */
    public boolean connected;

    /**
     * A status message
     */
    public String message;

    /**
     * The role of the controller
     */
    public String role;

    /**
     * Create a controller description from a JSON entity.
     * 
     * @param entity the JSON entity
     */
    public ControllerDesc(JsonStructure entity) {
        this((JsonObject) entity);
    }

    /**
     * Create a controller description from a JSON object.
     * 
     * @param root the JSON object
     */
    public ControllerDesc(JsonObject root) {
        this.id = root.getString("controller");
        try {
            this.host = InetAddress.getByName(root.getString("host"));
        } catch (UnknownHostException e) {
            this.host = null;
        }
        this.port = root.getInt("port");
        this.message = root.getString("message");
        this.role = root.getString("role");
        this.tls = root.getBoolean("tls");
        this.connected = root.getBoolean("connected");
    }
}
