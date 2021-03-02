/*
 * Copyright (c) 2021, Regents of the University of Lancaster
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
 *  Author: Steven Simpson <s.simpson@lancaster.ac.uk>
 */

package uk.ac.lancs.networks;

import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Identifies circuits which should not be connected.
 *
 * @author simpsons
 */
public final class CircuitBlocker {
    private final Map<String, BitSet> blocked;

    /**
     * Create a blocker from a set of circuits identified in properties.
     * Every property with the name <samp><var>tname</var></samp>
     * identifies a terminal called <samp><var>tname</var></samp> (as
     * returned by {@link Terminal#name()}). Its value is a space- or
     * comma-separated list of integer labels.
     * 
     * <p>
     * This method calls {@link #CircuitBlocker(Properties, String)}}
     * with an empty prefix.
     * 
     * @param props the properties specifying circuits to be blocked
     */
    public CircuitBlocker(Properties props) {
        this(props, "");
    }

    /**
     * Create a blocker from a set of circuits identified in properties
     * with a given prefix. Every property with the name
     * <samp><var>prefix</var><var>tname</var></samp> identifies a
     * terminal called <samp><var>tname</var></samp> (as returned by
     * {@link Terminal#name()}). Its value is a space- or
     * comma-separated list of integer labels.
     * 
     * @param props the properties specifying circuits to be blocked
     * 
     * @param prefix the prefix selecting properties identifying
     * circuits
     */
    public CircuitBlocker(Properties props, String prefix) {
        final int plen = prefix.length();
        Map<String, BitSet> blocked = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith(prefix)) continue;
            String vtxt = props.getProperty(key);
            int value = Integer.valueOf(vtxt);
            key = key.substring(plen);
            blocked.computeIfAbsent(key, k -> new BitSet()).set(value);
        }
        this.blocked = Collections.unmodifiableMap(blocked);
    }

    /**
     * Determine whether a circuit belongs to the set of blocked
     * circuits.
     * 
     * @param c the circuit to be tested
     * 
     * @return {@code true} if the circuit is to be blocked;
     * {@code false} otherwise
     * 
     * @see #isClear(Circuit)
     */
    public boolean isBlocked(Circuit c) {
        Terminal t = c.getTerminal();
        BitSet ls = blocked.get(t.name());
        if (ls == null) return false;
        return ls.get(c.getLabel());
    }

    /**
     * Determine whether a circuit is excluded from the set of blocked
     * circuits.
     * 
     * @param c the circuit to be tested
     * 
     * @return {@code false} if the circuit is to be blocked;
     * {@code true} otherwise
     * 
     * @see #isBlocked(Circuit)
     */
    public boolean isClear(Circuit c) {
        return !isBlocked(c);
    }
}
