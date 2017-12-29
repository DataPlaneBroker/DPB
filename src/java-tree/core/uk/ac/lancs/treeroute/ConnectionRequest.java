/**
 * 
 */
package uk.ac.lancs.treeroute;

/**
 * 
 * 
 * @author simpsons
 */
public interface ConnectionRequest {
    EndPoint source();
    
    EndPoint destination();
    
    boolean bidi();
    
    long bandwidth();
    
    int delay();
}
