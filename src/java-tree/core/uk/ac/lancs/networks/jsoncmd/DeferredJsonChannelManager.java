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

import java.util.function.Supplier;

/**
 * Obtains a base manager only when the first channel is asked for, or
 * when the base refuses to provide more channels.
 * 
 * @author simpsons
 */
public class DeferredJsonChannelManager implements JsonChannelManager {
    private final Supplier<JsonChannelManager> supplier;
    private JsonChannelManager base;

    /**
     * Create a manager that delegates to another that may be created
     * only on first use, or after the current base manager has stopped
     * providing channels.
     * 
     * @param supplier the supplier of the base manager
     */
    public DeferredJsonChannelManager(Supplier<JsonChannelManager> supplier) {
        this.supplier = supplier;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * This implementation invokes the supplier on its first call, and
     * obtains all channels subsequently from the result of that, until
     * it returns {@code null}, and then re-invokes the supplier. If a
     * new supplier yields {@code null} immediately, this method also
     * returns {@code null}.
     */
    @Override
    public JsonChannel getChannel() {
        for (int i = 0; i < 2; i++) {
            synchronized (this) {
                if (base == null) base = supplier.get();
                if (base == null) return null;
                JsonChannel result = base.getChannel();
                if (result == null) {
                    base = null;
                    continue;
                }
                return result;
            }
        }
        return null;
    }
}
