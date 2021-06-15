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

import javax.script.ScriptException;

/**
 * Models the resources available in an inferior network, permitting
 * cost evaluation of proposed sub-services.
 *
 * @author simpsons
 */
public interface NetworkModel {
    /**
     * Evaluate the cost of deploying a service across this model. An
     * estimation may be returned. The number of goals must be the
     * degree of the demand function. The indices of the {@code goals}
     * array must correspond to the bit positions of the bit sets
     * supplied to the demand function.
     * 
     * @param goals the numbers of the ports to be connected
     * 
     * @param demand the function determining demand on an edge given a
     * set of goals reachable from one of its vertices without
     * traversing it
     * 
     * @return the cost of the proposed service
     */
    double evaluate(int[] goals, DemandFunction demand);

    /**
     * Get a Python representation of the function. See
     * {@link #fromScript(String)} for its specification.
     * 
     * @return the Python representation of the function
     * 
     * @see #fromScript(String)
     */
    String asScript();

    /**
     * The name of the function embedded in the output of
     * {@link #asScript()}
     */
    String EVALUATE_FUNCTION_NAME = "evaluate";

    /**
     * Create a model function from a Python representation. The string
     * must be the body of a Python class definition with no
     * indentation. A class method called
     * {@value #EVALUATE_FUNCTION_NAME} takes a class reference, an
     * array of integer port numbers, and demand function, and returns a
     * cost evaluation.
     * 
     * <pre>
     * &#64;classmethod
     * def evaluate(cls, gls, df):
     *     <var>...</var>
     * </pre>
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
    public static NetworkModel fromScript(String text) throws ScriptException {
        throw new UnsupportedOperationException("unimplemented");
    }
}
