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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
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
             * Increment the least significant valid digit. If it
             * reaches its base, reset it, and increment the next, and
             * so on, until a digit increments without reaching its
             * base, or the maximum value of the number has been
             * exceeded.
             * 
             * @return {@code true} if an increment took place without
             * exceeding the maximum value of the number; {@code false}
             * otherwise
             */
            private boolean increment() {
                if (false) try {
                    Thread.sleep(60);
                } catch (InterruptedException ex) {

                }
                /* Increase each counter until we don't have to
                 * carry. */
                while (invalidated < digits.length) {
                    int i = invalidated++;
                    if (++digits[i] < radixes.applyAsInt(i)) {
                        /* We didn't have to carry. */
                        if (false) System.err
                            .printf("%2d/%2d: %s%n", invalidated, magnitude,
                                    IntStream.of(digits)
                                        .mapToObj(mi -> String.format("%2d",
                                                                      mi))
                                        .collect(Collectors.joining(" ")));
                        return true;
                    }
                    /* We have incremented a digit beyond its maximum
                     * value. */

                    /* Abort if we have overflowed the most significant
                     * digit. */
                    if (i + 1 == digits.length) break;

                    /* Reset this digit. */
                    digits[i] = 0;
                    if (false) System.err
                        .printf("%2d/%2d: %s%n", invalidated, magnitude,
                                IntStream.of(digits)
                                    .mapToObj(mi -> String.format("%2d", mi))
                                    .collect(Collectors.joining(" ")));
                }
                return false;
            }

            /**
             * Ensure that a valid number not yet yielded to the user
             * has been reached. First, if the {@link #found} flag is
             * set, a number is already available, and nothing more is
             * done. Second, if the most significant digit has already
             * reached its limit, the iterator is exhausted. Finally, an
             * attempt to validate all remaining invalidated digits is
             * made. Each time this fails, an increment occurs, and the
             * attempt is made again.
             * 
             * @return {@code true} if a valid number has been reached;
             * {@code false} otherwise
             */
            private boolean ensure() {
                if (digits.length == 0) return false;

                /* Have we already got a solution to be delivered by
                 * next()? */
                if (invalidated == 0) return true;
                /* We must find the next solution. */

                /* If the most slowly increasing counter has exceeded
                 * its limit, we are done. */
                if (digits[digits.length - 1]
                    >= radixes.applyAsInt(digits.length - 1)) return false;

                /* Keep looking for a valid combination. */
                next_combination: do {
                    /* For the digits that have changed, skip if the
                     * digit with its current value belongs to an
                     * illegal combination. */
                    while (invalidated > 0) {
                        final int en = --invalidated;

                        /* Check this digit against all more significant
                         * (and currently valid) digits for
                         * conflicts. */
                        if (!validator.test(en, getter)) {
                            Arrays.fill(digits, 0, en, 0);
                            continue next_combination;
                        }
                        /* All requirements were met, so this digit is
                         * validated with respect to all more
                         * significant digits. */
                    }

                    /* The current combination has all digits
                     * validated. */
                    break;
                } while (increment());
                return invalidated == 0;
            }

            /**
             * Determine whether there are more valid combinations.
             * 
             * @return {@code true} if at least one more valid
             * combination exists; {@code false} otherwise
             */
            @Override
            public boolean hasNext() {
                return ensure();
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
                if (!ensure()) throw new NoSuchElementException();

                /* Get a result based on the current status. */
                E result = translator.apply(getter);

                /* Make sure we look for a new solution after this. */
                increment();

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
        int[] bases = Arrays.stream(args).mapToInt(Integer::parseInt).toArray();
        Iterable<String> iterable = MixedRadixIterable
            .to(digits -> IntStream.range(0, bases.length).map(digits)
                .mapToObj(Integer::toString).collect(Collectors.joining(", ")))
            .over(bases.length, i -> bases[i]).constrainedBy((i, digits) -> {
                if (i == bases.length - 1) return true;
                return digits.applyAsInt(i) != digits.applyAsInt(i + 1);
            }).build();
        for (String s : iterable) {
            System.out.println(s);
        }
    }
}
