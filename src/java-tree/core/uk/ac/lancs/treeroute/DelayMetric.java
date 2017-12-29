/**
 * 
 */
package uk.ac.lancs.treeroute;

/**
 * Represents a metric that accumulates by addition, with lower values
 * being better.
 * 
 * @author simpsons
 */
public final class DelayMetric extends UnitNamedMetric {
    /**
     * Create a delay-like metric with a given name and units.
     * 
     * @param name the metric's name
     * 
     * @param units the metric's units
     */
    public DelayMetric(String name, String units) {
        super(name, units);
    }

    /**
     * {@inheritDoc}
     * 
     * @return the sum of the two arguments
     */
    @Override
    public double accumulate(double v1, double v2) {
        return v1 + v2;

    }

    /**
     * {@inheritDoc} Smaller values are considered better.
     */
    @Override
    public int compare(double v1, double v2) {
        return Double.compare(v2, v1);
    }
}
