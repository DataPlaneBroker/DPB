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

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONObject;

/**
 * Describes the Corsa REST API.
 * 
 * @author simpsons
 */
public class APIDesc {
    /**
     * The REST URI for managing users
     */
    public final URI users;

    /**
     * The REST URI for datapath management
     */
    public final URI datapath;

    /**
     * The REST URI for bridge management
     */
    public final URI bridges;

    /**
     * The REST URI for statistics
     */
    public final URI stats;

    /**
     * The REST URI for system management
     */
    public final URI system;

    /**
     * The REST URI for managing physical equipment
     */
    public final URI equipment;

    /**
     * The REST URI for managing application containers
     */
    public final URI containers;

    /**
     * The REST URI for queue profile management
     */
    public final URI queueProfiles;

    /**
     * The REST URI for namespace management
     */
    public final URI netns;

    /**
     * The REST URI for port management
     */
    public final URI ports;

    /**
     * The REST URI for tunnel management
     */
    public final URI tunnels;

    /**
     * REST URIs for unknown components
     */
    public final Map<String, URI> generic = new HashMap<>();

    /**
     * Create a description of a Corsa REST API from a JSON entity.
     * 
     * @param entity the JSON object
     */
    public APIDesc(JSONEntity entity) {
        this(entity.map);
    }

    /**
     * Create a description of a Corsa REST API from a JSON object.
     * 
     * @param root the JSON object
     */
    public APIDesc(JSONObject root) {
        JSONObject links = (JSONObject) root.get("links");
        @SuppressWarnings("unchecked")
        Collection<Map.Entry<String, JSONObject>> entries = links.entrySet();
        for (Map.Entry<String, JSONObject> entry : entries) {
            String key = entry.getKey();
            String value = (String) entry.getValue().get("href");
            URI href = URI.create(value);
            generic.put(key, href);
        }
        users = generic.get("users");
        datapath = generic.get("datapath");
        bridges = generic.get("bridges");
        stats = generic.get("stats");
        system = generic.get("system");
        equipment = generic.get("equipment");
        containers = generic.get("containers");
        queueProfiles = generic.get("queue-profiles");
        netns = generic.get("netns");
        ports = generic.get("ports");
        tunnels = generic.get("tunnels");
    }
}
