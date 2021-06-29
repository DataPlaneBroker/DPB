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

package uk.ac.lancs.dpb.graph.eval;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.stream.IntStream;

/**
 * Detects stability in a changing signal.
 * 
 * @author simpsons
 */
public final class StabilityDetector {
    private final double maxFactor;

    private final double minFactor;

    /**
     * Create a stability detector.
     * 
     * @param factor the factor in (0,1) by which upper and lower limits
     * decay towards the signal
     * 
     * @param nth the number of limit-difference thresholds to check
     * 
     * @param thresholds the thresholds for difference between the
     * limits
     * 
     * @param durations the number of cycles to pass within each
     * threshold (in reverse order) to be considered stability
     */
    public StabilityDetector(double factor, final int nth,
                             DoubleSupplier thresholds, IntSupplier durations) {
        if (factor <= 0.0 || factor >= 1.0)
            throw new IllegalArgumentException("factor " + factor
                + " not in (0,1)");
        this.maxFactor = factor;
        this.minFactor = 1.0 - factor;
        this.threshold = new double[nth];
        this.duration = new int[nth];
        this.stability = new int[nth];
        for (int i = 0; i < nth; i++) {
            this.threshold[i] = thresholds.getAsDouble();
            this.duration[nth - i - 1] = durations.getAsInt();
        }
        final int maxDuration = IntStream.of(duration).max().getAsInt();
        this.history = new double[maxDuration + 1];
    }

    /**
     * Create a geometric progression.
     * 
     * @param init the initial value
     * 
     * @param ratio the ratio of each supplied value to its previous
     * 
     * @return the requested progression
     */
    public static DoubleSupplier geometricProgression(double init,
                                                      double ratio) {
        return new DoubleSupplier() {
            private double next = init;

            @Override
            public double getAsDouble() {
                double result = next;
                next *= ratio;
                return result;
            }
        };
    }

    /**
     * Create an arithmetic progression.
     * 
     * @param init the initial value
     * 
     * @param diff the difference between each supplied value and its
     * previous
     * 
     * @return the requested progression
     */
    public static IntSupplier intArithmeticProgression(int init, int diff) {
        return new IntSupplier() {
            private int next = init;

            @Override
            public int getAsInt() {
                int result = next;
                next += diff;
                return result;
            }
        };
    }

    private final double[] history;

    private double sumDiff;

    private double sumSqDiff;

    private final double[] threshold;

    private final int[] duration;

    private final int[] stability;

    private double min = Double.MAX_VALUE;

    private double max = Double.MIN_VALUE;

    private int elapsed = 0;

    /**
     * Contribute another signal to the record.
     * 
     * @param signal the current signal
     * 
     * @return {@code true} if stability has been reached; {@code false}
     * otherwise
     */
    boolean recordAndTest(double signal) {
        /* Record the elapsed time. */
        if (++elapsed >= history.length) elapsed -= history.length;

        /* If the signal goes over the upper limit, reset the limit.
         * Otherwise, allow the upper limit to creep down towards the
         * current signal. */
        if (signal > max) {
            max = signal;
        } else {
            max = maxFactor * (max - signal) + signal;
        }

        /* If the signal goes under the lower limit, reset the limit.
         * Otherwise, allow the lower limit to creep up towards the
         * signal. */
        if (signal < min) {
            min = signal;
        } else {
            min = minFactor * (signal - min) + min;
        }

        final double diff = max - min;
        try {
            final boolean debug = false;
            if (debug) System.err.printf("%9.2g %9.2g %9.2g %9.2g", min, signal,
                                         max, diff);
            boolean stable = false;
            for (int i = 0; i < threshold.length; i++) {
                if (diff > threshold[i]) {
                    stability[i] = 0;
                } else if (++stability[i] >= duration[i]) {
                    stable = true;
                }
                if (debug) System.err.printf(" %6d", stability[i]);
            }
            if (debug) System.err.printf("%n");
            return stable;
        } finally {
            final double oldDiff = history[elapsed];
            sumDiff -= oldDiff;
            sumSqDiff -= oldDiff * oldDiff;
            history[elapsed] = diff;
            sumDiff += diff;
            sumSqDiff += diff * diff;
        }
    }
}
