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

package uk.ac.lancs.dpb.tree;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Holds a list of distinct objects with a zero-based index.
 * 
 * @author simpsons
 */
public class Sequence<E> extends AbstractList<E> {
    private final E[] array;

    private final Map<E, Integer> map;

    private Sequence(E[] array, Map<E, Integer> map) {
        this.array = array;
        this.map = map;
    }

    /**
     * Create a sequence. The iteration order of the collection will
     * determine the index of each element. Duplicates (according to
     * {@link Object#equals(Object)} and {@link Object#hashCode()} will
     * be removed.
     * 
     * @param <E> the element type
     * 
     * @param input the collection to create a sequence of
     * 
     * @return a sequence of the provided elements
     */
    public static <E> Sequence<E> copyOf(Collection<? extends E> input) {
        List<E> order = List.copyOf(new LinkedHashSet<>(input));
        Map<E, Integer> map = IntStream.range(0, order.size()).boxed()
            .collect(Collectors.toMap(order::get, i -> i));
        @SuppressWarnings("unchecked")
        E[] array = (E[]) new Object[order.size()];
        order.toArray(array);
        return new Sequence<>(array, map);
    }

    /**
     * Create a sequence using identity. The iteration order of the
     * collection will determine the index of each element. Duplicate
     * references to the same elements will be removed. No element's
     * {@link Object#equals(Object)} method will be invoked by this
     * collection.
     * 
     * @param <E> the element type
     * 
     * @param input the collection to create an index of
     * 
     * @return an index of the provided elements
     */
    public static <E> Sequence<E>
        identityCopyOf(Collection<? extends E> input) {
        List<E> order = new ArrayList<>(input.size());
        Map<E, Integer> map = new IdentityHashMap<>();
        for (E e : input)
            if (map.putIfAbsent(e, order.size()) == null) order.add(e);
        @SuppressWarnings("unchecked")
        E[] array = order.toArray(n -> (E[]) new Object[n]);
        return new Sequence<>(array, Collections.unmodifiableMap(map));
    }

    /**
     * Get the index position of an element.
     * 
     * @param elem the element whose position is sought
     * 
     * @return the element's position; or {@code -1} if not present
     */
    public int getAsInt(E elem) {
        Integer r = map.get(elem);
        return r == null ? -1 : r;
    }

    private final Set<E> elements = new AbstractSet<E>() {
        @Override
        public Iterator<E> iterator() {
            return new Iterator<E>() {
                int i = 0;

                @Override
                public boolean hasNext() {
                    return i < array.length;
                }

                @Override
                public E next() {
                    if (i < array.length) return array[i++];
                    throw new NoSuchElementException();
                }
            };
        }

        @Override
        public int size() {
            return array.length;
        }
    };

    private final Set<Integer> indices = new AbstractSet<Integer>() {
        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<Integer>() {
                int i = 0;

                @Override
                public boolean hasNext() {
                    return i < array.length;
                }

                @Override
                public Integer next() {
                    if (i < array.length) return i++;
                    throw new NoSuchElementException();
                }
            };
        }

        @Override
        public int size() {
            return array.length;
        }
    };

    /**
     * Get a map view of indices to elements. The iteration order will
     * be by increasing index.
     * 
     * @return an immutable map view of indices to elements
     */
    public Map<Integer, E> decode() {
        return indexToElem;
    }

    /**
     * Get a map view of elements to indices. The iteration order will
     * be by increasing index.
     * 
     * @return an immutable map view of elements to indices
     */
    public Map<E, Integer> encode() {
        return elemToIndex;
    }

    /**
     * Test whether an element is part of the sequence.
     * 
     * @param elem the element to test
     * 
     * @return {@code true} if the element is part of the sequence;
     * {@code false} otherwise
     */
    public boolean containsElement(E elem) {
        return map.containsKey(elem);
    }

    /**
     * Test whether an index is part of the sequence. Only indices in
     * the range {@code 0} (inclusive) to {@link #size()} (exclusive)
     * are in the sequence.
     * 
     * @param index the index to test
     * 
     * @return {@code true} if the index is part of the index;
     * {@code false} otherwise
     */
    public boolean containsIndex(int index) {
        return index >= 0 && index < array.length;
    }

    private final Map<E, Integer> elemToIndex = new AbstractMap<E, Integer>() {
        @Override
        public Collection<Integer> values() {
            return indices;
        }

        @Override
        public Set<E> keySet() {
            return elements;
        }

        @Override
        public Integer get(Object key) {
            return map.get(key);
        }

        @Override
        public boolean containsKey(Object key) {
            return map.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            if (!(value instanceof Integer)) return false;
            int i = (Integer) value;
            return i >= 0 && i < array.length;
        }

        @Override
        public int size() {
            return array.length;
        }

        @Override
        public Set<Map.Entry<E, Integer>> entrySet() {
            return elemToIndexEntries;
        }
    };

    private final Map<Integer, E> indexToElem = new AbstractMap<Integer, E>() {
        @Override
        public Collection<E> values() {
            return elements;
        }

        @Override
        public Set<Integer> keySet() {
            return indices;
        }

        @Override
        public E get(Object key) {
            if (!(key instanceof Integer)) return null;
            int i = (Integer) key;
            return i >= 0 && i < array.length ? array[i] : null;
        }

        @Override
        public boolean containsKey(Object key) {
            if (!(key instanceof Integer)) return false;
            int i = (Integer) key;
            return i >= 0 && i < array.length;
        }

        @Override
        public boolean containsValue(Object value) {
            /* This is not a suspicious key, because we have to accept
             * anything, and the map we call is also required to accept
             * anything, and must respond the same way we must. */
            return map.containsKey(value);
        }

        @Override
        public int size() {
            return array.length;
        }

        @Override
        public Set<Map.Entry<Integer, E>> entrySet() {
            return indexToElemEntries;
        }
    };

    private final Set<Map.Entry<E, Integer>> elemToIndexEntries =
        new AbstractSet<Map.Entry<E, Integer>>() {
            @Override
            public Iterator<Map.Entry<E, Integer>> iterator() {
                return new Iterator<Map.Entry<E, Integer>>() {
                    int i = 0;

                    @Override
                    public boolean hasNext() {
                        return i < array.length;
                    }

                    @Override
                    public Map.Entry<E, Integer> next() {
                        if (i < array.length) {
                            final int p = i++;
                            return Map.entry(array[p], p);
                        }
                        throw new NoSuchElementException();
                    }
                };
            }

            @Override
            public int size() {
                return array.length;
            }
        };

    private final Set<Map.Entry<Integer, E>> indexToElemEntries =
        new AbstractSet<Map.Entry<Integer, E>>() {
            @Override
            public Iterator<Map.Entry<Integer, E>> iterator() {
                return new Iterator<Map.Entry<Integer, E>>() {
                    int i = 0;

                    @Override
                    public boolean hasNext() {
                        return i < array.length;
                    }

                    @Override
                    public Map.Entry<Integer, E> next() {
                        if (i < array.length) {
                            final int p = i++;
                            return Map.entry(p, array[p]);
                        }
                        throw new NoSuchElementException();
                    }
                };
            }

            @Override
            public int size() {
                return array.length;
            }
        };

    /**
     * Get the element at a given position.
     * 
     * @param index the position in the index
     * 
     * @return the indexed element; or {@code null} if the index is not
     * part of the sequence (negative or not less than the size)
     */
    @Override
    public E get(int index) {
        if (index >= 0 && index < array.length) return array[index];
        return null;
    }

    /**
     * Get the number of elements in this sequence.
     * 
     * @return the size of this sequence
     */
    @Override
    public int size() {
        return array.length;
    }

    /**
     * @undocumented
     */
    public static void main(String[] args) throws Exception {
        String stong = new String("stong");
        String stong2 = new String("stong");
        Sequence<String> eqidx =
            Sequence.copyOf(Arrays.asList("hello", stong, "big", "stong"));
        System.out.printf("equals: %s%n", eqidx);
        assert stong != stong2;
        Sequence<String> ididx = Sequence
            .identityCopyOf(Arrays.asList("hello", stong, "big", stong2));
        System.out.printf("identity: %s%n", ididx);
    }
}
