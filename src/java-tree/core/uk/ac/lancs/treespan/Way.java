/**
 * 
 */
package uk.ac.lancs.treespan;

/**
 * A distance-vector tuple
 * 
 * @param <N> the node type
 * 
 * @author simpsons
 */
public final class Way<N> {
    /**
     * The next hop to some given destination
     */
    public final N nextHop;

    /**
     * This distance to the destination
     */
    public final double distance;

    /**
     * Create a tuple.
     * 
     * @param nextHop the next hop to the implicit destination
     * 
     * @param distance the distance to the implicit destination
     */
    public Way(N nextHop, double distance) {
        this.nextHop = nextHop;
        this.distance = distance;
    }

    @Override
    public String toString() {
        return distance + " via " + nextHop;
    }

    /**
     * Get the hash code for this distance-vector.
     * 
     * @return the hash code of this object
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        long temp;
        temp = Double.doubleToLongBits(distance);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result =
            prime * result + ((nextHop == null) ? 0 : nextHop.hashCode());
        return result;
    }

    /**
     * Determine whether this distance-vector equals another object.
     * 
     * @param obj the other object
     * 
     * @return {@code true} iff the other object is also a
     * distance-vector, and has the same distance and next hop
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Way<?> other = (Way<?>) obj;
        if (Double.doubleToLongBits(distance) != Double
            .doubleToLongBits(other.distance)) return false;
        if (nextHop == null) {
            if (other.nextHop != null) return false;
        } else if (!nextHop.equals(other.nextHop)) return false;
        return true;
    }
}
