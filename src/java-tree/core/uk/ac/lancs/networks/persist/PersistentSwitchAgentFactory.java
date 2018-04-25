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
package uk.ac.lancs.networks.persist;

import java.sql.SQLException;
import java.util.concurrent.Executor;

import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.networks.fabric.Fabric;
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.networks.mgmt.Switch;
import uk.ac.lancs.networks.util.agent.Agent;
import uk.ac.lancs.networks.util.agent.AgentBuilder;
import uk.ac.lancs.networks.util.agent.AgentContext;
import uk.ac.lancs.networks.util.agent.AgentCreationException;
import uk.ac.lancs.networks.util.agent.AgentException;
import uk.ac.lancs.networks.util.agent.AgentFactory;
import uk.ac.lancs.networks.util.agent.AgentInitiationException;
import uk.ac.lancs.scc.jardeps.Service;

/**
 * Creates persistent network switches as agents.
 * 
 * @see PersistentSwitch
 * 
 * @author simpsons
 */
@Service(AgentFactory.class)
public class PersistentSwitchAgentFactory implements AgentFactory {
    /**
     * @undocumented
     */
    public static final String TYPE_NAME = "persistent-switch";

    /**
     * {@inheritDoc}
     * 
     * <p>
     * This implementation recognizes only the string
     * <samp>{@value #TYPE_NAME}</samp>.
     */
    @Override
    public boolean recognize(String type) {
        return TYPE_NAME.equals(type);
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * The agent presents its {@link PersistentSwitch} as the default
     * services {@link Switch} and {@link Network}.
     */
    @Override
    public Agent makeAgent(AgentContext ctxt, Configuration conf)
        throws AgentCreationException {
        try {
            Agent system = ctxt.getAgent("system");
            Executor executor = system.getService(Executor.class);
            PersistentSwitch result =
                new PersistentSwitch(conf.get("name"), executor,
                                     conf.subview("db"));

            return AgentBuilder.start().add(result, Network.class)
                .add(result, Switch.class).create(() -> {
                    try {
                        /* Get the fabric. */
                        String agent = conf.get("fabric.agent");
                        String key = conf.get("fabric.agent.key");
                        Agent fabricAgent = ctxt.getAgent(agent);
                        Fabric fabric =
                            fabricAgent.getService(Fabric.class, key);
                        result.init(fabric);
                    } catch (AgentException e) {
                        throw new AgentInitiationException(e);
                    } catch (SQLException e) {
                        throw new AgentInitiationException("DB error", e);
                    }
                });
        } catch (AgentException ex) {
            throw new AgentCreationException(TYPE_NAME, ex);
        }
    }

}
