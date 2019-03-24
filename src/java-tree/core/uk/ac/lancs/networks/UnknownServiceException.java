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
package uk.ac.lancs.networks;

/**
 * Indicates that a service identifier was not recognized.
 * 
 * @author simpsons
 */
public class UnknownServiceException extends NetworkControlException {
    private static final long serialVersionUID = 1L;

    private final int serviceId;

    /**
     * Get the id of the service that was unknown.
     * 
     * @return the service id
     */
    public int getServiceId() {
        return serviceId;
    }

    /**
     * Create an exception.
     * 
     * @param control the control interface of the network to which the
     * exception pertains
     * 
     * @param serviceId the service id
     */
    public UnknownServiceException(NetworkControl control, int serviceId) {
        super(control, "unknown service id: " + serviceId);
        this.serviceId = serviceId;
    }

    /**
     * Create an exception with a cause.
     * 
     * @param cause the cause
     * 
     * @param control the control interface of the network to which the
     * exception pertains
     * 
     * @param serviceId the service id
     */
    public UnknownServiceException(NetworkControl control, int serviceId,
                                   Throwable cause) {
        super(control, "unknown service id: " + serviceId, cause);
        this.serviceId = serviceId;
    }
}
