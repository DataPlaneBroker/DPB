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
import uk.ac.lancs.networks.fabric.Channel;
import uk.ac.lancs.networks.fabric.Interface;

/**
 * Represents the hierarchy of physical and virtual interfaces that can
 * be specified as tunnel attachments to a Corsa VFC. Operations on this
 * interface allow navigation to subinterfaces, representing additional
 * levels of encapsulation, and back again.
 * 
 * @author simpsons
 */
public interface CorsaInterface extends Interface {
    /**
     * Get a more specific interface by tagging.
     * 
     * @param kind the kind of encapsulation, or {@code null} for the
     * default
     * 
     * @param TagKind circuitKind the kind of circuits to create from
     * the subinterface, or {@code null} if all are acceptable
     * 
     * @param label the label to tag with
     * 
     * @return an interface based on this one but tagged with the
     * specified label
     * 
     * @throws NullPointerException if the tag kind is {@code null} and
     * no default encapsulation is available
     * 
     * @throws IllegalArgumentException if the tag is invalid, e.g., the
     * label is out of range
     * 
     * @throws UnsupportedOperationException if the tag kind is invalid
     * 
     * @default The default behaviour is to throw
     * {@link NullPointerException} if the tag kind is {@code null}, or
     * throw {@link UnsupportedOperationException} to report the tag
     * kind as unsupported.
     */
    default CorsaInterface tag(TagKind kind, TagKind circuitKind, int label) {
        if (kind == null) throw new NullPointerException();
        throw new UnsupportedOperationException("unsupported: " + kind);
    }

    /**
     * Get the encapsulations supported by this interface.
     * 
     * @return the set of encapsulations supported by this interface
     * 
     * @default The default behaviour is to return an empty set.
     */
    default Collection<TagKind> getEncapsulations() {
        return Collections.emptySet();
    }

    /**
     * Get the default encapsulation, if available.
     * 
     * @return the default encapsulation, or {@code null} if not defined
     * 
     * @default The default behaviour is to return {@code null}.
     */
    default TagKind getDefaultEncapsulation() {
        return null;
    }

    /**
     * Get the parent interface by removing some implicit tagging.
     * 
     * @return the untagged interface that this one is based on, or
     * {@code null} if there is none
     * 
     * @default The default behaviour is to return {@code null}.
     */
    default CorsaInterface untag() {
        return null;
    }

    /**
     * Get the kind of outer encapsulation applied to define this
     * interface.
     * 
     * @return the outer encapsulation kind, or {@code null} if not
     * encapsulated
     * 
     * @default The default behaviour is to return {@code null}.
     */
    default TagKind getTagKind() {
        return null;
    }

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
     * Get the encapsulation used by circuits.
     * 
     * @return the circuit encapsulation
     */
    TagKind getCircuitEncapsulation();

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
     * 
     * @default The default behaviour is to throw
     * {@link NullPointerException} if the tag kind is {@code null}, or
     * throw {@link UnsupportedOperationException} to report the tag
     * kind as unsupported.
     */
    default int getMinimumLabel(TagKind kind) {
        if (kind == null) throw new NullPointerException();
        throw new UnsupportedOperationException("unsupported: " + kind);
    }

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
     * 
     * @default The default behaviour is to throw
     * {@link NullPointerException} if the tag kind is {@code null}, or
     * throw {@link UnsupportedOperationException} to report the tag
     * kind as unsupported.
     */
    default int getMaximumLabel(TagKind kind) {
        if (kind == null) throw new NullPointerException();
        throw new UnsupportedOperationException("unsupported: " + kind);
    }

    /**
     * Get the minimum valid label for circuits of this interface. This
     * prescribes the acceptable range for
     * {@link Interface#circuit(int)}.
     * 
     * @return the minimum valid label
     */
    int getMinimumCircuitLabel();

    /**
     * Get the maximum valid label for circuits of this interface. This
     * prescribes the acceptable range for
     * {@link Interface#circuit(int)}.
     * 
     * @return the maximum valid label
     */
    int getMaximumCircuitLabel();

    /**
     * Get the encapsulation label used to define this interface with
     * respect to its parent.
     * 
     * @return the encapsulation label, or an unspecified value if not
     * encapsulated
     * 
     * @default The default behaviour is to return {@code -1}.
     */
    default int getLabel() {
        return -1;
    }

    /**
     * Configure a tunnel description to match a label on this
     * interface.
     * 
     * @param desc the description to modify
     * 
     * @param label the label within this interface
     * 
     * @return <code>desc</code>
     * 
     * @throws IndexOutOfBoundsException if the label is outside the
     * range defined by {@link Interface#getMinimumLabel(TagKind)} and
     * {@link Interface#getMaximumCircuitLabel()}
     */
    TunnelDesc configureTunnel(TunnelDesc desc, int label);

    /**
     * Resolve a label on this interface into a circuit. The circuit's
     * interface need not be this interface, and its label need not be
     * the supplied label, but the result must be the canonical
     * equivalent of calling {@link Interface#circuit(int)} on this
     * interface with the provided label.
     * 
     * @param label the label of the circuit within this interface
     * 
     * @return the resolved circuit
     * 
     * @default This implementation simply calls
     * {@link Interface#circuit(int)} on itself with the given label.
     */
    default Channel resolve(int label) {
        return this.channel(label);
    }
}
