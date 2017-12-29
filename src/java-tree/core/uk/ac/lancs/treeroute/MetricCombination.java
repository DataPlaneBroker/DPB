/**
 * 
 */
package uk.ac.lancs.treeroute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

/**
 * Combines a set of distinct metrics.
 * 
 * @author simpsons
 */
public final class MetricCombination {
    private final Metric[] metrics;

    private final byte[] base;

    private final Collection<Comparator<Tuple>> orders;

    private class TupleComparator implements Comparator<Tuple> {
        private final int index;

        private TupleComparator(int index) {
            this.index = index;
        }

        @Override
        public int compare(Tuple arg0, Tuple arg1) {
            for (int i = 0; i < metrics.length; i++) {
                int offset = base[index + i];
                Metric metric = metrics[offset];
                int rc =
                    metric.compare(arg0.values[offset], arg1.values[offset]);
                if (rc != 0) return rc;
            }
            return 0;
        }

        @Override
        public int hashCode() {
            return MetricCombination.this.hashCode() + index;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }
    }

    /**
     * Combine several metrics.
     * 
     * @param metrics the collection of metrics to be combined
     */
    public MetricCombination(Collection<? extends Metric> metrics) {
        if (metrics.size() > Byte.MAX_VALUE)
            throw new IllegalArgumentException("too many metrics: "
                + metrics.size());

        /* Give each metric a distinct index. */
        this.metrics = metrics.toArray(new Metric[metrics.size()]);

        /* Compute the number of arrangements. */
        int arrangements = 1;
        for (int i = metrics.size(); i > 1; i--)
            arrangements *= i;

        Collection<Comparator<Tuple>> orders = new ArrayList<>(arrangements);
        this.orders = Collections.unmodifiableCollection(orders);

        /* Define the initial arrangement. */
        base = new byte[arrangements * metrics.size()];
        for (int i = 0; i < this.metrics.length; i++)
            base[i] = (byte) i;
        orders.add(new TupleComparator(orders.size() * this.metrics.length));

        /* Create the other arrangements. */
        int[] indices = new int[this.metrics.length];
        for (int i = 0; i < this.metrics.length;) {
            if (indices[i] < i) {
                /* Create the next arrangement initially as a copy of
                 * the previous. */
                final int cmpnum = orders.size();
                final int bi = cmpnum * this.metrics.length;
                System.arraycopy(base, (cmpnum - 1) * bi, base, bi,
                                 this.metrics.length);
                orders.add(new TupleComparator(cmpnum * this.metrics.length));

                /* Swap two elements. */
                final int ap, bp;
                if (i % 2 == 0) {
                    ap = bi;
                    bp = ap + i;
                } else {
                    ap = bi + indices[i];
                    bp = bi + i;
                }
                byte tmp = base[ap];
                base[ap] = base[bp];
                base[bp] = tmp;

                /* Increment the counter. */
                indices[i]++;
                i = 0;
            } else {
                indices[i] = 0;
                i++;
            }
        }
        assert orders.size() == arrangements;
    }

    /**
     * Get the count of different ways to sort metrics.
     * 
     * @return the number of different ways to sort metrics
     */
    public int permutations() {
        return orders.size();
    }

    /**
     * Get the set of orderings defined by this combination of metrics.
     * 
     * @return an immutable collection of metric orderings
     */
    public Collection<Comparator<Tuple>> orders() {
        return orders;
    }

    /**
     * Create a tuple of metric values.
     * 
     * @param values the input values
     * 
     * @return a tuple of values
     */
    public Tuple tupleOf(double... values) {
        if (values.length != metrics.length)
            throw new IllegalArgumentException("meter-metric mismatch");
        return new Tuple(Arrays.copyOf(values, metrics.length));
    }

    /**
     * Holds a tuple of values, one for each metric.
     * 
     * @author simpsons
     */
    public final class Tuple {
        private final double[] values;

        private Tuple(double... values) {
            assert values.length == metrics.length;
            this.values = values;
        }

        private MetricCombination otherContainer() {
            return MetricCombination.this;
        }

        /**
         * Combine this tuple with another tuple.
         * 
         * @param other the other tuple
         * 
         * @return the combined tuple
         */
        public Tuple accumulate(Tuple other) {
            if (other.otherContainer() != otherContainer())
                throw new IllegalArgumentException("meters from "
                    + "different metrics" + " not combinable");
            double[] result = Arrays.copyOf(values, values.length);
            for (int i = 0; i < result.length; i++)
                result[i] = metrics[i].accumulate(result[i], other.values[i]);
            return new Tuple(result);
        }

        /**
         * Get the value of an element of the tuple.
         * 
         * @param i which element to get
         * 
         * @return the value of the requested element
         */
        public double get(int i) {
            return values[i];
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + Arrays.hashCode(values);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            Tuple other = (Tuple) obj;
            if (!getOuterType().equals(other.getOuterType())) return false;
            if (!Arrays.equals(values, other.values)) return false;
            return true;
        }

        private MetricCombination getOuterType() {
            return MetricCombination.this;
        }
    }
}
