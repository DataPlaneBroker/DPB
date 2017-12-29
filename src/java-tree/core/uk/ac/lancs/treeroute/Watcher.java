/**
 * 
 */
package uk.ac.lancs.treeroute;

/**
 * 
 * 
 * @author simpsons
 */
public interface Watcher<T> {
    void invalidate();
    
    void update(T newValue);
}
