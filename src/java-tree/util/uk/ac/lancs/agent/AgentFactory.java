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
package uk.ac.lancs.agent;

import uk.ac.lancs.config.Configuration;

/**
 * Creates agents from configuration.
 * 
 * @author simpsons
 */
public interface AgentFactory {
    /**
     * Detect whether this factory can create an agent base on its
     * textual configuration.
     * 
     * @default A simple practice would be to look for a well-known
     * configuration parameter (e.g., <samp>type</samp>), and check
     * whether it is set to a well-known value.
     * 
     * @param conf the agent's textual configuration
     * 
     * @return {@code true} iff this factory can create agents with the
     * supplied configuration
     */
    boolean recognize(Configuration conf);

    /**
     * Create an agent with given context and configuration.
     * 
     * @param ctxt the run-time context for the agent, used to look up
     * other resources
     * 
     * @param conf the agent's textual configuration
     * 
     * @return the new agent
     * 
     * @throws AgentCreationException if there was a problem in creating
     * the agent
     */
    Agent makeAgent(AgentContext ctxt, Configuration conf)
        throws AgentCreationException;
}
