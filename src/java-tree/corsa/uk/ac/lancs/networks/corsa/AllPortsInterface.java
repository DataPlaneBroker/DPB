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
package uk.ac.lancs.networks.corsa;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.function.IntFunction;

import uk.ac.lancs.networks.corsa.rest.TunnelDesc;
import uk.ac.lancs.networks.fabric.TagKind;

/**
 * 
 * 
 * @author simpsons
 */
final class AllPortsInterface implements CorsaInterface {
    private final int minPort;
    private final int maxPort;
    private final String name;
    private final IntFunction<String> mapping;

    public AllPortsInterface(String name, IntFunction<String> mapping,
                             int minPort, int maxPort) {
        this.name = name;
        this.minPort = minPort;
        this.maxPort = maxPort;
        this.mapping = mapping;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result =
            prime * result + ((mapping == null) ? 0 : mapping.hashCode());
        result = prime * result + minPort;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + maxPort;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        AllPortsInterface other = (AllPortsInterface) obj;
        if (mapping == null) {
            if (other.mapping != null) return false;
        } else if (!mapping.equals(other.mapping)) return false;
        if (minPort != other.minPort) return false;
        if (name == null) {
            if (other.name != null) return false;
        } else if (!name.equals(other.name)) return false;
        if (maxPort != other.maxPort) return false;
        return true;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public CorsaInterface tag(TagKind kind, TagKind circuitKind, int label) {
        if (kind == null) kind = TagKind.ENUMERATION;
        switch (kind) {
        case ENUMERATION:
            if (label < 1 || label > maxPort)
                throw new IllegalArgumentException("illegal port: " + label);
            return new PortInterface(this, label,
                                     circuitKind == TagKind.VLAN_STAG_CTAG);

        default:
            throw new UnsupportedOperationException("unsupported: " + kind);
        }
    }

    @Override
    public Collection<TagKind> getEncapsulations() {
        return Collections.unmodifiableSet(EnumSet.of(TagKind.ENUMERATION));
    }

    @Override
    public TagKind getDefaultEncapsulation() {
        return TagKind.ENUMERATION;
    }

    @Override
    public int getMinimumLabel(TagKind kind) {
        if (kind == null) kind = TagKind.ENUMERATION;
        switch (kind) {
        case ENUMERATION:
            return minPort;

        default:
            throw new UnsupportedOperationException("unsupported: " + kind);
        }
    }

    @Override
    public int getMaximumLabel(TagKind kind) {
        if (kind == null) kind = TagKind.ENUMERATION;
        switch (kind) {
        case ENUMERATION:
            return maxPort;

        default:
            throw new UnsupportedOperationException("unsupported: " + kind);
        }
    }

    @Override
    public TunnelDesc configureTunnel(TunnelDesc desc, int label) {
        return desc.port(mapping.apply(label)).noInnerVlanId().noVlanId();
    }

    @Override
    public int getMinimumCircuitLabel() {
        return minPort;
    }

    @Override
    public int getMaximumCircuitLabel() {
        return maxPort;
    }

    @Override
    public TagKind getCircuitEncapsulation() {
        return TagKind.ENUMERATION;
    }
}
