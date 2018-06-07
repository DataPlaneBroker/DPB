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
 * The files contain Java properties. When loaded, keys ending with
 * <samp>inherit.<var>num</var></samp> and an integer are found. Their
 * values are URI references that identify another part of a properties
 * file, possible the same one. Properties from that file are copied to
 * the referring configuration, with their keys modified. The number
 * specifies the order in which to inherit properties this way, with
 * lower numbers overriding higher ones.
 * 
 * <p>
 * For example, if keys beginning with <samp>foo.bar.</samp> are
 * referenced by entry <samp>yan.tan.inherit.0</samp>, then
 * <samp>foo.bar.baz</samp> will be copied to <samp>yan.tan.baz</samp>.
 * 
 * <p>
 * Inherit directives are removed from the view presented to the user.
 * 
 * <p>
 * Example:
 * 
 * <pre>
 * ConfigurationContext ctxt = new ConfigurationContext();
 * Configuration root = ctxt.get("config.properties");
 * Configuration foo = root.subview("foo");
 * assert foo.get("bar").equals(root.get("foo.bar"));
 * </pre>
 * 
 * @author simpsons
 */
package uk.ac.lancs.config;
