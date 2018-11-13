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
package uk.ac.lancs.rest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;

/**
 * Performs basic REST operations to a specified service.
 * 
 * @author simpsons
 */
public class RESTClient {
    /**
     * The root service URI
     */
    protected final URI service;

    /**
     * An authorization string to send with each request
     */
    protected final String authz;

    /**
     * A source of fresh HTTP clients
     */
    protected final Supplier<? extends HttpClient> httpProvider;

    private final JsonReaderFactory readerFactory =
        Json.createReaderFactory(Collections.emptyMap());

    private static final JsonWriterFactory writerFactory =
        Json.createWriterFactory(Collections.emptyMap());

    /**
     * Create a REST client for a given service, using the supplied HTTP
     * clients and authorization.
     * 
     * @param service the root URI (ending in a slash) of the REST API
     * 
     * @param httpProvider a source of fresh HTTP clients
     * 
     * @param authz an authorization string to send with each request,
     * or {@code null} if not required
     */
    protected RESTClient(URI service,
                         Supplier<? extends HttpClient> httpProvider,
                         String authz) {
        this.service = service;
        this.authz = authz;
        this.httpProvider = httpProvider;
    }

    private RESTResponse<JsonStructure> request(HttpUriRequest request)
        throws IOException {
        HttpClient client = httpProvider.get();
        if (authz != null) request.setHeader("Authorization", authz);
        HttpResponse rsp = client.execute(request);
        final int code = rsp.getStatusLine().getStatusCode();
        // System.err.printf("rc=%d%n", code);
        final JsonStructure result;
        HttpEntity ent = rsp.getEntity();
        if (ent == null) {
            result = null;
        } else {
            ByteArrayOutputStream bufOut = new ByteArrayOutputStream();
            ent.getContent().transferTo(bufOut);
            // System.err.printf("rsp: %s%n", bufOut.toString("UTF-8"));
            ByteArrayInputStream bufIn =
                new ByteArrayInputStream(bufOut.toByteArray());
            JsonReader reader =
                readerFactory.createReader(bufIn, StandardCharsets.UTF_8);
            result = reader.read();
        }
        return new RESTResponse<JsonStructure>(code, result);
    }

    /**
     * Perform a GET request on the service.
     * 
     * @param sub the resource within the service
     * 
     * @return the JSON response and code
     * 
     * @throws IOException if an I/O error occurred
     */
    protected RESTResponse<JsonStructure> get(String sub) throws IOException {
        URI location = service.resolve(sub);
        HttpGet request = new HttpGet(location);
        return request(request);
    }

    /**
     * Perform a DELETE request on the service.
     * 
     * @param sub the resource within the service
     * 
     * @return the JSON response and code
     * 
     * @throws IOException if an I/O error occurred
     */
    protected RESTResponse<JsonStructure> delete(String sub)
        throws IOException {
        URI location = service.resolve(sub);
        HttpDelete request = new HttpDelete(location);
        return request(request);
    }

    /**
     * Perform a POST request on the service.
     * 
     * @param sub the resource within the service
     * 
     * @param params named JSON objects that will form the request body
     * 
     * @return the JSON response and code
     * 
     * @throws IOException if an I/O error occurred
     */
    protected RESTResponse<JsonStructure> post(String sub, Map<?, ?> params)
        throws IOException {
        URI location = service.resolve(sub);
        HttpPost request = new HttpPost(location);
        request.setEntity(entityOf(params));
        return request(request);
    }

    /**
     * Perform a PATCH request on the service.
     * 
     * @param sub the resource within the service
     * 
     * @param params a list of patch commands that will form the request
     * body
     * 
     * @return the JSON response and code
     * 
     * @throws IOException if an I/O error occurred
     */
    protected RESTResponse<JsonStructure> patch(String sub, List<?> params)
        throws IOException {
        URI location = service.resolve(sub);
        HttpPatch request = new HttpPatch(location);
        request.setEntity(entityOf(params));
        return request(request);
    }

    private static HttpEntity entityOf(Map<?, ?> params) throws IOException {
        /* Convert the map into a JSON object. */
        JsonObjectBuilder builder = Json.createObjectBuilder();
        for (Map.Entry<?, ?> entry : params.entrySet()) {
            String key = entry.getKey().toString();
            Object obj = entry.getValue();
            if (obj instanceof JsonValue)
                builder.add(key, (JsonValue) obj);
            else if (obj instanceof BigDecimal)
                builder.add(key, (BigDecimal) obj);
            else if (obj instanceof Boolean)
                builder.add(key, (Boolean) obj);
            else if (obj instanceof BigInteger)
                builder.add(key, (BigInteger) obj);
            else if (obj instanceof Long || obj instanceof Integer)
                builder.add(key, ((Number) obj).longValue());
            else if (obj instanceof Float || obj instanceof Double)
                builder.add(key, ((Number) obj).doubleValue());
            else
                builder.add(key, obj.toString());
        }

        /* Convert the JSON object into a string. */
        StringWriter out = new StringWriter();
        try (JsonWriter writer = writerFactory.createWriter(out)) {
            writer.writeObject(builder.build());
        }
        String text = out.toString();

        /* Create an HTTP entity whose content is the string. */
        return EntityBuilder.create()
            .setContentType(ContentType.APPLICATION_JSON).setText(text)
            .build();
    }

    private static HttpEntity entityOf(List<?> params) throws IOException {
        /* Convert the list to a JSON array. */
        JsonArrayBuilder builder = Json.createArrayBuilder();
        for (Object obj : params) {
            if (obj instanceof JsonValue)
                builder.add((JsonValue) obj);
            else if (obj instanceof BigDecimal)
                builder.add((BigDecimal) obj);
            else if (obj instanceof Boolean)
                builder.add((Boolean) obj);
            else if (obj instanceof BigInteger)
                builder.add((BigInteger) obj);
            else if (obj instanceof Long || obj instanceof Integer)
                builder.add(((Number) obj).longValue());
            else if (obj instanceof Float || obj instanceof Double)
                builder.add(((Number) obj).doubleValue());
            else
                builder.add(obj.toString());
        }

        /* Convert the JSON array to a string. */
        StringWriter out = new StringWriter();
        try (JsonWriter writer = writerFactory.createWriter(out)) {
            writer.writeArray(builder.build());
        }

        /* Create an HTTP entity whose content is the string. */
        String text = out.toString();
        return EntityBuilder.create()
            .setContentType(ContentType.APPLICATION_JSON).setText(text)
            .build();
    }
}
