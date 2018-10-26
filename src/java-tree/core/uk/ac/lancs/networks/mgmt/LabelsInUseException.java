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
package uk.ac.lancs.networks.mgmt;

import java.util.BitSet;

import uk.ac.lancs.networks.Terminal;

/**
 * Indicates that one or more labels are in use, so can't be released or
 * used for another purpose.
 * 
 * @author simpsons
 */
public class LabelsInUseException extends LabelManagementException {
    private static final long serialVersionUID = 1L;

    /**
     * Create an exception with a cause.
     * 
     * @param network the network originating this exception
     * 
     * @param cause the cause
     * 
     * @param terminal the terminal to which this exception pertains
     * 
     * @param labels the labels to which this exception pertains
     */
    public LabelsInUseException(Network network, Terminal terminal,
                               BitSet labels, Throwable cause) {
        super(network, terminal, labels, "labels in use: " + labels, cause);
    }

    /**
     * Create an exception with a cause.
     * 
     * @param network the network originating this exception
     * 
     * @param cause the cause
     * 
     * @param terminal the terminal to which this exception pertains
     * 
     * @param label the label to which this exception pertains
     */
    public LabelsInUseException(Network network, Terminal terminal, int label,
                               Throwable cause) {
        super(network, terminal, label, "label in use: " + label, cause);
    }

    /**
     * Create an exception.
     * 
     * @param network the network originating this exception
     * 
     * @param terminal the terminal to which this exception pertains
     * 
     * @param labels the labels to which this exception pertains
     */
    public LabelsInUseException(Network network, Terminal terminal,
                               BitSet labels) {
        super(network, terminal, labels, "labels in use: " + labels);
    }

    /**
     * Create an exception.
     * 
     * @param network the network originating this exception
     * 
     * @param terminal the terminal to which this exception pertains
     * 
     * @param label the label to which this exception pertains
     */
    public LabelsInUseException(Network network, Terminal terminal,
                               int label) {
        super(network, terminal, label, "label in use: " + label);
    }
}