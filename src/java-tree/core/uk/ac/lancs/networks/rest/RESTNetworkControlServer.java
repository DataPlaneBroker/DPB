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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonParsingException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ByteArrayEntity;
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
 * Implements a REST API for a network controller. Use
 * {@link #bind(RESTDispatcher, String)} to attach it to an HTTP server.
 * 
 * <p>
 * The following requests are defined:
 * 
 * <dl>
 * 
 * <dt><code>GET <var>prefix</var>/services</code>
 * 
 * <dd>Invoke {@link NetworkControl#getServiceIds()}, and yield a JSON
 * array of the service ids.
 * 
 * <dt><code>POST <var>prefix</var>/create-service</code>
 * 
 * <dd>Invoke {@link NetworkControl#newService()}, returning an object
 * with the single field <samp>service-id</samp> containing the new
 * service's id.
 * 
 * <dt><code>PUT <var>prefix</var>/service/<var>sid</var>/define</code>
 * <dt><code>POST <var>prefix</var>/service/<var>sid</var>/define</code>
 * 
 * <dd>Invoke {@link Service#define(uk.ac.lancs.networks.Segment)}. The
 * request body must be a JSON object containing an array of objects
 * <samp>segment</samp>. Each object identifies the terminal by name
 * <samp>terminal-name</samp>, the label <samp>label</samp>, the ingress
 * bandwidth <samp>ingress</samp>, and the egress bandwidth
 * <samp>egress</samp>.
 * 
 * <dt><code>POST <var>prefix</var>/service/<var>sid</var>/activate</code>
 * <dt><code>POST <var>prefix</var>/service/<var>sid</var>/deactivate</code>
 * <dt><code>POST <var>prefix</var>/service/<var>sid</var>/release</code>
 * 
 * <dd>Invoke {@link Service#activate()}, {@link Service#deactivate()}
 * or {@link Service#release()} respectively with the given service id.
 * No content is returned on success.
 * 
 * <dt><code>GET <var>prefix</var>/service/<var>sid</var>/await-status?acceptable=<var>status</var>&amp;timeout-millis=<var>num</var></code>
 * <dt><code>POST <var>prefix</var>/service/<var>sid</var>/await-status</code>
 * 
 * <dd>Invoke {@link Service#awaitStatus(java.util.Collection, long)}.
 * The POST request body must contain an array <samp>acceptable</samp>
 * listing acceptable statuses, and an integer
 * <samp>timeout-millis</samp> giving the timeout in milliseconds. The
 * GET request URI must include <samp>timeout-millis</samp>, and any
 * number of <samp>acceptable</samp>s (including none).
 * 
 * </dl>
 * 
 * <p>
 * The <samp><var>prefix</var></samp> is specified in the
 * {@link #bind(RESTDispatcher, String)} call, and should begin with a
 * forward slash but not end in one, for example,
 * <samp>/network/aggregator</samp>.
 * 
 * @author simpsons
 */
public class RESTNetworkControlServer {
    private final NetworkControl network;
    private static final RESTField<Integer> SID =
        RESTField.ofInt().from("sid").done();

    /**
     * Create a REST adaptation of a network controller interface.
     * 
     * @param network the network controller
     */
    public RESTNetworkControlServer(NetworkControl network) {
        this.network = network;
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

    @Route("/services")
    private void listServices(HttpRequest request, HttpResponse response,
                              HttpContext context)
        throws HttpException,
            IOException {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        for (int name : network.getServiceIds())
            builder.add(name);
        JsonObject rsp =
            Json.createObjectBuilder().add("service-ids", builder).build();
        setResponseObject(response, rsp);
        response.setStatusCode(HttpStatus.SC_OK);
    }

    @Method("POST")
    @Route("/service/(?<sid>[0-9]+)/activate")
    private void activateService(HttpRequest request, HttpResponse response,
                                 HttpContext context)
        throws HttpException,
            IOException {
        RESTContext rest = RESTContext.get(context);
        int sid = rest.get(SID);
        Service srv = network.getService(sid);
        if (srv == null) {
            response.setStatusCode(HttpStatus.SC_NOT_FOUND);
            return;
        }
        srv.activate();
        response.setStatusCode(HttpStatus.SC_NO_CONTENT);
    }

    @Method("POST")
    @Route("/service/(?<sid>[0-9]+)/deactivate")
    private void deactivateService(HttpRequest request, HttpResponse response,
                                   HttpContext context)
        throws HttpException,
            IOException {
        RESTContext rest = RESTContext.get(context);
        int sid = rest.get(SID);
        Service srv = network.getService(sid);
        if (srv == null) {
            response.setStatusCode(HttpStatus.SC_NOT_FOUND);
            return;
        }
        srv.deactivate();
        response.setStatusCode(HttpStatus.SC_NO_CONTENT);
    }

    @Method("POST")
    @Route("/service/(?<sid>[0-9]+)/release")
    private void releaseService(HttpRequest request, HttpResponse response,
                                HttpContext context)
        throws HttpException,
            IOException {
        RESTContext rest = RESTContext.get(context);
        int sid = rest.get(SID);
        Service srv = network.getService(sid);
        if (srv == null) {
            response.setStatusCode(HttpStatus.SC_NOT_FOUND);
            return;
        }
        srv.release();
        response.setStatusCode(HttpStatus.SC_NO_CONTENT);
    }

    private void setResponseObject(HttpResponse response, JsonStructure rsp) {
        final JsonWriterFactory factory =
            Json.createWriterFactory(Collections.emptyMap());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (JsonWriter writer =
            factory.createWriter(buffer, StandardCharsets.UTF_8)) {
            writer.write(rsp);
        }
        byte[] buf = buffer.toByteArray();
        HttpEntity entity = new ByteArrayEntity(buf, JSON);
        response.setEntity(entity);
    }

    private static final ContentType JSON =
        ContentType.create("application/json");

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

    @Method("POST")
    @Method("PUT")
    @Route("/service/(?<sid>[0-9]+)/define")
    private void defineService(HttpRequest request, HttpResponse response,
                               HttpContext context)
        throws HttpException,
            IOException {
        RESTContext rest = RESTContext.get(context);
        int sid = rest.get(SID);
        Service srv = network.getService(sid);
        if (srv == null) {
            response.setStatusCode(HttpStatus.SC_NOT_FOUND);
            return;
        }
        JsonObject req = getRequestObject(request, response);
        if (req == null) return;
        JsonArray segmentDesc = req.getJsonArray("segment");
        Map<Circuit, TrafficFlow> parts = new HashMap<>();
        for (JsonObject endPoint : segmentDesc
            .getValuesAs(JsonObject.class)) {
            Terminal term =
                network.getTerminal(endPoint.getString("terminal-name"));
            int label = endPoint.getInt("label");
            Circuit circuit = term.circuit(label);
            double ingress =
                endPoint.getJsonNumber("ingress-bw").doubleValue();
            double egress = endPoint.getJsonNumber("egress-bw").doubleValue();
            TrafficFlow flow = TrafficFlow.of(ingress, egress);
            parts.put(circuit, flow);
        }
        Segment segment = Segment.create(parts);
        try {
            srv.define(segment);
        } catch (InvalidServiceException e) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            return;
        }
        response.setStatusCode(HttpStatus.SC_NO_CONTENT);
    }

    @Method("POST")
    @Route("/create-service")
    private void createService(HttpRequest request, HttpResponse response,
                               HttpContext context)
        throws HttpException,
            IOException {
        Service srv = network.newService();
        JsonObject rsp =
            Json.createObjectBuilder().add("service-id", srv.id()).build();
        setResponseObject(response, rsp);
        response.setStatusCode(HttpStatus.SC_OK);
    }

    private static final ContentType ERROR_TYPE =
        ContentType.create("text/plain", StandardCharsets.UTF_8);

    @Method("GET")
    @Method("POST")
    @Route("/service/(?<sid>[0-9]+)/await-status")
    private void awaitServiceStatus(HttpRequest request,
                                    HttpResponse response,
                                    HttpContext context)
        throws HttpException,
            IOException {
        RESTContext rest = RESTContext.get(context);
        int sid = rest.get(SID);
        Service srv = network.getService(sid);
        if (srv == null) {
            response.setStatusCode(HttpStatus.SC_NOT_FOUND);
            return;
        }
        final Collection<ServiceStatus> accepted;
        final long timeoutMillis;
        if (request.getRequestLine().getMethod().equals("POST")) {
            JsonObject req = getRequestObject(request, response);
            if (req == null) return;
            accepted = EnumSet.noneOf(ServiceStatus.class);
            for (JsonString txt : req.getJsonArray("acceptable")
                .getValuesAs(JsonString.class)) {
                ServiceStatus v = ServiceStatus.valueOf(txt.getString());
                accepted.add(v);
            }
            timeoutMillis = req.getJsonNumber("timeout-millis").longValue();
        } else {
            try {
                accepted = rest.params("acceptable").stream()
                    .map(ServiceStatus::valueOf).collect(Collectors.toSet());
                timeoutMillis = Long.parseLong(rest.param("timeout-millis"));
            } catch (NumberFormatException ex) {
                response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                StringEntity rspEnt =
                    new StringEntity("timeout-millis not integer\n",
                                     ERROR_TYPE);
                response.setEntity(rspEnt);
                return;
            } catch (IllegalArgumentException ex) {
                response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                StringEntity rspEnt = new StringEntity("Bad status in "
                    + rest.params("acceptable") + '\n', ERROR_TYPE);
                response.setEntity(rspEnt);
                return;
            } catch (NullPointerException ex) {
                response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                StringEntity rspEnt =
                    new StringEntity("timeout-millis not specified\n",
                                     ERROR_TYPE);
                response.setEntity(rspEnt);
                return;
            }
        }
        ServiceStatus result = srv.awaitStatus(accepted, timeoutMillis);
        JsonObject rsp = Json.createObjectBuilder()
            .add("status", result.toString()).build();
        setResponseObject(response, rsp);
        response.setStatusCode(HttpStatus.SC_OK);
    }
}
