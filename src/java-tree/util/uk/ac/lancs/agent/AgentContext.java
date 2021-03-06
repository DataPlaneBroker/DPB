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
package uk.ac.lancs.agent;

import uk.ac.lancs.config.Configuration;

/**
 * Allows agents to find each other.
 * 
 * <p>
 * Agents are named according to the application that is assembling
 * them. Since an agent receives a {@link Configuration} object as its
 * configuration, applications will typically co-opt a parameter such as
 * <samp>name</samp> in that configuration to define the agent's name.
 * 
 * @author simpsons
 */
public interface AgentContext {
    /**
     * Get a named agent.
     * 
     * @default This implementation passes its argument to
     * {@link #findAgent(String)}, and throws the exception if the
     * result is {@code null}.
     * 
     * @param name the agent's name
     * 
     * @return the requested agent
     * 
     * @throws UnknownAgentException if the requested agent does not
     * exist
     */
    default Agent getAgent(String name) throws UnknownAgentException {
        Agent result = findAgent(name);
        if (result == null) throw new UnknownAgentException(name);
        return result;
    }

    /**
     * Find a named agent without raising an exception if missing.
     * 
     * @param name the agent's name
     * 
     * @return the requested agent, or {@code null} if not found
     */
    Agent findAgent(String name);
}
