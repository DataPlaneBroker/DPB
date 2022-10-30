// -*- c-basic-offset: 4; indent-tabs-mode: nil -*-

/*
 * Copyright 2018,2019, Lancaster University
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
 *  * Neither the name of the copyright holder nor the names of
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
 * Author: Steven Simpson <http://github.com/simpsonst>
 */

package uk.ac.lancs.rest.server;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

import uk.ac.lancs.rest.service.Method;
import uk.ac.lancs.rest.service.Methods;
import uk.ac.lancs.rest.service.Route;
import uk.ac.lancs.rest.service.Routes;
import uk.ac.lancs.rest.service.Subpath;

/**
 * Contains a set of mappings from HTTP Request URI to REST request
 * handlers to be registered as a unit with a dispatcher. Build a
 * registration with multiple calls to
 * {@link #start(HttpRequestHandler)}:
 * 
 * <pre>
 * RESTRegistration reg = new RESTRegistration();
 * reg.start(this::func1)
 *    .at("/path/to/func1/(?&lt;uuid&gt;[0-9a-fA-F]+)")
 *    .on("POST")
 *    .on("PUT")
 *    .on("GET")
 *    .done();
 * reg.start(this::func2)
 *    .at("/path/to/func2/(?&lt;uuid&gt;[0-9a-fA-F]+)")
 *    .on("GET")
 *    .done();
 * </pre>
 * 
 * <p>
 * &hellip;or with {@link #record(Object, String)} on an object
 * annotated with {@link Route &#64;Route}:
 * 
 * <pre>
 * reg.record(myObject, "/nodes/" + myObject.name());
 * </pre>
 * 
 * <p>
 * Then register all handlers with their paths in one go:
 * 
 * <pre>
 * RESTDispatcher disp = <var>...</var>;
 * reg.register(disp);
 * </pre>
 * 
 * @see RESTDispatcher
 * 
 * @see RESTHandler
 * 
 * @author simpsons
 */
public final class RESTRegistration {
    /**
     * @resume An entry being created in the registration
     * 
     * @author simpsons
     */
    public class Entry {
        final HttpRequestHandler handler;
        final Collection<String> methods = new HashSet<>();
        Pattern pattern;

        Entry(HttpRequestHandler handler) {
            this.handler = handler;
        }

        /**
         * Specify an additional method to match on.
         * 
         * @param method the additional method
         * 
         * @return this object
         */
        public Entry on(String method) {
            requireNonNull(method, "method");
            methods.add(method);
            return this;
        }

        /**
         * Specify a pattern to match the request URI path on.
         * 
         * @param pattern the pattern to match against
         * 
         * @return this object
         */
        public Entry matching(Pattern pattern) {
            requireNonNull(pattern, "pattern");
            this.pattern = pattern;
            return this;
        }

        /**
         * Specify a pattern to match the request URI path or a prefix
         * of it on. The supplied pattern is prefixed with
         * <code>{@value Routes#PATTERN_PREFIX}</code> and suffixed with
         * <code>{@value Routes#OPT_SUBPATH_PATTERN_SUFFIX}</code>
         * before compilation.
         * 
         * @param pattern the pattern to match against
         * 
         * @return this object
         * 
         * @throws PatternSyntaxException if the pattern's syntax is
         * invalid
         * 
         * @see Pattern#compile(String)
         */
        public Entry atOrUnder(String pattern) {
            requireNonNull(pattern, "pattern");
            return matching(Routes.atOrUnder(pattern));
        }

        /**
         * Specify a pattern to match a prefix of the request URI path
         * on. The supplied pattern is prefixed with
         * <code>{@value Routes#PATTERN_PREFIX}</code> and suffixed with
         * <code>{@value Routes#SUBPATH_PATTERN_SUFFIX}</code> before
         * compilation.
         * 
         * @param pattern the pattern to match against
         * 
         * @return this object
         * 
         * @throws PatternSyntaxException if the pattern's syntax is
         * invalid
         * 
         * @see Pattern#compile(String)
         */
        public Entry under(String pattern) {
            requireNonNull(pattern, "pattern");
            return matching(Routes.under(pattern));
        }

        /**
         * Specify a pattern to match the request URI path on exactly.
         * The supplied pattern is prefixed with
         * <code>{@value Routes#PATTERN_PREFIX}</code> and suffixed with
         * <code>{@value Routes#EXACT_PATTERN_SUFFIX}</code> before
         * compilation.
         * 
         * @param pattern the pattern to match against
         * 
         * @return this object
         * 
         * @throws PatternSyntaxException if the pattern's syntax is
         * invalid
         * 
         * @see Pattern#compile(String)
         */
        public Entry at(String pattern) {
            requireNonNull(pattern, "pattern");
            return matching(Routes.at(pattern));
        }

        /**
         * Complete this entry.
         */
        public void done() {
            entries.add(new Record(this));
        }
    }

    @SuppressWarnings("unused")
    private static String toStringVector(String s) {
        return s;
    }

    @SuppressWarnings("deprecation")
    private static Class<RESTHandler> REST_HANDLER_CLASS = RESTHandler.class;

    private static final MethodType extHandleMethodType = MethodType
        .methodType(void.class, HttpRequest.class, HttpResponse.class,
                    HttpContext.class, RESTContext.class);

    private static boolean isCallableAs(MethodType subject,
                                        MethodType target) {
        Class<?> tarRet = target.returnType();
        if (tarRet != Void.TYPE) {
            Class<?> subRet = subject.returnType();
            if (!tarRet.isAssignableFrom(subRet)) return false;
        }
        List<Class<?>> subArgs = subject.parameterList();
        List<Class<?>> tarArgs = target.parameterList();
        if (subArgs.size() != tarArgs.size()) return false;
        for (int i = 0; i < subArgs.size(); i++) {
            Class<?> sub = subArgs.get(i);
            Class<?> tar = tarArgs.get(i);
            if (!sub.isAssignableFrom(tar)) return false;
        }
        return true;
    }

    /**
     * Record annotated methods in the registration.
     * 
     * <p>
     * Each method annotated with {@link Route &#64;Route} is recorded.
     * It must match the signature of one of the functional interface
     * {@link HttpRequestHandler} or {@link RESTHandler}.
     * 
     * <p>
     * The argument <samp>prefix</samp> prefixes the annotation's value,
     * which is then wrapped in
     * <samp>{@value Routes#PATTERN_PREFIX}</samp> and
     * <samp>{@value Routes#OPT_SUBPATH_PATTERN_SUFFIX}</samp>, and
     * compiled as a regular expression with
     * {@link Pattern#compile(String)}.
     * 
     * <p>
     * Multiple {@link Method &#64;Method} annotations may be applied.
     * If none are expressed, <samp>GET</samp> is assumed.
     * 
     * <p>
     * The annotation {@link Subpath &#64;Subpath} may be applied,
     * causing the path specified by {@link Route &#64;Route} to match
     * subpaths as well as the exact path. By setting
     * {@link Subpath#required()} to {@code true}, only strict subpaths
     * will be matched.
     * 
     * @param base the base object to invoke
     * 
     * @param prefix a literal prefix to place before regular
     * expressions
     * 
     * @throws IllegalAccessException if an annotated method is
     * inaccessible
     * 
     * @throws WrongMethodTypeException if an annotated method is of the
     * wrong type
     * 
     * @see Route
     * 
     * @see Method
     */
    public void record(Object base, String prefix)
        throws IllegalAccessException {
        prefix = Pattern.quote(prefix);
        final Class<?> type = base.getClass();
        for (java.lang.reflect.Method func : type.getDeclaredMethods()) {
            /* Eliminate unannotated methods. */
            Route rt = func.getAnnotation(Route.class);
            if (rt == null) continue;
            func.setAccessible(true);

            /* Determine the HTTP methods that this Java method will be
             * invoked on. */
            Collection<String> mtns = new HashSet<>();
            Methods mts = func.getAnnotation(Methods.class);
            if (mts != null) for (Method mt : mts.value())
                mtns.add(mt.value().toUpperCase());
            Method mt = func.getAnnotation(Method.class);
            if (mt != null) mtns.add(mt.value().toUpperCase());
            if (mtns.isEmpty()) mtns.add("GET");
            Subpath ex = func.getAnnotation(Subpath.class);

            /* Create a proxy to invoke the method. */
            final HttpRequestHandler iface;
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle handleHandle = lookup.unreflect(func).bindTo(base);
            if (isCallableAs(handleHandle.type(), extHandleMethodType)) {
                @SuppressWarnings("deprecation")
                RESTHandler foo = MethodHandleProxies
                    .asInterfaceInstance(REST_HANDLER_CLASS, handleHandle);
                /* The default method on foo isn't implemented by the
                 * generated proxy, so we have to wrap it in a 'proper'
                 * one. */
                @SuppressWarnings("deprecation")
                HttpRequestHandler bar = new RESTHandler() {
                    @Override
                    public void
                        handle(HttpRequest request, HttpResponse response,
                               HttpContext context, RESTContext restCtxt)
                            throws HttpException,
                                IOException {
                        foo.handle(request, response, context, restCtxt);
                    }
                };
                iface = bar;
            } else {
                iface = MethodHandleProxies
                    .asInterfaceInstance(HttpRequestHandler.class,
                                         handleHandle);
            }

            Entry en = start(iface);
            for (String m : mtns)
                en.on(m);
            if (ex == null)
                en.at(prefix + rt.value());
            else if (ex.required())
                en.under(prefix + rt.value());
            else
                en.atOrUnder(prefix + rt.value());
            en.done();
        }
    }

    /**
     * Start a new entry for a given handler.
     * 
     * @param handler the handler to be invoked
     * 
     * @return an entry to be built
     * 
     * @constructor
     */
    public Entry start(HttpRequestHandler handler) {
        requireNonNull(handler, "handler");
        return new Entry(handler);
    }

    static class Record {
        final HttpRequestHandler handler;
        final Collection<String> methods;
        final Pattern pattern;

        Record(Entry entry) {
            this.handler = entry.handler;
            this.methods = new HashSet<>(entry.methods);
            this.pattern = entry.pattern;
        }
    }

    private final Collection<Record> entries = new ArrayList<>();

    /**
     * Register the entries with a dispatcher.
     * 
     * @param dispatcher the dispatcher to register with
     */
    public void register(RESTDispatcher dispatcher) {
        dispatcher.register(entries);
    }

    /**
     * Deregister the entries from a dispatcher.
     * 
     * @param dispatcher the dispatcher to deregister from
     */
    public void deregister(RESTDispatcher dispatcher) {
        dispatcher.deregister(entries);
    }

    /**
     * Any trailing URI path elements for URIs matching a pattern
     * supplied to {@link Entry#under(String)} or
     * {@link Entry#atOrUnder(String)}
     */
    public static final RESTField<URI> PATH_INFO =
        RESTField.of(URI::create).from(Routes.PATH_INFO_FIELD_NAME).done();
}
