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
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;

/**
 * Holds a modifiable set of goals. An immutable set can be created from
 * one with {@link #freeze()}.
 *
 * @author simpsons
 */
class MutableGoalSet extends GoalSet {
    MutableGoalSet(int degree, long[] words) {
        super(degree, words);
    }

    /**
     * Create an immutable copy of this goal set.
     * 
     * @return an immutable copy of this goal set
     * 
     * @constructor
     */
    public GoalSet freeze() {
        return new GoalSet(degree, Arrays.copyOf(words, words.length));
    }

    /**
     * Create a goal set from an array of integers.
     * 
     * @param degree the new set's degree
     * 
     * @param goals the goals
     * 
     * @return a new set containing the goals
     * 
     * @throws IllegalArgumentException if a goal is outside the range
     * permitted by the degree
     * 
     * @constructor
     */
    public static MutableGoalSet of(int degree, int... goals) {
        return new MutableGoalSet(degree, ofInternal(degree, goals));
    }

    /**
     * Create a goal set from a set of numbers.
     * {@link Number#intValue()} is applied to each element of the input
     * set.
     * 
     * @param degree the new set's degree
     * 
     * @param goals the set of goals
     * 
     * @return a new set containing the goals
     * 
     * @throws IllegalArgumentException if a goal is outside the range
     * permitted by the degree
     * 
     * @see #degree()
     * 
     * @constructor
     */
    public static MutableGoalSet of(int degree,
                                    Collection<? extends Number> goals) {
        return new MutableGoalSet(degree, ofInternal(degree, goals));
    }

    /**
     * Create an empty goal set.
     * 
     * @param degree the new set's degree
     * 
     * @return an empty goal set
     * 
     * @constructor
     */
    public static MutableGoalSet of(int degree) {
        return new MutableGoalSet(degree, ofInternal(degree));
    }

    /**
     * Create a goal set from the bit pattern of an integer.
     * 
     * @param degree the new set's degree
     * 
     * @param value the integer value whose bits identify the set's
     * members
     * 
     * @return a set containing the specified goals
     * 
     * @throws IllegalArgumentException if a goal is outside the range
     * permitted by the degree
     * 
     * @see #degree()
     * 
     * @constructor
     */
    public static MutableGoalSet of(int degree, BigInteger value) {
        return new MutableGoalSet(degree, ofInternal(degree, value));
    }

    /**
     * Create a goal set from a bit set.
     * 
     * @param degree the new set's degree
     * 
     * @param value the set of goals
     * 
     * @return a set containing the specified goals
     * 
     * @throws IllegalArgumentException if a goal is outside the range
     * permitted by the degree
     * 
     * @see #degree()
     * 
     * @constructor
     */
    public static MutableGoalSet of(int degree, BitSet value) {
        return new MutableGoalSet(degree, ofInternal(degree, value));
    }

    private void op(int bitIndex, Operator op) {
        if (bitIndex < 0 || bitIndex >= degree)
            throw new IllegalArgumentException("bad index 0 <= " + bitIndex
                + " < " + degree);
        final int wi = bitIndex / WORD_SIZE;
        final int bn = bitIndex % WORD_SIZE;
        op.operate(wi, 1L << bn);
    }

    /**
     * Remove all members from this set.
     */
    public void clear() {
        op(this::andNot);
    }

    /**
     * Add all possible members to this set.
     */
    public void set() {
        op(this::or);
    }

    /**
     * Toggle the presence of all possible members of this set.
     */
    public void flip() {
        op(this::xor);
    }

    private void or(int idx, long val) {
        words[idx] |= val;
    }

    private void andNot(int idx, long val) {
        words[idx] &= ~val;
    }

    private void and(int idx, long val) {
        words[idx] &= ~val;
    }

    private void xor(int idx, long val) {
        words[idx] ^= val;
    }

    private void op(GoalSet other, Operator op) {
        if (other.degree != degree)
            throw new IllegalArgumentException("our degree: " + degree
                + "; their degree: " + other.degree);
        for (int i = 0; i < words.length; i++)
            op.operate(i, other.word(i));
    }

    /**
     * Remove members of a another set from this one.
     * 
     * @param other the other set
     */
    public void clear(GoalSet other) {
        op(other, this::andNot);
    }

    /**
     * Add members of a another set to this one.
     * 
     * @param other the other set
     */
    public void set(GoalSet other) {
        op(other, this::or);
    }

    /**
     * Remove members of this set that are not in another one.
     * 
     * @param other the other set
     */
    public void retain(GoalSet other) {
        op(other, this::and);
    }

    /**
     * Toggle the presence of members of this set that are also in
     * another.
     * 
     * @param other the other set
     */
    public void flip(GoalSet other) {
        op(other, this::xor);
    }

    /**
     * Create a set containing all possible members.
     * 
     * @param degree the set's degree
     * 
     * @return a set of all members
     * 
     * @constructor
     */
    public static MutableGoalSet allOf(int degree) {
        return new MutableGoalSet(degree, allOfInternal(degree));
    }

    private void op(Operator op) {
        for (int i = 0; i < words.length; i++)
            op.operate(i, mask(i));
    }

    /**
     * Include a goal.
     * 
     * @param bitIndex the goal to include
     */
    public void set(int bitIndex) {
        op(bitIndex, this::or);
    }

    /**
     * Exclude a goal.
     * 
     * @param bitIndex the goal to exclude
     */
    public void clear(int bitIndex) {
        op(bitIndex, this::andNot);
    }

    /**
     * Toggle the presence of a goal.
     * 
     * @param bitIndex the goal whose presence is to be toggled
     */
    public void flip(int bitIndex) {
        op(bitIndex, this::xor);
    }

    /**
     * Set the presence of a goal.
     * 
     * @param bitIndex the goal to be included or excluded
     * 
     * @param value {@code true} if the goal is to be included;
     * {@code false} if it is to be excluded
     */
    public void set(int bitIndex, boolean value) {
        if (value)
            set(bitIndex);
        else
            clear(bitIndex);
    }

    /**
     * Set the presence of a range of goals.
     * 
     * @param fromIndex the index of the first goal to modify
     * 
     * @param toIndex the index just after the last goal to modify
     * 
     * @throws IllegalArgumentException if either index is negative, the
     * upper index is less then the lower index, the lower index is not
     * less than the degree, or the upper index is more than the degree
     * 
     * @param value {@code true} if the specified goals are to be
     * included; {@code false} if they are to be excluded
     */
    public void set(int fromIndex, int toIndex, boolean value) {
        if (value)
            set(fromIndex, toIndex);
        else
            clear(fromIndex, toIndex);
    }

    @FunctionalInterface
    private interface Operator {
        void operate(int index, long value);
    }

    private void op(int fromIndex, int toIndex, Operator op) {
        if (fromIndex < 0 || fromIndex >= degree || toIndex < 0 ||
            toIndex > degree || toIndex < fromIndex)
            throw new IllegalArgumentException("bad index 0 <= " + fromIndex
                + ".." + toIndex + " < " + degree);

        if (fromIndex == toIndex) return;

        final int fwi = fromIndex / WORD_SIZE;
        final int fbn = fromIndex % WORD_SIZE;
        final int twi = toIndex / WORD_SIZE;
        final int tbn = toIndex % WORD_SIZE;

        if (fwi == twi) {
            /* Modify using the pattern 00111000, with the different in
             * bit index as the number of 1s, and fbn trailing
             * zeroes. */
            op.operate(fwi, ~(~0L << (tbn - fbn)) << fbn);
            return;
        }

        /* Modify the lowest word using 11100000. */
        op.operate(fwi, ~0L << fbn);
        for (int i = fwi + 1; i < twi; i++)
            /* Modify all bits of intermediate words. */
            op.operate(i, 0xffffffffffffffffL);
        /* Modify the highest word using 00000111. */
        if (tbn > 0) op.operate(twi, ~0L >>> (WORD_SIZE - tbn));
    }

    /**
     * Toggle the presence of a range of goals.
     * 
     * @param fromIndex the index of the first goal to modify
     * 
     * @param toIndex the index just after the last goal to modify
     * 
     * @throws IllegalArgumentException if either index is negative, the
     * upper index is less then the lower index, the lower index is not
     * less than the degree, or the upper index is more than the degree
     */
    public void flip(int fromIndex, int toIndex) {
        op(fromIndex, toIndex, this::xor);
    }

    /**
     * Include a range of goals.
     * 
     * @param fromIndex the index of the first goal to modify
     * 
     * @param toIndex the index just after the last goal to modify
     * 
     * @throws IllegalArgumentException if either index is negative, the
     * upper index is less then the lower index, the lower index is not
     * less than the degree, or the upper index is more than the degree
     */
    public void set(int fromIndex, int toIndex) {
        op(fromIndex, toIndex, this::or);
    }

    /**
     * Retain only a range of goals.
     * 
     * @param fromIndex the index of the first goal to retain
     * 
     * @param toIndex the index just after the last goal to retain
     * 
     * @throws IllegalArgumentException if either index is negative, the
     * upper index is less then the lower index, the lower index is not
     * less than the degree, or the upper index is more than the degree
     */
    public void retain(int fromIndex, int toIndex) {
        op(fromIndex, toIndex, this::and);
    }

    /**
     * Exclude a range of goals.
     * 
     * @param fromIndex the index of the first goal to modify
     * 
     * @param toIndex the index just after the last goal to modify
     * 
     * @throws IllegalArgumentException if either index is negative, the
     * upper index is less then the lower index, the lower index is not
     * less than the degree, or the upper index is more than the degree
     */
    public void clear(int fromIndex, int toIndex) {
        op(fromIndex, toIndex, this::andNot);
    }

    /**
     * @undocumented
     */
    public static void main(String[] args) {
        {
            MutableGoalSet ex0 = MutableGoalSet.allOf(10);
            System.out.println(ex0);
            ex0.clear(3);
            System.out.println(ex0);
            ex0.clear(1);
            System.out.println(ex0);
            ex0.clear(0);
            System.out.println(ex0);
            ex0.clear(1, 4);
            System.out.println(ex0);
            ex0.flip(5, 8);
            System.out.println(ex0);
            ex0.set(0, 2);
            System.out.println(ex0);
            ex0.set(4, 7);
            System.out.println(ex0);
        }

        {
            MutableGoalSet ex0 = MutableGoalSet.of(80);
            System.out.println(ex0);
            ex0.clear(3);
            System.out.println(ex0);
            ex0.set(19, 34);
            System.out.println(ex0);
            ex0.set(60, 71);
            System.out.println(ex0);
            ex0.flip(30, 65);
            System.out.println(ex0);
        }

        {
            MutableGoalSet ex0 = MutableGoalSet.of(140);
            System.out.println(ex0);
            ex0.clear(3);
            System.out.println(ex0);
            ex0.set(95, 110);
            System.out.println(ex0);
            ex0.set(120, 135);
            System.out.println(ex0);
            ex0.flip(105, 125);
            System.out.println(ex0);
        }

        {
            MutableGoalSet ex0 = MutableGoalSet.of(140);
            ex0.set(50, 120);
            MutableGoalSet ex1 = MutableGoalSet.of(140);
            ex1.set(20, 37);
            ex1.set(60, 72);
            ex1.set(94, 105);
            ex1.set(94, 105);
            ex1.set(125, 130);
            ex0.flip(ex1);
            System.out.println(ex0);
        }
    }
}
