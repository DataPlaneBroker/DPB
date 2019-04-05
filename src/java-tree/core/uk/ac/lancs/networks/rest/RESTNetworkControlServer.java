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
import java.io.StringWriter;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonStructure;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
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
import uk.ac.lancs.rest.RESTContext;
import uk.ac.lancs.rest.RESTField;
import uk.ac.lancs.rest.RESTRegistration;
import uk.ac.lancs.rest.RESTRequestHandlerMapper;

/**
 * Implements a REST API for a network controller. Use
 * {@link #bind(RESTRequestHandlerMapper, String)} to attach it to an
 * HTTP server.
 * 
 * <p>
 * The following requests are defined:
 * 
 * <dl>
 * 
 * <dt><code>GET /services</code>
 * 
 * <dd>Invoke {@link NetworkControl#getServiceIds()}, and yield a JSON
 * array of the service ids.
 * 
 * <dt><code>POST /create-service</code>
 * 
 * <dd>Invoke {@link NetworkControl#newService()}, returning an object
 * with the single field <samp>service-id</samp> containing the new
 * service's id.
 * 
 * <dt><code>POST /service/<var>sid</var>/define</code>
 * 
 * <dd>Invoke {@link Service#define(uk.ac.lancs.networks.Segment)}. The
 * request body must be a JSON object containing an array of objects
 * <samp>segment</samp>. Each object identifies the terminal by name
 * <samp>terminal-name</samp>, the label <samp>label</samp>, the ingress
 * bandwidth <samp>ingress</samp>, and the egress bandwidth
 * <samp>egress</samp>.
 * 
 * <dt><code>POST /service/<var>sid</var>/activate</code>
 * <dt><code>POST /service/<var>sid</var>/deactivate</code>
 * <dt><code>POST /service/<var>sid</var>/release</code>
 * 
 * <dd>Invoke {@link Service#activate()}, {@link Service#deactivate()}
 * or {@link Service#release()} respectively with the given service id.
 * 
 * <dt><code>POST /service/<var>sid</var>/await-status</code>
 * 
 * <dd>Invoke {@link Service#awaitStatus(java.util.Collection, long)}.
 * The request body must contain an array <samp>acceptable</samp>
 * listing acceptable statuses, and an integer
 * <samp>timeout-millis</samp> giving the timeout in milliseconds.
 * 
 * </dl>
 * 
 * @author simpsons
 */
public class RESTNetworkControlServer {
    private final NetworkControl network;
    private static final RESTField<Integer> SID =
        RESTField.ofInt(10).from("sid").done();

    /**
     * Create a REST adaptation of a network controller interface.
     * 
     * @param network the network controller
     */
    public RESTNetworkControlServer(NetworkControl network) {
        this.network = network;
    }

    /**
     * Bind this network controller to a prefix.
     * 
     * @param mapper the mapper consulted by the HTTP server
     * 
     * @param prefix the path prefix, e.g.,
     * <samp>/network/mynet/v1</samp>
     */
    public void bind(RESTRequestHandlerMapper mapper, String prefix) {
        RESTRegistration.start().on("GET").at(prefix + "/services")
            .register(mapper, this::listServices);
        RESTRegistration.start().on("POST")
            .at(prefix + "/service/(?<sid>[0-9]+)/activate").with(SID)
            .register(mapper, this::activateService);
        RESTRegistration.start().on("POST")
            .at(prefix + "/service/(?<sid>[0-9]+)/deactivate").with(SID)
            .register(mapper, this::deactivateService);
        RESTRegistration.start().on("POST")
            .at(prefix + "/service/(?<sid>[0-9]+)/release").with(SID)
            .register(mapper, this::releaseService);
        RESTRegistration.start().on("POST")
            .at(prefix + "/service/(?<sid>[0-9]+)/define").with(SID)
            .register(mapper, this::defineService);
        RESTRegistration.start().on("POST")
            .at(prefix + "/service/(?<sid>[0-9]+)/await-status").with(SID)
            .register(mapper, this::awaitServiceStatus);
        RESTRegistration.start().on("POST").at(prefix + "/create-service")
            .register(mapper, this::createService);
    }

    private void listServices(HttpRequest request, HttpResponse response,
                              HttpContext context, RESTContext restCtxt)
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

    private void activateService(HttpRequest request, HttpResponse response,
                                 HttpContext context, RESTContext restCtxt)
        throws HttpException,
            IOException {
        int sid = restCtxt.get(SID);
        Service srv = network.getService(sid);
        if (srv == null) {
            response.setStatusCode(HttpStatus.SC_NOT_FOUND);
            return;
        }
        srv.activate();
        response.setStatusCode(HttpStatus.SC_NO_CONTENT);
    }

    private void deactivateService(HttpRequest request, HttpResponse response,
                                   HttpContext context, RESTContext restCtxt)
        throws HttpException,
            IOException {
        int sid = restCtxt.get(SID);
        Service srv = network.getService(sid);
        if (srv == null) {
            response.setStatusCode(HttpStatus.SC_NOT_FOUND);
            return;
        }
        srv.deactivate();
        response.setStatusCode(HttpStatus.SC_NO_CONTENT);
    }

    private void releaseService(HttpRequest request, HttpResponse response,
                                HttpContext context, RESTContext restCtxt)
        throws HttpException,
            IOException {
        int sid = restCtxt.get(SID);
        Service srv = network.getService(sid);
        if (srv == null) {
            response.setStatusCode(HttpStatus.SC_NOT_FOUND);
            return;
        }
        srv.release();
        response.setStatusCode(HttpStatus.SC_NO_CONTENT);
    }

    private void setResponseObject(HttpResponse response, JsonStructure rsp) {
        StringWriter out = new StringWriter();
        Json.createWriter(out).write(rsp);
        StringEntity entity =
            new StringEntity(out.toString(), ContentType.APPLICATION_JSON);
        response.setEntity(entity);
    }

    private JsonObject getRequestObject(HttpRequest request,
                                        HttpResponse response)
        throws IOException {
        HttpEntityEnclosingRequest encReq =
            (HttpEntityEnclosingRequest) request;
        HttpEntity reqEnt = encReq.getEntity();
        // TODO: Check content type.
        return Json.createReader(reqEnt.getContent()).readObject();
    }

    private void defineService(HttpRequest request, HttpResponse response,
                               HttpContext context, RESTContext restCtxt)
        throws HttpException,
            IOException {
        int sid = restCtxt.get(SID);
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

    private void createService(HttpRequest request, HttpResponse response,
                               HttpContext context, RESTContext restCtxt)
        throws HttpException,
            IOException {
        Service srv = network.newService();
        JsonObject rsp =
            Json.createObjectBuilder().add("service-id", srv.id()).build();
        setResponseObject(response, rsp);
        response.setStatusCode(HttpStatus.SC_OK);
    }

    private void awaitServiceStatus(HttpRequest request,
                                    HttpResponse response,
                                    HttpContext context, RESTContext restCtxt)
        throws HttpException,
            IOException {
        int sid = restCtxt.get(SID);
        Service srv = network.getService(sid);
        if (srv == null) {
            response.setStatusCode(HttpStatus.SC_NOT_FOUND);
            return;
        }
        JsonObject req = getRequestObject(request, response);
        if (req == null) return;
        EnumSet<ServiceStatus> accepted = EnumSet.noneOf(ServiceStatus.class);
        for (JsonString txt : req.getJsonArray("acceptable")
            .getValuesAs(JsonString.class)) {
            ServiceStatus v = ServiceStatus.valueOf(txt.getString());
            accepted.add(v);
        }
        long timeoutMillis = req.getJsonNumber("timeout-millis").longValue();
        ServiceStatus result = srv.awaitStatus(accepted, timeoutMillis);
        JsonObject rsp = Json.createObjectBuilder()
            .add("status", result.toString()).build();
        setResponseObject(response, rsp);
        response.setStatusCode(HttpStatus.SC_OK);
    }
}
