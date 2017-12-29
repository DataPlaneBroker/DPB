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
package uk.ac.lancs.treeroute;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Keeps the best meter tuples according to all permutations of a
 * combination of metrics.
 * 
 * @author simpsons
 */
public class BestTupleSet {
    private final MetricCombination metrics;

    /**
     * Create a set for a given combination of metrics.
     * 
     * @param metrics the combination of metrics
     */
    public BestTupleSet(MetricCombination metrics) {
        this.metrics = metrics;
    }

    private final Collection<MetricCombination.Tuple> best =
        new ArrayList<>();

    /**
     * Get the best set of meter tuples.
     * 
     * @return the best set of tuples
     */
    public Collection<MetricCombination.Tuple> get() {
        return Collections.unmodifiableCollection(new HashSet<>(best));
    }

    /**
     * Add a meter tuple to the set.
     * 
     * @param row the tuple to be added
     * 
     * @return {@code true} if and only if the set was changed
     */
    public boolean submit(MetricCombination.Tuple row) {
        /* Record whether we have changed the set. */
        boolean changed = false;

        for (Iterator<MetricCombination.Tuple> iter = best.iterator(); iter
            .hasNext();) {
            MetricCombination.Tuple existing = iter.next();

            /* We must keep the existing tuple if it is better than the
             * new in at least one permutation of the metrics. */
            boolean keepExisting = false;
            boolean keepLatest = false;
            for (Comparator<MetricCombination.Tuple> order : metrics
                .orders()) {
                final int rc = order.compare(existing, row);
                if (rc == 0) {
                    /* The new tuple is identical to an existing one, so
                     * no change needs to be made. Stop now. */
                    assert !changed;
                    return changed;
                }
                if (rc < 0) {
                    /* The new tuple is better than the existing. We
                     * must keep the new tuple. */
                    keepLatest = true;
                } else {
                    /* The new tuple is worse than the existing. We must
                     * keep the existing tuple. */
                    keepExisting = true;
                }

                /* We can stop if we've already decided to keep both. */
                if (keepLatest && keepExisting) break;
            }

            /* Remove the existing tuple if it's worse in all respects
             * than the new tuple. */
            if (!keepExisting) {
                iter.remove();
                changed = true;
            }

            /* If the new tuple is worse in all respects than the
             * existing one, drop it now. */
            if (!keepLatest) {
                assert !changed;
                return changed;
            }
        }

        /* Keep the new tuple. */
        best.add(row);
        return true;
    }
}
