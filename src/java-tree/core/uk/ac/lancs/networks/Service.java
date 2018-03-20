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
package uk.ac.lancs.networks;

import java.util.Collection;

/**
 * A new service is obtained from {@link NetworkControl#newService()}.
 * Each service has a persistent identifier which can be used to recover
 * the service object through {@link NetworkControl#getService(int)} if
 * lost. Listeners can be added to a service to be informed of changes
 * to its state.
 * 
 * <p>
 * A service has internal state and potentially inferior state, i.e.,
 * that which is held by inferior or subservient entities, e.g.,
 * subswitches.
 * 
 * <p>
 * Call {@link #initiate(ServiceDescription)} with service parameters
 * (end points and bandwidth) to initiate a service.
 * <code>{@linkplain ServiceListener#newStatus(ServiceStatus) ServiceListener.newStatus}({@link ServiceStatus#INACTIVE INACTIVE})</code>
 * will be invoked if the service is established (but not yet
 * activated).
 * 
 * <p>
 * Once established, {@link #activate()} can be called to activate the
 * service, allowing traffic to flow.
 * <code>{@linkplain ServiceListener#newStatus(ServiceStatus) ServiceListener.newStatus}({@link ServiceStatus#ACTIVATING ACTIVATING})</code>
 * will be invoked as activation begins, and
 * <code>{@linkplain ServiceListener#newStatus(ServiceStatus) ServiceListener.newStatus}({@link ServiceStatus#ACTIVE ACTIVE})</code>
 * will be invoked when activation is complete.
 * 
 * <p>
 * A service can be deactived with {@link #deactivate()}, and traffic
 * will no longer flow.
 * <code>{@linkplain ServiceListener#newStatus(ServiceStatus) ServiceListener.newStatus}({@link ServiceStatus#DEACTIVATING ACTIVATING})</code>
 * will be invoked as deactivation starts, and
 * <code>{@linkplain ServiceListener#newStatus(ServiceStatus) ServiceListener.newStatus}({@link ServiceStatus#INACTIVE INACTIVE})</code>
 * will be invoked when de-activation is complete.
 * 
 * <p>
 * A service can be activated and deactivated any number of times.
 * 
 * <p>
 * {@linkplain ServiceListener#newStatus(ServiceStatus)
 * newStatus}({@link ServiceStatus#FAILED FAILED}) will be invoked on
 * error, and errors {@link Service#errors()} can be used to get details
 * on the failure.
 * 
 * <p>
 * Calling {@link #release()} ensures the service is deactivated, and
 * all resources will be released.
 * <code>{@linkplain ServiceListener#newStatus(ServiceStatus) ServiceListener.newStatus}({@link ServiceStatus#RELEASING RELEASING})</code>
 * will be called immediately, and then
 * <code>{@linkplain ServiceListener#newStatus(ServiceStatus) ServiceListener.newStatus}({@link ServiceStatus#RELEASED RELEASED})</code>
 * when all resources have been released.
 * 
 * @summary A connectivity service with QoS guarantees
 * 
 * @author simpsons
 */
public interface Service {
    /**
     * Get the network that owns this service.
     * 
     * @return the owning network, or {@code null} if the service has
     * been released
     */
    NetworkControl getNetwork();

    /**
     * Get the request associated with this service.
     * 
     * @return the associated request, or {@code null} if this service
     * is released or has not been initiated
     */
    ServiceDescription getRequest();

    /**
     * Initiate allocation of resources.
     * 
     * @param request the service details
     */
    void initiate(ServiceDescription request) throws InvalidServiceException;

    /**
     * Add a listener for events.
     * 
     * @param events the listener to be added
     */
    void addListener(ServiceListener events);

    /**
     * Remove a listener.
     * 
     * @param events the listener to be removed
     */
    void removeListener(ServiceListener events);

    /**
     * Activate the service, allowing it to carry traffic. This method
     * has no effect if called while the service is active or
     * activating. If called before the service is ready, it is
     * remembered for when the service has been established, and
     * triggers activation at that point.
     * 
     * @see #status()
     * 
     * @see #deactivate()
     * 
     * @throws IllegalStateException if this service has been released
     */
    void activate();

    /**
     * Prevent the service from carrying traffic.
     * 
     * @see #status()
     * 
     * @see #activate()
     * 
     * @throws IllegalStateException if this service has been released
     */
    void deactivate();

    /**
     * Determine whether the service is active. When
     * 
     * @return the service status
     * 
     * @see #activate()
     * 
     * @see #deactivate()
     * 
     * @throws IllegalStateException if this service has been released
     */
    ServiceStatus status();

    /**
     * Get the set of errors that have put this service into the
     * {@link ServiceStatus#FAILED} state.
     * 
     * @return the set of errors causing this service to fail; empty if
     * it has not failed
     */
    Collection<Throwable> errors();

    /**
     * Release all resources pertaining to this service. All methods on
     * this object will subsequently raise
     * {@link IllegalStateException}.
     */
    void release();

    /**
     * Get the service's identifier within the network that created it.
     * The identifier can be used to re-acquire the interface to a
     * service if the original is lost.
     * 
     * @see NetworkControl#getService(int)
     * 
     * @return the service identifier
     * 
     * @throws IllegalStateException if this service has been released
     */
    int id();
}
