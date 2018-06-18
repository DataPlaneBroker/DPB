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

import java.net.URI;

/**
 * Views only parts of a configuration with a given prefix.
 * 
 * @author simpsons
 */
class PrefixConfiguration implements Configuration {
    private final ConfigurationContext context;
    private final String prefix;
    private final Configuration base;
    private final int prefixLength;

    PrefixConfiguration(ConfigurationContext context, Configuration base,
                        String prefix) {
        this.context = context;
        this.base = base;
        this.prefix = Configuration.normalizePrefix(prefix);
        prefixLength = this.prefix.length();
    }

    @Override
    public URI resolve(String value) {
        return base.resolve(value);
    }

    @Override
    public Reference find(String key) {
        return base.find(prefix + key);
    }

    @Override
    public Configuration base() {
        return base;
    }

    @Override
    public String get(String key) {
        return base.get(prefix + key);
    }

    @Override
    public Configuration subview(String prefix) {
        prefix = Configuration.normalizePrefix(this.prefix + prefix);
        if (prefix.isEmpty()) return base;
        return new PrefixConfiguration(context, base, prefix);
    }

    @Override
    public Iterable<String> keys(String prefix) {
        prefix = Configuration.normalizePrefix(this.prefix + prefix);
        return base.transformedKeys(prefix, s -> s.substring(prefixLength));
    }

    @Override
    public String prefix() {
        return prefix;
    }
}
