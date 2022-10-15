// -*- c-basic-offset: 4; indent-tabs-mode: nil -*-

/**
 * 
 */
package uk.ac.lancs.rest.server;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.http.protocol.HttpContext;

/**
 * Weakly associates objects of a type with HTTP contexts. This is meant
 * to be used as a type-safe alternative to
 * {@link HttpContext#setAttribute(String, Object)} and
 * {@link HttpContext#getAttribute(String)}.
 * 
 * <p>
 * Instances of this class should be created as {@code static final}s,
 * for example:
 * 
 * <pre>
 * private static final ContextKey&lt;HttpContext,
 *                                 MyAuthenticationRecord&gt; auths =
 *     new ContextKey<>();
 * </pre>
 * 
 * @author simpsons
 * 
 * @param <C> the context type
 * 
 * @param <T> the type of the objects associated with the context type
 */
public final class ContextKey<C, T> {
    private final Map<C, T> augments = new WeakHashMap<C, T>();

    private final Function<? super C, ? extends T> initializer;

    /**
     * Create a context key with a context aware initializer.
     * 
     * @param initializer Called to set the initial value by
     * {@link #get(Object)} if no value is presently associated with a
     * specified context.
     */
    public ContextKey(Function<? super C, ? extends T> initializer) {
        this.initializer = initializer;
    }

    /**
     * Create a context key with an initializer.
     * 
     * @param initializer Called to set the initial value by
     * {@link #get(Object)} if no value is presently associated with a
     * specified context.
     */
    public ContextKey(Supplier<? extends T> initializer) {
        this(k -> initializer.get());
    }

    /**
     * Create a context key with an initializer that yields
     * {@code null}.
     */
    public ContextKey() {
        this(k -> null);
    }

    /**
     * Set the value of this key in the specified context.
     * 
     * @param ctxt the context to set the value in
     * 
     * @param newValue the new value
     * 
     * @return the previous value
     */
    public synchronized T set(C ctxt, T newValue) {
        return augments.put(ctxt, newValue);
    }

    /**
     * Get the value of this key in the specified context. If no prior
     * value has been set, or it has been reset with
     * {@link #reset(Object)}, the configured initializer is called to
     * get the initial value.
     * 
     * @param ctxt the context to get the value from
     * 
     * @return the current value
     */
    public synchronized T get(C ctxt) {
        return augments.computeIfAbsent(ctxt, initializer);
    }

    /**
     * Remove the value of this key in the specified context. A
     * subsequent call to {@link #get(Object)} will invoke the
     * initializer.
     * 
     * @param ctxt the context in which to reset the value
     * 
     * @return the old value
     */
    public synchronized T reset(C ctxt) {
        return augments.remove(ctxt);
    }
}
