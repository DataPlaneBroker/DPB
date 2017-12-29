/**
 * 
 */
package uk.ac.lancs.treeroute;

/**
 * Represents a metric that is accumulated by multiplication, with
 * higher values being better.
 * 
 * @author simpsons
 */
public final class ReliabilityMetric extends UnitNamedMetric {
    /**
     * Create a reliability-like metric with a given name and units.
     * 
     * @param name the metric's name
     * 
     * @param units the metric's units
     */
    public ReliabilityMetric(String name, String units) {
        super(name, units);
    }

    /**
     * {@inheritDoc}
     * 
     * @return the product of the two arguments
     */
    @Override
    public double accumulate(double v1, double v2) {
        return v1 * v2;
    }

    /**
     * {@inheritDoc} Higher values are considered better.
     */
    @Override
    public int compare(double v1, double v2) {
        return Double.compare(v1, v2);
    }
}
