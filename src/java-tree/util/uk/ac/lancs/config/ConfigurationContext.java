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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Caches mutually referenced configurations. When configurations loaded
 * through the same context contain references to the same
 * configuration, the configuration is fetched only once.
 * 
 * @author simpsons
 */
public final class ConfigurationContext {
    private final Map<URI, BaseConfiguration> cache = new HashMap<>();
    private final Properties defaults;

    /**
     * Create a configuration context with a set of default parameters.
     * 
     * @param defaults the default parameters as Java properties
     */
    public ConfigurationContext(Properties defaults) {
        this.defaults = defaults;
    }

    /**
     * Create a configuration context with no defaults.
     */
    public ConfigurationContext() {
        this.defaults = new Properties();
    }

    private BaseConfiguration getRoot(URI location, Properties defaults)
        throws IOException {
        assert location.getFragment() == null;
        BaseConfiguration result = cache.get(location);
        if (result != null) return result;
        result = new BaseConfiguration(this, location);
        Properties props = ConfigurationContext.load(location, defaults);
        cache.put(location, result);
        result.init(props);
        return result;
    }

    /**
     * Get the configuration for a given location. The fragment may be a
     * subview identifier.
     * 
     * @param location the location to load the configuration from
     * 
     * @return the requested configuration
     * 
     * @throws IOException if there was an error loading the
     * configuration
     */
    public Configuration get(URI location) throws IOException {
        location = location.normalize();
        String fragment = location.getFragment();
        if (fragment != null)
            location = BaseConfiguration.defragment(location);
        Configuration root = getRoot(location, defaults);
        if (fragment != null) root = root.subview(fragment);
        return root;
    }

    /**
     * Get the configuration from a file.
     * 
     * <p>
     * This method simply converts the {@link File} into a {@link URI},
     * and calls {@link #get(URI)}.
     * 
     * @param file the location to load the configuration from
     * 
     * @return the requested configuration
     * 
     * @throws IOException if there was an error loading the
     * configuration
     */
    public Configuration get(File file) throws IOException {
        return get(file.toURI());
    }

    /**
     * Get the configuration from a file named by a string.
     * 
     * <p>
     * This method simply converts the {@link String} into a
     * {@link File}, and calls {@link #get(File)}.
     * 
     * @param name the name of the file to load the configuration from
     * 
     * @return the requested configuration
     * 
     * @throws IOException if there was an error loading the
     * configuration
     */
    public Configuration get(String name) throws IOException {
        return get(new File(name));
    }

    static Properties load(URI location, Properties defaults)
        throws IOException {
        URL url = location.toURL();
        URLConnection conn = url.openConnection();
        try (InputStream in = conn.getInputStream()) {
            Properties result = new Properties(defaults);
            result.load(in);
            return result;
        }
    }

    /**
     * @undocumented
     */
    public static void main(String[] args) throws Exception {
        ConfigurationContext ctxt =
            new ConfigurationContext(System.getProperties());
        Configuration base = ctxt.get(new File(args[0]));
        for (int i = 1; i < args.length; i++) {
            Configuration sub = base.subview(args[i]);
            System.out.printf("%nSubview %s:%n", args[i]);
            for (String key : sub.keys()) {
                System.out.printf("  %s -> [%s]%n", key, sub.get(key));
                try {
                    URI loc = sub.getLocation(key);
                    System.out.printf("  (as URI) -> [%s]%n", loc);
                    File file = sub.getFile(key);
                    System.out.printf("  (as File) -> [%s]%n", file);
                } catch (IllegalArgumentException ex) {
                    // Ignore.
                }
                try {
                    File file = sub.getExpandedFile(key);
                    System.out.printf("  (as expanded File) -> [%s]%n", file);
                } catch (IllegalArgumentException ex) {
                    // Ignore.
                }
            }
        }
    }
}
