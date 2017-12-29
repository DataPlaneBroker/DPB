/**
 * 
 */
package uk.ac.lancs.treeroute;

/**
 * Represents accumulated metrics of a path, route or link.
 * 
 * @author simpsons
 */
public interface Metric {
    /**
     * Accumulate two meters.
     * 
     * @param v1 the first meter
     * 
     * @param v2 the second meter
     * 
     * @return the accumulation of the two meters
     */
    double accumulate(double v1, double v2);

    /**
     * Compare two meters.
     * 
     * @param v1 the first meter
     * 
     * @param v2 the second meter
     * 
     * @return negative if the first meter is better than the second;
     * positive if the second is better; zero if they are equivalent
     */
    int compare(double v1, double v2);

    /**
     * Get the metric name. Examples include <samp>bandwidth</samp>,
     * <samp>delay</samp>, etc.
     * 
     * @return the metric's name
     */
    String name();

    /**
     * Get the metric units.
     * 
     * @return the metric's units
     */
    String units();
}
