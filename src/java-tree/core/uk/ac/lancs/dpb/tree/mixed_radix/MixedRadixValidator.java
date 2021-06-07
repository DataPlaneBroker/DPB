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

package uk.ac.lancs.dpb.tree.mixed_radix;

import java.util.function.IntUnaryOperator;

/**
 * Checks that a combination of digits is valid.
 */
@FunctionalInterface
public interface MixedRadixValidator {
    /**
     * Check that a combination of digits is valid. Digits in positions
     * less then {@code min} are to be ignored.
     *
     * @param min the least significant digit to be checked
     *
     * @param digits a means to obtain the value of each digit
     *
     * @return {@code true} if the digits are valid; {@code false}
     * otherwise
     */
    boolean test(int min, IntUnaryOperator digits);

    /**
     * Get a validator that imposes no constraints.
     * 
     * @return a validator that always yields {@code true}
     */
    static MixedRadixValidator unconstrained() {
        return (min, digits) -> true;
    }

    /**
     * Get a validator that is the logical negation of this validator.
     * 
     * @return a validator that is the logical negation of this
     * validator
     */
    default MixedRadixValidator negate() {
        return new NegatedMixedRadixValidator(this);
    }

    /**
     * Get a validator that is the short-circuiting logical OR of this
     * validator and another.
     * 
     * @param other the other validator
     * 
     * @return a validator that is the logical OR of this validator and
     * the argument
     */
    default MixedRadixValidator or(MixedRadixValidator other) {
        return new LogicalOrMixedRadixValidator(this, other);
    }

    /**
     * Get a validator that is the short-circuiting logical AND of this
     * validator and another.
     * 
     * @param other the other validator
     * 
     * @return a validator that is the logical AND of this validator and
     * the argument
     */
    default MixedRadixValidator and(MixedRadixValidator other) {
        return new LogicalAndMixedRadixValidator(this, other);
    }

    /**
     * Get the negation of a validator. This method simply calls
     * {@link #negate()} on the argument, and returns the result.
     * 
     * @param target the validator to negate
     * 
     * @return the negated validator
     */
    static MixedRadixValidator not(MixedRadixValidator target) {
        return target.negate();
    }
}