/*
 * Copyright 2017, Regents of the University of Lancaster
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
package uk.ac.lancs.switches.mgmt;

/**
 * Allows a trunk defined within an aggregator to have its resources
 * administratively modified.
 * 
 * @summary The management interface for an aggregator's trunks
 * 
 * @author simpsons
 */
public interface Trunk {
    /**
     * Get the configured delay for this trunk.
     * 
     * @return the trunk's delay
     */
    double getDelay();

    /**
     * Set the delay for this trunk.
     * 
     * @param delay the new delay
     */
    void setDelay(double delay);

    /**
     * Withdraw bandwidth from this trunk.
     * 
     * @param upstream the amount to deduct from the available bandwidth
     * in the direction from the start terminal to the end
     * 
     * @param downstream the amount to deduct from the available
     * bandwidth in the direction from the end terminal to the start
     * 
     * @throws IllegalArgumentException if either amount is negative or
     * exceeds the corresponding available level
     */
    void withdrawBandwidth(double upstream, double downstream);

    /**
     * Withdraw bandwidth from this trunk.
     * 
     * @param amount the amount to deduct from the available bandwidth
     * in each direction
     * 
     * @throws IllegalArgumentException if the amount is negative or
     * exceeds either available level
     */
    default void withdrawBandwidth(double amount) {
        withdrawBandwidth(amount, amount);
    }

    /**
     * Provide bandwidth to this trunk.
     * 
     * @param upstream the amount to add to the available bandwidth in
     * the direction from the start terminal to the end
     * 
     * @param downstream the amount to add to the available bandwidth in
     * the direction from the end terminal to the start
     * 
     * @throws IllegalArgumentException if either amount is negative
     */
    void provideBandwidth(double upstream, double downstream);

    /**
     * Provide bandwidth to this trunk.
     * 
     * @param amount the amount to add to the available bandwidth in
     * both directions
     * 
     * @throws IllegalArgumentException if the amount is negative
     */
    default void provideBandwidth(double amount) {
        provideBandwidth(amount, amount);
    }

    /**
     * Make a range of labels available.
     * 
     * @param startBase the first available label at the start side of
     * the link
     * 
     * @param amount the number of labels from the base to make
     * available
     * 
     * @param endBase the first available label at the end side of the
     * link
     */
    void defineLabelRange(int startBase, int amount, int endBase);

    /**
     * Make a range of labels available.
     * 
     * <p>
     * By default, this method calls
     * {@link #defineLabelRange(int, int, int)}, using the first
     * argument also as the last.
     * 
     * @param startBase the first available label at either side of the
     * link
     * 
     * @param amount the number of labels from the base to make
     * available
     */
    default void defineLabelRange(int startBase, int amount) {
        defineLabelRange(startBase, amount, startBase);
    }
}
