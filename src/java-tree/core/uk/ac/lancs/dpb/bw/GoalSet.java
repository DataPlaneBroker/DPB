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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.function.IntConsumer;
import java.util.function.LongBinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

/**
 * Holds an immutable set of goal indices. A goal set has a fixed
 * <dfn>degree</dfn>, <var>n</var>. Each goal has a number in
 * [0,<var>n</var>). A mutable copy can be obtained with
 * {@link #mutate()}.
 * 
 * <p>
 * Goal sets are not intrinsically considered comparable, so this class
 * does not implement {@link Comparable}. However, a static comparison
 * method {@link #compare(GoalSet, GoalSet)} is defined, to help where
 * (say) a canonical ordering is required.
 * 
 * @author simpsons
 */
class GoalSet {
    static final int WORD_SIZE = Long.SIZE;

    final long[] words;

    final int degree;

    /**
     * Compute the number of bits used by the last word. This is the
     * remainder after dividing one less than the sum of the degree and
     * the word size by the word size.
     * 
     * @return the number of bits used by the last word, in the range
     * [1, {@value #WORD_SIZE}]
     */
    static int tail(int degree) {
        return (degree + (WORD_SIZE - 1)) % WORD_SIZE + 1;
    }

    static long tailMask(int degree) {
        return ~0L >>> (WORD_SIZE - tail(degree));
    }

    /**
     * Compute the number of words required. This is the quotient of one
     * less than the sum of the degree and the word size divided by the
     * word size.
     * 
     * @return the number of words required
     */
    static int wordCount(int degree) {
        return (degree + (WORD_SIZE - 1)) / WORD_SIZE;
    }

    static long[] ofInternal(int degree) {
        return new long[wordCount(degree)];
    }

    /**
     * Get the set's degree. This is simply the number of distinct goals
     * that may be present, in the range 0 to <var>n</var>&minus;1,
     * where <var>n</var> is the degree.
     * 
     * @return the set's degree
     */
    public int degree() {
        return degree;
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
    public static GoalSet of(int degree) {
        return new GoalSet(degree, ofInternal(degree));
    }

    static long[] allOfInternal(int degree) {
        long[] words = new long[wordCount(degree)];
        for (int i = 0; i + 1 < words.length; i++)
            words[i] = ~0L;
        words[words.length - 1] = tailMask(degree);
        return words;
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
    public static GoalSet allOf(int degree) {
        return new GoalSet(degree, allOfInternal(degree));
    }

    GoalSet(int degree, long[] words) {
        assert words.length == wordCount(degree);
        this.degree = degree;
        this.words = words;
    }

    static long[] ofInternal(int degree, int... goals) {
        long[] words = ofInternal(degree);
        for (int goal : goals) {
            if (goal < 0 || goal >= degree)
                throw new IllegalArgumentException("bad index 0 <= " + goal
                    + " < " + degree);
            words[goal / WORD_SIZE] |= 1L << goal % WORD_SIZE;
        }
        return words;
    }

    /**
     * Get the complement set.
     * 
     * @return the complement set
     */
    public GoalSet complement() {
        long[] words = new long[this.words.length];
        for (int i = 0; i < words.length; i++)
            words[i] = this.words[i] & mask(i);
        return new GoalSet(degree, words);
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
     * @see #degree()
     * 
     * @constructor
     */
    public static GoalSet of(int degree, int... goals) {
        return new GoalSet(degree, ofInternal(degree, goals));
    }

    static long[] ofInternal(int degree, Collection<? extends Number> goals) {
        long[] words = ofInternal(degree);
        for (Number i : goals) {
            int goal = i.intValue();
            if (goal < 0 || goal >= degree)
                throw new IllegalArgumentException("bad index 0 <= " + goal
                    + " < " + degree);
            words[goal / WORD_SIZE] |= 1L << goal % WORD_SIZE;
        }
        return words;
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
    public static GoalSet of(int degree, Collection<? extends Number> goals) {
        return new GoalSet(degree, ofInternal(degree, goals));
    }

    /**
     * Create a mutable copy of this set.
     * 
     * @return a mutable copy of this goal set
     * 
     * @constructor
     */
    public MutableGoalSet mutate() {
        return new MutableGoalSet(degree, Arrays.copyOf(words, words.length));
    }

    /**
     * Get the hash code for this set.
     * 
     * @return the hash code for this set
     */
    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + Arrays.hashCode(words);
        hash = 67 * hash + degree;
        return hash;
    }

    /**
     * Test whether another object equals this set. It must a
     * {@link GoalSet} or {@link MutableGoalSet} of the same degree and
     * contents.
     * 
     * @param obj the other object
     * 
     * @return {@code true} if the other object is an identical goal set
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        final GoalSet other = (GoalSet) obj;
        if (degree != other.degree) return false;
        return Arrays.equals(words, other.words);
    }

    /**
     * Get a string representation of this set. This is the decimal
     * value of each member in increasing order, comma-separated, and
     * surrounded by braces, e.g., <samp>&#123;1,4,9&#125;</samp>.
     * 
     * @return the string representation
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("{");
        int wi = 0;
        long word = words[wi++];
        int prev = -3, last = -2;

        outer: for (;;) {
            while (word == 0) {
                if (wi >= words.length) break outer;
                word = words[wi++];
            }
            long old = word;
            word = word & (word - 1);
            int index =
                (wi - 1) * WORD_SIZE + Long.numberOfTrailingZeros(old ^ word);
            assert index > last;
            if (index > last + 1) {
                /* We have a gap. Print the previous gap first. */
                if (prev < 0) {
                    /* This is the first interval. Do nothing. */
                } else if (last == prev + 1) {
                    result.append(prev).append(',').append(last).append(',');
                } else if (last == prev) {
                    result.append(prev).append(',');
                } else {
                    result.append(prev).append('-').append(last).append(',');
                }
                prev = index;
            }
            last = index;
        }
        if (prev < 0) {
            /* This is the first interval. Do nothing. */
        } else if (last == prev + 1) {
            result.append(prev).append(',').append(last);
        } else if (last == prev) {
            result.append(prev);
        } else {
            result.append(prev).append('-').append(last);
        }
        return result.append('}').toString();
    }

    static long[] binInternal(GoalSet a, GoalSet b, LongBinaryOperator op) {
        if (a.degree() != b.degree())
            throw new IllegalArgumentException("degree mismatch: " + a.degree()
                + " != " + b.degree());
        long[] words = ofInternal(a.degree());
        for (int w = 0; w < words.length; w++)
            words[w] = op.applyAsLong(a.word(w), b.word(w));
        return words;
    }

    /**
     * Create a goal set containing all the members of two other sets.
     * 
     * @param a one of the sets
     * 
     * @param b the other set
     * 
     * @return the union of the two sets
     * 
     * @throws IllegalArgumentException if the goals have different
     * degrees
     */
    public static GoalSet or(GoalSet a, GoalSet b) {
        return new GoalSet(a.degree(), binInternal(a, b, (x, y) -> x | y));
    }

    /**
     * Create a goal set containing only members present in both of two
     * other sets.
     * 
     * @param a one of the sets
     * 
     * @param b the other set
     * 
     * @return the intersection of the two sets
     * 
     * @throws IllegalArgumentException if the goals have different
     * degrees
     */
    public static GoalSet and(GoalSet a, GoalSet b) {
        return new GoalSet(a.degree(), binInternal(a, b, (x, y) -> x & y));
    }

    /**
     * Determine whether this set is a loose superset of another.
     * 
     * @param other the other set
     * 
     * @return {@code true} if this set contains all the members of the
     * other set; {@code false} otherwise
     * 
     * @throws IllegalArgumentException if the goals have different
     * degrees
     */
    public boolean containsAll(GoalSet other) {
        if (degree() != other.degree())
            throw new IllegalArgumentException("degree mismatch: " + degree()
                + " != " + other.degree());
        for (int w = 0; w < words.length; w++) {
            long a = word(w);
            long b = other.word(w);
            if ((a & b) != b) return false;
        }
        return true;
    }

    /**
     * Determine whether the intersection of this set and another is
     * non-empty.
     * 
     * @param other the other set
     * 
     * @return {@code false} if the intersection is empty; {@code true}
     * otherwise
     * 
     * @throws IllegalArgumentException if the goals have different
     * degrees
     */
    public boolean intersects(GoalSet other) {
        if (degree() != other.degree())
            throw new IllegalArgumentException("degree mismatch: " + degree()
                + " != " + other.degree());
        for (int w = 0; w < words.length; w++) {
            long a = word(w);
            long b = other.word(w);
            if ((a & b) != 0) return false;
        }
        return true;
    }

    /**
     * Determine whether the set is empty.
     * 
     * @return {@code true} if the set is empty; {@code false} otherwise
     */
    public boolean isEmpty() {
        for (long w : words)
            if (w != 0) return false;
        return true;
    }

    static long[] ofInternal(int degree, BigInteger value) {
        if (value.signum() < 0)
            throw new IllegalArgumentException("-ve value: " + value);
        BigInteger lim = BigInteger.ONE.shiftLeft(degree);
        if (value.compareTo(lim) >= 0)
            throw new IllegalArgumentException("too big " + value
                + " for degree " + degree);
        byte[] bytes = value.toByteArray();
        long[] words = ofInternal(degree);
        for (int w = 0; w < words.length; w++) {
            final int b0 = w * (Long.SIZE / Byte.SIZE);
            final int b1 = Math.min(b0 + (Long.SIZE / Byte.SIZE), bytes.length);
            final int r1 = bytes.length - b0;
            final int r0 = bytes.length - b1;
            for (int b = r0; b < r1; b++) {
                words[w] <<= Byte.SIZE;
                words[w] |= bytes[b] & 0xffL;
            }
        }
        return words;
    }

    /**
     * Get the value of a word, with invalid bits masked out.
     * 
     * @param i the word index
     * 
     * @return the value of the word
     */
    long word(int i) {
        return words[i] & mask(i);
    }

    /**
     * Get the bit pattern for the valid bits of a word.
     * 
     * @param i the word index
     * 
     * @return the valid bit pattern
     */
    long mask(int i) {
        return i + 1 == words.length ? tailMask(degree) : ~0L;
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
    public static GoalSet of(int degree, BigInteger value) {
        return new GoalSet(degree, ofInternal(degree, value));
    }

    static long[] ofInternal(int degree, BitSet value) {
        return value.get(0, degree).toLongArray();
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
    public static GoalSet of(int degree, BitSet value) {
        return new GoalSet(degree, ofInternal(degree, value));
    }

    /**
     * Get an iteration over all possible values of a goal set, except
     * <var>none</var> and <var>all</var>.
     * 
     * @param degree the degree of each set
     * 
     * @return the requested iteration
     */
    public static Iterable<GoalSet> allValidSets(int degree) {
        return () -> {
            return new Iterator<GoalSet>() {
                private final long[] words = ofInternal(degree);

                private final long[] lims = new long[words.length];

                private final int last = words.length - 1;

                {
                    for (int i = 0; i < last; i++)
                        lims[i] = 0xffffffffffffffffL;
                    if (words.length > 0) {
                        words[0] = 1;
                        lims[last] = tailMask(degree);
                    }
                }

                @Override
                public boolean hasNext() {
                    return Arrays.compare(words, lims) != 0;
                }

                @Override
                public GoalSet next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    GoalSet result =
                        new GoalSet(degree, Arrays.copyOf(words, words.length));
                    for (int i = 0; i < words.length; i++) {
                        if (words[i] < lims[i]) {
                            words[i]++;
                            break;
                        }
                        words[i] = 0;
                    }
                    return result;
                }
            };
        };
    }

    /**
     * Get an immutable list of all values of a goal set of a given
     * degree, except <var>none</var> and <var>all</var>.
     * 
     * @param degree the degree of all sets
     * 
     * @return the requested list
     */
    public static List<GoalSet> listOfValidSets(int degree) {
        List<GoalSet> result = new ArrayList<>((1 << degree) - 2);
        for (GoalSet s : allValidSets(degree))
            result.add(s);
        assert result.size() == (1 << degree) - 2;
        return List.copyOf(result);
    }

    /**
     * Test whether a goal is in the set.
     * 
     * @param bitIndex the goal number
     * 
     * @return {@code true} if the goal is in the set; {@code false}
     * otherwise
     * 
     * @throws IllegalArgumentException if the goal is outside the range
     * permitted by the degree
     */
    public boolean get(int bitIndex) {
        if (bitIndex < 0 || bitIndex >= degree)
            throw new IllegalArgumentException("bad index 0 <= " + bitIndex
                + " < " + degree);
        final int word = bitIndex / WORD_SIZE;
        if (word >= words.length) return false;
        final int bit = bitIndex % WORD_SIZE;
        return (words[word] & (1 << bit)) != 0;
    }

    /**
     * Get the number of goals in this set.
     * 
     * @return the number of goals in this set
     */
    public int size() {
        int sum = 0;
        for (long word : words)
            sum += Long.bitCount(word);
        return sum;
    }

    /**
     * Get a stream of the goals in this set. They are in increasing
     * order.
     * 
     * @return a stream of the goals in this set
     */
    public IntStream stream() {
        final int card = size();

        Spliterator.OfInt sp = new Spliterator.OfInt() {
            int wi = 0;

            long word = words[wi++];

            @Override
            public OfInt trySplit() {
                return null;
            }

            @Override
            public boolean tryAdvance(IntConsumer action) {
                while (word == 0) {
                    if (wi >= words.length) return false;
                    word = words[wi++];
                }
                long old = word;
                word = word & (word - 1);
                action.accept((wi - 1) * WORD_SIZE
                    + Long.numberOfTrailingZeros(old ^ word));
                return true;
            }

            @Override
            public long estimateSize() {
                return card;
            }

            @Override
            public int characteristics() {
                return Spliterator.DISTINCT | Spliterator.NONNULL |
                    Spliterator.ORDERED | Spliterator.SIZED;
            }
        };
        return StreamSupport.intStream(sp, false);
    }

    /**
     * Get a bit set equivalent to this goal set.
     * 
     * @return an equivalent bit set
     */
    public BitSet toBitSet() {
        return BitSet.valueOf(words);
    }

    /**
     * Get a big integer whose bits correspond to this goal set.
     * 
     * @return an equivalent big integer
     */
    public BigInteger toBigInteger() {
        byte[] bytes = new byte[(degree + (Byte.SIZE - 1)) / Byte.SIZE + 1];
        for (int i = 0; i < bytes.length - 1; i++) {
            final int ri = bytes.length - i - 1;
            final int w = i / (Long.SIZE / Byte.SIZE);
            final int b = i % (Long.SIZE / Byte.SIZE);
            final byte val = (byte) ((words[w] >>> (b * Byte.SIZE)) & 0xff);
            bytes[ri] = val;
        }
        return new BigInteger(bytes);
    }

    /**
     * @undocumented
     */
    public static void main(String[] args) {
        {
            GoalSet ex0 = GoalSet.of(0);
            assert ex0.words.length == 0;
        }

        {
            GoalSet ex0 = GoalSet.of(1);
            assert ex0.words.length == 1;
            assert ex0.words[0] == 0;
            assert ex0.mask(0) == 0x1L;
            assert !ex0.get(0);
        }

        {
            GoalSet ex0 = GoalSet.of(7);
            assert ex0.words.length == 1;
            assert ex0.words[0] == 0;
            assert ex0.mask(0) == 0x7fL;
            assert !ex0.get(0);
            assert !ex0.get(1);
            assert !ex0.get(2);
            assert !ex0.get(3);
            assert !ex0.get(4);
            assert !ex0.get(5);
            assert !ex0.get(6);
        }

        {
            GoalSet ex0 = GoalSet.of(64);
            assert tail(ex0.degree) == 64;
            assert ex0.words.length == 1;
            assert ex0.words[0] == 0;
            assert ex0.mask(0) == 0xffffffffffffffffL;
        }

        {
            GoalSet ex0 = GoalSet.of(65);
            assert ex0.words.length == 2;
            assert ex0.words[0] == 0;
            assert ex0.words[1] == 0;
            assert ex0.word(0) == 0;
            assert ex0.word(1) == 0;
            assert ex0.mask(0) == 0xffffffffffffffffL;
            assert ex0.mask(1) == 0x1L;
        }

        {
            GoalSet ex0 = GoalSet.allOf(3);
            assert ex0.words.length == 1;
            assert ex0.words[0] == 7;
            assert ex0.get(0);
            assert ex0.get(1);
            assert ex0.get(2);
            assert ex0.toBigInteger().intValueExact() == 7;
        }

        {
            GoalSet ex0 = GoalSet.of(3, BigInteger.valueOf(7));
            assert ex0.words.length == 1;
            assert ex0.words[0] == 7;
            assert ex0.get(0);
            assert ex0.get(1);
            assert ex0.get(2);
            assert ex0.toBigInteger().intValueExact() == 7;
        }

        {
            GoalSet ex0 = GoalSet.of(7, 4, 1, 2);
            assert ex0.words.length == 1;
            assert ex0.words[0] == 22;
            assert ex0.get(4);
            assert ex0.get(1);
            assert ex0.get(2);
            assert !ex0.get(0);
            assert !ex0.get(3);
            assert !ex0.get(5);
            assert !ex0.get(6);
            assert ex0.toBigInteger().intValueExact() == 22;
            System.out.println(ex0.stream().mapToObj(Integer::toString)
                .collect(Collectors.toList()));
            assert ex0.toString().equals("{1,2,4}");
        }

        {
            GoalSet ex0 = GoalSet.of(128, 63, 64, 65);
            assert ex0.words.length == 2;
            assert ex0.words[0] == 0x8000000000000000L;
            assert ex0.words[1] == 0x3L;
            assert ex0.get(63);
            assert ex0.get(64);
            assert ex0.toBigInteger()
                .equals(new BigInteger("38000000000000000", 16));
            System.out.println(ex0.stream().mapToObj(Integer::toString)
                .collect(Collectors.toList()));
            assert ex0.toString().equals("{63,64,65}");
        }

        {
            GoalSet ex0 = GoalSet.of(64, BigInteger.valueOf(0x800000e5L));
            assert ex0.words.length == 1;
            assert ex0.words[0] == 0x800000e5L;
            assert ex0.toBigInteger().longValueExact() == 0x800000e5L;
        }

        {
            BigInteger i = new BigInteger("98872953120981628", 16);
            GoalSet ex0 = GoalSet.of(20 * 4, i);
            assert ex0.toBigInteger().equals(i);
        }

        {
            BigInteger i = new BigInteger("800672cf99c3ba798d30", 16);
            GoalSet ex0 = GoalSet.of(20 * 4, i);
            BigInteger back = ex0.toBigInteger();
            assert back.equals(i);
        }

        for (long i = 0; i < 0xffffffffL; i += 231L) {
            GoalSet ex0 = GoalSet.of(64, BigInteger.valueOf(i));
            try {
                assert ex0.words.length == 1;
                assert ex0.words[0] == i;
                assert ex0.toBigInteger().longValueExact() == i;
            } catch (AssertionError ex) {
                System.out.printf("%x=%x%n", i, ex0.words[0]);
                throw ex;
            }
        }

        {
            BigInteger limit = new BigInteger("ffffffffffffffffffff", 16);
            BigInteger step = new BigInteger("98872953120981628", 16);
            for (BigInteger i = BigInteger.ZERO; i.compareTo(limit) < 0;
                 i = i.add(step)) {
                GoalSet ex0 = GoalSet.of(20 * 4, i);
                try {
                    assert ex0.toBigInteger().equals(i);
                } catch (AssertionError ex) {
                    System.out.printf("%x=%x%n", i, ex0.toBigInteger());
                    throw ex;
                }
            }
        }
    }

    /**
     * Compare two goal sets. A set of higher degree is considered
     * greater than one of lower degree, regardless of membership. For
     * sets with the same degree, the highest bit that differs is
     * identified, and the set whose bit is set is considered
     * &lsquo;greater than&rsquo; the other.
     * 
     * @param a one of the sets
     * 
     * @param b the other set
     * 
     * @return positive if the the first set is considered greater than
     * the second set; negative if less than; zero otherwise
     */
    public static int compare(GoalSet a, GoalSet b) {
        int dc = Integer.compare(a.degree, b.degree);
        if (dc != 0) return dc;
        for (int i = a.words.length; i > 0;) {
            i--;
            dc = Long.compareUnsigned(a.word(i), b.word(i));
            if (dc != 0) return dc;
        }
        return 0;
    }
}
