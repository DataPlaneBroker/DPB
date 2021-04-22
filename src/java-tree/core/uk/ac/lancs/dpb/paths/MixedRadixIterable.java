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

package uk.ac.lancs.dpb.paths;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

/**
 * Iterates over all valid values of a mixed-radix number. The user may
 * provide a validator for early elimination of invalid combinations.
 * 
 * @author simpsons
 */
public final class MixedRadixIterable<E> implements Iterable<E> {
    private final int magnitude;

    private final IntUnaryOperator radixes;

    private final Function<? super IntUnaryOperator, ? extends E> translator;

    private final MixedRadixValidator validator;

    /**
     * Create a fresh iterator over all valid combinations of digits.
     * 
     * @return the new iterator
     */
    @Override
    public Iterator<E> iterator() {
        /* Our regular iterator will go into an infinite loop if there
         * are no digits. */
        if (magnitude == 0) return new Iterator<E>() {
            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public E next() {
                throw new NoSuchElementException();
            }
        };

        return new Iterator<E>() {
            /**
             * Holds the next state to be tried. Each entry is limited
             * by the value obtained by submitting its index to
             * {@link #radixes}. Since each digit can have a different
             * base, this array represents a mixed-radix number.
             */
            private final int[] digits = new int[magnitude];

            /**
             * Passed to the user to access the current digits' states.
             */
            private final IntUnaryOperator getter = i -> digits[i];

            /**
             * Identifies the number of least significant digits that
             * have not yet been validated with respect to more
             * significant digits.
             */
            private int invalidated = magnitude;

            /**
             * Determine whether there are more valid combinations.
             * 
             * @return {@code true} if at least one more valid
             * combination exists; {@code false} otherwise
             */
            @Override
            public boolean hasNext() {
                /* Stop now if we already have a result. */
                if (invalidated == 0) return true;

                outer: while (true) {
                    assert invalidated > 0;
                    invalidated--;
                    while (true) {
                        if (digits[invalidated]
                            == radixes.applyAsInt(invalidated)) {
                            assert invalidated < magnitude;
                            invalidated++;
                            if (invalidated == magnitude) return false;
                        } else if (validator.test(invalidated, getter)) {
                            if (invalidated > 0) {
                                digits[invalidated - 1] = 0;
                                continue outer;
                            }
                            if (false) System.err
                                .printf("%2d/%2d: %s%n", invalidated, magnitude,
                                        IntStream.of(digits)
                                            .mapToObj(mi -> String.format("%2d",
                                                                          mi))
                                            .collect(Collectors.joining(" ")));
                            return true;
                        }
                        increment();
                    }
                }
            }

            private void increment() {
                if (false) try {
                    Thread.sleep(60);
                } catch (InterruptedException ex) {}
                digits[invalidated]++;
            }

            /**
             * Get the next valid combination. The current state is
             * submitted to the translator to convert it into the
             * element type.
             * 
             * @return the next valid combination
             * 
             * @throws NoSuchElementException if there are no more valid
             * combinations
             */
            @Override
            public E next() {
                if (!hasNext()) throw new NoSuchElementException();

                /* Get a result based on the current status. */
                E result = translator.apply(getter);

                /* Make sure we look for a new solution after this. */
                assert invalidated == 0;
                increment();
                invalidated++;

                return result;
            }
        };
    }

    /**
     * Builds a mixed-radix iterable in steps.
     * 
     * @param <E> the element type of the iterable
     */
    public static final class Builder<E> {
        int magnitude = 0;

        IntUnaryOperator radixes;

        MixedRadixValidator validator = (i, digits) -> true;

        final IntFunction<? extends Function<? super IntUnaryOperator,
                                             ? extends E>> translatorSupplier;

        Builder(IntFunction<? extends Function<? super IntUnaryOperator,
                                               ? extends E>> translatorSupplier) {
            this.translatorSupplier = translatorSupplier;
        }

        /**
         * Create an iterable with the current parameters.
         * 
         * @return the new iterable
         * 
         * @constructor
         */
        public MixedRadixIterable<E> build() {
            return new MixedRadixIterable<>(magnitude, radixes, validator,
                                            translatorSupplier
                                                .apply(magnitude));
        }

        /**
         * Specify how to get the radices of the digits.
         * 
         * @param magnitude the number of digits forming the mixed-radix
         * number to iterate through
         * 
         * @param radixes the radices of each digit, least significant
         * first. The object must continue to yield consistent values
         * during the lifetime of the iterable.
         * 
         * @return this object
         */
        public Builder<E> over(int magnitude, IntUnaryOperator radixes) {
            this.magnitude = magnitude;
            this.radixes = radixes;
            return this;
        }

        /**
         * Set the radices of the digits.
         * 
         * @param radixes the radices of each digit, least significant
         * first. This is iterated over at once, and the values stored,
         * so subsequent modifications to this argument do not affect
         * generated iterators.
         * 
         * @return this object
         */
        public Builder<E> over(Iterable<? extends Number> radixes) {
            int[] bases = StreamSupport.stream(radixes.spliterator(), false)
                .mapToInt(Number::intValue).toArray();
            return over(bases.length, i -> bases[i]);
        }

        /**
         * Set the radices of the digits.
         * 
         * @param radixes the radices of each digit, least significant
         * first. The elements must not be modified while the iterable
         * is in use.
         * 
         * @return this object
         */
        public Builder<E> over(int[] radixes) {
            Objects.requireNonNull(radixes, "radixes");
            return over(radixes.length, i -> radixes[i]);
        }

        /**
         * Set the radices of the digits from part of an array.
         * 
         * @param radixes an array with a portion that defines the
         * radices of each digit, least significant first. The elements
         * must not be modified while the iterable is in use.
         * 
         * @param off the offset into the array of the radix of the
         * least significant digit
         * 
         * @param len the number of digits
         * 
         * @return this object
         * 
         * @throws IllegalArgumentException if the offset or length are
         * negative
         * 
         * @throws ArrayIndexOutOfBoundsException if the offset plus the
         * length exceed the length of the array
         */
        public Builder<E> over(int[] radixes, int off, int len) {
            Objects.requireNonNull(radixes, "radixes");
            if (off < 0)
                throw new IllegalArgumentException("-ve offset: " + off);
            if (len < 0)
                throw new IllegalArgumentException("-ve length: " + len);
            if (off + len > radixes.length)
                throw new ArrayIndexOutOfBoundsException(off + len - 1);
            return over(len, i -> radixes[i + off]);
        }

        /**
         * Specify a validator to prevent combinations that it rejects
         * from being yielded.
         * 
         * @param validator the validator
         * 
         * @return this object
         */
        public Builder<E> constrainedBy(MixedRadixValidator validator) {
            this.validator = validator;
            return this;
        }
    }

    /**
     * Create a builder of iterables whose elements are of a
     * user-defined type.
     * 
     * @param <E> the element type
     * 
     * @param translatorSupplier a supplier of a translator, given the
     * magnitude. This will be invoked when {@link Builder#build()} is
     * called. The method {@link IntUnaryOperator#applyAsInt(int)} can
     * be called on the second argument passed to the translator with a
     * digit number from 0 (inclusive) to the magnitude (exclusive) to
     * get a digit's current value. This must be done before returning
     * control back to the iterator.
     * 
     * @return the new builder
     * 
     * @constructor
     */
    public static <E> Builder<E>
        toDeferred(IntFunction<? extends Function<? super IntUnaryOperator,
                                                  ? extends E>> translatorSupplier) {
        return new Builder<>(translatorSupplier);
    }

    /**
     * Create a builder of iterables whose elements are of a
     * user-defined type.
     * 
     * @param <E> the element type
     * 
     * @param translator a translator from the digits to the element
     * type. The method {@link IntUnaryOperator#applyAsInt(int)} can be
     * called on the second argument with a digit number from 0
     * (inclusive) to the magnitude (exclusive) to get a digit's current
     * value. This must be done before returning control back to the
     * iterator.
     * 
     * @return the new builder
     * 
     * @constructor
     */
    public static <E> Builder<E>
        to(Function<? super IntUnaryOperator, ? extends E> translator) {
        return new Builder<>(magnitude -> translator);
    }

    /**
     * Create a builder of iterables yielding arrays of digits.
     * 
     * @return the new builder
     * 
     * @constructor
     */
    public static Builder<int[]> toIntArray() {
        return new Builder<>(magnitude -> digits -> IntStream
            .range(0, magnitude).map(digits).toArray());
    }

    /**
     * Create a builder of iterables yielding lists of digits.
     * 
     * @return the new builder
     * 
     * @constructor
     */
    public static Builder<List<Integer>> toIntList() {
        return new Builder<>(magnitude -> digits -> IntStream
            .range(0, magnitude).map(digits).boxed()
            .collect(Collectors.toList()));
    }

    private MixedRadixIterable(int magnitude, IntUnaryOperator radixes,
                               MixedRadixValidator validator,
                               Function<? super IntUnaryOperator,
                                        ? extends E> translator) {
        if (magnitude < 0)
            throw new IllegalArgumentException("-ve digits: " + magnitude);
        this.magnitude = magnitude;
        this.radixes = radixes;
        this.translator = translator;
        this.validator = validator;
    }

    /**
     * @undocumented
     */
    public static void main(String[] args) throws Exception {
        if (false) {
            int[] bases =
                Arrays.stream(args).mapToInt(Integer::parseInt).toArray();
            Iterable<String> iterable = MixedRadixIterable
                .to(digits -> IntStream.range(0, bases.length).map(digits)
                    .mapToObj(Integer::toString)
                    .collect(Collectors.joining(", ")))
                .over(bases.length, i -> bases[i])
                .constrainedBy((i, digits) -> {
                    if (i == bases.length - 1) return true;
                    return digits.applyAsInt(i) != digits.applyAsInt(i + 1);
                }).build();
            for (String s : iterable) {
                System.out.println(s);
            }
        }

        Random rng = new Random(1);

        for (int csi = 0; csi < 1000; csi++) {
            /* Define a mixed-radix counting system. */
            int[] bases = new int[2 + rng.nextInt(5)];
            for (int i = 0; i < bases.length; i++)
                bases[i] = 2 + rng.nextInt(5);

            /* Choose an arbitrary constraint on two digits. */
            final int b0 = rng.nextInt(rng.nextInt(bases.length - 1) + 1);
            final int b1 = rng.nextInt(bases.length - b0 - 1) + b0 + 1;
            assert b0 < bases.length;
            assert b1 < bases.length;
            assert b0 < b1;
            final int v0 = rng.nextInt(bases[b0]);
            final int v1 = rng.nextInt(bases[b1]);

            System.out.printf("Original: %s%n",
                              testConstraintToString(bases, b0, b1, v0, v1));

            /* Get a set of the original members. */
            final Collection<List<Integer>> origMembers;
            {
                final MixedRadixValidator val = (min, digits) -> {
                    if (min != b0) return true;
                    if (digits.applyAsInt(b0) == v0 &&
                        digits.applyAsInt(b1) == v1) return false;
                    return true;
                };

                origMembers = StreamSupport
                    .stream(MixedRadixIterable.toIntList().over(bases)
                        .constrainedBy(val).build().spliterator(), false)
                    .collect(Collectors.toSet());
            }

            for (int ci = 0; ci < 100; ci++) {
                /* Randomize the radices. */
                final int[] swaps; // from new indices to original
                final int[] invSwaps; // from original indices to new
                final int[] altBases;
                final int altB0, altB1;
                {
                    final List<Integer> boxedSwaps =
                        IntStream.range(0, bases.length).boxed()
                            .collect(Collectors.toList());
                    Collections.shuffle(boxedSwaps, rng);
                    swaps = boxedSwaps.stream().mapToInt(Integer::intValue)
                        .toArray();
                    assert swaps.length == bases.length;

                    invSwaps = new int[swaps.length];
                    for (int i = 0; i < swaps.length; i++)
                        invSwaps[swaps[i]] = i;

                    altBases = IntStream.range(0, bases.length)
                        .map(i -> bases[swaps[i]]).toArray();
                    assert altBases.length == bases.length;
                    altB0 = invSwaps[b0];
                    altB1 = invSwaps[b1];
                    assert altB0 != altB1;
                    assert altB0 >= 0;
                    assert altB1 >= 0;
                    assert altB0 < altBases.length;
                    assert altB1 < altBases.length;
                }

                /* Express the constraint, taking account of the
                 * mapping. */
                final int lower = Math.min(altB0, altB1);
                final MixedRadixValidator val = (min, digits) -> {
                    if (min != lower) return true;
                    if (digits.applyAsInt(altB0) == v0 &&
                        digits.applyAsInt(altB1) == v1) return false;
                    return true;
                };

                final Collection<List<Integer>> members = StreamSupport
                    .stream(MixedRadixIterable.toIntList().over(altBases)
                        .constrainedBy(val).build().spliterator(), false)
                    .map(l -> mapBack(invSwaps, l)).collect(Collectors.toSet());
                final long entries = members.size();
                if (!origMembers.equals(members)) {
                    Collection<List<Integer>> origUnique =
                        new HashSet<>(origMembers);
                    Collection<List<Integer>> unique = new HashSet<>(members);
                    origUnique.removeAll(members);
                    unique.removeAll(origMembers);
                    System.out.printf("  Got: %d  Expected: %d%n", entries,
                                      origMembers.size());
                    System.out.printf("original unique: %s%n", origUnique);
                    System.out.printf("test unique: %s%n", unique);
                    System.out.printf("Failure: %s%n",
                                      testConstraintToString(altBases, altB0,
                                                             altB1, v0, v1));
                    System.out
                        .printf("Original: %s%n",
                                testConstraintToString(bases, b0, b1, v0, v1));
                    System.exit(1);
                }
            }
            System.out.printf("  Count: %d%n", origMembers.size());
        }
    }

    private static List<Integer> mapBack(int[] mapping, List<Integer> digits) {
        return IntStream.range(0, mapping.length).map(i -> mapping[i])
            .mapToObj(digits::get).collect(Collectors.toList());
    }

    private static String testConstraintToString(int[] bases, int b0, int b1,
                                                 int v0, int v1) {
        return IntStream.range(0, bases.length).mapToObj(i -> {
            String r = Integer.toString(bases[i]);
            if (i == b0) return r + "(" + v0 + ")";
            if (i == b1) return r + "(" + v1 + ")";
            return r;
        }).collect(Collectors.joining(", "));
    }
}
