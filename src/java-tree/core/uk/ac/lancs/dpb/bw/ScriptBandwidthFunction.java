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

import java.math.BigInteger;
import java.util.BitSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import org.python.core.PyList;

/**
 * Expresses bandwidth requirements specified by the body of a Python
 * class definition. The definition must have a
 * {@value BandwidthFunction#DEGREE_FIELD_NAME} field specifying the
 * function's degree, and a {@value BandwidthFunction#GET_FUNCTION_NAME}
 * class method taking the enclosing class and a single integer argument
 * representing the <cite>from</cite> set as a bit set, and returning a
 * 2-array of the minimum and maximum rates.
 *
 * @author simpsons
 */
final class ScriptBandwidthFunction implements BandwidthFunction {
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
    public ScriptBandwidthFunction(String text) throws ScriptException {
        this.text = text;
        ScriptEngineManager manager = new ScriptEngineManager();
        engine = manager.getEngineByName("jython");
        assert engine != null : "no python script engine";
        ScriptContext ctxt = new SimpleScriptContext();
        engine.setContext(ctxt);
        engine.eval("class Foo:\n" + indent(text));
        degree = (Integer) engine.eval("Foo." + DEGREE_FIELD_NAME);
    }

    static final Pattern LINESEP = Pattern.compile("\n");

    static String indent(String text) {
        return Stream.of(LINESEP.split(text))
            .map(s -> "    " + s.stripTrailing() + "\n")
            .collect(Collectors.joining());
    }

    @Override
    public BandwidthRange get(BitSet from) {
        String cmd = "Foo." + GET_FUNCTION_NAME + "("
            + toBigInteger(from.get(0, degree())) + ")";
        try {
            PyList r = (PyList) engine.eval(cmd);
            double min = ((Number) r.get(0)).doubleValue();
            double max = ((Number) r.get(1)).doubleValue();
            return BandwidthRange.between(min, max);
        } catch (ScriptException ex) {
            throw new IllegalArgumentException(cmd, ex);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException |
                 ClassCastException ex) {
            throw new IllegalArgumentException("invalid 'from' set " + from,
                                               ex);
        }
    }

    @Override
    public String asScript() {
        return text;
    }

    @Override
    public int degree() {
        return degree;
    }

    private static void reverse(byte[] array) {
        for (int i = 0; i < array.length / 2; i++) {
            byte tmp = array[i];
            array[i] = array[array.length - 1 - i];
            array[array.length - 1 - i] = tmp;
        }
    }

    static BigInteger toBigInteger(BitSet set) {
        byte[] bs = set.toByteArray();
        reverse(bs);
        if (bs.length > 0 && bs[0] < 0) {
            /* The first byte defines the sign of big integer. We don't
             * want a negative, so we insert an extra zero byte. */
            byte[] tmp = new byte[bs.length + 1];
            System.arraycopy(bs, 0, tmp, 1, bs.length);
        }
        return new BigInteger(bs);
    }
}