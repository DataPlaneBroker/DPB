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

package uk.ac.lancs.networks.jsoncmd;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.function.Consumer;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonStructure;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;

/**
 * Writes multiple JSON entities to a stream. A fresh writer is created
 * for each entity, and is written in UTF-8 to a byte array. The size of
 * the array is transmitted as four bytes, then the byte array itself.
 * 
 * @author simpsons
 */
public final class ContinuousJsonWriter implements JsonWriter {
    private final JsonWriterFactory factory =
        Json.createWriterFactory(Collections.emptyMap());
    private final DataOutputStream base;

    /**
     * Create a JSON writer for a given stream.
     * 
     * @param base the output stream
     */
    public ContinuousJsonWriter(OutputStream base) {
        this.base = new DataOutputStream(base);
    }

    /**
     * Close the base stream.
     */
    @Override
    public void close() {
        try {
            base.close();
        } catch (IOException e) {
            throw new JsonException("base", e);
        }
    }

    private void send(Consumer<? super JsonWriter> action) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (JsonWriter writer =
            factory.createWriter(buffer, StandardCharsets.UTF_8)) {
            action.accept(writer);
            writer.close();
        }
        try {
            base.writeInt(buffer.size());
            buffer.writeTo(base);
            base.flush();
        } catch (IOException e) {
            throw new JsonException("base", e);
        }
    }

    /**
     * Write an arbitrary JSON structure to the base stream.
     * 
     * @param value the structure to be written
     */
    @Override
    public void write(JsonStructure value) {
        send(w -> w.write(value));
    }

    /**
     * Write a JSON array to the base stream.
     * 
     * @param array the array to be written
     */
    @Override
    public void writeArray(JsonArray array) {
        send(w -> w.writeArray(array));
    }

    /**
     * Write a JSON object to the base stream.
     * 
     * @param object the object to be written
     */
    @Override
    public void writeObject(JsonObject object) {
        send(w -> w.writeObject(object));
    }
}
