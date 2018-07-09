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

import uk.ac.lancs.networks.corsa.rest.TunnelDesc;

/**
 * 
 * 
 * @author simpsons
 */
final class AllAggregationsInterface implements CorsaInterface {
    final int maxGroups;

    public AllAggregationsInterface(int maxGroups) {
        this.maxGroups = maxGroups;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + maxGroups;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        AllAggregationsInterface other = (AllAggregationsInterface) obj;
        if (maxGroups != other.maxGroups) return false;
        return true;
    }

    @Override
    public String toString() {
        return "lag";
    }

    @Override
    public CorsaInterface tag(TagKind kind, int label) {
        if (kind == null) kind = TagKind.ENUMERATION;
        switch (kind) {
        case ENUMERATION:
            if (label < 1 || label > maxGroups)
                throw new IllegalArgumentException("illegal lag: " + label);
            return new AggregationInterface(this, label);

        default:
            throw new UnsupportedOperationException("unsupported: " + kind);
        }
    }

    @Override
    public Collection<TagKind> getEncapsulations() {
        return Collections.singleton(TagKind.ENUMERATION);
    }

    @Override
    public TagKind getDefaultEncapsulation() {
        return TagKind.ENUMERATION;
    }

    @Override
    public CorsaInterface untag() {
        return null;
    }

    @Override
    public TagKind getTagKind() {
        return null;
    }

    @Override
    public int getMinimumLabel(TagKind kind) {
        if (kind == null) kind = TagKind.ENUMERATION;
        switch (kind) {
        case ENUMERATION:
            return 1;

        default:
            throw new UnsupportedOperationException("unsupported: " + kind);
        }
    }

    @Override
    public int getMaximumLabel(TagKind kind) {
        if (kind == null) kind = TagKind.ENUMERATION;
        switch (kind) {
        case ENUMERATION:
            return maxGroups;

        default:
            throw new UnsupportedOperationException("unsupported: " + kind);
        }
    }

    @Override
    public int getLabel() {
        return 0;
    }

    @Override
    public TunnelDesc configureTunnel(TunnelDesc desc, int label) {
        return desc.port("lag" + label).noInnerVlanId().noVlanId();
    }

    @Override
    public int getMinimumEndPointLabel() {
        return 1;
    }

    @Override
    public int getMaximumEndPointLabel() {
        return maxGroups;
    }

    @Override
    public TagKind getEndPointEncapsulation() {
        return TagKind.ENUMERATION;
    }
}
