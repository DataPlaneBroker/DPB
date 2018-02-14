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
package uk.ac.lancs.switches.backend;

import java.util.Collection;
import java.util.Map;

import uk.ac.lancs.switches.EndPoint;
import uk.ac.lancs.switches.NetworkControl;
import uk.ac.lancs.switches.Terminal;
import uk.ac.lancs.switches.TrafficFlow;

/**
 * Abstracts a physical switch. This is a simpler interface than
 * {@link NetworkControl}, as there is no creation of ports, only
 * acquisition, and only binary service state (no reserved/active
 * distinction).
 * 
 * <p>
 * Physical or virtual interfaces on the switch correspond to
 * {@link Terminal}s in the higher abstraction, and are identifiable by
 * implementation-defined strings, usually supplied to the management of
 * the network abstraction along with an abstract terminal name. The
 * network passes this abstraction to {@link #getInterface(String)} when
 * it needs to set up a service (a bridge, at this level).
 * 
 * <p>
 * When end points of several interfaces and bandwidth requirements have
 * been gathered to implement a service, they are a requested as a
 * bridge with {@link #bridge(BridgeListener, Map)}. The physical switch
 * ensures that each requested bridge exists (it might already), and
 * then a call to {@link #retainBridges(Collection)} can be used at the
 * end of a recovery phase to flush out resources left over from
 * previous invocations.
 * 
 * @author simpsons
 */
public interface Switch {
    /**
     * Identify an interface by description. Terminal objects generated
     * by a switch should equate when described using the same string.
     * 
     * @param desc a description of the interface
     * 
     * @return a reference which identifies the interface
     */
    Terminal getInterface(String desc);

    /**
     * Ensure that a bridge exists. A distinct {@link Bridge} object may
     * be returned even if the bridge details are the same, as the
     * listener may be different.
     * 
     * @param listener an object informed about changes to the state of
     * the bridge
     * 
     * @param details end points and bandwidth requirements of the
     * bridge
     * 
     * @return a reference to the bridge
     */
    Bridge bridge(BridgeListener listener,
                  Map<? extends EndPoint, ? extends TrafficFlow> details);

    /**
     * Retain only the specified bridges, discarding all others. Since
     * two {@link Bridge} objects may refer to the same bridge, a bridge
     * should be retained as long as at least one {@link Bridge} object
     * refers to it.
     * 
     * @param bridges the set of bridges to retain
     */
    void retainBridges(Collection<? extends Bridge> bridges);
}
