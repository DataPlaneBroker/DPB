/**
 * 
 */
package uk.ac.lancs.treespan;

/**
 * Holds an unordered pair of nodes. This object is suitable as a hash
 * key.
 * 
 * @param <N> the node type
 * 
 * @author simpsons
 */
public final class Pair<N> {
    /**
     * The first node
     */
    public final N first;

    /**
     * The second node
     */
    public final N second;

    private final int firstHash, secondHash;

    /**
     * Create a pair of nodes. The supplied arguments might not match
     * the eventual field values.
     * 
     * @param first one of the nodes
     * 
     * @param second the other node
     * 
     * @param <N> the node type
     */
    public static <N> Pair<N> of(N first, N second) {
        return new Pair<>(first, second);
    }

    private Pair(N first, N second) {
        if (first == null) throw new NullPointerException("first");
        if (second == null) throw new NullPointerException("second");
        int firstHash = first.hashCode();
        int secondHash = second.hashCode();
        if (firstHash < secondHash) {
            this.first = first;
            this.second = second;
            this.firstHash = firstHash;
            this.secondHash = secondHash;
        } else {
            this.first = second;
            this.second = first;
            this.firstHash = secondHash;
            this.secondHash = firstHash;
        }
    }

    /**
     * Get the hash code of this pair.
     * 
     * @return the hash code of this pair, a combination of the hash
     * codes of the elements
     */
    @Override
    public int hashCode() {
        return firstHash * 31 + secondHash;
    }

    /**
     * Determine whether this pair equals another object.
     * 
     * @param other the other object
     * 
     * @return {@code true} iff the other object is a pair of the same
     * elements
     */
    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (!(other instanceof Pair)) return false;
        Pair<?> p = (Pair<?>) other;
        return first.equals(p.first) && second.equals(p.second);
    }

    /**
     * Get a string representation of this pair.
     * 
     * @return string representations of the two elements, separated by
     * a comma, and surrounded by angle brackets
     */
    @Override
    public String toString() {
        return "<" + first + "," + second + ">";
    }
}
