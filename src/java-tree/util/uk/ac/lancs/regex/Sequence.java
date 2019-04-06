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

package uk.ac.lancs.regex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 
 * 
 * @author simpsons
 */
class Sequence extends Expression {
    final List<Expression> parts;
    final boolean starting, ending;

    
    @Override
    boolean starting() {
        return starting;
    }

    @Override
    boolean ending() {
        return ending;
    }

    @Override
    Expression join(Expression next) {
        if (next.isEmpty()) return this;
        if (next instanceof Sequence) {
            Sequence snext = (Sequence) next;
            List<Expression> parts =
                new ArrayList<>(this.parts.size() + snext.parts.size());
            parts.addAll(this.parts);
            parts.addAll(snext.parts);
            return of(parts);
        }
        List<Expression> parts = new ArrayList<>(this.parts.size() + 1);
        parts.addAll(this.parts);
        parts.add(next);
        return of(parts);
    }

    static Expression of(Expression... parts) {
        return of(new ArrayList<>(Arrays.asList(parts)));
    }

    static Expression of(List<Expression> parts) {
        if (parts.isEmpty()) return Expression.EMPTY;
        for (int i = 1; i < parts.size(); i++) {
            Expression joined = parts.get(i - 1).join(parts.get(i));
            if (joined != null) {
                parts.set(i - 1, joined);
                parts.remove(i);
                i--;
            }
        }
        if (parts.size() == 1) return parts.get(0);
        for (Expression sub : parts.subList(1, parts.size()))
            if (sub.starting())
                throw new IllegalArgumentException("start will never match: "
                    + parts);
        for (Expression sub : parts.subList(0, parts.size() - 1))
            if (sub.ending())
                throw new IllegalArgumentException("end will never match: "
                    + parts);
        boolean starting = parts.get(0).starting();
        boolean ending = parts.get(parts.size() - 1).ending();
        return new Sequence(parts, starting, ending);
    }

    private Sequence(List<Expression> parts, boolean starting,
                     boolean ending) {
        if (parts.size() < 2)
            throw new IllegalArgumentException("must have at least 2 parts: "
                + parts);
        this.parts = parts;
        this.starting = starting;
        this.ending = ending;
    }

    @Override
    void render(StringBuilder result, Rendition ctxt) {
        result.append("(?:");
        for (Expression sub : parts)
            sub.render(result, ctxt);
        result.append(')');
    }
}
