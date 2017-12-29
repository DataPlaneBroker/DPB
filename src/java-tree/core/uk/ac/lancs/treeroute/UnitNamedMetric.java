/**
 * 
 */
package uk.ac.lancs.treeroute;

/**
 * Holds the name and units of a metric. This serves as a convenience
 * for building actual metric classes.
 * 
 * @author simpsons
 */
public abstract class UnitNamedMetric implements Metric {
    private final String name, units;

    /**
     * Create a metric with a given name and units.
     * 
     * @param name the metric's name
     * 
     * @param units the metric's units
     */
    public UnitNamedMetric(String name, String units) {
        this.name = name;
        this.units = units;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String units() {
        return units;
    }
}
