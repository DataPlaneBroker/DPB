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
import java.util.EnumSet;

import uk.ac.lancs.networks.corsa.rest.TunnelDesc;
import uk.ac.lancs.networks.fabric.TagKind;

/**
 * 
 * 
 * @author simpsons
 */
final class PortInterface implements STaggableInterface {
    final AllPortsInterface base;
    final int port;

    public PortInterface(AllPortsInterface base, int port) {
        this.base = base;
        this.port = port;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((base == null) ? 0 : base.hashCode());
        result = prime * result + port;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        PortInterface other = (PortInterface) obj;
        if (base == null) {
            if (other.base != null) return false;
        } else if (!base.equals(other.base)) return false;
        if (port != other.port) return false;
        return true;
    }

    @Override
    public String toString() {
        return base.toString() + '.' + port;
    }

    @Override
    public CorsaInterface tag(TagKind kind, int label) {
        if (kind == null) kind = TagKind.VLAN_STAG;
        switch (kind) {
        case VLAN_STAG:
            if (label < 0 || label > 4095)
                throw new IllegalArgumentException("illegal VLAN: " + label);
            return new STagInterface(this, label);

        default:
            throw new UnsupportedOperationException("unsupported: " + kind);
        }
    }

    @Override
    public Collection<TagKind> getEncapsulations() {
        return EnumSet.of(TagKind.VLAN_STAG);
    }

    @Override
    public CorsaInterface untag() {
        return base;
    }

    @Override
    public TagKind getTagKind() {
        return TagKind.ENUMERATION;
    }

    @Override
    public int getLabel() {
        return port;
    }

    @Override
    public int getMinimumLabel(TagKind kind) {
        if (kind == null) kind = TagKind.VLAN_STAG;
        switch (kind) {
        case VLAN_STAG:
            return 0;

        default:
            throw new UnsupportedOperationException("unsupported: " + kind);
        }
    }

    @Override
    public int getMaximumLabel(TagKind kind) {
        if (kind == null) kind = TagKind.VLAN_STAG;
        switch (kind) {
        case VLAN_STAG:
            return 4095;

        default:
            throw new UnsupportedOperationException("unsupported: " + kind);
        }
    }

    @Override
    public TagKind getDefaultEncapsulation() {
        return TagKind.VLAN_STAG;
    }

    @Override
    public TunnelDesc configureTunnel(TunnelDesc desc, int label) {
        return base.configureTunnel(desc, port).noInnerVlanId().vlanId(label);
    }

    @Override
    public TagKind getCircuitEncapsulation() {
        return TagKind.VLAN_CTAG;
    }

    @Override
    public int getMinimumCircuitLabel() {
        return 0;
    }

    @Override
    public int getMaximumCircuitLabel() {
        return 4095;
    }
}
