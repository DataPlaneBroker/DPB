/**
 * 
 */
package uk.ac.lancs.treeroute;

/**
 * 
 * 
 * @author simpsons
 */
public interface PhysicalPort extends Port {
    Switch getSwitch();
    String getName();
}
