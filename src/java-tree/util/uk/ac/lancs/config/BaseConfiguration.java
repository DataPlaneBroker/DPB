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
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BaseConfiguration implements Configuration {
    private final ConfigurationContext context;
    private final URI location;
    private final Properties props;
    private final Collection<String> removed = new HashSet<>();

    private static final Pattern INHERITANCE =
        Pattern.compile("^((?:[^.]+\\.)*)inherit\\.(\\d+)$");

    BaseConfiguration(ConfigurationContext context, URI location,
                      Properties props)
        throws IOException {
        this.context = context;
        this.location = location;
        this.props = new Properties(props);
    }

    void init() throws IOException {
        /* Gather all inheritance declarations. */
        Map<String, Map<Integer, URI>> inheritance = new TreeMap<>();
        for (String key : props.stringPropertyNames()) {
            Matcher m = INHERITANCE.matcher(key);
            if (!m.matches()) continue;
            removed.add(key);
            String prefix = m.group(1);
            int seqno = Integer.parseInt(m.group(2));
            URI reference = this.location.resolve(props.getProperty(key));
            // TODO: Resolve the fragment against the key?
            inheritance.computeIfAbsent(prefix,
                                        k -> new TreeMap<>((a, b) -> Integer
                                            .compare(b, a)))
                .put(seqno, reference);
        }
        for (String key : removed)
            props.remove(key);

        for (Map.Entry<String, Map<Integer, URI>> entry : inheritance
            .entrySet()) {
            /* Identify a prefix to be duplicated from other
             * configurations. */
            final String prefix = entry.getKey();
            final List<URI> uris = new ArrayList<>(entry.getValue().values());
            /* For each of those configurations, copy all its properties
             * using our prefix. */
            for (URI uri : uris) {
                Configuration config = context.get(uri);
                for (String key : config.keys()) {
                    String value = config.get(key);
                    props.put(prefix + key, value);
                }
            }
        }
    }

    private static Properties load(URI location) throws IOException {
        URL url = location.toURL();
        URLConnection conn = url.openConnection();
        try (InputStream in = conn.getInputStream()) {
            Properties result = new Properties();
            result.load(in);
            return result;
        }
    }

    /**
     * Create a configuration from a resource location.
     * 
     * @param location the location of a properties file
     * 
     * @throws IOException if there was an error in loading the
     * properties
     */
    BaseConfiguration(ConfigurationContext context, URI location)
        throws IOException {
        this(context, location, load(location));
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

    public String get(String key) {
        if (removed.contains(key)) return null;
        return props.getProperty(key);
    }

    public Configuration subview(String prefix) {
        prefix = Configuration.normalizePrefix(prefix);
        if (prefix.isEmpty()) return this;
        return new PrefixConfiguration(context, location, this, prefix);
    }

    @Override
    public Iterable<String> keys() {
        return new Iterable<String>() {
            @Override
            public Iterator<String> iterator() {
                return new FilterIterator<String>(props.stringPropertyNames()
                    .iterator(), k -> !removed.contains(k));
            }
        };
    }

    @Override
    public Configuration reference(String key, String value) {
        return subview(Configuration.resolveKey(key, value));
    }

    @Override
    public String absoluteHome() {
        return ".";
    }
}
