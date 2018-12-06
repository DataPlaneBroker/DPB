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
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.function.Function;

import uk.ac.lancs.agent.Agent;
import uk.ac.lancs.agent.AgentContext;
import uk.ac.lancs.agent.AgentCreationException;
import uk.ac.lancs.agent.AgentException;
import uk.ac.lancs.agent.AgentFactory;
import uk.ac.lancs.agent.CacheAgent;
import uk.ac.lancs.agent.ServiceCreationException;
import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.mgmt.Aggregator;
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.networks.util.SequencedExecutor;
import uk.ac.lancs.scc.jardeps.Service;

/**
 * Creates agents each presenting a persistent network aggregator.
 * 
 * @author simpsons
 */
@Service(AgentFactory.class)
public class PersistentAggregatorAgentFactory implements AgentFactory {
    /**
     * @undocumented
     */
    public static final String TYPE_NAME = "persistent-aggregator";

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
     * The agent presents its {@link PersistentAggregator} as the
     * default services {@link Aggregator} and {@link Network}.
     */
    @Override
    public Agent makeAgent(AgentContext ctxt, Configuration conf)
        throws AgentCreationException {
        return new CacheAgent(new Agent() {
            @Override
            public Collection<String> getKeys(Class<?> type) {
                if (type == Network.class || type == Aggregator.class)
                    return Collections.singleton(null);
                return Collections.emptySet();
            }

            @Override
            public <T> T findService(Class<T> type, String key)
                throws ServiceCreationException {
                if (key != null) return null;
                if (type != Network.class && type != Aggregator.class)
                    return null;
                try {
                    Agent system = ctxt.getAgent("system");
                    Executor sysExecutor = system.getService(Executor.class);
                    SequencedExecutor executor = new SequencedExecutor();
                    sysExecutor.execute(executor);
                    Function<String, NetworkControl> inferiors = n -> {
                        try {
                            return system.findService(NetworkControl.class,
                                                      n);
                        } catch (ServiceCreationException e) {
                            return null;
                        }
                    };
                    PersistentAggregator agg =
                        new PersistentAggregator(executor, inferiors, conf);
                    return type.cast(agg);
                } catch (SQLException | AgentException ex) {
                    throw new ServiceCreationException(ex);
                }
            }
        });
    }
}
