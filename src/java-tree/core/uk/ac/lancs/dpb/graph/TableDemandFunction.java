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

package uk.ac.lancs.dpb.graph;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Expresses bandwidth requirements using an underlying table indexed by
 * a bit pattern representing the indices of the <cite>from</cite> set.
 *
 * @author simpsons
 */
final class TableDemandFunction implements DemandFunction {
    private final Capacity[] table;

    private final int degree;

    /**
     * Get the logarithm to base 2 of an integer, i.e., the number of
     * bits required to represent it.
     * 
     * @param value the value to be analysed
     * 
     * @return the number of bits required to represent the value
     */
    private static int log2(int value) {
        return Integer.SIZE - Integer.numberOfLeadingZeros(value);
    }

    /**
     * Create a table-based function from a list. The size of the list
     * must be 2<sup><var>n</var></sup>&minus;2, where <var>n</var> is
     * the degree of the function. Adding one to the list index gives
     * the bit set of endpoint indices that form the <cite>from</cite>
     * set.
     * 
     * @param data the data that will define the function
     * 
     * @throws IllegalArgumentException if the list's size is not two
     * less than a power of two
     */
    public TableDemandFunction(List<? extends Capacity> data) {
        int degree = log2(data.size() + 2);
        if ((1 << degree) - 2 != data.size())
            throw new IllegalArgumentException("sequence length " + data.size()
                + " not 2 less than a power of 2");
        this.table = data.toArray(new Capacity[data.size()]);
        this.degree = degree;
    }

    /**
     * Create a table-based function from part of an array. The number
     * of elements selected from the array must be
     * 2<sup><var>n</var></sup>&minus;2, where <var>n</var> is the
     * degree of the function. Adding one to the index within the
     * selection gives the bit set of endpoint indices that form the
     * <cite>from</cite> set.
     * 
     * @param arr an array containing the data that will define the
     * function
     * 
     * @param off the offset of the first element of the array that
     * defines the function
     * 
     * @param len the number of array elements that define the function
     * 
     * @throws IllegalArgumentException if the specified length is not
     * two less than a power of two
     */
    public TableDemandFunction(Capacity[] arr, int off, int len) {
        int degree = log2(len + 2);
        if ((1 << degree) - 2 != len)
            throw new IllegalArgumentException("sequence length " + len
                + " not 2 less than a power of 2");
        this.table = Arrays.copyOfRange(arr, off, off + len);
        this.degree = degree;
    }

    /**
     * Create a table-based function from part of an array. The length
     * of the array must be 2<sup><var>n</var></sup>&minus;2, where
     * <var>n</var> is the degree of the function. Adding one to the
     * array index gives the bit set of endpoint indices that form the
     * <cite>from</cite> set.
     * 
     * @param arr an array containing the data that will define the
     * function
     * 
     * @throws IllegalArgumentException if the array length is not two
     * less than a power of two
     */
    public TableDemandFunction(Capacity[] arr) {
        this(arr, 0, arr.length);
    }

    /**
     * Create a table-based function by flattening out another function.
     * The other function will be interrogated for every possible value,
     * which will populate this function's table.
     * 
     * @param other the other function
     */
    private TableDemandFunction(DemandFunction other) {
        final int size = (1 << other.degree()) - 2;
        this.table = new Capacity[size];
        long[] buf = new long[1];
        for (int i = 0; i < this.table.length; i++) {
            buf[0] = i + 1;
            BitSet set = BitSet.valueOf(buf);
            this.table[i] = other.get(set);
        }
        this.degree = other.degree();
    }

    /**
     * Try to simplify a complex function by reducing it to a table.
     * 
     * @default If the degree is too high, the original function will be
     * returned.
     * 
     * @param other a function to simplify
     * 
     * @return either the original function, or one based on a table
     */
    public static DemandFunction tabulate(DemandFunction other) {
        if (other.degree() > 8) return other;
        return new TableDemandFunction(other);
    }

    @Override
    public Capacity get(BitSet from) {
        try {
            BigInteger value = ScriptDemandFunction.toBigInteger(from);
            int index = value.subtract(BigInteger.ONE).intValueExact();
            return table[index];
        } catch (ArrayIndexOutOfBoundsException | ArithmeticException ex) {
            throw new IllegalArgumentException("invalid 'from' set " + from,
                                               ex);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @default This implementation represents itself as a simple array,
     * looked up by subtracting one from the bit pattern.
     */
    @Override
    public String asScript() {
        return "data = [                                                 \n"
            + Arrays.asList(table).stream()
                .map(e -> "  [" + e.min() + ", " + e.max() + "]")
                .collect(Collectors.joining(",\n"))
            + " ]                                                        \n"
            + "@classmethod                                              \n"
            + "def " + GET_FUNCTION_NAME + "(cls, bits):                 \n"
            + "    return cls.data[bits - 1]";
    }

    @Override
    public int degree() {
        return degree;
    }
}
