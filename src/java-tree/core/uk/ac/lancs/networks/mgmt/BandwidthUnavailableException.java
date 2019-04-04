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
package uk.ac.lancs.networks.mgmt;

/**
 * Indicates that an attempt was made to withdraw bandwidth that did not
 * remain available.
 * 
 * @author simpsons
 */
public class BandwidthUnavailableException extends TrunkManagementException {
    private static final long serialVersionUID = 1L;

    private final boolean upstream;

    private final double available;

    /**
     * Determine whether an attempt failed to withdraw upstream
     * bandwidth.
     * 
     * @return {@code true} if upstream bandwidth was unavailable
     */
    public boolean isUpstream() {
        return upstream;
    }

    /**
     * Get the actual amount of bandwidth available in the affected
     * direction.
     * 
     * @return the available bandwidth
     */
    public double getAvailable() {
        return available;
    }

    /**
     * Create an exception.
     * 
     * @param networkName the name of the network originating this
     * exception
     * 
     * @param startTerminal the identity of the start terminal of the
     * trunk
     * 
     * @param endTerminal the identity of the end terminal of the trunk
     * 
     * @param upstream if upstream bandwidth was available
     * 
     * @param available the amount of available bandwidth
     */
    public BandwidthUnavailableException(String networkName,
                                         TerminalId startTerminal,
                                         TerminalId endTerminal,
                                         boolean upstream, double available) {
        super(networkName, startTerminal, endTerminal,
              "bandwidth unavailable: " + available
                  + (upstream ? "up" : "down") + "stream");
        this.upstream = upstream;
        this.available = available;
    }

    /**
     * Create an exception with a cause.
     * 
     * @param cause the cause
     * 
     * @param networkName the name of the network originating this
     * exception
     * 
     * @param startTerminal the identity of the start terminal of the
     * trunk
     * 
     * @param endTerminal the identity of the end terminal of the trunk
     * 
     * @param upstream if upstream bandwidth was available
     * 
     * @param available the amount of available bandwidth
     */
    public BandwidthUnavailableException(String networkName,
                                         TerminalId startTerminal,
                                         TerminalId endTerminal,
                                         boolean upstream, double available,
                                         Throwable cause) {
        super(networkName, startTerminal, endTerminal,
              "bandwidth unavailable: " + available
                  + (upstream ? "up" : "down") + "stream",
              cause);
        this.upstream = upstream;
        this.available = available;
    }
}
