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

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import static uk.ac.lancs.dpb.graph.ScriptDemandFunction.indent;

/**
 * Expresses an inferior network model as the body of a Python class
 * definition.
 *
 * @author simpsons
 */
final class ScriptNetworkModel implements NetworkModel {
    private final int degree;

    private final ScriptEngine engine;

    private final String text;

    private final BiFunction<List<Integer>, Function<Integer, List<Double>>,
                             Number> fooer;

    public ScriptNetworkModel(int degree, String text) throws ScriptException {
        this.text = text;
        this.degree = degree;
        ScriptEngineManager manager = new ScriptEngineManager();
        engine = manager.getEngineByName("jython");
        assert engine != null : "no python script engine";
        engine.eval("class Foo:\n" + indent(text));
        Object foo = engine.get("Foo");
        @SuppressWarnings("unchecked")
        BiFunction<List<Integer>, Function<Integer, List<Double>>,
                   Number> fooer =
                       ((Invocable) engine).getInterface(foo, BiFunction.class);
        this.fooer = fooer;
    }

    @Override
    public double evaluate(List<? extends Number> goals,
                           DemandFunction demand) {
        Function<Integer, List<Double>> demandWrapper = i -> {
            System.err.printf("asking for %d%n", i);
            Capacity c = demand.get(BitSet.valueOf(new long[] { i }));
            return Arrays.asList(c.min(), c.max());
        };
        return fooer.apply(goals.stream().mapToInt(Number::intValue).boxed()
            .collect(Collectors.toList()), demandWrapper).doubleValue();
    }

    @Override
    public String asScript() {
        return text;
    }

    /**
     * @undocumented
     */
    public static void main(String[] args) throws Exception {
        StringBuilder script = new StringBuilder();
        script.append("@classmethod\n");
        script.append("def apply(cls, goals, demand):\n");
        script.append("    demand.apply(7)\n");
        script.append("    return 123.5\n");
        NetworkModel model = new ScriptNetworkModel(12, script.toString());
        DemandFunction demand = new DemandFunction() {
            @Override
            public Capacity get(BitSet from) {
                System.out.printf("reached: %s%n", from);
                return Capacity.between(3.0, 4.0);
            }

            @Override
            public String asScript() {
                throw new UnsupportedOperationException("unsupported");
            }

            @Override
            public int degree() {
                throw new UnsupportedOperationException("unsupported");
            }
        };
        double cost = model.evaluate(Arrays.asList(4, 1, 2), demand);
        System.out.printf("cost = %g%n", cost);
    }

    @Override
    public int degree() {
        return degree;
    }
}
