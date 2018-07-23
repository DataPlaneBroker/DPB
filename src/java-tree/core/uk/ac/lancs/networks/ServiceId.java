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
package uk.ac.lancs.networks;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * Identifies a service as a distinct sequence of bytes.
 * 
 * @author simpsons
 */
final class ServiceId {
    private final byte[] nonce;

    private ServiceId(byte[] nonce) {
        if (nonce == null) throw new NullPointerException();
        this.nonce = Arrays.copyOf(nonce, nonce.length);
    }

    /**
     * Get a string representation of this service id.
     * 
     * @return the Base64 representation of this id's bytes
     */
    @Override
    public String toString() {
        return Base64.getEncoder().encodeToString(nonce);
    }

    /**
     * Get the hash code for this service id.
     * 
     * @return the hash code
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(nonce);
        return result;
    }

    /**
     * Test whether this object and another object identify the same
     * service.
     * 
     * @param obj the other object
     * 
     * @return {@code true} if the other object is a {@link ServiceId}
     * for the same byte sequence
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        ServiceId other = (ServiceId) obj;
        if (!Arrays.equals(nonce, other.nonce)) return false;
        return true;
    }

    /**
     * Create a service id from raw bytes.
     * 
     * @param nonce the raw bytes
     * 
     * @return the corresponding service id
     * 
     * @throws NullPointerException if the argument is {@code null}
     */
    public static ServiceId of(byte[] nonce) {
        return new ServiceId(nonce);
    }

    /**
     * Create a service id from a UTF-8-encoded string.
     * 
     * @param text the text to be encoded as UTF-8
     * 
     * @return the corresponding service id
     */
    public static ServiceId of(CharSequence text) {
        return new ServiceId(text.toString()
            .getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Get the length of the service id.
     * 
     * @return the length of the service id in bytes
     */
    public int getLength() {
        return nonce.length;
    }

    /**
     * Write the service id's raw bytes to a stream.
     * 
     * @param out the destination stream
     * 
     * @throws IOException if an I/O error occurs while writing
     */
    public void write(OutputStream out) throws IOException {
        out.write(nonce);
    }

    /**
     * Get a copy of the raw bytes of the service id, with padding
     * either side.
     * 
     * @param prefix the number of spare bytes to allocate before the
     * service id
     * 
     * @param suffix the number of spare bytes to allocate after the
     * service id
     * 
     * @return a new array with the service id's raw bytes between the
     * requested padding
     */
    public byte[] get(int prefix, int suffix) {
        byte[] result = new byte[prefix + nonce.length + suffix];
        System.arraycopy(nonce, 0, result, prefix, nonce.length);
        return result;
    }

    /**
     * Get a copy of the raw bytes of the service id.
     * 
     * @return the raw bytes of the service id
     */
    public byte[] get() {
        return Arrays.copyOf(nonce, nonce.length);
    }

    /**
     * Store the raw bytes of the service id at the start of an array.
     * 
     * @param buf the destination for the bytes
     * 
     * @throws NullPointerException if the destination array is
     * {@code null}
     * 
     * @throws IndexOutOfBoundsException if the destination array is too
     * short
     */
    public void get(byte[] buf) {
        get(buf, 0);
    }

    /**
     * Store the raw bytes of the service id in part of an array.
     * 
     * @param buf the destination for the bytes
     * 
     * @param off the offset into the destination array to write the
     * first byte
     * 
     * @throws NullPointerException if the destination array is
     * {@code null}
     * 
     * @throws IndexOutOfBoundsException if there are insufficient bytes
     * to store the id at the specified position
     */
    public void get(byte[] buf, int off) {
        System.arraycopy(nonce, 0, buf, off, nonce.length);
    }
}
