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

/**
 * 
 * 
 * @author simpsons
 */
final class Repetition extends Expression {
    final Expression base;
    final int min;
    final Integer max;
    final Zeal zeal;

    static Expression of(Expression base, Zeal zeal, int min) {
        if (min < 0) throw new IllegalArgumentException("-ve: " + base);
        if (base.isEmpty()) return Expression.EMPTY;
        return new Repetition(base, zeal, min, null);
    }

    static Expression of(Expression base, Zeal zeal, int min, int max) {
        if (max < min) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        if (min < 0) throw new IllegalArgumentException("-ve min: " + base);
        if (max < 0) throw new IllegalArgumentException("-ve max: " + base);
        if (max == 0) return Expression.EMPTY;
        if (base.isEmpty()) return Expression.EMPTY;
        if (min == 1 && max == 1) return base;
        return new Repetition(base, zeal, min, max);
    }

    private Repetition(Expression base, Zeal zeal, int min, Integer max) {
        this.base = base;
        this.zeal = zeal;
        this.min = min;
        this.max = max;
    }

    @Override
    void render(StringBuilder buf, Rendition ctxt) {
        buf.append("(?:").append(base).append(')');
        if (max == null) {
            if (min == 0)
                buf.append('*');
            else if (min == 1)
                buf.append('+');
            else
                buf.append('{').append(min).append(",}");
        } else if (min == max) {
            buf.append('{').append(min).append('}');
        } else {
            buf.append('{').append(min).append(',').append(max).append('}');
        }
        buf.append(zeal.suffix);
    }
}
