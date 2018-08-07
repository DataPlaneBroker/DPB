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

/**
 * The initial state is {@link #DORMANT}.
 * 
 * @resume A service status, distinguishing between dormant, inactive,
 * active and released states, including intermediate states
 * 
 * @author simpsons
 */
public enum ServiceStatus {
    /**
     * The service is not in use, and has no associated request. This is
     * the initial state.
     */
    DORMANT {
        @Override
        public boolean isStable() {
            return true;
        }
    },

    /**
     * The underlying service resources have not yet been established.
     */
    ESTABLISHING {
        @Override
        public boolean isStable() {
            return false;
        }
    },

    /**
     * The service has link resources allocated, but no switch
     * resources.
     */
    INACTIVE {
        @Override
        public boolean isStable() {
            return true;
        }
    },

    /**
     * The service is in the process of becoming {@link #ACTIVE}. Some
     * traffic might begin to get through.
     */
    ACTIVATING {
        @Override
        public boolean isStable() {
            return false;
        }
    },

    /**
     * The service is active, and so can carry traffic.
     */
    ACTIVE {
        @Override
        public boolean isStable() {
            return true;
        }
    },

    /**
     * The service is deactivating. Some traffic might still get
     * through.
     */
    DEACTIVATING {
        @Override
        public boolean isStable() {
            return false;
        }
    },

    /**
     * The service has failed outright.
     */
    FAILED {
        @Override
        public boolean isStable() {
            return true;
        }
    },

    /**
     * The service is deactivated and is being released.
     */

    RELEASING {
        @Override
        public boolean isStable() {
            return false;
        }
    },
    /**
     * The service has been released, and can no longer be used.
     */
    RELEASED {
        @Override
        public boolean isStable() {
            return true;
        }
    };

    /**
     * Determine whether this is a stable state. The following states
     * are stable:
     * 
     * <ul>
     * 
     * <li>{@link #DORMANT}
     * 
     * <li>{@link #INACTIVE}
     * 
     * <li>{@link #ACTIVE}
     * 
     * <li>{@link #RELEASED}
     * 
     * <li>{@link #FAILED}
     * 
     * </ul>
     * 
     * @return {@code true} iff this is a stable state
     */
    public abstract boolean isStable();
}
