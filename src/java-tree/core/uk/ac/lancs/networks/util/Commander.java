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
package uk.ac.lancs.networks.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.config.ConfigurationContext;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.mgmt.PluggableAggregator;
import uk.ac.lancs.networks.mgmt.PluggableNetwork;
import uk.ac.lancs.networks.mgmt.UnpluggableNetwork;

/**
 * Instantiates networks according to configuration, and allows
 * command-line modification to them. The networks are then discarded,
 * so this is only of much use with networks that have external
 * persistent state.
 * 
 * @author simpsons
 */
public final class Commander {
    Map<String, UnpluggableNetwork> networks = new HashMap<>();
    ConfigurationContext configCtxt = new ConfigurationContext();
    Configuration config = null;
    UnpluggableNetwork network = null;
    PluggableNetwork baseNetwork = null;
    PluggableAggregator aggregator = null;

    void process(Iterator<? extends String> iter) throws IOException {
        final String arg = iter.next();
        if (config == null) {
            config = configCtxt.get(arg);
            return;
        }

        if ("-n".equals(arg)) {
            String name = iter.next();
            network = networks.get(name);
            if (network == null) {
                System.err.printf("Unknown network: %s%n", name);
                System.exit(1);
            }
            if (network instanceof PluggableNetwork)
                baseNetwork = (PluggableNetwork) network;
            else
                baseNetwork = null;
            if (network instanceof PluggableAggregator)
                aggregator = (PluggableAggregator) network;
            else
                aggregator = null;
            return;
        }

        if ("+t".equals(arg)) {
            String name = iter.next();
            network.removeTerminal(name);
            return;
        }

        if ("-t".equals(arg)) {
            String name = iter.next();
            String desc = iter.next();
            if (aggregator != null) {
                Terminal inner = findTerminal(desc);
                aggregator.addTerminal(name, inner);
            } else if (baseNetwork != null) {
                baseNetwork.addTerminal(name, desc);
            } else {
                System.err.printf("No network to add terminal to: %s (%s)%n",
                                  name, desc);
                System.exit(1);
            }
            return;
        }
    }

    private Terminal findTerminal(String name) {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    /**
     * Load networks from configuration, then modify them according to
     * command-line arguments.
     * 
     * @param args The first argument must be a configuraton file. The
     * following switches are recognized:
     * 
     * <dl>
     * 
     * <dt><samp>-n <var>network</var></samp>
     * 
     * <dd>Apply subsequent commands to the specified network.
     * 
     * <dt><samp>+t <var>terminal</var></samp>
     * 
     * <dd>Remove the specified terminal from the current network. The
     * name of the terminal is local to the network.
     * 
     * </dl>
     */
    public static void main(String[] args) throws Exception {
        Commander me = new Commander();
        for (Iterator<String> iter = Arrays.asList(args).iterator(); iter
            .hasNext();) {
            me.process(iter);
        }
    }
}
