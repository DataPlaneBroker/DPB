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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a regular expression structurally. Start with either
 * {@link #EMPTY} or {@link #START}, then append with
 * {@link #then(CharSequence)}, {@link #then(Expression)} or
 * {@link #then(Expression, Expression, Expression...)}. Finally,
 * convert to a {@link Pattern} with {@link #render()}. Captures are
 * accessed by defining {@link Capture} objects that convert text to a
 * specific type, and associating these with parts of the expression
 * with {@link #capture(Capture)}.
 * 
 * @author simpsons
 */
public abstract class Expression {
    Expression() {}

    /**
     * Append an expression to this one.
     * 
     * @param next the following expression
     * 
     * @return an expression equivalent to this one followed by the next
     * one
     */
    public final Expression then(Expression next) {
        Expression joined = join(next);
        if (joined != null) return joined;
        return Sequence.of(this, next);
    }

    /**
     * Create an expression matching literal text.
     * 
     * @param text the literal text to match
     * 
     * @return an expression matching the literal text
     */
    public static Expression literal(CharSequence text) {
        return Literal.of(text);
    }

    /**
     * Create an excluding character set between two codepoints.
     * 
     * @param from the first codepoint
     * 
     * @param to the last codepoint
     * 
     * @return a character set excluding exactly the specified
     * codepoints
     */
    public static CharacterSet notChars(int from, int to) {
        return CharacterSet.except(from, to);
    }

    /**
     * Create an including character set between two codepoints.
     * 
     * @param from the first codepoint
     * 
     * @param to the last codepoint
     * 
     * @return a character set including exactly the specified
     * codepoints
     */
    public static CharacterSet chars(int from, int to) {
        return CharacterSet.of(from, to);
    }

    /**
     * Create an excluding character set of the specified characters
     * 
     * @param text the characters to be excluded
     * 
     * @return a character set excluding exactly the specified
     * characters
     */
    public static CharacterSet notChars(CharSequence text) {
        return CharacterSet.except(text);
    }

    /**
     * Create an including character set of the specified characters
     * 
     * @param text the characters to be included
     * 
     * @return a character set including exactly the specified
     * characters
     */
    public static CharacterSet chars(CharSequence text) {
        return CharacterSet.of(text);
    }

    /**
     * Append a choice to this expression.
     * 
     * @param next1 the first choice
     * 
     * @param next2 the second choice
     * 
     * @param rem remaining choices
     * 
     * @return an expression equivalent to this one followed by a choice
     * of the given expressions
     */
    public final Expression then(Expression next1, Expression next2,
                                 Expression... rem) {
        return then(choice(next1, next2, rem));
    }

    /**
     * Create a choice expression.
     * 
     * @param next1 the first choice
     * 
     * @param next2 the second choice
     * 
     * @param rem remaining choices
     * 
     * @return an expression matching any of the choices
     */
    public static Expression choice(Expression next1, Expression next2,
                                    Expression... rem) {
        Expression[] arr = new Expression[rem.length + 2];
        arr[0] = next1;
        arr[1] = next2;
        System.arraycopy(rem, 0, arr, 2, rem.length);
        return Choice.choice(arr);
    }

    /**
     * Append a choice of literal text to this expression.
     * 
     * @param next1 the first choice
     * 
     * @param next2 the second choice
     * 
     * @param rem remaining choices
     * 
     * @return an expression equivalent to this one followed by a choice
     * of the given literal texts
     */
    public final Expression then(CharSequence next1, CharSequence next2,
                                 CharSequence... rem) {
        return then(choice(next1, next2, rem));
    }

    /**
     * Create a choice of literal text.
     * 
     * @param next1 the first choice
     * 
     * @param next2 the second choice
     * 
     * @param rem remaining choices
     * 
     * @return an expression matching any of the given literal texts
     */
    public static Expression choice(CharSequence next1, CharSequence next2,
                                    CharSequence... rem) {
        Expression[] arr = new Expression[rem.length + 2];
        arr[0] = literal(next1);
        arr[1] = literal(next2);
        for (int i = 0; i < rem.length; i++)
            arr[i + 2] = literal(rem[i]);
        return Choice.choice(arr);
    }

    /**
     * Append a literal sequence to this one.
     * 
     * @param text the literal text
     * 
     * @return an expression equivalent to this one followed by the
     * given literal text
     */
    public final Expression then(CharSequence text) {
        return then(Literal.of(text));
    }

    /**
     * Render the expression as a pattern. Captures within the
     * expression are assigned their respective positions.
     * 
     * @return the equivalent pattern
     */
    public final Pattern render() {
        Rendition ctxt = new Rendition();
        StringBuilder text = new StringBuilder();
        render(text, ctxt);
        Pattern result = Pattern.compile(text.toString());
        ctxt.assign(result);
        return result;
    }

    /**
     * Capture the current expression.
     * 
     * @param c the key for extracting the capture
     * 
     * @return an expression which captures this expression
     */
    public final Expression capture(Capture<?> c) {
        Expression base = this;
        return new Expression() {
            @Override
            void render(StringBuilder buf, Rendition ctxt) {
                buf.append('(');
                if (ctxt != null) ctxt.claim(c);
                base.render(buf, ctxt);
                buf.append(')');
            }
        };
    }

    abstract void render(StringBuilder buf, Rendition ctxt);

    /**
     * Fuse this expression with another one.
     * 
     * @param next an expression to be joined in sequence with this one
     * 
     * @return a new expression equivalent to this one joined to the
     * supplied one, or {@code null} if no such fusion can occur
     */
    Expression join(Expression next) {
        if (isEmpty()) return next;
        if (next.isEmpty()) return this;
        if (next instanceof Sequence) {
            Sequence snext = (Sequence) next;
            List<Expression> parts = new ArrayList<>(1 + snext.parts.size());
            parts.add(this);
            parts.addAll(snext.parts);
            return Sequence.of(parts);
        }
        return null;
    }

    /**
     * Determine whether this expression matches nothing.
     * 
     * @return {@code true} if this expression matches nothing
     */
    boolean isEmpty() {
        return false;
    }

    /**
     * Create an expression matching any of several options.
     * 
     * @param options the options
     * 
     * @return an expression matching any of the given options
     */
    public static Expression choice(Expression... options) {
        return Choice.of(Arrays.asList(options));
    }

    boolean starting() {
        return false;
    }

    boolean ending() {
        return false;
    }

    /**
     * An empty expression
     */
    public static final Expression EMPTY = new Expression() {
        @Override
        Expression join(Expression next) {
            return next;
        }

        @Override
        boolean isEmpty() {
            return true;
        }

        @Override
        void render(StringBuilder buf, Rendition ctxt) {}
    };

    /**
     * An expression matching the start of the string
     */
    public static final Expression START = new Expression() {
        @Override
        void render(StringBuilder buf, Rendition ctxt) {
            buf.append('^');
        }

        @Override
        public boolean starting() {
            return true;
        }
    };

    /**
     * An expression matching the end of the string
     */
    public static final Expression END = new Expression() {
        @Override
        void render(StringBuilder buf, Rendition ctxt) {
            buf.append('$');
        }

        @Override
        public boolean ending() {
            return true;
        }
    };

    /**
     * An expression matching any single character
     */
    static final Expression ANY = new Expression() {
        @Override
        void render(StringBuilder buf, Rendition ctxt) {
            buf.append('.');
        }
    };

    /**
     * Create a string representation of this expression.
     * 
     * @return this expression as a regular expression
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        render(result, null);
        return result.toString();
    }

    private final Expression times(Zeal zeal, int min, int max) {
        return Repetition.of(this, zeal, min, max);
    }

    private final Expression atLeast(Zeal zeal, int min) {
        return Repetition.of(this, zeal, min);
    }

    private final Expression upTo(Zeal zeal, int max) {
        return times(zeal, 0, max);
    }

    private final Expression times(Zeal zeal, int amount) {
        return times(zeal, amount, amount);
    }

    private final Expression some(Zeal zeal) {
        return atLeast(zeal, 1);
    }

    private final Expression any(Zeal zeal) {
        return atLeast(zeal, 0);
    }

    /**
     * Create an expression equivalent to a greedy repetition of this
     * one a given range of times.
     * 
     * @param min the minimum number of times the expression may appear
     * 
     * @param max the maximum number of times the expression may appear
     * 
     * @return an expression matching this one repeated the specified
     * range of times
     */
    public final Expression times(int min, int max) {
        return times(Zeal.GREEDY, min, max);
    }

    /**
     * Create an expression equivalent to a greedy repetition of this
     * one at least a given number of times.
     * 
     * @param min the minimum number of times the expression may appear
     * 
     * @return an expression matching this one repeated no more than the
     * specified number of times
     */
    public final Expression atLeast(int min) {
        return atLeast(Zeal.GREEDY, min);
    }

    /**
     * Create an expression equivalent to a greedy repetition of this
     * one up to a given number of times.
     * 
     * @param max the maximum number of times the expression may appear
     * 
     * @return an expression matching this one repeated up to the
     * specified number of times
     */
    public final Expression upTo(int max) {
        return upTo(Zeal.GREEDY, max);
    }

    /**
     * Create an expression equivalent to a greedy repetition of this
     * one a given number of times.
     * 
     * @param amount the number of times the expression must appear
     * 
     * @return an expression matching this one repeated the specified
     * number of times
     */
    public final Expression times(int amount) {
        return times(Zeal.GREEDY, amount);
    }

    /**
     * Create an expression equivalent to a greedy repetition of this
     * one at least once.
     * 
     * @return an expression matching this one repeated at least once
     */
    public final Expression some() {
        return some(Zeal.GREEDY);
    }

    /**
     * Create an expression equivalent to a greedy repetition of this
     * one any number of times, including zero.
     * 
     * @return an expression matching this one repeated any number of
     * times
     */
    public final Expression any() {
        return any(Zeal.GREEDY);
    }

    /**
     * Create an expression equivalent to this one at the end of the
     * string.
     * 
     * @return an expression matching this one at the end of the string
     */
    public final Expression end() {
        return then(END);
    }

    /**
     * Create an expression equivalent to a reluctant repetition of this
     * one a given range of times.
     * 
     * @param min the minimum number of times the expression may appear
     * 
     * @param max the maximum number of times the expression may appear
     * 
     * @return an expression matching this one repeated the specified
     * range of times
     */
    public final Expression timesReluctant(int min, int max) {
        return times(Zeal.RELUCTANT, min, max);
    }

    /**
     * Create an expression equivalent to a reluctant repetition of this
     * one at least a given number of times.
     * 
     * @param min the minimum number of times the expression may appear
     * 
     * @return an expression matching this one repeated no more than the
     * specified number of times
     */
    public final Expression atLeastReluctant(int min) {
        return atLeast(Zeal.RELUCTANT, min);
    }

    /**
     * Create an expression equivalent to a reluctant repetition of this
     * one up to a given number of times.
     * 
     * @param max the maximum number of times the expression may appear
     * 
     * @return an expression matching this one repeated up to the
     * specified number of times
     */
    public final Expression upToReluctant(int max) {
        return upTo(Zeal.RELUCTANT, max);
    }

    /**
     * Create an expression equivalent to a reluctant repetition of this
     * one a given number of times.
     * 
     * @param amount the number of times the expression must appear
     * 
     * @return an expression matching this one repeated the specified
     * number of times
     */
    public final Expression timesReluctant(int amount) {
        return times(Zeal.RELUCTANT, amount);
    }

    /**
     * Create an expression equivalent to a reluctant repetition of this
     * one at least once.
     * 
     * @return an expression matching this one repeated at least once
     */
    public final Expression someReluctant() {
        return some(Zeal.RELUCTANT);
    }

    /**
     * Create an expression equivalent to a reluctant repetition of this
     * one any number of times, including zero.
     * 
     * @return an expression matching this one repeated any number of
     * times
     */
    public final Expression anyReluctant() {
        return any(Zeal.RELUCTANT);
    }

    /**
     * Create an expression equivalent to a possessive repetition of
     * this one a given range of times.
     * 
     * @param min the minimum number of times the expression may appear
     * 
     * @param max the maximum number of times the expression may appear
     * 
     * @return an expression matching this one repeated the specified
     * range of times
     */
    public final Expression timesPossessive(int min, int max) {
        return times(Zeal.POSSESSIVE, min, max);
    }

    /**
     * Create an expression equivalent to a possessive repetition of
     * this one at least a given number of times.
     * 
     * @param min the minimum number of times the expression may appear
     * 
     * @return an expression matching this one repeated no more than the
     * specified number of times
     */
    public final Expression atLeastPossessive(int min) {
        return atLeast(Zeal.POSSESSIVE, min);
    }

    /**
     * Create an expression equivalent to a possessive repetition of
     * this one up to a given number of times.
     * 
     * @param max the maximum number of times the expression may appear
     * 
     * @return an expression matching this one repeated up to the
     * specified number of times
     */
    public final Expression upToPossessive(int max) {
        return upTo(Zeal.POSSESSIVE, max);
    }

    /**
     * Create an expression equivalent to a possessive repetition of
     * this one a given number of times.
     * 
     * @param amount the number of times the expression must appear
     * 
     * @return an expression matching this one repeated the specified
     * number of times
     */
    public final Expression timesPossessive(int amount) {
        return times(Zeal.POSSESSIVE, amount);
    }

    /**
     * Create an expression equivalent to a possessive repetition of
     * this one at least once.
     * 
     * @return an expression matching this one repeated at least once
     */
    public final Expression somePossessive() {
        return some(Zeal.POSSESSIVE);
    }

    /**
     * Create an expression equivalent to a possessive repetition of
     * this one any number of times, including zero.
     * 
     * @return an expression matching this one repeated any number of
     * times
     */
    public final Expression anyPossessive() {
        return any(Zeal.POSSESSIVE);
    }

    /**
     * @undocumented
     */
    public static void main(String[] args) throws Exception {
        Capture<String> ANSWER = Capture.ofString();
        Pattern p =
            notChars('a', 'z').with("ACE").express().capture(ANSWER).render();
        System.out.printf("Result: %s%n", p.pattern());
        for (String arg : args) {
            Matcher m = p.matcher(arg);
            if (m.matches())
                System.out.printf("%s -> %s%n", arg, ANSWER.get(m));
        }
    }
}
