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

/**
 * Indicates an error in the management of a specific label range.
 * 
 * @author simpsons
 */
public class LabelManagementException extends TrunkManagementException {
    private static final long serialVersionUID = 1L;

    private final BitSet labels;

    /**
     * Get the labels to which this exception pertains.
     * 
     * @return the label set
     */
    public BitSet getLabels() {
        return labels;
    }

    /**
     * Create an exception with a detail message and a cause.
     * 
     * @param network the network originating this exception
     * 
     * @param message the detail message
     * 
     * @param cause the cause
     * 
     * @param trunk the trunk to which this exception pertains
     * 
     * @param labels the labels to which this exception pertains
     */
    public LabelManagementException(Network network, Trunk trunk,
                                    BitSet labels, String message,
                                    Throwable cause) {
        super(network, trunk, message, cause);
        this.labels = (BitSet) labels.clone();
    }

    private static BitSet ofOne(int label) {
        BitSet result = new BitSet();
        result.set(label);
        return result;
    }

    /**
     * Create an exception with a detail message and a cause.
     * 
     * @param network the network originating this exception
     * 
     * @param message the detail message
     * 
     * @param cause the cause
     * 
     * @param trunk the trunk to which this exception pertains
     * 
     * @param label the label to which this exception pertains
     */
    public LabelManagementException(Network network, Trunk trunk,
                                    int label, String message,
                                    Throwable cause) {
        this(network, trunk, ofOne(label), message, cause);
    }

    /**
     * Create an exception with a detail message.
     * 
     * @param network the network originating this exception
     * 
     * @param message the detail message
     * 
     * @param trunk the trunk to which this exception pertains
     * 
     * @param labels the labels to which this exception pertains
     */
    public LabelManagementException(Network network, Trunk trunk,
                                    BitSet labels, String message) {
        super(network, trunk, message);
        this.labels = (BitSet) labels.clone();
    }

    /**
     * Create an exception with a detail message.
     * 
     * @param network the network originating this exception
     * 
     * @param message the detail message
     * 
     * @param trunk the trunk to which this exception pertains
     * 
     * @param label the label to which this exception pertains
     */
    public LabelManagementException(Network network, Trunk trunk,
                                    int label, String message) {
        this(network, trunk, ofOne(label), message);
    }

    /**
     * Create an exception with a cause.
     * 
     * @param network the network originating this exception
     * 
     * @param cause the cause
     * 
     * @param trunk the trunk to which this exception pertains
     * 
     * @param labels the labels to which this exception pertains
     */
    public LabelManagementException(Network network, Trunk trunk,
                                    BitSet labels, Throwable cause) {
        super(network, trunk, cause);
        this.labels = (BitSet) labels.clone();
    }

    /**
     * Create an exception with a cause.
     * 
     * @param network the network originating this exception
     * 
     * @param cause the cause
     * 
     * @param trunk the trunk to which this exception pertains
     * 
     * @param label the label to which this exception pertains
     */
    public LabelManagementException(Network network, Trunk trunk,
                                    int label, Throwable cause) {
        this(network, trunk, ofOne(label), cause);
    }

    /**
     * Create an exception.
     * 
     * @param network the network originating this exception
     * 
     * @param trunk the trunk to which this exception pertains
     * 
     * @param labels the labels to which this exception pertains
     */
    public LabelManagementException(Network network, Trunk trunk,
                                    BitSet labels) {
        super(network, trunk);
        this.labels = (BitSet) labels.clone();
    }

    /**
     * Create an exception.
     * 
     * @param network the network originating this exception
     * 
     * @param trunk the trunk to which this exception pertains
     * 
     * @param label the label to which this exception pertains
     */
    public LabelManagementException(Network network, Trunk trunk,
                                    int label) {
        this(network, trunk, ofOne(label));
    }
}
