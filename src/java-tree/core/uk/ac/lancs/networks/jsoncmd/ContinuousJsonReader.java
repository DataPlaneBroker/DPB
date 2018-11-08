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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.function.Function;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonStructure;

/**
 * Reads multiple JSON entities from a stream. A fresh reader is created
 * for each entity, then a 32-bit integer is read in. A byte array of
 * that size is created, and filled from the input stream. The contents
 * are then interpreted as UTF-8.
 * 
 * @author simpsons
 */
public class ContinuousJsonReader implements JsonReader {
    private final JsonReaderFactory factory =
        Json.createReaderFactory(Collections.emptyMap());
    private final DataInputStream base;

    /**
     * Create a JSON reader for a given stream.
     * 
     * @param base the input stream
     */
    public ContinuousJsonReader(InputStream base) {
        this.base = new DataInputStream(base);
    }

    @Override
    public void close() {
        try {
            base.close();
        } catch (IOException e) {
            throw new JsonException("base", e);
        }
    }

    private <T> T receive(Function<? super JsonReader, T> action) {
        try {
            int size = base.readInt();
            byte[] buf = new byte[size];
            base.readFully(buf);
            try (JsonReader reader =
                factory.createReader(new ByteArrayInputStream(buf),
                                     StandardCharsets.UTF_8)) {
                return action.apply(reader);
            }
        } catch (EOFException ex) {
            return null;
        } catch (IOException ex) {
            throw new JsonException("base", ex);
        }
    }

    @Override
    public JsonStructure read() {
        return receive(JsonReader::read);
    }

    @Override
    public JsonArray readArray() {
        return receive(JsonReader::readArray);
    }

    @Override
    public JsonObject readObject() {
        return receive(JsonReader::readObject);
    }
}
