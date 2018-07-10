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

import uk.ac.lancs.networks.circuits.Bundle;
import uk.ac.lancs.networks.circuits.Circuit;
import uk.ac.lancs.networks.corsa.rest.TunnelDesc;
import uk.ac.lancs.networks.fabric.Interface;
import uk.ac.lancs.networks.fabric.TagKind;

/**
 * Represents the hierarchy of physical and virtual interfaces that can
 * be specified as tunnel attachments to a Corsa VFC. Operations on this
 * interface allow navigation to subinterfaces, representing additional
 * levels of encapsulation, and back again.
 * 
 * @author simpsons
 */
public interface CorsaInterface extends Interface<CorsaInterface> {
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
     * Resolve a label on this interface into a circuit. The end point's
     * interface need not be this interface, and its label need not be
     * the supplied label, but the result must be the canonical
     * equivalent of calling {@link Bundle#circuit(int)} on this
     * interface with the provided label.
     * 
     * @param label the label of the circuit within this interface
     * 
     * @return the resolved circuit
     * 
     * @default This implementation simply calls
     * {@link Bundle#circuit(int)} on itself with the given label.
     */
    default Circuit<? extends Interface<CorsaInterface>> resolve(int label) {
        return this.circuit(label);
    }
}
