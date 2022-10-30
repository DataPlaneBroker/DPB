// -*- c-basic-offset: 4; indent-tabs-mode: nil -*-

/*
 * Copyright (c) 2022, Lancaster University
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the
 *   distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *
 *  Author: Steven Simpson <https://github.com/simpsonst>
 */

package uk.ac.lancs.networks.apps.server;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * Provides a stream interface to a writable byte channel. Closing the
 * stream does not close the channel. However, an action can be
 * specified to be taken on close of the stream.
 * 
 * @author simpsons
 */
class ByteChannelOutputStream extends OutputStream {
    private final WritableByteChannel channel;

    private final Closeable onClose;

    /**
     * Create a stream from a channel.
     * 
     * @param channel the channel whose
     * {@link ReadableByteChannel#write(ByteBuffer)} method is to be
     * invoked
     * 
     * @param onClose an action to take when the stream is closed;
     * invoked at most once; may be {@code null}
     */
    public ByteChannelOutputStream(WritableByteChannel channel,
                                   Closeable onClose) {
        this.channel = channel;
        this.onClose = onClose;
    }

    /**
     * Create a stream from a channel, performing no action on close.
     * 
     * @param channel the channel whose
     * {@link ReadableByteChannel#write(ByteBuffer)} method is to be
     * invoked
     */
    public ByteChannelOutputStream(WritableByteChannel channel) {
        this(channel, null);
    }

    private boolean open = true;

    private IOException fault;

    private final ByteBuffer oneByte = ByteBuffer.allocate(1);

    /**
     * Close the stream. On first invocation, the configured on-close
     * action is performed. Subsequent invocations have no effect.
     * 
     * @throws IOException if the configured on-close action throws an
     * exception
     */
    @Override
    public void close() throws IOException {
        if (!open) return;
        open = false;
        if (onClose != null) onClose.close();
    }

    private void writeFully(ByteBuffer buf) throws IOException {
        try {
            while (buf.remaining() > 0)
                channel.write(buf);
        } catch (IOException ex) {
            fault = ex;
            throw fault;
        }
    }

    private void checkFault() throws IOException {
        if (!open) throw new IOException("output closed");
        if (fault != null) throw new IOException("earlier fault", fault);
    }

    /**
     * Write a portion of a byte array to the channel.
     * 
     * @param b the containing array
     * 
     * @param off the index of the first byte to write
     * 
     * @param len the number of bytes to write
     * 
     * @throws IOException if an I/O error occurs; or the stream has
     * been closed
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkFault();
        ByteBuffer buf = ByteBuffer.wrap(b, off, len);
        writeFully(buf);
    }

    /**
     * Write a single byte to the channel.
     * 
     * @param i the value of the byte to write
     * 
     * @throws IOException if an I/O error occurs; or the stream has
     * been closed
     */
    @Override
    public void write(int i) throws IOException {
        checkFault();
        oneByte.clear();
        oneByte.put(0, (byte) i);
        writeFully(oneByte);
    }
}
