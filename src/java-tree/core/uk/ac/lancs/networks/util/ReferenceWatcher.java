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
package uk.ac.lancs.networks.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Watches indexed objects for collectability, creates them on demand,
 * and discards them when collectable.
 * 
 * @param <X> the external type of the referenced objects; must be an
 * interface
 * 
 * @param <I> an internal type (often a subtype of {@code <X>}) not
 * exposed to the clients
 * 
 * @param <K> the key type used to index objects
 * 
 * @author simpsons
 */
public final class ReferenceWatcher<X, I, K> {
    private final Class<X> type;
    private final ClassLoader loader;
    private final Function<? super K, ? extends I> builder;
    private final Function<? super I, ? extends X> exposer;
    private final Consumer<? super I> terminator;

    private final ReferenceQueue<X> queue = new ReferenceQueue<>();

    private static class TReference<X, I, K> extends WeakReference<X> {
        final K key;
        final I base;

        /**
         * @param referent
         */
        public TReference(K key, X referent, I base,
                          ReferenceQueue<X> queue) {
            super(referent, queue);
            this.key = key;
            this.base = base;
        }
    }

    private final Map<K, TReference<X, I, K>> references = new HashMap<>();

    /**
     * Create a reference watcher where the internal type is a subtype
     * of the external type.
     * 
     * @param <T> the external type of the referenced objects; must be
     * an interface
     * 
     * @param <S> the internal type (a subtype of {@code <T>}) not
     * exposed to the clients
     * 
     * @param <K> the key type used to index objects
     * 
     * @param type the type of the referenced objects
     * 
     * @param loader used to create proxies for the external type
     * 
     * @param builder invoked to create new objects given a key
     * 
     * @param terminator invoked to clean up discarded objects
     * 
     * @constructor
     * 
     * @return the requested watcher
     */
    public static <T, S extends T, K> ReferenceWatcher<T, S, K>
        on(Class<T> type, ClassLoader loader,
           Function<? super K, ? extends S> builder,
           Consumer<? super S> terminator) {
        return new ReferenceWatcher<>(type, loader, builder, obj -> obj,
                                      terminator);
    }

    /**
     * Create a reference watcher.
     * 
     * @param <T> the external type of the referenced objects; must be
     * an interface
     * 
     * @param <S> the internal type not exposed to the clients
     * 
     * @param <K> the key type used to index objects
     * 
     * @param type the type of the referenced objects
     * 
     * @param loader used to create proxies for the external type
     * 
     * @param builder invoked to create new objects given a key
     * 
     * @param exposer maps the internal object to the external object
     * 
     * @param terminator invoked to clean up discarded objects
     * 
     * @constructor
     * 
     * @return the requested watcher
     */
    public static <T, S, K> ReferenceWatcher<T, S, K>
        on(Class<T> type, ClassLoader loader,
           Function<? super K, ? extends S> builder,
           Function<? super S, ? extends T> exposer,
           Consumer<? super S> terminator) {
        return new ReferenceWatcher<>(type, loader, builder, exposer,
                                      terminator);
    }

    private ReferenceWatcher(Class<X> type, ClassLoader loader,
                             Function<? super K, ? extends I> builder,
                             Function<? super I, ? extends X> exposer,
                             Consumer<? super I> terminator) {
        if (!type.isInterface())
            throw new IllegalArgumentException("not an interface: " + type);
        if (builder == null) throw new NullPointerException("builder");
        if (terminator == null) throw new NullPointerException("terminator");
        this.type = type;
        this.loader = loader;
        this.builder = builder;
        this.exposer = exposer;
        this.terminator = terminator;
    }

    /**
     * Start watching for discarded references. A daemon thread is
     * created.
     */
    public void start() {
        Thread t = new Thread(this::poll, "reference watcher");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Get the base for a given key. If the external reference is not
     * also held, this object could be terminated while in use.
     * 
     * @param key the index of the required object
     * 
     * @return the required object
     */
    public synchronized I getBase(K key) {
        return references.get(key).base;
    }

    /**
     * Discard a reference for a given key.
     * 
     * @param key the index of the redundant object
     */
    public synchronized void discard(K key) {
        TReference<X, I, K> ref = references.get(key);
        if (ref == null) return;
        X result = ref.get();
        if (result != null) {
            ref.clear();
            terminator.accept(ref.base);
        }
        references.remove(key);
    }

    /**
     * Get the reference for a given key.
     * 
     * @param key the index of the required object
     * 
     * @return a proxy to the required object
     */
    public synchronized X get(K key) {
        /* Use the cached object if still available. */
        TReference<X, I, K> ref = references.get(key);
        if (ref != null) {
            X result = ref.get();
            if (result != null) return result;
            references.remove(key);
        }

        /* Create and retain a new base. */
        I base = builder.apply(key);
        if (base == null) return null;

        /* Create a proxy for the base, and retain a weak reference for
         * it. */
        X exposed = exposer.apply(base);
        X proxy = proxify(exposed);
        ref = new TReference<X, I, K>(key, proxy, base, queue);
        references.put(key, ref);

        return proxy;
    }

    /**
     * Get the reference for a given key, having deleted any prior
     * object.
     * 
     * @param key the index of the required object
     * 
     * @return a proxy to the required object
     */
    public synchronized X getFresh(K key) {
        /* Eliminate the cached object if present. */
        TReference<X, I, K> ref = references.get(key);
        if (ref != null) {
            X result = ref.get();
            if (result != null) {
                ref.clear();
                terminator.accept(ref.base);
            }
            references.remove(key);
        }

        /* Create and retain a new base. */
        I base = builder.apply(key);
        if (base == null) return null;

        /* Create a proxy for the base, and retain a weak reference for
         * it. */
        X exposed = exposer.apply(base);
        X proxy = proxify(exposed);
        ref = new TReference<X, I, K>(key, proxy, base, queue);
        references.put(key, ref);

        return proxy;
    }

    private void poll() {
        for (;;) {
            try {
                @SuppressWarnings("unchecked")
                TReference<X, I, K> ref =
                    (TReference<X, I, K>) queue.remove();
                K key = ref.key;
                synchronized (this) {
                    I base = ref.base;
                    terminator.accept(base);
                    references.remove(key);
                }
            } catch (InterruptedException e) {
                /* Do nothing. Go round again. This thread should never
                 * be interrupted. */
            }
        }
    }

    @SuppressWarnings("unchecked")
    private X proxify(X base) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
                System.err.printf("invoking %s.%s(%s)%n", base, method, args);
                try {
                    return method.invoke(base, args);
                } catch (InvocationTargetException ex) {
                    try {
                        throw ex.getCause();
                    } catch (RuntimeException | Error e) {
                        throw e;
                    } catch (Throwable t) {
                        for (Class<?> ok : method.getExceptionTypes()) {
                            if (ok.isInstance(t)) throw t;
                        }
                        throw ex;
                    }
                }
            }
        };
        return (X) Proxy.newProxyInstance(loader, new Class<?>[] { type },
                                          handler);
    }
}
