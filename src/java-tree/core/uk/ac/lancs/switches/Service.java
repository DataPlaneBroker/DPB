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
package uk.ac.lancs.switches;

/**
 * Represents a connection with allocated resources. A new connection is
 * obtained from {@link NetworkControl#newService()}. Each connection has
 * a persistent identifier which can be used to recover the connection
 * object through {@link NetworkControl#getService(int)} if lost.
 * 
 * Listeners can be added to a connection to be informed of changes to
 * its state.
 * 
 * <p>
 * A connection has internal state and potentially inferior state, i.e.,
 * that which is held by inferior or subservient entities, e.g.,
 * subswitches.
 * 
 * <p>
 * Call {@link #initiate(ServiceDescription)} with connection parameters
 * (end points and bandwidth) to initiate a connection.
 * {@link ServiceListener#ready()} will be invoked if the connection is
 * established (but not yet activated).
 * {@link ServiceListener#failed(Throwable)} will be invoked on error.
 * 
 * <p>
 * Once established, {@link #activate()} can be called to activate the
 * connection. Inferior resources will be set up, allowing traffic to
 * flow between its end points. {@link ServiceListener#activated()} will
 * be invoked when activation is complete.
 * 
 * <p>
 * A connection can be deactived with {@link #deactivate()}. Inferior
 * resources will be released, and traffic will no longer flow.
 * {@link ServiceListener#deactivated()} will be invoked when
 * de-activation is complete.
 * 
 * <p>
 * A connection can be activated and deactivated any number of times.
 * 
 * <p>
 * Calling {@link #release()} ensures the connection is deactivated, and
 * all resources will be released. {@link ServiceListener#released()}
 * will finally be called.
 * 
 * @author simpsons
 */
public interface Service {
    /**
     * Get the switch that owns this connection.
     * 
     * @return the owning switch, or {@code null} if the connection has
     * been released
     */
    NetworkControl getSwitch();

    /**
     * Get the request associated with this connection.
     * 
     * @return the associated request, or {@code null} if this
     * connection is released or has not been initiated
     */
    ServiceDescription getRequest();

    /**
     * Initiate allocation of resources.
     * 
     * @param request the connection details
     */
    void initiate(ServiceDescription request);

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
     * Activate the connection, allowing it to carry traffic. This
     * method has no effect if called while the connection is active or
     * activating.
     * 
     * @see #status()
     * 
     * @see #deactivate()
     * 
     * @throws IllegalStateException if this connection has been
     * released
     */
    void activate();

    /**
     * Prevent the connection from carrying traffic.
     * 
     * @see #status()
     * 
     * @see #activate()
     * 
     * @throws IllegalStateException if this connection has been
     * released
     */
    void deactivate();

    /**
     * Determine whether the connection is active. When
     * 
     * @return the connection's status
     * 
     * @see #activate()
     * 
     * @see #deactivate()
     * 
     * @throws IllegalStateException if this connection has been
     * released
     */
    ServiceStatus status();

    /**
     * Release all resources pertaining to this connection. All methods
     * on this object will subsequently raise
     * {@link IllegalStateException}.
     */
    void release();

    /**
     * Get the connection's identifier within the switch that created
     * it. The identifier can be used to re-acquire the interface to a
     * connection if the original is lost.
     * 
     * @see NetworkControl#getService(int)
     * 
     * @return the connection identifier
     * 
     * @throws IllegalStateException if this connection has been
     * released
     */
    int id();
}
