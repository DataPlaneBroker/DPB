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

package uk.ac.lancs.dpb.graph;

import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.script.ScriptException;

/**
 * Determines the demands on the capacity of any edge in a tree, given
 * the endpoints reachable from one end of that edge. Its primary
 * purpose is to express bandwidth demands on edge capacity, though it
 * could apply to any similar, cumulative capacity-oriented metric. Each
 * endpoint is a distinct leaf of the tree. Given a direction of traffic
 * along the edge, the endpoints able to supply that traffic from the
 * <dfn>from</dfn> set; the remaining endpoints form the <dfn>to</dfn>
 * set. Neither set is permitted to be empty (or there would be no
 * traffic). A demand function computes the requirements of the traffic
 * in one direction along an edge, given the <cite>from</cite> set. By
 * providing the complement set as the <cite>from</cite> set, it yields
 * the requirements of traffic flowing in the opposite direction.
 * 
 * <p>
 * A demand function has a <dfn>degree</dfn>, the number of endpoints to
 * be connected (i.e., the goals). Endpoints are numbered from 0 to
 * <var>n</var>&minus;1, where <var>n</var> is the degree.
 * 
 * <p>
 * For example, when four goals are to be connected (so the degree is
 * 4), and goals 0 and 1 are expected to be reachable from one end
 * <var>A</var> of a candidate edge, and 2 and 3 from the other end
 * <var>B</var>, a function representing the capacity requirements
 * between these goals may be supplied with a <cite>from</cite> set of {
 * 0, 1 }, and will yield the requirement from <var>A</var> to
 * <var>B</var>. When supplied with a <cite>from</cite> set of { 2, 3 },
 * it will yield the requirement from <var>B</var> to <var>A</var>. This
 * is the purpose of the {@link #get(java.util.BitSet)} method.
 * 
 * <p>
 * An implementation is required to have a self-contained Python
 * representation, so that it can be transmitted and executed remotely.
 * {@link #asScript()} yields this representation, and
 * {@link #fromScript(int, String)} creates a demand function object
 * from such a script.
 * 
 * <p>
 * A demand function is <dfn>reducible</dfn>. That is, its goals can be
 * arbitrarily partitioned into <var>n</var>&prime;&lt;<var>n</var>
 * groups, yielding a new function of degree <var>n</var>&prime;. Each
 * of the reduced function's goals corresponds to a distinct group of
 * the original function's goals. A reduced function can be implemented
 * by converting a supplied <cite>from</cite> set in its own domain into
 * the domain of the original function, and invoking the original
 * function with the converted <cite>from</cite> set. A reduced
 * function's Python representation can be formed by embedding the
 * original function's representation, and similarly transforming its
 * argument into the original domain before invoking the embedded
 * representation. This is why a demand function must have a
 * <em>self-contained</em> Python representation. The
 * {@link #reduce(List)} method yields a reduced function.
 * 
 * <p>
 * Reduction is vital in a hierarchy of network control. For example, a
 * superior network might be asked to connect 10 goals using a given
 * demand function. In the process of finding potential solutions, it
 * has identified a suitable tree whose vertices are inferiors. One of
 * those inferiors, being only a single vertex in the tree, is required
 * to connect (say) only three of its goals. Inferior goal 0 will reach
 * superior goals 2, 4 and 5; inferior 1 will reach superiors 1, 6, 7
 * and 8; and inferior 2 will reach the remainder, superiors 0, 3 and 9.
 * The superior network can provide the sequence of sets &#10216; { 2,
 * 4, 5 }, { 1, 6, 7, 8 }, { 0, 3, 9 } &#10217; to {@link #reduce(List)}
 * to yield a reduced function to submit to the inferior. When asked to
 * specify the capacity requirement from the <cite>from</cite> set { 0,
 * 1 }, the new function will yield the same result as the original
 * function with the <cite>from</cite> set { 2, 4, 5 } &cup; { 1, 6, 7,
 * 8 }.
 * 
 * <p>
 * Since any given function has a fixed number of valid inputs
 * (specifically, 2<sup><var>n</var></sup>&minus;2), and always yields
 * the same result given the same input, it could be implemented as a
 * table of cached results. A reduced function could benefit from such
 * an implementation, as there is no need to retain the original
 * function, nor to embed its Python representation in that of the
 * reduced function. However, for high-degree functions, a table incurs
 * a considerable memory overhead, but a reduced function will
 * necessarily have a smaller degree than its origin. The method
 * {@link #tabulate()} is provided to check whether such an
 * implementation is possible and recommended, yielding either the
 * original implementation or its tabulated implementation accordingly.
 * This is applied to the {@link #reduce(List)} result by default.
 * 
 * @author simpsons
 */
public interface DemandFunction {
    /**
     * Get the bandwidth requirement for the forward direction of an
     * edge.
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
    Capacity get(BitSet from);

    /**
     * Get the bandwidth requirements of both directions of an edge.
     * 
     * @default {@link #get(BitSet)} is called with the argument to get
     * the forward requirement. The 'to' set is computed by calling
     * {@link #degree()}, and is then passed to {@link #get(BitSet)} to
     * get the reverse requirement.
     * 
     * @param from the set of endpoints connected to the
     * forward-transmitting vertex of the edge
     * 
     * @return the bandwidth requirement
     * 
     * @throws IllegalArgumentException if the set is invalid, e.g., it
     * contains members outside the range defined by {@link #degree()},
     * it contains all such members, or it is empty
     */
    default BidiCapacity getPair(BitSet from) {
        Capacity fwd = get(from);
        BitSet to = new BitSet();
        to.set(0, degree());
        to.xor(from);
        Capacity rev = get(to);
        return BidiCapacity.of(fwd, rev);
    }

    /**
     * Get a Python representation of the function. See
     * {@link #fromScript(int, String)} for the required format.
     * 
     * @return the Python representation
     * 
     * @see #fromScript(int, String)
     */
    String asScript();

    /**
     * The name of the function embedded in the output of
     * {@link #asScript()}
     */
    String GET_FUNCTION_NAME = "get";

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
     * references the original, and whose Python representation embeds
     * the original's representation, and then to apply
     * {@link #tabulate()} and return the result.
     */
    default DemandFunction reduce(List<? extends BitSet> groups) {
        return new ReducedDemandFunction(this, groups).tabulate();
    }

    /**
     * Attempt to simplify the implementation of this function by
     * enumerating the outputs for all possible inputs, and storing in a
     * table.
     * 
     * @default If the table would be too big, this function is returned
     * unchanged. The goal of this method is to simplify the Python
     * representation after undergoing several reductions.
     * 
     * @return an identical function, possibly with a simpler
     * implementation
     */
    default DemandFunction tabulate() {
        return TableDemandFunction.tabulate(this);
    }

    /**
     * Create a demand function from Python. The string must be the body
     * of a Python class definition with no indentation. A class method
     * called {@value #GET_FUNCTION_NAME} takes a class reference and an
     * integer to be interpreted as the set of upstream endpoints, and
     * returns an array of two numbers, the minimum and maximum upstream
     * demand on the edge. For example:
     * 
     * <pre>
     * &#64;classmethod
     * def get(cls, bs):
     *     <var>...</var>
     * </pre>
     * 
     * <p>
     * The rest of the definition may be used to hold other static data
     * required to implement {@value #GET_FUNCTION_NAME}.
     * 
     * <p>
     * The script will be invoked by indenting all lines, prefixing with
     * a <code>class Foo:</code> line (or similar), and executing
     * <code>Foo.get(bs)</code>.
     * 
     * <p>
     * Note that the degree is not embedded in the code, and must be
     * transmitted by other means. For example, when a client pushes a
     * service description into a network, the description includes both
     * the demand function script and a list of endpoints, implying the
     * number of goals (i.e., the degree).
     * 
     * @param degree the function's degree
     * 
     * @param text the Python source code
     * 
     * @return a function that yields the same results as the supplied
     * Python
     * 
     * @throws ScriptException if there is a problem parsing the script
     * 
     * @constructor
     * 
     * @see #asScript()
     */
    public static DemandFunction fromScript(int degree, String text)
        throws ScriptException {
        return new ScriptDemandFunction(degree, text).tabulate();
    }

    /**
     * Provide an equivalent function with a different goal mapping.
     * Each index of the mapping is a goal number in the result; the
     * value in corresponding the sequence element is the number
     * submitted to the original function.
     * 
     * @default If the mapping is identity, this implementation returns
     * this function. Otherwise, it creates a new function wrapped
     * around the original. Implementations may return the same
     * function, use a reconfiguration of the same implementation, or
     * use any other appropriate strategy.
     * 
     * @param mapping the mapping, whose size must match this function's
     * degree <var>n</var>, and must contain each goal number in
     * [0,&nbsp;<var>n</var>) exactly once
     * 
     * @return the mapped function
     * 
     * @throws IllegalArgumentException if the mapping is invalid
     */
    default DemandFunction map(List<? extends Number> mapping) {
        int[] alt = validateMapping(degree(), mapping);
        if (alt == null) return this;
        return new MappedDemandFunction(this, alt);
    }

    /**
     * Validate a mapping.
     * 
     * @param degree the expected number of elements
     * 
     * @param mapping the mapping
     * 
     * @return an array representation of the mapping if not identity;
     * {@code null} if identity
     * 
     * @throws IllegalArgumentException if the mapping has the wrong
     * number of elements; or if its element values don't constitute the
     * set [0,&nbsp;<var>n</var>] where <var>n</var> is the degree
     */
    public static int[] validateMapping(int degree,
                                        List<? extends Number> mapping) {
        /* The size of the mapping and the function's degree must
         * match. */
        if (mapping.size() != degree)
            throw new IllegalArgumentException("degree " + degree
                + " mismatch with mapping size " + mapping.size());

        /* Normalize the mapping to ints. */
        int[] result = mapping.stream().mapToInt(Number::intValue).toArray();

        /* Check that every destination goal is valid (in [0,n)). */
        int[] invalid =
            IntStream.of(result).filter(i -> i < 0 || i >= degree).toArray();
        if (invalid.length > 0) throw new IllegalArgumentException("bad index: "
            + IntStream.of(invalid).mapToObj(Integer::toString)
                .collect(Collectors.joining(",")));

        /* Check that every goal is a destination. */
        if (IntStream.of(result).distinct().count() != degree)
            throw new IllegalArgumentException("incomplete mapping for degree "
                + degree + ": " + mapping);

        if (IntStream.range(0, result.length).anyMatch(i -> i != result[i]))
            return result;
        return null;
    }
}
