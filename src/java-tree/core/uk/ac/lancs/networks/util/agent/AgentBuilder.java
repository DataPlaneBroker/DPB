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
package uk.ac.lancs.networks.util.agent;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates agents with static information.
 * 
 * @author simpsons
 */
public final class AgentBuilder {
    private Map<Class<?>, Map<String, Object>> services = new HashMap<>();

    private AgentBuilder() {}

    /**
     * Start building an agent.
     * 
     * @return a builder to which services can be added
     */
    public static AgentBuilder start() {
        return new AgentBuilder();
    }

    /**
     * Add a service under a specific type and key.
     * 
     * @param service the service to be added
     * 
     * @param type the service type
     * 
     * @param key the service key, to distinguish multiple services of
     * the same type
     * 
     * @return this object
     */
    public <T> AgentBuilder add(T service, Class<T> type, String key) {
        Map<String, Object> bank =
            services.computeIfAbsent(type, t -> new HashMap<>());
        bank.put(key, service);
        return this;
    }

    /**
     * Add the default service for a specific type.
     * 
     * @param service the service to be added
     * 
     * @param type the service type
     * 
     * @return this object
     */
    public <T> AgentBuilder add(T service, Class<T> type) {
        return add(service, type, null);
    }

    /**
     * Create the agent with the current services and no initiation.
     * 
     * @return the new agent
     */
    public Agent create() {
        return create(() -> {});
    }

    /**
     * Create the agent with the current services.
     * 
     * @param initiation to be executed upon initiation
     * 
     * @return the new agent
     */
    public Agent create(Initiation initiation) {
        /* Do a deep clone of the service tree. */
        Map<Class<?>, Map<String, Object>> clone = new HashMap<>();
        for (Map.Entry<Class<?>, Map<String, Object>> outer : services
            .entrySet()) {
            Map<String, Object> copy = new HashMap<>(outer.getValue());
            clone.put(outer.getKey(), copy);
        }

        /* Build the agent from the clone, in case other services are
         * added later. */
        return new StaticAgent(initiation, clone);
    }

    /**
     * Defines the initiation process for a generated agent.
     * 
     * @author simpsons
     */
    public interface Initiation {
        /**
         * Perform the actions necessary to initiate the agent.
         * 
         * @throws AgentInitiationException if there was a problem
         * during initiation
         */
        void initiate() throws AgentInitiationException;
    }

    private static class StaticAgent implements Agent {
        private final Initiation initiation;
        private final Map<Class<?>, Map<String, Object>> services;

        private StaticAgent(Initiation initiation,
                            Map<Class<?>, Map<String, Object>> services) {
            this.initiation = initiation;
            this.services = services;
        }

        @Override
        public void initiate() throws AgentInitiationException {
            initiation.initiate();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T findService(Class<? super T> type, String key) {
            Map<String, Object> bank = services.get(type);
            if (bank == null) return null;
            return (T) bank.get(key);
        }

        @Override
        public Collection<String> getKeys(Class<?> type) {
            Map<String, Object> bank = services.get(type);
            if (bank == null) return Collections.emptySet();
            return Collections.unmodifiableCollection(bank.keySet());
        }
    }
}
