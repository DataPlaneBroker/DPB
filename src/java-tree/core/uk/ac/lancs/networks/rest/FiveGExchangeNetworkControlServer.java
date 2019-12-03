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

package uk.ac.lancs.networks.rest;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntFunction;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.stream.JsonParsingException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;

import uk.ac.lancs.networks.Circuit;
import uk.ac.lancs.networks.InvalidServiceException;
import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.Segment;
import uk.ac.lancs.networks.Service;
import uk.ac.lancs.networks.ServiceStatus;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.TrafficFlow;
import uk.ac.lancs.rest.server.RESTContext;
import uk.ac.lancs.rest.server.RESTDispatcher;
import uk.ac.lancs.rest.server.RESTField;
import uk.ac.lancs.rest.server.RESTRegistration;
import uk.ac.lancs.rest.service.Method;
import uk.ac.lancs.rest.service.Route;

/**
 * Implements a REST API for a network controller, intended for calling
 * from the INITIATE 5GExchange. Use
 * {@link #bind(RESTDispatcher, String)} to attach it to an HTTP server
 * under a given prefix, <samp><var>prefix</var></samp>. The prefix
 * should begin with a forward slash but not end in one, for example,
 * <samp>/network/aggregator</samp>.
 * 
 * <p>
 * The following requests are defined:
 * 
 * <dl>
 * 
 * <dt><code>PUT <var>prefix</var>/service/by-handle/<var>uuid</var></code>
 * 
 * <dd>Create a service using the supplied UUID as a handle by calling
 * {@link NetworkControl#newService(String)}. The request body must be a
 * a JSON object containing an array <samp>endpoints</samp>. Each
 * element of the array must be an object with the integer fields
 * <samp>island_switch_port</samp> and
 * <samp>island_service_vlan_id</samp>. The port is treated as a
 * terminal name. This array is used to build a {@link Segment}, and
 * passed to {@link Service#define(Segment)}. The service is then
 * activated with {@link Service#activate()}, and the call returns when
 * the service has reached the active state, or after 30 seconds. A
 * nominal bandwidth of 10Mbps is used.
 * 
 * The HTTP status 409 is returned if the UUID is already in use as a
 * handle; 500 if the service could not be activated within the timeout;
 * 400 if a port does not exist as a terminal; 204 on success.
 * 
 * <dt><code>DELETE <var>prefix</var>/service/by-handle/<var>uuid</var></code>
 * 
 * <dd>Look up the service using the supplied UUID as a handle by
 * calling {@link NetworkControl#getService(String)}. If not found,
 * return 404. Otherwise, release the service, and immediately return
 * 204.
 * 
 * </dl>
 * 
 * @author simpsons
 */
public class FiveGExchangeNetworkControlServer {
    private final IntFunction<String> portMapper;
    private final NetworkControl network;
    private static final RESTField<UUID> UUID_FIELD =
        RESTField.ofUUID().from("uuid").done();

    private static final ContentType ERROR_TYPE =
        ContentType.create("text/plain", StandardCharsets.UTF_8);

    /**
     * Create a REST adaptation of a network controller interface.
     * 
     * @param network the network controller
     */
    public FiveGExchangeNetworkControlServer(NetworkControl network) {
        this(network, Integer::toString);
    }

    /**
     * Create a REST adaptation of a network controller interface, using
     * an arbitrary mapping from port number to terminal name.
     * 
     * @param network the network controller
     * 
     * @param portMapper the mapping function from an integer port
     * number to a terminal name
     */
    public FiveGExchangeNetworkControlServer(NetworkControl network,
                                             IntFunction<String> portMapper) {
        this.network = network;
        this.portMapper = portMapper;
    }

    /**
     * Bind this network controller to a prefix in a dispatcher.
     * 
     * @param dispatcher the dispatcher consulted by the HTTP server
     * 
     * @param prefix the path prefix, e.g.,
     * <samp>/network/mynet/v1</samp>
     */
    public void bind(RESTDispatcher dispatcher, String prefix) {
        RESTRegistration reg = new RESTRegistration();
        try {
            reg.record(this, prefix);
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new UnsupportedOperationException("unimplemented", e);
        }
        reg.register(dispatcher);
    }

    @Route("/service/by-handle/(?<uuid>(?:[0-9a-f]{8})-(?:[0-9a-f]{4})"
        + "-(?:[0-9a-f]{4})-(?:[0-9a-f]{4})-(?:[0-9a-f]{12}))")
    @Method("PUT")
    private void establish(HttpRequest request, HttpResponse response,
                           HttpContext context)
        throws IOException {
        RESTContext rest = RESTContext.get(context);
        UUID uuid = rest.get(UUID_FIELD);

        JsonObject req = getRequestObject(request, response);
        System.err.printf("request: %s%n", req);
        JsonArray segemntDesc = req.getJsonArray("endpoints");
        Map<Circuit, TrafficFlow> parts = new HashMap<>();
        for (JsonObject endPoint : segemntDesc
            .getValuesAs(JsonObject.class)) {
            final int port = endPoint.getInt("island_switch_port");
            final int vlan = endPoint.getInt("island_service_vlan_id");
            final Terminal term = network.getTerminal(portMapper.apply(port));
            if (term == null) {
                response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                return;
            }
            final Circuit circuit = term.circuit(vlan);
            /* TODO: Get bandwidth(s) from the request. */
            TrafficFlow flow = TrafficFlow.of(10.0, 10.0);
            parts.put(circuit, flow);
        }
        Segment segment = Segment.create(parts);

        /* Create the service, define end points, activate it, and wait
         * for it to complete activation. */
        Service srv = network.newService(uuid.toString());
        if (srv == null) {
            response.setStatusCode(HttpStatus.SC_CONFLICT);
            return;
        }
        try {
            srv.define(segment);
            srv.activate();
            ServiceStatus st =
                srv.awaitStatus(Collections.singleton(ServiceStatus.ACTIVE),
                                30 * 1000);
            if (st != ServiceStatus.ACTIVE) {
                response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                return;
            }
            srv = null;
        } catch (InvalidServiceException e) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            return;
        } finally {
            if (srv != null) srv.release();
        }
        response.setStatusCode(HttpStatus.SC_NO_CONTENT);
    }

    @Route("/service/by-handle/(?<uuid>(?:[0-9a-f]{8})-(?:[0-9a-f]{4})"
        + "-(?:[0-9a-f]{4})-(?:[0-9a-f]{4})-(?:[0-9a-f]{12}))")
    @Method("DELETE")
    private void release(HttpRequest request, HttpResponse response,
                         HttpContext context) {
        RESTContext rest = RESTContext.get(context);
        UUID uuid = rest.get(UUID_FIELD);
        Service srv = network.getService(uuid.toString());
        if (srv == null) {
            response.setStatusCode(HttpStatus.SC_NOT_FOUND);
            return;
        }
        srv.release();
        response.setStatusCode(HttpStatus.SC_NO_CONTENT);
    }

    private JsonObject getRequestObject(HttpRequest request,
                                        HttpResponse response)
        throws IOException {
        HttpEntityEnclosingRequest encReq =
            (HttpEntityEnclosingRequest) request;
        HttpEntity reqEnt = encReq.getEntity();
        // TODO: Check content type.

        try {
            final JsonReaderFactory factory =
                Json.createReaderFactory(Collections.emptyMap());
            final JsonReader jr;
            if (false) {
                StringWriter msg = new StringWriter();
                try (Reader in =
                    new InputStreamReader(reqEnt.getContent(),
                                          StandardCharsets.UTF_8)) {
                    char[] buf = new char[1024];
                    int got;
                    while ((got = in.read(buf)) > 0)
                        msg.write(buf, 0, got);
                }
                System.err.printf("Message: %s%n", msg.toString());
                jr = factory.createReader(new StringReader(msg.toString()));
            } else {
                jr = factory.createReader(reqEnt.getContent());
            }
            return jr.readObject();
        } catch (JsonParsingException ex) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            StringEntity rspEnt =
                new StringEntity("bad JSON request\n", ERROR_TYPE);
            response.setEntity(rspEnt);
            return null;
        }
    }
}