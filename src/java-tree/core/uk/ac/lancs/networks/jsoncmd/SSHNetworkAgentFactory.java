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
package uk.ac.lancs.networks.jsoncmd;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;

import uk.ac.lancs.agent.Agent;
import uk.ac.lancs.agent.AgentContext;
import uk.ac.lancs.agent.AgentCreationException;
import uk.ac.lancs.agent.AgentFactory;
import uk.ac.lancs.agent.CacheAgent;
import uk.ac.lancs.agent.ServiceCreationException;
import uk.ac.lancs.agent.UnknownAgentException;
import uk.ac.lancs.agent.UnknownServiceException;
import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.networks.mgmt.Aggregator;
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.networks.mgmt.Switch;

/**
 * Creates agents presenting a remote network, switch or aggregator.
 * 
 * @author simpsons
 */
@uk.ac.lancs.scc.jardeps.Service(AgentFactory.class)
public class SSHNetworkAgentFactory implements AgentFactory {
    /**
     * @undocumented
     */
    public static final String NETWORK_TYPE_NAME = "ssh-network";

    /**
     * @undocumented
     */
    public static final String SWITCH_TYPE_NAME = "ssh-switch";

    /**
     * @undocumented
     */
    public static final String AGGREGATOR_TYPE_NAME = "ssh-aggregator";

    /**
     * @undocumented
     */
    public static final String TYPE_FIELD = "type";

    /**
     * {@inheritDoc}
     * 
     * @default This implementation recognizes only the strings
     * <samp>{@value #NETWORK_TYPE_NAME}</samp>,
     * <samp>{@value #SWITCH_TYPE_NAME}</samp> and
     * <samp>{@value #AGGREGATOR_TYPE_NAME}</samp> in the field
     * <samp>{@value #TYPE_FIELD}</samp>.
     */
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

    /**
     * {@inheritDoc}
     * 
     * @default This agent presents either a {@link JsonAggregator} (as
     * {@link Aggregator} and {@link Network}), {@link JsonSwitch} (as
     * {@link Switch} and {@link Network}) or {@link JsonNetwork} (as
     * just {@link Network}) according to the value of
     * <samp>{@value #TYPE_FIELD}</samp> being
     * <samp>{@value #AGGREGATOR_TYPE_NAME}</samp>,
     * <samp>{@value #SWITCH_TYPE_NAME}</samp> or
     * <samp>{@value #NETWORK_TYPE_NAME}</samp>.
     */
    @Override
    public Agent makeAgent(AgentContext ctxt, Configuration conf)
        throws AgentCreationException {
        /* Extract configuration. */
        Configuration sshConf = conf.subview("ssh");
        String name = conf.get("name", sshConf.get("network"));

        /* Create channels that invoke SSH to the remote account, then
         * make each fresh channel select a particular network, then
         * pool open channels. */
        JsonChannelManager sshChannels =
            new SSHJsonChannelManager(name, sshConf);
        JsonChannelManager pool = new PoolingJsonChannelManager(sshChannels);
        JsonChannelManager cm = new DeferredJsonChannelManager(() -> {
            System.err.printf("Getting new multiplexer...%n");
            MultiplexingJsonClient result =
                new MultiplexingJsonClient(pool.getChannel(), true);
            result.initiate();
            return result;
        });

        /* Create an agent according to the particular type. */
        try {
            Agent system = ctxt.getAgent("system");
            Executor executor = system.getService(Executor.class);
            final String agentType = conf.get(TYPE_FIELD);
            switch (agentType) {
            case NETWORK_TYPE_NAME:
                return new CacheAgent(new Agent() {
                    @Override
                    public <T> T findService(Class<T> type, String key)
                        throws ServiceCreationException {
                        if (key != null) return null;
                        if (type != Network.class) return null;
                        Network result = new JsonNetwork(name, executor, cm);
                        return type.cast(result);
                    }

                    @Override
                    public Collection<String> getKeys(Class<?> type) {
                        if (type != Network.class)
                            return Collections.emptySet();
                        return Collections.singleton(null);
                    }
                });

            case SWITCH_TYPE_NAME:
                return new CacheAgent(new Agent() {
                    @Override
                    public <T> T findService(Class<T> type, String key)
                        throws ServiceCreationException {
                        if (key != null) return null;
                        if (type != Network.class && type != Switch.class)
                            return null;
                        Network result = new JsonSwitch(name, executor, cm);
                        return type.cast(result);
                    }

                    @Override
                    public Collection<String> getKeys(Class<?> type) {
                        if (type != Network.class && type != Switch.class)
                            return Collections.emptySet();
                        return Collections.singleton(null);
                    }
                });

            case AGGREGATOR_TYPE_NAME:
                return new CacheAgent(new Agent() {
                    @Override
                    public <T> T findService(Class<T> type, String key)
                        throws ServiceCreationException {
                        if (key != null) return null;
                        if (type != Network.class && type != Aggregator.class)
                            return null;
                        Network result =
                            new JsonAggregator(name, executor, cm);
                        return type.cast(result);
                    }

                    @Override
                    public Collection<String> getKeys(Class<?> type) {
                        if (type != Network.class && type != Aggregator.class)
                            return Collections.emptySet();
                        return Collections.singleton(null);
                    }
                });

            default:
                throw new IllegalArgumentException("unknown type "
                    + agentType);
            }
        } catch (UnknownAgentException | ServiceCreationException
            | UnknownServiceException ex) {
            throw new AgentCreationException(ex);
        }
    }
}
