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
package uk.ac.lancs.networks;

import java.util.Collection;
import java.util.regex.Pattern;

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
 * Call {@link #define(Segment)} with service parameters (circuits and
 * bandwidth) to initiate a service.
 * <code>{@linkplain ServiceListener#newStatus(ServiceStatus) ServiceListener.newStatus}({@linkplain ServiceStatus#INACTIVE INACTIVE})</code>
 * will be invoked if the service is established (but not yet
 * activated).
 * 
 * <p>
 * Once established, {@link #activate()} can be called to activate the
 * service, allowing traffic to flow.
 * <code>{@linkplain ServiceListener#newStatus(ServiceStatus) ServiceListener.newStatus}({@linkplain ServiceStatus#ACTIVATING ACTIVATING})</code>
 * will be invoked as activation begins, and
 * <code>{@linkplain ServiceListener#newStatus(ServiceStatus) ServiceListener.newStatus}({@linkplain ServiceStatus#ACTIVE ACTIVE})</code>
 * will be invoked when activation is complete.
 * 
 * <p>
 * A service can be deactived with {@link #deactivate()}, and traffic
 * will no longer flow.
 * <code>{@linkplain ServiceListener#newStatus(ServiceStatus) ServiceListener.newStatus}({@linkplain ServiceStatus#DEACTIVATING ACTIVATING})</code>
 * will be invoked as deactivation starts, and
 * <code>{@linkplain ServiceListener#newStatus(ServiceStatus) ServiceListener.newStatus}({@linkplain ServiceStatus#INACTIVE INACTIVE})</code>
 * will be invoked when de-activation is complete.
 * 
 * <p>
 * A service can be activated and deactivated any number of times.
 * 
 * <p>
 * <code>{@linkplain ServiceListener#newStatus(ServiceStatus)
 * newStatus}({@linkplain ServiceStatus#FAILED FAILED})</code> will be
 * invoked on error, and {@link Service#errors()} can be used to get
 * details on the failure.
 * 
 * <p>
 * Calling {@link #release()} ensures the service is deactivated, and
 * all resources will be released.
 * <code>{@linkplain ServiceListener#newStatus(ServiceStatus) ServiceListener.newStatus}({@linkplain ServiceStatus#RELEASING RELEASING})</code>
 * will be called immediately, and then
 * <code>{@linkplain ServiceListener#newStatus(ServiceStatus) ServiceListener.newStatus}({@linkplain ServiceStatus#RELEASED RELEASED})</code>
 * when all resources have been released.
 * 
 * @resume A connectivity service with QoS guarantees
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
    Segment getRequest();

    /**
     * Initiate allocation of resources.
     * 
     * @param request the service details
     * 
     * @throws InvalidServiceException if the request is invalid
     */
    void define(Segment request) throws InvalidServiceException;

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
     * Wait until the status changes to an acceptable value, or the
     * service is released, or a timeout occurs.
     * 
     * @param accept the set of acceptable values
     * 
     * @param timeoutMillis the maximum amount of time to wait before
     * giving up
     * 
     * @return the latest status, which might not be one of the accepted
     * ones if the status is released, or the timeout or interrupt
     * occurred
     * 
     * @default The default implementation creates a listener on the
     * service object, and waits for one of the accepted statuses,
     * {@link ServiceStatus#RELEASED} or the timeout, or an interrupt.
     */
    default ServiceStatus
        awaitStatus(Collection<? extends ServiceStatus> accept,
                    long timeoutMillis) {
        final long expiry = System.currentTimeMillis() + timeoutMillis;
        class Ctxt implements ServiceListener {
            ServiceStatus got;
            boolean waiting = true;

            @Override
            public void newStatus(ServiceStatus newStatus) {
                synchronized (this) {
                    this.got = newStatus;
                    if (acceptable(newStatus)) {
                        this.waiting = false;
                        notify();
                    }
                }
            }

            private boolean acceptable(ServiceStatus status) {
                return status == ServiceStatus.RELEASED
                    || accept.contains(status);
            }

            synchronized ServiceStatus await() {
                got = status();
                if (acceptable(got)) return got;
                while (waiting) {
                    long delay = expiry - System.currentTimeMillis();
                    if (delay < 0) break;
                    try {
                        System.err.printf("Waiting %gs for %s%n",
                                          delay / 1000.0, accept);
                        wait(delay);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                return got;
            }
        }
        Ctxt me = new Ctxt();
        this.addListener(me);
        try {
            return me.await();
        } finally {
            this.removeListener(me);
        }
    }

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

    /**
     * This must be set during calls to {@link Service} if the service
     * was created with {@link NetworkControl#SERVICE_AUTH_TOKEN} set.
     * Its value must match the string provided during that call.
     * 
     * @summary The token used to check authorization for modifications
     * to a service
     */
    static ThreadLocal<Pattern> AUTH_TOKEN = new InheritableThreadLocal<>();
}
