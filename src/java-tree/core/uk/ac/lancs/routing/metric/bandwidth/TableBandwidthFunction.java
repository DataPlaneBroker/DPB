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

package uk.ac.lancs.routing.metric.bandwidth;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Expresses bandwidth requirements using an underlying table indexed by
 * a bit pattern representing the indices of the 'from' set.
 *
 * @author simpsons
 */
public final class TableBandwidthFunction implements BandwidthFunction {
    private final BandwidthRange[] table;

    private final int degree;

    /**
     * Get the logarithm to base 2 of an integer, i.e., the number of
     * bits required to represent it.
     * 
     * @see <a href=
     * "https://stackoverflow.com/questions/3305059/how-do-you-calculate-log-base-2-in-java-for-integers#answer-3305710">an
     * answer to <cite>How do you calculate log base 2 in Java for
     * integers?</cite></a>
     * 
     * @param value the value to be analysed
     * 
     * @return the number of bits required to represent the value
     */
    private static int binlog(int value) {
        int log = 0;
        if ((value & 0xffff0000) != 0) {
            value >>>= 16;
            log = 16;
        }
        if (value >= 256) {
            value >>>= 8;
            log += 8;
        }
        if (value >= 16) {
            value >>>= 4;
            log += 4;
        }
        if (value >= 4) {
            value >>>= 2;
            log += 2;
        }
        return log + (value >>> 1);
    }

    /**
     * Create a table from a sequence of data. The length of the
     * sequence must be 2<sup><var>n</var></sup>&minus;1, where
     * <var>n</var> is the degree of the function. Adding one to the
     * index of sequence gives the bit set of endpoint indices that form
     * the 'from' set.
     * 
     * @param data the data that will define the function
     * 
     * @throws IllegalArgumentException if the sequence's length is not
     * two less than a power of two
     */
    public TableBandwidthFunction(List<? extends BandwidthRange> data) {
        int degree = binlog(data.size() + 2);
        if ((1 << degree) - 2 != data.size())
            throw new IllegalArgumentException("sequence length " + data.size()
                + " not 2 less than a power of 2");
        this.table = data.toArray(new BandwidthRange[data.size()]);
        this.degree = degree;
    }

    /**
     * Create a table-based function by flattening out another function.
     * The other function will be interrogated for every possible value,
     * which will populate this function's table.
     * 
     * @param other the other function
     * 
     * @throws IllegalArgumentException if the other function's degree
     * is too high
     */
    public TableBandwidthFunction(BandwidthFunction other) {
        if (other.degree() > 8)
            throw new IllegalArgumentException("degree too great"
                + " to represent as table");
        final int size = (1 << other.degree()) - 2;
        this.table = new BandwidthRange[size];
        long[] buf = new long[1];
        for (int i = 0; i < this.table.length; i++) {
            buf[0] = i + 1;
            BitSet set = BitSet.valueOf(buf);
            this.table[i] = other.apply(set);
        }
        this.degree = other.degree();
    }

    @Override
    public BandwidthRange apply(BitSet from) {
        BigInteger value = BandwidthFunction.toBigInteger(from);
        int index = value.subtract(BigInteger.ONE).intValueExact();
        return table[index];
    }

    /**
     * {@inheritDoc}
     * 
     * @default This implementation represents itself as a simple array,
     * looked up by subtracting one from the bit pattern.
     */
    @Override
    public String asJavaScript() {
        return "{                                                        \n"
            + "  degree : " + degree + ",                                \n"
            + "  data : [ "
            + Arrays.asList(table).stream()
                .map(e -> "[" + e.min + ", " + e.max + "]")
                .collect(Collectors.joining(", "))
            + "],                                                        \n"
            + "  apply : function(bits) {                                \n"
            + "    return this.data[bits - 1];                           \n"
            + "  },                                                      \n"
            + "}\n";
    }

    @Override
    public int degree() {
        return degree;
    }
}
