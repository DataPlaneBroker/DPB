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
public interface FormattedLogger {
    /**
     * Get the unformatted logger that supports this formatted logger.
     * 
     * @return the supporting unformatted logger
     */
    Logger base();

    /**
     * The numeric value of the log-level abstraction {@link Level#ALL},
     * namely {@value}
     */
    public static final int ALL = 0;

    /**
     * The numeric value of the log-level abstraction
     * {@link Level#CONFIG}, namely {@value}
     */
    public static final int CONFIG = 700;

    /**
     * The numeric value of the log-level abstraction
     * {@link Level#FINE}, namely {@value}
     */
    public static final int FINE = 500;

    /**
     * The numeric value of the log-level abstraction
     * {@link Level#FINER}, namely {@value}
     */
    public static final int FINER = 400;

    /**
     * The numeric value of the log-level abstraction
     * {@link Level#FINEST}, namely {@value}
     */
    public static final int FINEST = 300;

    /**
     * The numeric value of the log-level abstraction {@link Level#OFF},
     * namely {@value}
     */
    public static final int OFF = Integer.MAX_VALUE;

    /**
     * The numeric value of the log-level abstraction
     * {@link Level#SEVERE}, namely {@value}
     */
    public static final int SEVERE = 1000;

    /**
     * The numeric value of the log-level abstraction
     * {@link Level#WARNING}, namely {@value}
     */
    public static final int WARNING = 900;

    /**
     * The numeric value of the log-level abstraction
     * {@link Level#INFO}, namely {@value}
     */
    public static final int INFO = 800;

    /**
     * Get a formatted logger for a given type, basing it on the named
     * logger. It is equivalent to:
     * 
     * <pre>
     * FormattedLogger.{@linkplain #get(Logger, Class) get}({@linkplain Logger#getLogger(String) Logger.getLogger}(name), type)
     * </pre>
     * 
     * @param name the logger name, to be supplied to
     * {@link Logger#getLogger(String)}
     * 
     * @param type the formatted type
     * 
     * @return the requested formatted logger
     */
    public static <T extends FormattedLogger> T get(String name,
                                                    Class<T> type) {
        return get(Logger.getLogger(name), type);
    }

    /**
     * Get a formatted logger for a given type, basing it on the given
     * logger.
     * 
     * @param logger the base logger that the formatted logger will
     * delegate to
     * 
     * @param type the interface type annotated with the message formats
     * 
     * @return the requested formatted logger
     */
    public static <T extends FormattedLogger> T get(Logger logger,
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
            Format m = cand.getAnnotation(Format.class);
            if (m == null) throw new IllegalArgumentException("method " + cand
                + " not a log message");
            String fmt = m.value();
            if (fmt == null) throw new IllegalArgumentException("method "
                + cand + " has no format");

            /* Determine the log level for this message, using the
             * declaring interface type's setting as a default. */
            NumericDetail numDetail = cand.getAnnotation(NumericDetail.class);
            Detail detail = cand.getAnnotation(Detail.class);
            NumericDetail typeNumDetail =
                cand.getDeclaringClass().getAnnotation(NumericDetail.class);
            Detail typeDetail =
                cand.getDeclaringClass().getAnnotation(Detail.class);
            final Level lvl;
            if (numDetail != null) {
                lvl = new Level("MESSAGE_DEFINED", numDetail.value()) {
                    private static final long serialVersionUID = 1L;
                };
            } else if (numDetail != null) {
                lvl = detail.value().level;
            } else if (typeNumDetail != null) {
                lvl = new Level("INTERFACE_DEFINED", typeNumDetail.value()) {
                    private static final long serialVersionUID = 1L;
                };
            } else if (typeDetail != null) {
                lvl = typeDetail.value().level;
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
