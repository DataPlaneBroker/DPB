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
 * Represents a connection with allocated resources.
 * 
 * @author simpsons
 */
public interface Connection {
    /**
     * Initiate allocation of resources.
     * 
     * @param request the connection details
     */
    void initiate(ConnectionRequest request);

    /**
     * Add a listener for events.
     * 
     * @param events the listener to be added
     */
    void addListener(ConnectionListener events);

    /**
     * Remove a listener.
     * 
     * @param events the listener to be removed
     */
    void removeListener(ConnectionListener events);

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
    ConnectionStatus status();

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
     * @see SwitchControl#getConnection(int)
     * 
     * @return the connection identifier
     * 
     * @throws IllegalStateException if this connection has been
     * released
     */
    int id();
}
