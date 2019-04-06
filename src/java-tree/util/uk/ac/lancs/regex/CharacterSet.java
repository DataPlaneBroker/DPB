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

import java.util.BitSet;

/**
 * Creates expressions matching ranges of characters.
 * 
 * @author simpsons
 */
public final class CharacterSet {
    final BitSet selected;
    final boolean except;

    private CharacterSet(boolean except, BitSet selected) {
        this.selected = selected;
        this.except = except;
    }

    private static CharacterSet of(boolean except, BitSet selected) {
        return new CharacterSet(except, selected);
    }

    private static CharacterSet of(BitSet selected) {
        return of(false, selected);
    }

    static CharacterSet ofNone() {
        return of(false, new BitSet());
    }

    static CharacterSet exceptNone() {
        return of(true, new BitSet());
    }

    static CharacterSet of(int codePoint) {
        return of(codePoint, codePoint);
    }

    static CharacterSet of(int from, int to) {
        BitSet selected = new BitSet();
        selected.set(from, to + 1);
        return of(selected);
    }

    static CharacterSet of(CharSequence text) {
        BitSet selected = new BitSet();
        for (int i : text.codePoints().toArray())
            selected.set(i);
        return of(selected);
    }

    private static CharacterSet except(BitSet selected) {
        return of(true, selected);
    }

    static CharacterSet except(int codePoint) {
        return except(codePoint, codePoint);
    }

    static CharacterSet except(int from, int to) {
        BitSet selected = new BitSet();
        selected.set(from, to + 1);
        return except(selected);
    }

    static CharacterSet except(CharSequence text) {
        BitSet selected = new BitSet();
        for (int i : text.codePoints().toArray())
            selected.set(i);
        return except(selected);
    }

    /**
     * Add the given range of characters to the selection.
     * 
     * @param from the first character's codepoint
     * 
     * @param to the second character's codepoint
     * 
     * @return a new character set selecting the given characters
     */
    public CharacterSet with(int from, int to) {
        BitSet selected = (BitSet) this.selected.clone();
        selected.set(from, to + 1, true);
        return of(except, selected);
    }

    /**
     * Add the given character to the selection.
     * 
     * @param c the character to add
     * 
     * @return a new character set selecting the given character
     */
    public CharacterSet with(int c) {
        return with(c, c);
    }

    /**
     * Add the given characters to the selection.
     * 
     * @param text the characters to add
     * 
     * @return a new character set selecting the given characters
     */
    public CharacterSet with(CharSequence text) {
        BitSet selected = (BitSet) this.selected.clone();
        for (int i : text.codePoints().toArray())
            selected.set(i, true);
        return of(except, selected);
    }

    /**
     * Remove the given range of characters from the selection.
     * 
     * @param from the first character's codepoint
     * 
     * @param to the second character's codepoint
     * 
     * @return a new character set deselecting the given characters
     */
    public CharacterSet without(int from, int to) {
        BitSet selected = (BitSet) this.selected.clone();
        selected.set(from, to + 1, false);
        return of(except, selected);
    }

    /**
     * Remove the given character from the selection.
     * 
     * @param c the character to remove
     * 
     * @return a new character set deselecting the given character
     */
    public CharacterSet without(int c) {
        return without(c, c);
    }

    /**
     * Remove the given characters from the selection.
     * 
     * @param text the characters to remove
     * 
     * @return a new character set deselecting the given characters
     */
    public CharacterSet without(CharSequence text) {
        BitSet selected = (BitSet) this.selected.clone();
        for (int i : text.codePoints().toArray())
            selected.set(i, false);
        return of(except, selected);
    }

    private static String escape(int code) {
        switch (code) {
        case ']':
        case '[':
        case '\\':
        case '-':
            return "\\" + (char) code;
        default:
            if (code >= 32 && code < 127) return codePointToString(code);
            return "\\x" + Integer.toHexString(code);
        }
    }

    private static String codePointToString(int code) {
        char[] buf = Character.toChars(code);
        return new String(buf, 0, buf.length);
    }

    /**
     * Create an expression matching the given set of characters.
     * 
     * @return the expression matching the given characters
     */
    public Expression express() {
        final BitSet selected = (BitSet) this.selected.clone();
        final boolean except = this.except;
        if (except) {
            if (selected.nextSetBit(0) < 0) return Expression.ANY;
        } else {
            int got = selected.nextSetBit(0);
            if (got < 0)
                throw new IllegalArgumentException("empty character set");
            if (selected.cardinality() == 1)
                return Literal.of(codePointToString(got));
        }
        return new Expression() {
            @Override
            void render(StringBuilder buf, Rendition ctxt) {
                buf.append('[');
                if (except) buf.append('^');
                for (int next = 0; (next = selected.nextSetBit(next)) > -1;) {
                    int end = selected.nextClearBit(next);
                    buf.append(escape(next));
                    if (end > next + 1)
                        buf.append('-').append(escape(end - 1));
                    next = end;
                }
                buf.append(']');
            }
        };
    }
}
