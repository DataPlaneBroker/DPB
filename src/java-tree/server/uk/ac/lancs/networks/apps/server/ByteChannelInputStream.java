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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * Provides a stream interface to a readable byte channel. Closing the
 * stream does not close the channel. However, an action can be
 * specified to be taken on close of the stream.
 *
 * @author simpsons
 */
class ByteChannelInputStream extends InputStream {
    private final ReadableByteChannel channel;

    private final Closeable onClose;

    /**
     * Create a stream from a channel.
     * 
     * @param channel the channel whose
     * {@link ReadableByteChannel#read(ByteBuffer)} method is to be
     * invoked
     * 
     * @param onClose an action to take when the stream is closed;
     * invoked at most once; may be {@code null}
     */
    public ByteChannelInputStream(ReadableByteChannel channel,
                                  Closeable onClose) {
        this.channel = channel;
        this.onClose = onClose;
    }

    /**
     * Create a stream from a channel, performing no action on close.
     * 
     * @param channel the channel whose
     * {@link ReadableByteChannel#read(ByteBuffer)} method is to be
     * invoked
     */
    public ByteChannelInputStream(ReadableByteChannel channel) {
        this(channel, null);
    }

    private boolean open = true;

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

    private void checkFault() throws IOException {
        if (!open) throw new IOException("input closed");
    }

    /**
     * Read bytes from the channel into part of an array.
     * 
     * @param b the containing array
     * 
     * @param off the index of the first element of the array to read in
     * to
     * 
     * @param len the maximum number of bytes to read
     * 
     * @return the number of bytes read; or <samp>-1</samp> on
     * end-of-stream
     * 
     * @throws IOException if an I/O error occurs; or the stream has
     * been closed
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        checkFault();
        ByteBuffer buf = ByteBuffer.wrap(b, off, len);
        return channel.read(buf);
    }

    /**
     * Read a single byte from the channel.
     * 
     * @return the byte as a non-negative integer; or <samp>-1</samp> on
     * end-of-stream
     * 
     * @throws IOException if an I/O error occurs; or the stream has
     * been closed
     */
    @Override
    public int read() throws IOException {
        checkFault();
        oneByte.clear();
        int got = channel.read(oneByte);
        if (got < 0) return -1;
        return oneByte.get(0) & 0xff;
    }
}
