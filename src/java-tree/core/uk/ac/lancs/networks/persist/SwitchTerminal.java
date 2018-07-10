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
package uk.ac.lancs.networks.persist;

import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.circuits.Circuit;
import uk.ac.lancs.networks.fabric.Interface;

/**
 * @summary A terminal of a switch, mapping to a fabric interface
 * 
 * @author simpsons
 */
final class SwitchTerminal implements Terminal {
    private final NetworkControl network;
    private final String name;
    private final Interface<?> fabricInterface;
    private final int dbid;

    SwitchTerminal(NetworkControl network, String name,
                   Interface<?> fabricInterface, int dbid) {
        if (network == null) throw new NullPointerException("network");
        if (name == null) throw new NullPointerException("name");
        if (fabricInterface == null)
            throw new NullPointerException("fabricInterface");
        this.network = network;
        this.name = name;
        this.fabricInterface = fabricInterface;
        this.dbid = dbid;
    }

    Circuit<? extends Interface<?>> getInnerEndPoint(int label) {
        return fabricInterface.circuit(label);
    }

    @Override
    public NetworkControl getNetwork() {
        return network;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + dbid;
        result = prime * result + network.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        SwitchTerminal other = (SwitchTerminal) obj;
        if (dbid != other.dbid) return false;
        if (!network.equals(other.network)) return false;
        return true;
    }

    @Override
    public String name() {
        return name;
    }

    int id() {
        return dbid;
    }

    @Override
    public String toString() {
        return network.name() + ':' + name;
    }
}
