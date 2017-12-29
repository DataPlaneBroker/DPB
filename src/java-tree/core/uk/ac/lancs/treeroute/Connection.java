/**
 * 
 */
package uk.ac.lancs.treeroute;

/**
 * Represents a connection with allocated resources.
 * 
 * @author simpsons
 */
public interface Connection {
    /**
     * Allow the connection to carry traffic.
     */
    void activate();

    /**
     * Prevent the connection from carrying traffic.
     */
    void deactivate();
    
    boolean isActive();
    
    void release();
}
