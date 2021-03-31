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

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Assigns a zero-based integer to each distinct element of a
 * collection.
 * 
 * @author simpsons
 */
class Index<E> extends AbstractList<E> {
    private final E[] array;

    private final Map<E, Integer> map;

    private Index(E[] array, Map<E, Integer> map) {
        this.array = array;
        this.map = map;
    }

    /**
     * Create an index. The iteration order of the collection will
     * determine the index of each element.
     * 
     * @param <E> the element type
     * 
     * @param input the collection to create an index of
     * 
     * @return an index of the provided elements
     */
    public static <E> Index<E> of(Collection<? extends E> input) {
        List<E> order = List.copyOf(new LinkedHashSet<>(input));
        Map<E, Integer> map = IntStream.range(0, order.size()).boxed()
            .collect(Collectors.toMap(order::get, i -> i));
        @SuppressWarnings("unchecked")
        E[] array = (E[]) new Object[order.size()];
        order.toArray(array);
        return new Index<>(array, map);
    }

    /**
     * Get the index position of an element.
     * 
     * @param elem the element whose position is sought
     * 
     * @return the element's position
     * 
     * @throws NoSuchElementException if the element is not in the index
     */
    public int getAsInt(E elem) {
        Integer r = map.get(elem);
        if (r == null) throw new NoSuchElementException(elem.toString());
        return r;
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

    public Map<Integer, E> decode() {
        return indexToElem;
    }

    public Map<E, Integer> encode() {
        return elemToIndex;
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
                        if (i < array.length)
                            return new Map.Entry<E, Integer>() {
                                int p = i++;

                                @Override
                                public E getKey() {
                                    return array[p];
                                }

                                @Override
                                public Integer getValue() {
                                    return p;
                                }

                                @Override
                                public Integer setValue(Integer value) {
                                    throw new UnsupportedOperationException("unsupported");
                                }
                            };
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
                        if (i < array.length)
                            return new Map.Entry<Integer, E>() {
                                int p = i++;

                                @Override
                                public Integer getKey() {
                                    return p;
                                }

                                @Override
                                public E getValue() {
                                    return array[p];
                                }

                                @Override
                                public E setValue(E value) {
                                    throw new UnsupportedOperationException("unsupported");
                                }
                            };
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
     * @return the indexed element
     * 
     * @throws IndexOutOfBoundsException if the position is not in the
     * index
     */
    @Override
    public E get(int index) {
        try {
            return array[index];
        } catch (ArrayIndexOutOfBoundsException ex) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    @Override
    public int size() {
        return array.length;
    }
}
