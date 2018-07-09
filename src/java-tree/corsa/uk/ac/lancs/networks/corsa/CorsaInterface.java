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

import uk.ac.lancs.networks.corsa.rest.TunnelDesc;
import uk.ac.lancs.networks.end_points.EndPoint;
import uk.ac.lancs.networks.fabric.Interface;

/**
 * 
 * 
 * @author simpsons
 */
interface CorsaInterface extends Interface {
    /**
     * Get a more specific interface by tagging.
     * 
     * @param kind the kind of encapsulation, or {@code null} for the
     * default
     * 
     * @param label the label to tag with
     * 
     * @return an interface based on this one but tagged with the
     * specified label
     * 
     * @throws NullPointerException if the tag kind is {@code null} and
     * no default encapsulation is available
     * 
     * @throws IllegalArgumentException if the tag is invalid
     * 
     * @throws UnsupportedOperationException if the tag kind is invalid
     */
    CorsaInterface tag(TagKind kind, int label);

    /**
     * Get the encapsulations supported by this interface.
     * 
     * @return the set of encapsulations supported by this interface
     */
    Collection<TagKind> getEncapsulations();

    /**
     * Get the default encapsulation, if available.
     * 
     * @return the default encapsulation, or {@code null} if not defined
     */
    TagKind getDefaultEncapsulation();

    /**
     * Get the parent interface by removing some implicit tagging.
     * 
     * @return the untagged interface that this one is based on, or
     * {@code null} if there is none
     */
    CorsaInterface untag();

    /**
     * Get the kind of outer encapsulation applied to define this
     * interface.
     * 
     * @return the outer encapsulation kind, or {@code null} if not
     * encapsulated
     */
    TagKind getTagKind();

    /**
     * Determine whether a kind of encapsulation is supported.
     * 
     * @default The default implementation consults
     * {@link #getEncapsulations()}.
     * 
     * @param kind the encapsulation kind to be tested
     * 
     * @return {@code true} if the encapsulation is supported
     */
    default boolean isSupported(TagKind kind) {
        return getEncapsulations().contains(kind);
    }

    /**
     * Get the encapsulation used by end points. This is applied by
     * {@link #configureTunnel(TunnelDesc, int)}.
     * 
     * @return the end-point encapsulation
     */
    TagKind getEndPointEncapsulation();

    /**
     * Get the minimum valid label for a kind of encapsulation.
     * 
     * @param kind the kind of encapsulation, or {@code null} for the
     * default
     * 
     * @return the minimum valid label
     * 
     * @throws NullPointerException if the tag kind is {@code null} and
     * no default encapsulation is available
     * 
     * @throws UnsupportedOperationException if the tag kind is invalid
     */
    int getMinimumLabel(TagKind kind);

    /**
     * Get the maximum valid label for a kind of encapsulation.
     * 
     * @param kind the kind of encapsulation, or {@code null} for the
     * default
     * 
     * @return the maximum valid label
     * 
     * @throws NullPointerException if the tag kind is {@code null} and
     * no default encapsulation is available
     * 
     * @throws UnsupportedOperationException if the tag kind is invalid
     */
    int getMaximumLabel(TagKind kind);

    /**
     * Get the minimum valid label for end points of this interface.
     * This prescribes the acceptable range for
     * {@link #configureTunnel(TunnelDesc, int)}.
     * 
     * @return the minimum valid label
     */
    int getMinimumEndPointLabel();

    /**
     * Get the maximum valid label for end points of this interface.
     * This prescribes the acceptable range for
     * {@link #configureTunnel(TunnelDesc, int)}.
     * 
     * @return the maximum valid label
     */
    int getMaximumEndPointLabel();

    /**
     * Get the encapsulation label used to define this interface.
     * 
     * @return the encapsulation label, or an unspecified value if not
     * encapsulated
     */
    int getLabel();

    /**
     * Configure the tunnel description to match a label on this
     * interface.
     * 
     * @param desc the description to modify
     * 
     * @param label the label within this interface
     * 
     * @return <code>desc</code>
     * 
     * @throws IndexOutOfBoundsException if the label is outside the
     * range defined by {@link #getMinimumLabel(TagKind)} and
     * {@link #getMaximumEndPointLabel()}
     */
    TunnelDesc configureTunnel(TunnelDesc desc, int label);

    /**
     * Resolve a label on this interface into an end point. The end
     * point's interface need not be this interface, and its label need
     * not be the supplied label, but the result must be the canonical
     * equivalent.
     * 
     * @param label the label of the end point within this interface
     * 
     * @return the resolved end point
     * 
     * @default This implementation simply calls
     * {@link Bundle#getEndPoint(int)} on itself with the given label.
     */
    default EndPoint<Interface> resolve(int label) {
        return this.getEndPoint(label);
    }
}
