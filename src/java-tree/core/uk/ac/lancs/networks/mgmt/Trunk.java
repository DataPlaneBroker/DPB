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
package uk.ac.lancs.networks.mgmt;

import uk.ac.lancs.networks.Terminal;

/**
 * A trunk connects two terminals together, has a configured delay (used
 * as a metric for path finding), an upstream bandwidth capacity (from
 * the first terminal to the second), a downstream capacity (in the
 * opposite direction), and a set of labels (used to form circuits from
 * the terminals).
 * 
 * @resume A trunk connecting terminals of two inferior networks of an
 * aggregator
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
     * 
     * @throws IllegalArgumentException if the delay is negative
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
     * @throws IllegalArgumentException if either amount is negative
     * 
     * @throws TrunkManagementException if either amount exceeds the
     * corresponding available level
     * 
     * @throws NetworkManagementException if the change cannot be made
     * for other reasons
     */
    void withdrawBandwidth(double upstream, double downstream)
        throws NetworkManagementException;

    /**
     * Withdraw bandwidth from this trunk.
     * 
     * @param amount the amount to deduct from the available bandwidth
     * in each direction
     * 
     * @throws IllegalArgumentException if the amount is negative
     * 
     * @throws TrunkManagementException if the amount exceeds either
     * available level
     * 
     * @throws NetworkManagementException if the change cannot be made
     * for other reasons
     * 
     * @default This implementation calls
     * {@link #withdrawBandwidth(double, double)}, using passing the
     * argument twice
     */
    default void withdrawBandwidth(double amount)
        throws NetworkManagementException {
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
     * Identify which end of the trunk its terminal belongs.
     * 
     * @param term the terminal sought
     * 
     * @return 0 if the terminal is considered at start; 1 if at the
     * end; -1 if the terminal does not define the trunk
     */
    int position(Terminal term);

    /**
     * Get the terminal at one end of this trunk.
     * 
     * @param pos 0 for the start of the trunk; 1 for the end
     * 
     * @return the terminal at the specified end of the trunk
     * 
     * @throws IllegalArgumentException if the position is not 0 or 1
     */
    Terminal getTerminal(int pos);

    /**
     * Provide bandwidth to this trunk.
     * 
     * @param amount the amount to add to the available bandwidth in
     * both directions
     * 
     * @throws IllegalArgumentException if the amount is negative
     * 
     * @default This implementation calls
     * {@link #provideBandwidth(double, double)}, passing the argument
     * twice.
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
     * 
     * @throws TrunkManagementException if any of the labels are in use
     * 
     * @throws NetworkManagementException if the change cannot be made
     * for other reasons
     */
    void defineLabelRange(int startBase, int amount, int endBase)
        throws NetworkManagementException;

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
     * 
     * @throws TrunkManagementException if any of the labels are in use
     * 
     * @throws NetworkManagementException if the change cannot be made
     * for other reasons
     */
    default void defineLabelRange(int startBase, int amount)
        throws NetworkManagementException {
        defineLabelRange(startBase, amount, startBase);
    }

    /**
     * Revoke availability of a range of labels.
     * 
     * @param startBase the first label to remove at the start side of
     * the link
     * 
     * @param amount the number of labels to remove
     * 
     * @throws TrunkManagementException if elements of the range cannot
     * be revoked
     * 
     * @throws NetworkManagementException if the change cannot be made
     * for other reasons
     */
    void revokeStartLabelRange(int startBase, int amount)
        throws NetworkManagementException;

    /**
     * Revoke availability of a range of labels.
     * 
     * @param endBase the first label to remove at the end side of the
     * link
     * 
     * @param amount the number of labels to remove
     * 
     * @throws TrunkManagementException if elements of the range cannot
     * be revoked
     * 
     * @throws NetworkManagementException if the change cannot be made
     * for other reasons
     */
    void revokeEndLabelRange(int endBase, int amount)
        throws NetworkManagementException;

    /**
     * Decommission this trunk. No more tunnels will be created over it,
     * but existing ones remain.
     */
    void decommission();

    /**
     * Recommission this trunk. New tunnels can be created over it
     * again.
     */
    void recommission();

    /**
     * Determine whether this trunk is commissioned. The default state
     * is to be commissioned.
     * 
     * @return {@code true} iff the trunk is the commissioned.
     */
    boolean isCommissioned();

    /**
     * Get a reverse view of this trunk. Notions of upstream/downstream
     * and start/end are inverted. Delay is unaffected, as it applies to
     * both directions.
     * 
     * @return a reverse view of the trunk
     */
    default Trunk reverse() {
        final Trunk orig = this;
        return new Trunk() {
            @Override
            public void withdrawBandwidth(double upstream, double downstream)
                throws NetworkManagementException {
                orig.withdrawBandwidth(downstream, upstream);
            }

            @Override
            public void setDelay(double delay) {
                orig.setDelay(delay);
            }

            @Override
            public Terminal getTerminal(int pos) {
                return orig.getTerminal(1 - pos);
            }

            @Override
            public void revokeStartLabelRange(int startBase, int amount)
                throws NetworkManagementException {
                orig.revokeEndLabelRange(startBase, amount);
            }

            @Override
            public void revokeEndLabelRange(int endBase, int amount)
                throws NetworkManagementException {
                orig.revokeStartLabelRange(endBase, amount);
            }

            @Override
            public Trunk reverse() {
                return orig;
            }

            @Override
            public void provideBandwidth(double upstream, double downstream) {
                orig.provideBandwidth(downstream, upstream);
            }

            @Override
            public int position(Terminal term) {
                int r = orig.position(term);
                switch (r) {
                case 0:
                    return 1;
                case 1:
                    return 0;
                default:
                    return r;
                }
            }

            @Override
            public double getDelay() {
                return orig.getDelay();
            }

            @Override
            public void defineLabelRange(int startBase, int amount,
                                         int endBase)
                throws NetworkManagementException {
                orig.defineLabelRange(endBase, amount, startBase);
            }

            @Override
            public void decommission() {
                orig.decommission();
            }

            @Override
            public void recommission() {
                orig.recommission();
            }

            @Override
            public boolean isCommissioned() {
                return orig.isCommissioned();
            }
        };
    }
}
