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
package uk.ac.lancs.networks.transients;

import java.util.Collection;
import java.util.Collections;

import uk.ac.lancs.agent.Agent;
import uk.ac.lancs.agent.AgentContext;
import uk.ac.lancs.agent.AgentCreationException;
import uk.ac.lancs.agent.AgentFactory;
import uk.ac.lancs.agent.CacheAgent;
import uk.ac.lancs.agent.ServiceCreationException;
import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.networks.mgmt.Switch;

/**
 * Creates agents presenting a dummy switch.
 * 
 * @author simpsons
 */
@uk.ac.lancs.scc.jardeps.Service(AgentFactory.class)
public class DummySwitchAgentFactory implements AgentFactory {
    /**
     * @undocumented
     */
    public static final String TYPE_NAME = "dummy-switch";

    /**
     * @undocumented
     */
    public static final String TYPE_FIELD = "type";

    /**
     * {@inheritDoc}
     * 
     * @default This implementation recognizes only
     * <samp>{@value #TYPE_NAME}</samp> in the field
     * <samp>{@value #TYPE_FIELD}</samp>.
     */
    @Override
    public boolean recognize(Configuration conf) {
        String type = conf.get(TYPE_FIELD);
        switch (type) {
        case TYPE_NAME:
            return true;
        default:
            return false;
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @default This agent presents a {@link DummySwitch} as
     * {@link Switch} and {@link Network}.
     */
    @Override
    public Agent makeAgent(AgentContext ctxt, Configuration conf)
        throws AgentCreationException {
        String name = conf.get("name");
        DummySwitch zwitch = new DummySwitch(name);
        return new CacheAgent(new Agent() {
            @Override
            public Collection<String> getKeys(Class<?> type) {
                if (type != Network.class && type != Switch.class)
                    return Collections.emptySet();
                return Collections.singleton(null);
            }

            @Override
            public <T> T findService(Class<T> type, String key)
                throws ServiceCreationException {
                if (key != null) return null;
                if (type != Network.class && type != Switch.class)
                    return null;
                return type.cast(zwitch);
            }
        });
    }
}
