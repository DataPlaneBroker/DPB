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

import java.util.Collection;

/**
 * Allows a single object to provide multiple services, so that state
 * can be shared between them.
 * 
 * <p>
 * Each service is defined by a class or interface type, and multiple
 * services of the same type must be distinguished by a string key. One
 * service of each given type may be identified by a {@code null} key.
 * {@link #getKeys(Class)} provides the key set for a service type,
 * excluding the {@code null} key.
 * 
 * <p>
 * The <code>getService</code> methods expect a requested service to
 * exist, and throw {@link UnknownServiceException} if not. The
 * <code>findService</code> methods return {@code null} if the requested
 * service is not found.
 * 
 * 
 * @author simpsons
 */
public interface Agent {
    /**
     * Get the named service of a given type.
     * 
     * @default This method passes its arguments to
     * {@link #findService(Class, String)}, and throws an exception if
     * the result is {@code null}.
     * 
     * @param type the service type
     * 
     * @param key the key to distinguish multiple services of the same
     * type, or {@code null} to get the default
     * 
     * @return the requested service
     * 
     * @throws UnknownServiceException if the service does not exist
     * 
     * @throws ServiceCreationException if obtaining the service for the
     * first time failed
     */
    default <T> T getService(Class<T> type, String key)
        throws UnknownServiceException,
            ServiceCreationException {
        T result = findService(type, key);
        if (result == null)
            throw new UnknownServiceException(key + " of " + type);
        return result;
    }

    /**
     * Get the default service of a given type.
     * 
     * @default This method calls {@link #getService(Class, String)}
     * with a {@code null} key.
     * 
     * @param type the service type
     * 
     * @return the requested service
     * 
     * @throws UnknownServiceException if the service does not exist
     * 
     * @throws ServiceCreationException if obtaining the service for the
     * first time failed
     */
    default <T> T getService(Class<T> type)
        throws UnknownServiceException,
            ServiceCreationException {
        return getService(type, null);
    }

    /**
     * Find the named service of a given type without raising an
     * exception if missing.
     * 
     * @param type the service type
     * 
     * @param key the key to distinguish multiple services of the same
     * type, or {@code null} to get the default
     * 
     * @return the requested service, or {@code null} if it does not
     * exist
     * 
     * @throws ServiceCreationException if obtaining the service for the
     * first time failed
     */
    <T> T findService(Class<T> type, String key)
        throws ServiceCreationException;

    /**
     * Get a set of all keys of a service type.
     * 
     * @param type the service type
     * 
     * @return an immutable set of keys, possibly including {@code null}
     * for the default service
     */
    Collection<String> getKeys(Class<?> type);

    /**
     * Find the default service of a given type without raising an
     * exception if missing.
     * 
     * @default This method calls {@link #findService(Class, String)}
     * with a {@code null} key.
     * 
     * @param type the service type
     * 
     * @return the requested service, or {@code null} if it does not
     * exist
     * 
     * @throws ServiceCreationException if obtaining the service for the
     * first time failed
     */
    default <T> T findService(Class<T> type) throws ServiceCreationException {
        return findService(type, null);
    }
}
