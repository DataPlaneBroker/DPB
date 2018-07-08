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

/**
 * Contains packages for managing self-referencing configuration files.
 * The files contain Java properties, with keys of the form
 * <samp>foo.bar.baz</samp>. Subviews of a configuration can be obtained
 * by specifying a prefix. With a prefix of <samp>foo.</samp>, all
 * properties whose names don't begin with this prefix are hidden, and
 * the names of remaining ones have the prefix removed.
 * 
 * <p>
 * When a base configuration (one with no prefix) is loaded, keys ending
 * with <samp>inherit</samp> are found. Their values are space-separated
 * URI references that identify part of a configuration file, possibly
 * the same one. URI fragment identifiers identify subviews, so
 * <samp>foo.properties#bar.baz</samp> accesses only the properties in
 * <samp>foo.properties</samp> whose names begin with
 * <samp>bar.baz.</samp>, and with that prefix removed. Properties from
 * each referenced file are visible in place of the <samp>inherit</samp>
 * properties. For example, if keys beginning with <samp>foo.bar.</samp>
 * are referenced by entry <samp>yan.tan.inherit</samp>, then
 * <samp>foo.bar.baz</samp> in the referenced configuration will be
 * appear as <samp>yan.tan.baz</samp> in the referring configuration.
 * When multiple URI references are provided, properties inherited due
 * to earlier URIs hide later ones with the same derived name.
 * Uninherited properties hide inherited ones.
 * 
 * <p>
 * Inherit directives are removed from the view presented to the user.
 * 
 * <p>
 * The starting point is to create a {@link ConfigurationContext}, and
 * use it to load a root configuration from a file. For example:
 * 
 * <pre>
 * ConfigurationContext ctxt = new ConfigurationContext();
 * Configuration root = ctxt.get("config.properties");
 * Configuration foo = root.subview("foo");
 * assert foo.get("bar").equals(root.get("foo.bar"));
 * </pre>
 * 
 * <p>
 * Configurations loaded using the same context are cached, so that
 * complex references do not result in multiple loads of the same file.
 * 
 * @author simpsons
 */
package uk.ac.lancs.config;
