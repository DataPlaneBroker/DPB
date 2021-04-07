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
package uk.ac.lancs.networks.persist;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;
import uk.ac.lancs.agent.Agent;
import uk.ac.lancs.agent.AgentContext;
import uk.ac.lancs.agent.AgentCreationException;
import uk.ac.lancs.agent.AgentException;
import uk.ac.lancs.agent.AgentFactory;
import uk.ac.lancs.agent.CacheAgent;
import uk.ac.lancs.agent.ServiceCreationException;
import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.networks.CircuitBlocker;
import uk.ac.lancs.networks.fabric.Fabric;
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.networks.mgmt.Switch;
import uk.ac.lancs.networks.util.SequencedExecutor;
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
     * @undocumented
     */
    public static final String TYPE_FIELD = "type";

    /**
     * {@inheritDoc}
     * 
     * @default This implementation recognizes only the string
     * <samp>{@value #TYPE_NAME}</samp> in the field
     * <samp>{@value #TYPE_FIELD}</samp>.
     */
    @Override
    public boolean recognize(Configuration conf) {
        String type = conf.get(TYPE_FIELD);
        return TYPE_NAME.equals(type);
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * The agent presents its {@link PersistentSwitch} as the default
     * services {@link Switch} and {@link Network}.
     * 
     * <p>
     * Configuration consists of the following fields:
     * 
     * <dl>
     * 
     * <dt><samp>name</samp></dt>
     * 
     * <dd>The name of the switch, used to form the fully qualified
     * names of its terminals
     * 
     * <dt><samp>fabric.agent</samp></dt>
     * <dt><samp>fabric.agent.key</samp></dt>
     * 
     * <dd>The name of an agent providing a {@link Fabric} service, and
     * optionally a key within that agent
     * 
     * <dt><samp>fabric.<var>misc</var></samp></dt>
     * 
     * <dd>Other parameters used to configure the fabric
     * 
     * <dt><samp>db.service</samp></dt>
     * 
     * <dd>The URI of the database service
     * 
     * <dt><samp>db.<var>misc</var></samp></dt>
     * 
     * <dd>Fields to be passed when connecting to the database service,
     * e.g., <samp>password</samp>
     * 
     * <p>
     * These are passed as the <code>dbConfig</code> argument to
     * {@link PersistentSwitch#PersistentSwitch(String, Executor, Fabric, java.util.function.Predicate, Configuration)}.
     * 
     * <dt><samp>block.<var>misc</var></samp></dt>
     * 
     * <dd>Fields used to create a {@link CircuitBlocker}, passed as the
     * <samp>blocker</samp> argument to
     * {@link PersistentSwitch#PersistentSwitch(String, Executor, Fabric, java.util.function.Predicate, Configuration)}
     * 
     * </dl>
     */
    @Override
    public Agent makeAgent(AgentContext ctxt, Configuration conf)
        throws AgentCreationException {
        final String switchName = conf.get("name");
        final Configuration dbConf = conf.subview("db");
        final CircuitBlocker blocker =
            new CircuitBlocker(conf.subview("block").toProperties());
        final String agent = conf.get("fabric.agent");
        final String agentKey = conf.get("fabric.agent.key");
        return new CacheAgent(new Agent() {
            @Override
            public Collection<String> getKeys(Class<?> type) {
                if (type == Network.class || type == Switch.class)
                    return Collections.singleton(null);
                return Collections.emptySet();
            }

            @Override
            public <T> T findService(Class<T> type, String key)
                throws ServiceCreationException {
                if (key != null) return null;
                if (type != Network.class && type != Switch.class) return null;
                try {
                    Agent system = ctxt.getAgent("system");
                    Executor sysExecutor = system.getService(Executor.class);
                    SequencedExecutor executor = new SequencedExecutor();
                    sysExecutor.execute(executor);

                    /* Get the fabric. */
                    Agent fabricAgent = ctxt.getAgent(agent);
                    Fabric fabric =
                        fabricAgent.getService(Fabric.class, agentKey);
                    PersistentSwitch result =
                        new PersistentSwitch(switchName, executor, fabric,
                                             blocker::isBlocked, dbConf);
                    return type.cast(result);
                } catch (AgentException | SQLException ex) {
                    throw new ServiceCreationException(ex);
                }
            }
        });
    }
}
