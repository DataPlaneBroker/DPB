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

package uk.ac.lancs.logging;

import java.util.logging.Level;

/**
 * Shadows the standard logging levels, while being suitable for
 * annotation constants.
 * 
 * @author simpsons
 */
public enum ShadowLevel {
    /**
     * An annotation-compatible equivalent of {@link Level#ALL}
     */
    ALL(Level.ALL),

    /**
     * An annotation-compatible equivalent of {@link Level#SEVERE}
     */
    SEVERE(Level.SEVERE),

    /**
     * An annotation-compatible equivalent of {@link Level#WARNING}
     */
    WARNING(Level.WARNING),

    /**
     * An annotation-compatible equivalent of {@link Level#INFO}
     */
    INFO(Level.INFO),

    /**
     * An annotation-compatible equivalent of {@link Level#CONFIG}
     */
    CONFIG(Level.CONFIG),

    /**
     * An annotation-compatible equivalent of {@link Level#FINE}
     */
    FINE(Level.FINE),

    /**
     * An annotation-compatible equivalent of {@link Level#FINER}
     */
    FINER(Level.FINER),

    /**
     * An annotation-compatible equivalent of {@link Level#FINEST}
     */
    FINEST(Level.FINEST),

    /**
     * An annotation-compatible equivalent of {@link Level#OFF}
     */
    OFF(Level.OFF);

    ShadowLevel(Level lvl) {
        this.level = lvl;
    }

    /**
     * The native level that this shadow level corresponds to
     */
    public final Level level;
}
