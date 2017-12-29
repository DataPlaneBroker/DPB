/*
 * 
 */
package uk.ac.lancs.treeroute;

/**
 * Represents a physical or virtual switch.
 * 
 * @author simpsons
 */
public interface Switch {
    /**
     * Find the end point of this switch with the given identifier.
     * 
     * @param id the end-point identifier
     * 
     * @return the identified end point, or {@code null} if not found
     */
    EndPoint findEndPoint(String id);

    /**
     * Attempt to allocate a connection.
     * 
     * @param request a description of the required connection
     * 
     * @param response an object to be invoked on allocation completion
     */
    void connect(ConnectionRequest request, ConnectionResponse response);
}
