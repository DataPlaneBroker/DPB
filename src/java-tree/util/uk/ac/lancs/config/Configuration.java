/*
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

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Views a set of named configuration properties. Property names are the
 * same as for Java properties files.
 * 
 * <p>
 * Subviews of a configuration are obtainable. For example, if the
 * subview <samp>foo.bar</samp> is obtained, then only properties whose
 * names in the original view begin with <samp>foo.bar.</samp> will be
 * visible. Furthermore, their names will lack the prefix
 * <samp>foo.bar.</samp>.
 * 
 * @author simpsons
 */
public interface Configuration {
    /**
     * Identifies how a configuration parameter was obtained.
     * 
     * @author simpsons
     */
    interface Reference {
        /**
         * Get the base configuration that provides this parameter.
         * 
         * @return the requested base configuration
         */
        Configuration provider();

        /**
         * Get the key by which the provider knows the parameter.
         * 
         * @return the key with respect to the provider
         */
        String key();

        /**
         * Get the raw value of the parameter.
         * 
         * @return the parameter's raw value
         * 
         * @default <code>{@linkplain #provider() provider}().{@linkplain Configuration#get(String) get}({@linkplain #key() key}())</code>
         * is invoked.
         */
        default String value() {
            return provider().get(key());
        }

        /**
         * Get the expanded value of the parameter.
         * 
         * @return the parameter's expanded value
         * 
         * @default <code>{@linkplain #provider() provider}().{@linkplain Configuration#getExpanded(String) getExpanded}({@linkplain #key() key}())</code>
         * is invoked.
         */
        default String expandedValue() {
            return provider().getExpanded(key());
        }

        /**
         * Get the value of the parameter resolved against the provider.
         * 
         * @return the parameter's value resolved against the provider
         */
        default URI location() {
            return provider().resolve(value());
        }

        /**
         * Get the expanded value of the parameter resolved against the
         * provider.
         * 
         * @return the parameter's expanded value resolved against the
         * provider
         */
        default URI expandedLocation() {
            return provider().resolve(expandedValue());
        }
    }

    /**
     * Resolve a string against this configuration's location.
     * 
     * @param value the string to resolve against the configuration
     * 
     * @return the resolved URI
     */
    URI resolve(String value);

    /**
     * Expand a value by looking for <samp>$&#123;...&#125;</samp>
     * structures which are replaced by the expanded values of the
     * specified names.
     * 
     * @param value the value to be expanded
     * 
     * @return the expanded value, or {@code null} if the input is
     * {@code null}
     */
    String expand(String value);

    /**
     * Get a configuration parameter.
     * 
     * @param key the parameter key
     * 
     * @return the parameter's value, or {@code null} if not present
     * 
     * @default {@link #find(String)} is invoked, and
     * {@link Reference#value()} is returned if the result is not
     * {@code null}.
     */
    default String get(String key) {
        Reference ref = find(key);
        if (ref == null) return null;
        return ref.value();
    }

    /**
     * Get a configuration parameter expanded using other values.
     * 
     * @param key the parameter key
     * 
     * @return the parameter's expanded value, or {@code null} if not
     * present
     * 
     * @default {@link #expand(String)} is applied to the result of
     * {@link #get(String)}.
     */
    default String getExpanded(String key) {
        return expand(get(key));
    }

    /**
     * Find a parameter in this configuration or any it is built upon.
     * 
     * @param key the parameter key
     * 
     * @return a reference for the parameter, or {@code null} if not
     * present
     */
    Reference find(String key);

    /**
     * Get the base configuration.
     * 
     * @return the base configuration from which this one is derived
     */
    Configuration base();

    /**
     * Get a subview.
     * 
     * @param prefix the additional prefix to narrow down the available
     * parameters
     * 
     * @return the requested subview
     */
    Configuration subview(String prefix);

    /**
     * Get the prefix of this configuration view.
     * 
     * @return the hidden prefix of parameter names in this view
     */
    String prefix();

    /**
     * List keys in this configuration with a given prefix.
     * 
     * @param prefix the required prefix
     * 
     * @return the keys, retaining the prefix
     */
    Iterable<String> keys(String prefix);

    /**
     * List keys in this configuration.
     * 
     * @return the keys
     * 
     * @default <code>{@linkplain #keys(String) keys}("")</code> is
     * invoked.
     */
    default Iterable<String> keys() {
        return keys("");
    }

    /**
     * Get a configuration parameter, or a default.
     * 
     * @param key the parameter key
     * 
     * @param defaultValue the value to return if the parameter is not
     * set
     * 
     * @return the parameter's value, or <samp>defaultValue</samp> if
     * not set
     */
    default String get(String key, String defaultValue) {
        String value = get(key);
        if (value == null) return defaultValue;
        return value;
    }

    /**
     * Get a configuration parameter, or a default, expanded using other
     * values.
     * 
     * @param key the parameter key
     * 
     * @param defaultValue the value to return if the parameter is not
     * set
     * 
     * @return the parameter's expanded value, or the expansion of
     * <samp>defaultValue</samp> if not present
     * 
     * @default {@link #expand(String)} is applied to the result of
     * {@link #get(String, String)}.
     */
    default String getExpanded(String key, String defaultValue) {
        return expand(get(key, defaultValue));
    }

    /**
     * Convert the plain parameters in this configuration into a
     * conventional Java properties object.
     * 
     * @return a copy of this configuration's parameters
     * 
     * @default The parameters are iterated over using {@link #keys()},
     * and values are stored using {@link #get(String)}.
     */
    default Properties toProperties() {
        return toProperties(Configuration::get);
    }

    /**
     * Convert the transformed parameters in this configuration into a
     * conventional Java properties object.
     * 
     * @param getter Fetches and transforms each parameter.
     * 
     * @return a copy if this configurations parameters
     */
    default Properties
        toProperties(BiFunction<? super Configuration, ? super String, ? extends String> getter) {
        Properties result = new Properties();
        for (String key : keys())
            result.setProperty(key, getter.apply(this, key));
        return result;
    }

    /**
     * Obtain a locally referenced configuration if specified.
     * 
     * @param key the key used to obtain the reference, and as a base
     * for resolving a relative name
     * 
     * @return the referenced configuration, or {@code null} if not
     * specified
     */
    default Configuration reference(String key) {
        return reference(key, (Configuration) null);
    }

    /**
     * Obtain a locally referenced configuration, or a default if not
     * specified.
     * 
     * @param key the key used to obtain the reference, and as a base
     * for resolving a relative name
     * 
     * @param defaultValue the configuration to return if not found
     * 
     * @return the referenced configuration, or the default
     */
    default Configuration reference(String key, Configuration defaultValue) {
        String value = get(key);
        if (value == null) return defaultValue;
        return reference(key, value);
    }

    /**
     * Obtain a locally referenced configuration.
     * 
     * @param base the key used as a base for resolving a relative name
     * 
     * @param path the reference to the configuration
     * 
     * @return the referenced configuration
     * 
     * @throws NullPointerException if <samp>path</samp> is {@code null}
     */
    default Configuration reference(String base, String path) {
        String resolvedKey = resolveKey(base, path);
        return base().subview(resolvedKey);
    }

    /**
     * Obtain locally referenced configurations from a space- or
     * comma-separated list.
     * 
     * @param key the key used to obtain the references, and as a base
     * for resolving relative names
     * 
     * @return a list of configurations corresponding to the references
     * in the specified parameter, or {@code null} if not specified
     */
    default List<Configuration> references(String key) {
        String value = get(key);
        if (value == null) return null;
        return Arrays.asList(value.split("[\\s,]+")).stream()
            .map(s -> reference(key, s)).collect(Collectors.toList());
    }

    /**
     * List a subset of keys.
     * 
     * @param condition a condition selecting keys to include
     * 
     * @return the selected keys
     */
    default Iterable<String>
        selectedKeys(Predicate<? super String> condition) {
        return new Iterable<String>() {
            @Override
            public Iterator<String> iterator() {
                return new FilterIterator<>(keys().iterator(), condition);
            }
        };
    }

    /**
     * List transformed keys matching a prefix.
     * 
     * @param prefix the prefix of keys to match
     * 
     * @param <K> the type to transform keys into
     * 
     * @param xform the transformation function
     * 
     * @return the transformed keys
     */
    default <K> Iterable<K>
        transformedKeys(String prefix,
                        Function<? super String, ? extends K> xform) {
        return new Iterable<K>() {
            @Override
            public Iterator<K> iterator() {
                return new TransformIterator<>(keys(prefix).iterator(),
                                               xform);
            }
        };
    }

    /**
     * List transformed keys.
     * 
     * @param <K> the type to transform keys into
     * 
     * @param xform the transformation function
     * 
     * @return the transformed keys
     */
    default <K> Iterable<K>
        transformedKeys(Function<? super String, ? extends K> xform) {
        return transformedKeys("", xform);
    }

    /**
     * List selected and then transformed keys.
     * 
     * @param precondition a condition selecting keys to include
     * 
     * @param <K> the type to transform keys into
     * 
     * @param xform the transformation function
     * 
     * @return the selected and then transformed keys
     */
    default <K> Iterable<K>
        transformedSelectedKeys(Predicate<? super String> precondition,
                                Function<? super String, ? extends K> xform) {
        return new Iterable<K>() {
            @Override
            public Iterator<K> iterator() {
                return new TransformIterator<>(new FilterIterator<>(keys()
                    .iterator(), precondition), xform);
            }
        };
    }

    /**
     * List a selection of transformed keys.
     * 
     * @param <K> the type to transform keys into
     * 
     * @param xform the transformation function
     * 
     * @param postcondition a condition selecting transformed keys
     * 
     * @return the selected transformed keys
     */
    default <K> Iterable<K>
        selectedTransformedKeys(Function<? super String, ? extends K> xform,
                                Predicate<? super K> postcondition) {
        return new Iterable<K>() {
            @Override
            public Iterator<K> iterator() {
                return new FilterIterator<>(new TransformIterator<>(keys()
                    .iterator(), xform), postcondition);
            }
        };
    }

    /**
     * List a selection of transformed selected keys.
     * 
     * @param precondition a condition selecting keys to include
     * 
     * @param <K> the type to transform keys into
     * 
     * @param xform the transformation function
     * 
     * @param postcondition a condition selecting transformed keys
     * 
     * @return the selected transformed keys
     */
    default <K> Iterable<K>
        selectedTransformedSelectedKeys(Predicate<? super String> precondition,
                                        Function<? super String, ? extends K> xform,
                                        Predicate<? super K> postcondition) {
        return new Iterable<K>() {
            @Override
            public Iterator<K> iterator() {
                return new FilterIterator<>(new TransformIterator<>(new FilterIterator<>(keys()
                    .iterator(), precondition), xform), postcondition);
            }
        };
    }

    /**
     * Get the value of a configuration parameter resolved against the
     * referencing file's location.
     * 
     * @param key the parameter key
     * 
     * @return the resolved parameter as a URI, or {@code null} if not
     * specified
     */
    default URI getLocation(String key) {
        Reference ref = find(key);
        if (ref == null) return null;
        return ref.location();
    }

    /**
     * Get the expanded value of a configuration parameter resolved
     * against the referencing file's location.
     * 
     * @param key the parameter key
     * 
     * @return the resolved parameter as a URI, or {@code null} if not
     * specified
     */
    default URI getExpandedLocation(String key) {
        Reference ref = find(key);
        if (ref == null) return null;
        return ref.expandedLocation();
    }

    /**
     * Get the value of a configuration parameter resolved against the
     * referencing file's location, as a filename.
     * 
     * @param key the parameter key
     * 
     * @return the resolved parameter as a filename, or {@code null} if
     * not specified
     */
    default File getFile(String key) {
        URI location = getLocation(key);
        if (location == null) return null;
        return new File(location);
    }

    /**
     * Get the expanded value of a configuration parameter resolved
     * against the referencing file's location, as a filename.
     * 
     * @param key the parameter key
     * 
     * @return the resolved parameter as a filename, or {@code null} if
     * not specified
     */
    default File getExpandedFile(String key) {
        URI location = getExpandedLocation(key);
        if (location == null) return null;
        return new File(location);
    }

    /**
     * Get the value of a configuration parameter resolved against the
     * referencing file's location, as a file path.
     * 
     * @param key the parameter key
     * 
     * @return the resolved parameter as a file path, or {@code null} if
     * not specified
     */
    default Path getPath(String key) {
        URI location = getLocation(key);
        if (location == null) return null;
        return Paths.get(location);
    }

    /**
     * Get the expanded value of a configuration parameter resolved
     * against the referencing file's location, as a file path.
     * 
     * @param key the parameter key
     * 
     * @return the resolved parameter as a file path, or {@code null} if
     * not specified
     */
    default Path getExpandedPath(String key) {
        URI location = getExpandedLocation(key);
        if (location == null) return null;
        return Paths.get(location);
    }

    /**
     * Get a configuration parameter as an integer, or use a default.
     * 
     * @param key the parameter key
     * 
     * @param defaultValue the value to use if the key is absent
     * 
     * @return the value of the parameter, or the default value if not
     * present
     * 
     * @throws NumberFormatException if the key is present, but does not
     * parse as an integer
     */
    default int getInt(String key, int defaultValue) {
        String text = get(key);
        if (text == null) return defaultValue;
        return Integer.parseInt(text);
    }

    /**
     * Normalize a node key. Double dots are condensed to single ones.
     * Leading and trailing dots are removed.
     * 
     * @param key the node key to normalize
     * 
     * @return the normalized node key
     */
    public static String normalizeKey(String key) {
        if (key == null) return null;
        List<String> parts =
            new LinkedList<>(Arrays.asList(key.split("\\.+")));
        for (ListIterator<String> iter = parts.listIterator(); iter
            .hasNext();) {
            String val = iter.next();
            if (val.isEmpty()) iter.remove();
        }
        return parts.stream().collect(Collectors.joining("."));
    }

    /**
     * Normalize a node key prefix.
     * 
     * @param prefix the node key prefix to normalize
     * 
     * @return the normalized prefix
     */
    public static String normalizePrefix(String prefix) {
        return prefix == null ? null
            : prefix.isEmpty() ? "" : normalizeKey(prefix) + '.';
    }

    /**
     * Resolve a potentially relative key path against a base. If the
     * path does not begin with a <samp>.</samp>, it is returned
     * unchanged. Otherwise, everything after the last <samp>.</samp> in
     * the base is removed, and the path is appended.
     * 
     * @param base the base to resolve the key path against
     * 
     * @param path the key path to resolve
     * 
     * @return the key path resolved against the base
     */
    public static String resolveKey(String base, String path) {
        if (path.isEmpty()) return "";
        if (path.charAt(0) != '.') return path;
        int lastDot = base.lastIndexOf('.');
        if (lastDot < 0) return path.substring(1);
        return base.substring(0, lastDot) + path;
    }
}
