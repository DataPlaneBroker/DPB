/**
 * 
 */
package uk.ac.lancs.treeroute;

import java.util.Collection;

/**
 * Implements a virtual switch hierarchically configuring inferior
 * switches.
 * 
 * @author simpsons
 */
public class Aggregator implements Switch {
    /**
     * Create an aggregator consisting of a set of external end points
     * (in terms of the inferior switches that possess them) and links
     * between end points of inferior switches.
     * 
     * @param externalEndPoints the set of external end points
     * 
     * @param links the set of static links from which connections can
     * be built
     */
    public Aggregator(Collection<? extends EndPoint> externalEndPoints,
                      Collection<? extends LinkDescription> links) {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }

    @Override
    public void connect(ConnectionRequest request,
                        ConnectionResponse response) {
        throw new UnsupportedOperationException("unimplemented"); // TODO

    }

    @Override
    public EndPoint findEndPoint(String id) {
        throw new UnsupportedOperationException("unimplemented"); // TODO
    }
}
