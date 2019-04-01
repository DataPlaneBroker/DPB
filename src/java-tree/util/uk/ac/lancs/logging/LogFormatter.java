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

package uk.ac.lancs.logging;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Formats log messages.
 * 
 * @author simpsons
 */
public interface LogFormatter {
    /**
     * Get the unformatted logger that supports this formatted logger.
     * 
     * @return the supporting unformatted logger
     */
    Logger base();

    public static final int ALL = 0;
    public static final int CONFIG = 700;
    public static final int FINE = 500;
    public static final int FINER = 400;
    public static final int FINEST = 300;
    public static final int OFF = Integer.MAX_VALUE;
    public static final int SEVERE = 1000;
    public static final int WARNING = 900;
    public static final int INFO = 800;

    public static <T extends LogFormatter> T get(String name, Class<T> type) {
        return get(Logger.getLogger(name), type);
    }

    public static <T extends LogFormatter> T get(Logger logger,
                                                 Class<T> type) {
        /* Bind our generic functions to this specific logger. */
        MethodHandle logHandle = Statics.logHandle.bindTo(logger);
        MethodHandle getLOggerHandle = Statics.getLoggerHandle.bindTo(logger);

        /* Map each declared method to a handle. */
        Map<Method, MethodHandle> translation = new HashMap<>();
        for (Method cand : type.getMethods()) {
            /* Make sure the logger can be accessed. */
            if (cand.equals(Statics.getLoggerMethod)) {
                translation.put(cand, getLOggerHandle);
                continue;
            }

            /* Abort if the method returns a value or throws a checked
             * exception. */
            if (cand.getReturnType() != Void.TYPE)
                throw new IllegalArgumentException("method " + cand
                    + " does not return void but " + cand.getReturnType());
            if (cand.getExceptionTypes().length > 0)
                throw new IllegalArgumentException("method " + cand
                    + " throws");

            /* Abort if the method has no format. */
            Message m = cand.getAnnotation(Message.class);
            if (m == null) throw new IllegalArgumentException("method " + cand
                + " not a log message");
            String fmt = m.format();
            if (fmt == null) throw new IllegalArgumentException("method "
                + cand + " has no format");

            /* Determine the log level for this message, using the
             * declaring interface type's setting as a default. */
            Formatted defaults =
                cand.getDeclaringClass().getAnnotation(Formatted.class);
            final Level lvl;
            if (m.level() >= 0) {
                lvl = new Level("MESSAGE_DEFINED", m.level()) {
                    private static final long serialVersionUID = 1L;
                };
            } else if (defaults != null) {
                lvl = new Level("INTERFACE_DEFINED", defaults.level()) {
                    private static final long serialVersionUID = 1L;
                };
            } else {
                lvl = Level.INFO;
            }

            /* Bind the log level and format to the handle, and map the
             * method to it. */
            MethodHandle act = logHandle.bindTo(lvl).bindTo(fmt);
            translation.put(cand, act);
        }

        /* Create a proxy that invokes the handle corresponding to the
         * invoked method. */
        InvocationHandler actions = new InvocationHandler() {
            @Override
            public Object invoke(Object base, Method meth, Object[] args)
                throws Throwable {
                return translation.get(meth).invoke(args);
            }
        };
        return type
            .cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]
            { type }, actions));
    }
}
