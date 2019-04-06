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
 * Start by declaring your capture keys first. For example:
 * 
 * <pre>
 * static final Capture&lt;String&gt; NAME = Capture.ofString();
 * static final Capture&lt;Integer&gt; ID = Capture.ofInt();
 * </pre>
 * 
 * <p>
 * Use {@link Expression#EMPTY} to start building with an empty
 * expression, or {@link Expression#START} to start by matching the
 * start of the expression. Then append literals, choices and other
 * expressions with one of these:
 * 
 * <ul>
 * 
 * <li>{@link Expression#then(CharSequence)}
 * 
 * <li>{@link Expression#then(CharSequence, CharSequence, CharSequence...)}
 * 
 * <li>{@link Expression#then(Expression)}
 * 
 * <li>{@link Expression#then(Expression, Expression, Expression...)}
 * 
 * </ul>
 * 
 * <p>
 * Use {@link Expression#capture(Capture)} to capture anything matching
 * the current expression to the given key.
 * 
 * <p>
 * Finally, use {@link Expression#render()} to create the compiled
 * {@link java.util.regex.Pattern}. When you match it against a string,
 * you can extract the captures with the capture keys:
 * 
 * <pre>
 * Pattern p = Expression.<var>...</var>.render();
 * Matcher m = p.matcher();
 * if (m.matches()) {
 *   String name = NAME.get(m);
 *   int id = ID.get(m);
 *   <var>...</var>
 * }
 * </pre>
 * 
 * @resume Classes for structural representation of regular expressions
 * 
 * @author simpsons
 */
package uk.ac.lancs.regex;
