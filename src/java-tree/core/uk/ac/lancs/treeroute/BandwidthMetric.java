/**
 * 
 */
package uk.ac.lancs.treeroute;

/**
 * Represents a metric that accumulates by choosing the smaller value,
 * with larger values being better.
 * 
 * @author simpsons
 */
public final class BandwidthMetric extends UnitNamedMetric {
    /**
     * Create a bandwidth-like metric with a given name and units.
     * 
     * @param name the metric's name
     * 
     * @param units the metric's units
     */
    public BandwidthMetric(String name, String units) {
        super(name, units);
    }

    /**
     * {@inheritDoc}
     * 
     * @return the minimum of the two arguments
     */
    @Override
    public double accumulate(double v1, double v2) {
        return Math.min(v1, v2);
    }

    /**
     * {@inheritDoc} Larger values are considered better.
     */
    @Override
    public int compare(double v1, double v2) {
        return Double.compare(v1, v2);
    }
}
