/*
 * Copyright 2018,2019, Regents of the University of Lancaster
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
package uk.ac.lancs.config;

import java.util.Iterator;
import java.util.function.Predicate;

/**
 * Eliminates items from an iterator.
 * 
 * @param <E> the element type
 * 
 * @author simpsons
 */
class FilterIterator<E> implements Iterator<E> {
    private final Iterator<? extends E> base;
    private final Predicate<? super E> condition;

    /**
     * Create new view over an iterator that includes only a subset of
     * items.
     * 
     * @param base the original iterator
     * 
     * @param condition a predicate selecting items to be present in the
     * new view
     */
    FilterIterator(Iterator<? extends E> base,
                   Predicate<? super E> condition) {
        this.base = base;
        this.condition = condition;
    }

    private E next;

    private void ensureNext() {
        while (next == null) {
            if (!base.hasNext()) return;
            E cand = base.next();
            if (condition.test(cand)) {
                next = cand;
                return;
            }
        }
    }

    @Override
    public boolean hasNext() {
        ensureNext();
        return next != null;
    }

    @Override
    public E next() {
        ensureNext();
        E result = next;
        next = null;
        return result;
    }

}
