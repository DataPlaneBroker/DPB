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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Maps from keys to values, preserving insertion order and matching
 * keys on identity. This combines the behaviours of
 * {@link LinkedHashMap} and {@link IdentityHashMap}.
 * 
 * @param <K> the key type
 * 
 * @param <V> the value type
 * 
 * @author simpsons
 */
public class LinkedIdentityHashMap<K, V> extends AbstractMap<K, V> {
    private final class Container {
        final K key;

        Container(K key) {
            this.key = key;
        }
    }

    /**
     * Create an initially empty set that preserves insertion order and
     * matches elements on identity. This method simply calls the
     * constructor {@link #LinkedIdentityHashMap()}, and passes it to
     * {@link Collections#newSetFromMap(Map)}.
     * 
     * @param <E> the element type
     * 
     * @return the new set
     */
    public static <E> Set<E> asSet() {
        return Collections.newSetFromMap(new LinkedIdentityHashMap<>());
    }

    /**
     * Create a set that preserves insertion order and matches elements
     * on identity, with initial contents. This method simply calls the
     * constructor {@link #LinkedIdentityHashMap()}, passes it to
     * {@link Collections#newSetFromMap(Map)}, and adds the initial
     * members.
     * 
     * @param <E> the element type
     * 
     * @param initial the initial members
     * 
     * @return the new set
     */
    public static <E> Set<E> asSet(Collection<? extends E> initial) {
        Set<E> result =
            Collections.newSetFromMap(new LinkedIdentityHashMap<>());
        result.addAll(initial);
        return result;
    }

    private final Map<K, Container> containers = new IdentityHashMap<>();

    private final Map<Container, V> base = new LinkedHashMap<>();

    /**
     * Create an initially empty map.
     */
    public LinkedIdentityHashMap() {}

    /**
     * Create a map populated with the entries of another map. The new
     * map will have the iteration order of the other map at the time of
     * the former's construction.
     * 
     * @param initial the map with initial values
     */
    public LinkedIdentityHashMap(Map<? extends K, ? extends V> initial) {
        for (var e : initial.entrySet())
            put(e.getKey(), e.getValue());
    }

    /**
     * Get the size of this map.
     * 
     * @return the size of this map
     */
    @Override
    public int size() {
        final int r = base.size();
        assert containers.size() == r;
        return r;
    }

    /**
     * Test whether an object has an association.
     * 
     * @param key the object to test
     * 
     * @return {@code true} if the key has an associated value;
     * {@code false} otherwise
     */
    @Override
    public boolean containsKey(Object key) {
        return containers.containsKey(key);
    }

    /**
     * Test whether a value is associated with any key.
     * 
     * @param value the value to test
     * 
     * @return {@code true} if the value is associated with a key;
     * {@code false} otherwise
     */
    @Override
    public boolean containsValue(Object value) {
        return base.containsValue(value);
    }

    /**
     * Get the value associated with a key.
     * 
     * @param key the key
     * 
     * @return the associated value; or {@code null} if no value is
     * associated with the key
     */
    @Override
    public V get(Object key) {
        Container c = containers.get(key);
        if (c == null) return null;
        return base.get(c);
    }

    /**
     * Associate a value with a key. The previous association is
     * deleted.
     * 
     * @param key the key
     * 
     * @param value the value
     * 
     * @return the previous value; or {@code null} if there was no
     * previous association
     */
    @Override
    public V put(K key, V value) {
        Container c = containers.computeIfAbsent(key, Container::new);
        return base.put(c, value);
    }

    /**
     * Remove a key from this map. Its associated value will also be
     * removed.
     * 
     * @param key the key to remove
     * 
     * @return the removed value; or {@code null} if the key is not
     * present
     */
    @Override
    public V remove(Object key) {
        Container c = containers.remove(key);
        if (c == null) return null;
        assert c.key == key;
        return base.remove(c);
    }

    /**
     * Remove all entries from this map.
     */
    @Override
    public void clear() {
        containers.clear();
        base.clear();
    }

    private final Set<Entry<K, V>> entrySet = new AbstractSet<Entry<K, V>>() {
        @Override
        public int size() {
            return LinkedIdentityHashMap.this.size();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry)) return false;
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            Object k = e.getKey();
            Container c = containers.get(k);
            if (c == null) return false;
            V value = base.get(c);
            return Objects.equals(value, o);
        }

        @Override
        public Iterator<Entry<K, V>> iterator() {
            Iterator<Entry<Container, V>> iter = base.entrySet().iterator();
            return new Iterator<Entry<K, V>>() {
                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                private K last;

                @Override
                public Entry<K, V> next() {
                    var e = iter.next();
                    last = e.getKey().key;
                    return new Entry<K, V>() {
                        @Override
                        public K getKey() {
                            return e.getKey().key;
                        }

                        @Override
                        public V getValue() {
                            return e.getValue();
                        }

                        @Override
                        public V setValue(V value) {
                            return e.setValue(value);
                        }
                    };
                }

                @Override
                public void remove() {
                    iter.remove();
                    containers.remove(last);
                }
            };
        }

        @Override
        public boolean add(Entry<K, V> e) {
            K k = e.getKey();
            Container c = containers.computeIfAbsent(k, Container::new);
            if (base.containsKey(c)) {
                /* There is already a value. We return true if the new
                 * value is different. */
                V value = base.put(c, e.getValue());
                return !Objects.equals(e.getValue(), value);
            } else {
                base.put(c, e.getValue());
                return true;
            }
        }

        @Override
        public boolean remove(Object o) {
            if (!(o instanceof Entry)) return false;
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            Object k = e.getKey();
            Container c = containers.get(k);
            if (c == null) return false;
            V value = base.get(c);
            if (!Objects.equals(value, e.getValue())) return false;
            containers.remove(k);
            base.remove(c);
            return true;
        }

        @Override
        public void clear() {
            LinkedIdentityHashMap.this.clear();
        }
    };

    /**
     * Get a set view of all entries in this map. Iteration over this
     * set is in insertion order.
     * 
     * @return a set view of this map's entries
     */
    @Override
    public Set<Entry<K, V>> entrySet() {
        return entrySet;
    }

    /**
     * @undocumented
     */
    public static void main(String[] args) throws Exception {
        String stong = new String("stong");
        String stong2 = new String("stong");
        Map<String, String> map = new LinkedIdentityHashMap<>();
        map.put(stong, "keep");
        map.put("hello", "boys");
        map.put(stong2, "dame");
        map.put("steer", "wide");
        System.out.printf("map: %s%n", map);
    }
}
