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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 * 
 * @author simpsons
 */
final class Statics {
    private Statics() {}

    @SuppressWarnings("unused")
    private static void log(Logger logger, Level lvl, String fmt,
                            Object[] args) {
        if (!logger.isLoggable(lvl)) return;
        String txt = String.format(fmt, args);
        logger.log(lvl, txt);
    }

    @SuppressWarnings("unused")
    private static Logger getLogger(Logger logger) {
        return logger;
    }

    static final MethodHandle logHandle;
    static final Method getLoggerMethod;
    static final MethodHandle getLoggerHandle;

    static {
        try {
            getLoggerMethod = LogFormatter.class.getMethod("base");
        } catch (NoSuchMethodException | SecurityException e1) {
            throw new UnsupportedOperationException("getLogger", e1);
        }

        MethodHandles.Lookup lookup = MethodHandles.lookup();

        try {
            Method baseMeth = Statics.class
                .getDeclaredMethod("log", Logger.class, Level.class,
                                   String.class, Object[].class);
            baseMeth.setAccessible(true);
            logHandle = lookup.unreflect(baseMeth);
        } catch (IllegalAccessException | NoSuchMethodException
            | SecurityException e) {
            throw new UnsupportedOperationException("log", e);
        }

        try {
            Method baseMeth =
                Statics.class.getDeclaredMethod("getLogger", Logger.class);
            baseMeth.setAccessible(true);
            getLoggerHandle = lookup.unreflect(baseMeth);
        } catch (IllegalAccessException | NoSuchMethodException
            | SecurityException e) {
            throw new UnsupportedOperationException("getLogger", e);
        }
    }
}
