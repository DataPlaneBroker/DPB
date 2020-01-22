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

import java.util.Map;

import uk.ac.lancs.networks.Terminal;

/**
 * @resume A network switch which allows terminals to be added and
 * mapped to a fabric interface
 * 
 * @author simpsons
 */
public interface Switch extends Network {
    /**
     * Add a terminal mapping to an internal resource.
     * 
     * @param terminalName the new terminal's name
     * 
     * @param interfaceName the name of the fabric interface the
     * terminal maps to
     * 
     * @return the new terminal
     * 
     * @throws TerminalExistsException if a terminal with the proposed
     * name already exists
     * 
     * @throws TerminalConfigurationException if the configuration is
     * invalid
     * 
     * @throws TerminalNameException if the proposed terminal name is
     * invalid in some other way
     */
    Terminal addTerminal(String terminalName, String interfaceName)
        throws TerminalNameException,
            TerminalExistsException,
            TerminalConfigurationException;

    /**
     * Get a mapping from all terminals to their interface
     * configurations.
     * 
     * @return a set of all terminals and their interface mappings
     */
    Map<Terminal, String> getTerminals();

    /**
     * Set or adjust bandwidth allocations on a terminal.
     * 
     * @param terminalName the name of the terminal whose quota is to be
     * adjusted
     * 
     * @param setIngress {@code false} if the ingress bandwidth should
     * be adjusted; {@code true} if it should be set to an absolute
     * value
     * 
     * @param setEgress {@code false} if the egress bandwidth should be
     * adjusted; {@code true} if it should be set to an absolute value
     * 
     * @param ingress the new ingress bandwidth (or {@code null} to
     * disable), or its adjustment (or {@code null} for no change)
     * 
     * @param egress the new egress bandwidth (or {@code null} to
     * disable), or its adjustment (or {@code null} for no change)
     * 
     * @throws UnknownTerminalException if the terminal does not exist
     * 
     * @throws IllegalArgumentException if a new bandwidth setting would
     * be negative
     */
    void modifyBandwidth(String terminalName, boolean setIngress,
                         Double ingress, boolean setEgress, Double egress)
        throws UnknownTerminalException;
}
