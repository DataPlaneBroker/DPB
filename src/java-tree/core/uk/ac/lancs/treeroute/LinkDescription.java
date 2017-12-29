/**
 * 
 */
package uk.ac.lancs.treeroute;

import java.util.Map;

/**
 * Describes an available resource connecting two sets of end points
 * together.
 * 
 * @author simpsons
 */
public interface LinkDescription {
    /**
     * Get the bandwidth shared by all pairs of end points on this link.
     * 
     * @return the total bandwidth
     */
    long totalBandwidth();

    /**
     * Get the fixed delay of this link.
     * 
     * @return the link's delay
     */
    int delay();

    /**
     * Get the mapping of each end point to its peer.
     * 
     * @return the peer mapping
     */
    Map<? super EndPoint, ? extends EndPoint> mapping();

    /**
     * Determine whether this link is bidirectional.
     * 
     * @return {@code true} if the link is bidirectional; {@code false}
     * otherwise
     */
    boolean bidi();
}
