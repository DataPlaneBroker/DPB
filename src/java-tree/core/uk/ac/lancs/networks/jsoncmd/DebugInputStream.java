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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

class DebugInputStream extends FilterInputStream {
    public DebugInputStream(InputStream out) {
        super(out);
    }

    private void encode(int c, StringBuilder into) {
        switch (c) {
        case '\n':
            into.append("\\n");
            return;
        case '\r':
            into.append("\\r");
            return;
        case '\\':
            into.append("\\\\");
            return;
        default:
            if (c < 32 || c >= 127) {
                into.append(String.format("\\x%2X", c));
                return;
            }
            into.append((char) c);
            return;
        }
    }

    @Override
    public synchronized void mark(int readlimit) {
        super.mark(readlimit);
        System.err.printf("Marked for %d bytes%n", readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        System.err.printf("Reset%n");
        super.reset();
    }

    @Override
    public int read() throws IOException {
        int c = super.read();
        if (c < 0) {
            System.err.printf("Recv EOF%n");
        } else {
            StringBuilder msg = new StringBuilder();
            encode(c, msg);
            System.err.printf("Recv 1 byte (%d) [%s]%n", c, msg);
        }
        return c;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int got = super.read(b, off, len);
        if (got < 0) {
            System.err.printf("Recv EOF%n");
        } else {
            StringBuilder msg = new StringBuilder();
            for (int i = 0; i < got; i++)
                encode(b[i + off], msg);
            System.err.printf("Recv %d bytes [%s]%n", got, msg);
        }
        return got;
    }
}
