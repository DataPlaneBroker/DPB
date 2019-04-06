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

import java.util.Collection;

/**
 * 
 * 
 * @author simpsons
 */
class Choice extends Expression {
    final Collection<? extends Expression> options;
    final boolean starting, ending;

    static Expression of(Collection<? extends Expression> options) {
        if (options.isEmpty()) return Expression.EMPTY;
        if (options.size() == 1) return options.iterator().next();
        boolean starting = true, ending = true;
        for (Expression sub : options) {
            if (starting) {
                if (ending) {
                    if (!sub.ending()) ending = false;
                } else if (!sub.starting()) {
                    starting = false;
                    break;
                }
            } else if (ending) {
                if (!sub.ending()) {
                    ending = false;
                    break;
                }
            } else {
                break;
            }
        }
        return new Choice(options, starting, ending);
    }

    @Override
    boolean starting() {
        return starting;
    }

    @Override
    boolean ending() {
        return ending;
    }

    private Choice(Collection<? extends Expression> options, boolean starting,
                   boolean ending) {
        this.options = options;
        this.starting = starting;
        this.ending = ending;
    }

    @Override
    void render(StringBuilder result, Rendition ctxt) {
        String sep = "(?:";
        for (Expression sub : options) {
            result.append(sep);
            sub.render(result, ctxt);
            sep = "|";
        }
        result.append(')');
    }
}
