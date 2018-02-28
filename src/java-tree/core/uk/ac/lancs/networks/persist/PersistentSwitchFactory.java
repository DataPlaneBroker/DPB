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
package uk.ac.lancs.networks.persist;

import java.sql.SQLException;

import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.networks.mgmt.Network;
import uk.ac.lancs.networks.mgmt.NetworkContext;
import uk.ac.lancs.networks.mgmt.NetworkFactory;
import uk.ac.lancs.scc.jardeps.Service;

/**
 * Creates persistent network switches.
 * 
 * @see PersistentSwitch
 * 
 * @author simpsons
 */
@Service(NetworkFactory.class)
public final class PersistentSwitchFactory implements NetworkFactory {
    /**
     * @undocumented
     */
    public static final String TYPE_NAME = "persistent-switch";

    /**
     * {@inheritDoc}
     * 
     * <p>
     * This implementation recognizes only the string
     * <samp>{@value #TYPE_NAME}</samp>.
     */
    @Override
    public boolean recognize(String type) {
        return TYPE_NAME.equals(type);
    }

    @Override
    public Network makeNetwork(NetworkContext ctxt, Configuration conf) {
        PersistentSwitch result = new PersistentSwitch(ctxt.executor(), conf);
        try {
            result.init();
        } catch (SQLException ex) {
            throw new RuntimeException("initializing", ex);
        }
        return result;
    }
}
