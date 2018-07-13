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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Wraps another agent to cache all its responses. The wrapped agent
 * therefore does not need to store anything, just generate the service
 * on demand, knowing it will not be asked for a duplicate. Responses to
 * requests for absent services are also cached.
 * 
 * @author simpsons
 */
public final class CacheAgent implements Agent {
    private final Agent base;
    private final Map<Class<?>, Map<String, Object>> cache = new HashMap<>();;

    /**
     * Create a caching agent wrapped around another agent.
     * 
     * @param base the agent to be wrapped
     */
    public CacheAgent(Agent base) {
        this.base = base;
    }

    /**
     * {@inheritDoc}
     * 
     * @default This implementation checks the cache to see if the
     * particular service has already been requested. Otherwise, it
     * delegates to the base agent, and stores the result in the cache.
     */
    @Override
    public <T> T findService(Class<T> type, String key)
        throws ServiceCreationException {
        Map<String, Object> bank =
            cache.computeIfAbsent(type, k -> new HashMap<>());
        Object result = bank.get(key);
        if (result != null) return type.cast(result);
        T typedResult = base.findService(type, key);
        bank.put(key, typedResult);
        return typedResult;
    }

    /**
     * {@inheritDoc}
     * 
     * @default This implementation simply delegates to the base agent.
     */
    @Override
    public Collection<String> getKeys(Class<?> type) {
        return base.getKeys(type);
    }
}
