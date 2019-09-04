/*
 * Copyright 2018,2019, Regents of the University of Lancaster
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

package uk.ac.lancs.networks.services;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * Describes an asynchronous fault in establishing a network service.
 * 
 * @author simpsons
 */
public final class Fault {
    private final String formal, reason;
    private final Collection<Fault> causes;

    /**
     * Get the human-readable explanation of the fault.
     * 
     * @return the explanation
     */
    public String reason() {
        return reason;
    }

    /**
     * Get the formal detail of the fault.
     * 
     * @return the formal detail
     */
    public String formal() {
        return formal;
    }

    /**
     * Get an unmodifiable collection of the causes of this fault.
     * 
     * @return the causes of this fault
     */
    public Collection<? extends Fault> causes() {
        return causes;
    }

    /**
     * Express a fault.
     * 
     * @param formal a formal detail of the fault
     * 
     * @param reason a human-readable explanation of the fault
     * 
     * @param causes the set of causes of this fault
     */
    public Fault(String formal, String reason,
                 Collection<? extends Fault> causes) {
        this.formal = formal;
        this.reason = reason;
        this.causes =
            Collections.unmodifiableCollection(new HashSet<>(causes));
    }

    /**
     * Express a fault.
     * 
     * @param formal a formal detail of the fault
     * 
     * @param reason a human-readable explanation of the fault
     * 
     * @param causes the set of causes for this fault
     */
    public Fault(String formal, String reason, Fault... causes) {
        this(formal, reason, Arrays.asList(causes));
    }
}
