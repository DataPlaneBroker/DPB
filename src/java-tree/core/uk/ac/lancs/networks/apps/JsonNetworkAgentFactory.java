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
package uk.ac.lancs.networks.apps;

import java.util.Collection;
import java.util.Collections;

import uk.ac.lancs.agent.Agent;
import uk.ac.lancs.agent.AgentContext;
import uk.ac.lancs.agent.AgentCreationException;
import uk.ac.lancs.agent.AgentFactory;
import uk.ac.lancs.agent.CacheAgent;
import uk.ac.lancs.agent.ServiceCreationException;
import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.networks.NetworkControl;

/**
 * 
 * 
 * @author simpsons
 */
public class JsonNetworkAgentFactory implements AgentFactory {
    public static final String NETWORK_TYPE_NAME = "ssh-network";
    public static final String SWITCH_TYPE_NAME = "ssh-switch";
    public static final String AGGREGATOR_TYPE_NAME = "ssh-aggregator";

    public static final String TYPE_FIELD = "type";

    @Override
    public boolean recognize(Configuration conf) {
        String type = conf.get(TYPE_FIELD);
        switch (type) {
        case NETWORK_TYPE_NAME:
        case SWITCH_TYPE_NAME:
        case AGGREGATOR_TYPE_NAME:
            return true;
        default:
            return false;
        }
    }

    @Override
    public Agent makeAgent(AgentContext ctxt, Configuration conf)
        throws AgentCreationException {
        final String agentType = conf.get(TYPE_FIELD);
        switch (agentType) {
        case NETWORK_TYPE_NAME:
            return new CacheAgent(new Agent() {
                @Override
                public <T> T findService(Class<T> type, String key)
                    throws ServiceCreationException {
                    if (key != null) return null;
                    SSHNetwork result = new SSHNetwork(conf);
                    return type.cast(result);
                }

                @Override
                public Collection<String> getKeys(Class<?> type) {
                    if (type != NetworkControl.class)
                        return Collections.emptySet();
                    return Collections.singleton(null);
                }
            });
        }
        throw new AgentCreationException("unrecognized config");
    }
}
