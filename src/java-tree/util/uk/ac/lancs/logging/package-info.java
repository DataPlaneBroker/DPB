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

/**
 * Provides facilities for generating formatted messages using the
 * standard logging framework.
 * 
 * <p>
 * To create a formatted logger, define an interface (usually a nested
 * (package-)private one) to extend
 * {@link uk.ac.lancs.logging.FormattedLogger}, and define one method
 * for each message format. Each method must return {@code void} and
 * throw no checked exceptions. For example:
 * 
 * <pre>
 * public class MyClass {
 *   <var>...</var>
 *   
 *   private interface MyLogger {
 *     void coordsReceived(int x, int y);
 *   }
 * }
 * </pre>
 * 
 * <p>
 * Now annotate each method to indicate the format and logging level:
 * 
 * <pre>
 * public class MyClass {
 *   <var>...</var>
 *   
 *   private interface MyLogger {
 *     &#64;Format("Coords (%d, %d) received")
 *     &#64;Detail(ShadowLevel.INFO)
 *     void coordsReceived(int x, int y);
 *   }
 * }
 * </pre>
 * 
 * <p>
 * You can also specify a default logging level for all methods:
 * 
 * <pre>
 * public class MyClass {
 *   <var>...</var>
 *   
 *   &#64;Detail(ShadowLevel.INFO)
 *   private interface MyLogger {
 *     &#64;Format("Coords (%d, %d) received")
 *     void coordsReceived(int x, int y);
 *   }
 * }
 * </pre>
 * 
 * <p>
 * If you don't specify a level, {@link java.util.logging.Level#INFO} is
 * used.
 * 
 * <p>
 * Now create a logger for your class:
 * 
 * <pre>
 * public class MyClass {
 *   <var>...</var>
 *   
 *   private interface MyLogger {
 *     &#64;Format("Coords (%d, %d) received")
 *     &#64;Detail(ShadowLevel.INFO)
 *     void coordsReceived(int x, int y);
 *   }
 *   
 *   private static final MyLogger logger =
 *     FormattedLogger.get(MyClass.class.getName(), MyLogger.class);
 * }
 * </pre>
 * 
 * <p>
 * Now, when you write <code>logger.coordsReceived(a.x, a.y)</code>,
 * you're really writing:
 * 
 * <pre>
 * plainLogger.info("Coords (" + a.x + ", " + a.y + ") received");
 * </pre>
 * 
 * <p>
 * &hellip;where <code>plainLogger</code> is what you'd normally get
 * from {@link java.util.logging.Logger#getLogger}. As an optimization,
 * the message string is not constructed unless the message is to be
 * logged.
 * 
 * @author simpsons
 */
package uk.ac.lancs.logging;
