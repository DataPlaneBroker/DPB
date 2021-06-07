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

package uk.ac.lancs.dpb.tree;

/**
 * Holds an ordered pair of items, matching on identity. The objects'
 * implementations of {@link Object#hashCode()} and
 * {@link Object#equals(Object)} are not invoked to provide the
 * respective methods on this class.
 * 
 * @param <V1> the type of the first item
 * 
 * @param <V2> the type of the second item
 * 
 * @author simpsons
 */
public final class IdentityPair<V1, V2> {
    /**
     * The first item
     */
    public final V1 item1;

    /**
     * The second item
     */
    public final V2 item2;

    /**
     * Create an ordered pair of items, matching on identity.
     * 
     * @param item1 the first item
     * 
     * @param item2 the second item
     */
    public IdentityPair(V1 item1, V2 item2) {
        this.item1 = item1;
        this.item2 = item2;
    }

    /**
     * Get the hash code for this object.
     * 
     * @default This is computed from
     * {@link System#identityHashCode(Object)} applied to each item.
     * 
     * @return the hash code for this object
     */
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + System.identityHashCode(item1);
        hash = 37 * hash + System.identityHashCode(item2);
        return hash;
    }

    /**
     * Test whether another object equals this object.
     * 
     * @param obj the object to test against
     * 
     * @return {@code true} if the other object is also a pair of the
     * same objects; {@code false} otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        final IdentityPair<?, ?> other = (IdentityPair<?, ?>) obj;
        if (this.item1 != other.item1) return false;
        return this.item2 == other.item2;
    }
}
