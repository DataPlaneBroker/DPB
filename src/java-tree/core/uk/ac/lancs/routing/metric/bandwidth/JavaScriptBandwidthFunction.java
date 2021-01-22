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

package uk.ac.lancs.routing.metric.bandwidth;

import java.util.BitSet;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

/**
 * Expresses bandwidth requirements specified by a JavaScript object.
 * The object must have a <samp>degree</samp> field specifying the
 * function's degree, and an <samp>apply</samp> function taking a single
 * integer argument representing the 'from' set as a bit set, and
 * returning a 2-array of the minimum and maximum rates (the latter of
 * which may be {@code null}).
 *
 * @author simpsons
 */
public final class JavaScriptBandwidthFunction implements BandwidthFunction {
    private final ScriptEngine engine;

    private final String text;

    private final int degree;

    /**
     * Express bandwidth requirements specified by a JavaScript object.
     * 
     * @param text the JavaScript text
     * 
     * @throws ScriptException if there is an error parsing the script
     */
    public JavaScriptBandwidthFunction(String text) throws ScriptException {
        this.text = text;
        ScriptEngineManager manager = new ScriptEngineManager();
        engine = manager.getEngineByName("javascript");
        ScriptContext ctxt = new SimpleScriptContext();
        engine.setContext(ctxt);
        engine.eval("var obj = " + text + ";");
        degree = (Integer) engine.eval("obj.degree");
    }

    @Override
    public BandwidthRange apply(BitSet from) {
        String cmd = "obj.apply("
            + BandwidthFunction.toBigInteger(from.get(0, degree())) + ")";
        try {
            Double[] result = (Double[]) engine.eval(cmd);
            return result[1] == null ? BandwidthRange.from(result[0]) :
                BandwidthRange.between(result[0], result[1]);
        } catch (ScriptException ex) {
            throw new IllegalArgumentException(cmd, ex);
        }
    }

    @Override
    public String asJavaScript() {
        return text;
    }

    @Override
    public int degree() {
        return degree;
    }
}
