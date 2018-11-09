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
package uk.ac.lancs.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class BaseConfiguration implements Configuration {
    private final ConfigurationContext context;
    private final URI location;
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, List<Configuration>> inheritance =
        new HashMap<>();

    private static final Pattern URI_SEPARATOR = Pattern.compile("\\s+");
    private static final Pattern INHERITANCE =
        Pattern.compile("^((?:[^.]+\\.)*)inherit$");

    BaseConfiguration(ConfigurationContext context, URI location) {
        this.context = context;
        this.location = location;
    }

    @Override
    public Configuration.Reference find(String key) {
        /* See if we define this parameter directly. */
        String directValue = values.get(key);
        if (directValue != null) return new Configuration.Reference() {
            @Override
            public Configuration provider() {
                return BaseConfiguration.this;
            }

            @Override
            public String key() {
                return key;
            }

            @Override
            public String value() {
                return directValue;
            }
        };

        /* Look for a parameter inherited from another configuration (or
         * just from another location in this one). */
        List<String> components =
            Arrays.asList(COMPONENT_SEPARATOR.split(key));
        final int len = components.size();
        for (int i = len - 1; i > 0; i--) {
            String parentKey =
                String.join(".", components.subList(0, i)) + ".";
            String appendage = String.join(".", components.subList(i, len));
            List<Configuration> subconfs = inheritance.get(parentKey);
            if (subconfs == null) continue;
            for (Configuration subconf : subconfs) {
                Configuration.Reference value = subconf.find(appendage);
                if (value != null) return value;
            }
        }
        return null;
    }

    void init(Properties props) throws IOException {
        /* Record all inheritance. */
        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            Matcher m = INHERITANCE.matcher(key);
            if (m.matches()) {
                String prefix = m.group(1);
                List<URI> sources = Arrays.asList(URI_SEPARATOR.split(value))
                    .stream().map(loc -> location.resolve(loc))
                    .collect(Collectors.toList());
                List<Configuration> delegates =
                    new ArrayList<>(sources.size());
                for (URI source : sources) {
                    Configuration inherited = context.get(source);
                    delegates.add(inherited);
                }
                inheritance.put(prefix, delegates);
            } else {
                values.put(key, value);
            }
        }
    }

    // TODO: Move to ConfigurationContext.
    static Properties load(URI location) throws IOException {
        URL url = location.toURL();
        URLConnection conn = url.openConnection();
        try (InputStream in = conn.getInputStream()) {
            Properties result = new Properties();
            result.load(in);
            return result;
        }
    }

    static URI defragment(URI location) {
        try {
            return new URI(location.getScheme(), location.getRawUserInfo(),
                           location.getHost(), location.getPort(),
                           location.getRawPath(), location.getRawQuery(),
                           null);
        } catch (URISyntaxException ex) {
            throw new AssertionError("unreachable", ex);
        }
    }

    private static final Pattern COMPONENT_SEPARATOR = Pattern.compile("\\.");

    public String get(String key) {
        if (values.containsKey(key)) return values.get(key);
        List<String> components =
            Arrays.asList(COMPONENT_SEPARATOR.split(key));
        final int len = components.size();
        for (int i = len - 1; i > 0; i--) {
            String parentKey =
                String.join(".", components.subList(0, i)) + ".";
            String appendage = String.join(".", components.subList(i, len));
            List<Configuration> subconfs = inheritance.get(parentKey);
            if (subconfs == null) continue;
            for (Configuration subconf : subconfs) {
                String value = subconf.get(appendage);
                if (value != null) return value;
            }
        }
        return null;
    }

    public Configuration subview(String prefix) {
        prefix = Configuration.normalizePrefix(prefix);
        if (prefix.isEmpty()) return this;
        return new PrefixConfiguration(context, this, prefix);
    }

    @Override
    public Iterable<String> keys(String prefix) {
        String safePrefix = Configuration.normalizePrefix(prefix);
        return new Iterable<String>() {
            @Override
            public Iterator<String> iterator() {
                return new Iterator<String>() {
                    final Iterator<Map.Entry<String, List<Configuration>>> inhIter =
                        inheritance.entrySet().iterator();
                    final Collection<String> encountered = new HashSet<>();
                    Iterator<String> ownIter = values.keySet().iterator();
                    Iterator<Configuration> configIter;
                    String prepend = "";
                    String next;

                    private String ensureNext() {
                        while (next == null) {
                            while (!ownIter.hasNext()) {
                                while (configIter == null
                                    || !configIter.hasNext()) {
                                    if (!inhIter.hasNext()) {
                                        assert next == null;
                                        return null;
                                    }
                                    Map.Entry<String, List<Configuration>> entry =
                                        inhIter.next();
                                    prepend = entry.getKey();
                                    if (!prepend.startsWith(safePrefix)
                                        && !safePrefix.startsWith(prepend)) {
                                        continue;
                                    }
                                    configIter = entry.getValue().iterator();
                                }
                                Configuration conf = configIter.next();
                                ownIter = conf.keys().iterator();
                            }
                            next = prepend + ownIter.next();
                            if (!next.startsWith(safePrefix)) {
                                next = null;
                                continue;
                            }

                            /* Check that we haven't already provided
                             * this one. */
                            if (encountered.contains(next)) {
                                next = null;
                                continue;
                            }
                            encountered.add(next);
                        }

                        return next;
                    }

                    @Override
                    public boolean hasNext() {
                        return ensureNext() != null;
                    }

                    @Override
                    public String next() {
                        String result = ensureNext();
                        if (result == null)
                            throw new NoSuchElementException();
                        next = null;
                        return result;
                    }
                };
            }
        };
    }

    @Override
    public Configuration base() {
        return this;
    }

    @Override
    public String prefix() {
        return "";
    }

    @Override
    public URI resolve(String value) {
        return location.resolve(value);
    }

    private static final Pattern REFERENCE_MARK =
        Pattern.compile("(\\$\\{)|(\\$\\$.)|(\\})");

    private static String expand(String rawValue,
                                 Function<String, String> map) {
        if (rawValue == null) return null;
        StringBuilder result = new StringBuilder(rawValue);
        List<Integer> stack = new ArrayList<>();

        boolean found;
        for (Matcher m = REFERENCE_MARK.matcher(result); (found = m.find())
            || !stack.isEmpty();) {
            int end = result.length(), tail = end;
            if (found) {
                if (m.group(1) != null) {
                    /* A new expansion is starting. Remember it. */
                    stack.add(0, m.start());
                    continue;
                }

                if (m.group(2) != null) {
                    /* An escaped character is found. */
                    result.delete(m.start(), m.start() + 2);
                    m.region(m.start() + 1, result.length());
                    continue;
                }

                end = m.start();
                tail = m.end();
            }

            /* An expansion is complete. */
            if (stack.isEmpty()) {
                /* A stray close was found. Just delete it. */
                result.deleteCharAt(m.start());
                m.region(m.start(), result.length());
                continue;
            }

            /* Identify the referenced variable, get its expanded value,
             * and write it in place of the reference. */
            int start = stack.remove(0);
            String varName = result.substring(start + 2, m.start());
            String varVal = expand(map.apply(varName), k -> {
                if (varName.equals(k))
                    throw new IllegalArgumentException("expansion of "
                        + varName + " is recursive in " + rawValue);
                return map.apply(k);
            });
            if (varVal == null) varVal = "";
            result.delete(start, tail);
            result.insert(start, varVal);
            m.region(start + varVal.length(), result.length());
        }
        return result.toString();
    }

    @Override
    public String expand(String value) {
        if (value == null) return null;
        return BaseConfiguration.expand(value, k -> expand(get(k)));
    }
}
