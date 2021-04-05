/*
 * Copyright (c) 2021, Regents of the University of Lancaster
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the
 *   distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *
 *  Author: Steven Simpson <s.simpson@lancaster.ac.uk>
 */

package uk.ac.lancs.dpb.bw;

import java.util.BitSet;
import java.util.List;
import javax.script.ScriptException;

/**
 * Determines the bandwidth requirements of any edge in a tree, given
 * the endpoints reachable from one end of that edge. Each endpoint is a
 * distinct leaf of the tree. Given a direction of traffic along the
 * edge, the endpoints able to supply that traffic from the
 * <dfn>from</dfn> set; the remaining endpoints form the <dfn>to</dfn>
 * set. Neither set is permitted to be empty (or there would be no
 * traffic). A bandwidth function computes the bandwidth requirements of
 * the traffic in one direction along an edge, given the
 * <cite>from</cite> set. By providing the complement set as the
 * <cite>from</cite> set, it yields the bandwidth requirements of
 * traffic flowing in the opposite direction.
 * 
 * <p>
 * A bandwidth function has a <dfn>degree</dfn>, the number of endpoints
 * to be connected. Endpoints are numbered from 0 to
 * <var>n</var>&minus;1, where <var>n</var> is the degree.
 * 
 * <p>
 * For example, when four endpoints are to be connected (so the degree
 * is 4), and endpoints 0 and 1 are expected to be reachable from one
 * end <var>A</var> of a candidate edge, and 2 and 3 from the other end
 * <var>B</var>, a function representing the requirements for bandwidth
 * between these endpoints may be supplied with a <cite>from</cite> set
 * of { 0, 1 }, and will yield the bandwidth requirement from
 * <var>A</var> to <var>B</var>. When supplied with a <cite>from</cite>
 * set of { 2, 3 }, it will yield the requirement from <var>B</var> to
 * <var>A</var>. This is the purpose of the
 * {@link #get(java.util.BitSet)} method.
 * 
 * <p>
 * An implementation is required to have a self-contained JavaScript
 * representation, so that it can be transmitted and executed remotely.
 * The representation must be an object declaration with at least two
 * fields, similar to this form:
 * 
 * <pre>
 * {
 *   degree : <var>degree</var>,
   get : function(value) {
     <var>algorithm</var>
 *   },
 * }
 * </pre>
 * 
 * The value passed to the function is an integer whose bit pattern in
 * the first <var>n</var> bits identifies the <cite>from</cite> set.
 * 
 * <p>
 * A bandwidth function is <dfn>reducable</dfn>. That is, its endpoints
 * can be arbitrarily partitioned into
 * <var>n</var>&prime;&lt;<var>n</var> groups, yielding a new function
 * of degree <var>n</var>&prime;. Each of the reduced function's
 * endpoints corresponds to a distinct group. A reduced function can be
 * implemented by converting a supplied <cite>from</cite> set in its own
 * domain into the domain of the original function, and invoking the
 * original function with the converted <cite>from</cite> set. A reduced
 * function's JavaScript representation can be formed by embedding the
 * original function's representation, and similarly transforming its
 * argument into the original domain before invoking the embedded
 * representation. This is why a bandwidth function must have a
 * <em>self-contained</em> JavaScript representation. The
 * {@link #reduce(List)} method yields a reduced function.
 * 
 * <p>
 * Reduction is vital in a hierarchy of network control. For example, a
 * superior network might be asked to connect 10 endpoints using a given
 * bandwidth function. It has identified a suitable tree whose vertices
 * are inferiors. One of those inferiors, being only a single vertex in
 * the tree, is required to connect (say) only three of its endpoints.
 * Inferior endpoint 0 will reach superior endpoints 2, 4 and 5;
 * inferior 1 will reach superiors 1, 6, 7 and 8; and inferior 2 will
 * reach the remainder, superiors 0, 3 and 9. The superior network can
 * provide the sequence of sets &#10216; { 2, 4, 5 }, { 1, 6, 7, 8 }, {
 * 0, 3, 9 } &#10217; to {@link #reduce(List)} to yield a reduced
 * function to submit to the inferior. When asked to specify the
 * bandwidth requirement from the <cite>from</cite> set { 0, 1 }, the
 * new function will yield the same result as the original function with
 * the <cite>from</cite> set { 2, 4, 5 } &cup; { 1, 6, 7, 8 }.
 * 
 * <p>
 * Since any given function has a fixed number of valid inputs
 * (specifically, 2<sup><var>n</var></sup>&minus;2), and always yields
 * the same result given the same input, it could be implemented as a
 * table of cached results. A reduced function could benefit from such
 * an implementation, as there is no need to retain the original
 * function, nor to embed its JavaScript representation in that of the
 * reduced function. However, for high-degree functions, a table incurs
 * a considerable memory overhead, but a reduced function will
 * necessarily have a smaller degree than its origin. The method
 * {@link #tabulate()} is provided to check whether such an
 * implementation is possible and recommended, yielding either the new
 * implementation or its tabulated implementation accordingly. This is
 * applied to the {@link #reduce(java.util.List)} result by default.
 * 
 * @author simpsons
 */
public interface BandwidthFunction {
    /**
     * Get the bandwidth requirement for an edge.
     * 
     * <p>
     * Given the same argument, this method must return the same result
     * on every invocation.
     *
     * @param from the set of endpoints connected to the transmitting
     * vertex of the edge
     *
     * @return the bandwidth requirement
     *
     * @throws IllegalArgumentException if the set is invalid, e.g., it
     * contains members outside the range defined by {@link #degree()},
     * it contains all such members, or it is empty
     */
    BandwidthRange get(BitSet from);

    /**
     * Get a JavaScript representation of the function. The string must
     * be an object declaration with two fields. One is
     * {@value #JAVASCRIPT_DEGREE_NAME}, giving the function degree. The
     * other must be a function called
     * {@value #JAVASCRIPT_FUNCTION_NAME}, taking a single argument, a
     * bit set, to be interpreted as the set of upstream endpoints, and
     * return a map of two numbers, the minimum and maximum upstream
     * bandwidth of the edge.
     * 
     * @return the JavaScript representation
     */
    String asJavaScript();

    /**
     * The name of the function embedded in the output of
     * {@link #asJavaScript()}
     */
    String JAVASCRIPT_FUNCTION_NAME = "get";

    /**
     * The name of the field embedded in the output of
     * {@link #asJavaScript()} giving the function's degree
     */
    String JAVASCRIPT_DEGREE_NAME = "degree";

    /**
     * Get the function's degree. The argument to {@link #get(BitSet)}
     * must only contain set bits in positions zero to one less than the
     * degree.
     * 
     * <p>
     * This method must return same value on every invocation.
     *
     * @return the function's degree
     */
    int degree();

    /**
     * Reduce this function by grouping together endpoints.
     * 
     * @param groups the proposed set of endpoint groups
     * 
     * @return a new function whose endpoint indices correspond to
     * groups of endpoints of this function
     * 
     * @throws IllegalArgumentException if a proposed group contains
     * more bits than indicated by the degree
     * 
     * @default The default behaviour is to create a new function that
     * references the original, and whose JavaScript representation
     * embeds the original's representation, and then to apply
     * {@link #tabulate()} and return the result.
     */
    default BandwidthFunction reduce(List<? extends BitSet> groups) {
        return new ReducedBandwidthFunction(this, groups).tabulate();
    }

    /**
     * Attempt to simplify the implementation of this function by
     * enumerating the outputs for all possible inputs, and storing in a
     * table. If the table would be too big, this function is returned
     * unchanged. The goal of this method is to simplify the JavaScript
     * representation after undergoing several reductions.
     * 
     * @return an identical function, possibly with a simpler
     * implementation
     */
    default BandwidthFunction tabulate() {
        return TableBandwidthFunction.tabulate(this);
    }

    /**
     * Create a bandwidth function from JavaScript. The text must be an
     * object declaration with at least two members.
     * 
     * @param text the JavaScript source code
     * 
     * @return a function that yields the same results as the supplied
     * JavaScript
     * 
     * @throws ScriptException if there is a problem parsing the script
     * 
     * @constructor
     */
    public static BandwidthFunction fromJavaScript(String text)
        throws ScriptException {
        return new JavaScriptBandwidthFunction(text).tabulate();
    }
}
