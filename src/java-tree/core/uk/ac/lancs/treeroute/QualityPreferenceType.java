/**
 * 
 */
package uk.ac.lancs.treeroute;

import java.util.Arrays;
import java.util.List;

/**
 * Represents the possibility of choosing a sequence of constants in an
 * enumeration.
 * 
 * @param <T> the enumeration type
 * 
 * @author simpsons
 */
public class QualityPreferenceType<T extends Enum<T>> {
    private final T[] values;
    private final long codes;

    /**
     * Create a sequencing possibility for an enumerated type.
     * 
     * @param type the enumerated type
     */
    public QualityPreferenceType(Class<T> type) {
        this.values = type.getEnumConstants();

        {
            /* Count the number of possible codes. */
            long sum = 1;
            for (int i = 2; i <= values.length; i++)
                sum *= i;
            this.codes = sum;
        }
    }

    /**
     * Encode a preference as a potential hash key.
     * 
     * @param pref the preference to be encoded
     * 
     * @return the encoded preference
     */
    public Value of(List<? extends T> pref) {
        return new Value(encode(pref));
    }

    /**
     * Encode a preference as a potential hash key.
     * 
     * @param pref the preference to be encoded
     * 
     * @return the encoded preference
     */
    public Value of(@SuppressWarnings("unchecked") T... pref) {
        return new Value(encode(Arrays.asList(pref)));
    }

    /**
     * Expresses a preference as a potential hash key.
     * 
     * @author simpsons
     */
    public class Value {
        private final long code;

        private Value(long code) {
            this.code = code;
        }

        /**
         * Get the preference expressed by this encoding.
         * 
         * @return a list containing every constant of the enumeration
         * exactly once, expressing the original preference
         */
        public List<T> decode() {
            return decodeToList(code);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            @SuppressWarnings("unchecked")
            Value other = (Value) obj;
            if (code != other.code) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(code);
        }

        @Override
        public String toString() {
            return decode().toString();
        }
    }

    private long encode(List<? extends T> pref) {
        if (pref.size() < values.length - 1)
            throw new IllegalArgumentException("preference too short: "
                + pref);

        if (pref.size() > values.length)
            throw new IllegalArgumentException("preference too long: "
                + pref);
        /* Create an updateable mapping between constant and index. Each
         * time we use the index of a constant, the indices of all
         * constants after it must be decremented. */
        int[] poses = new int[values.length];
        for (int i = 0; i < values.length; i++)
            poses[i] = i;

        long result = 0;
        int radix = values.length - 1;
        for (T t : pref) {
            /* What index identifies the constant? */
            final int ord = t.ordinal();
            int c = poses[ord];
            if (c < 0) throw new IllegalArgumentException("duplicate " + t
                + " in preference " + pref);

            /* Mark the index as used, and decrement later indices. */
            poses[ord] = -1;
            for (int j = ord + 1; j < values.length; j++)
                poses[j]--;

            /* Incorporate the index into the variable-radix result. */
            result *= radix--;
            result += c;
        }
        assert radix <= 0;

        return result;
    }

    private T[] decodeToArray(long code) {
        if (code < 0 || code >= codes)
            throw new IllegalArgumentException("illegal combination code "
                + code + " for " + values.length + " choices");

        /* Generate the co-efficients. */
        int[] poses = new int[values.length];
        for (int i = 2; i < values.length; i++) {
            poses[values.length - i] = (int) (code % i);
            code /= i;
        }
        poses[0] = (int) code;

        T[] rems = Arrays.copyOf(values, values.length);
        @SuppressWarnings("unchecked")
        T[] result = (T[]) new Object[values.length];
        for (int i = 0; i < poses.length; i++) {
            result[i] = rems[poses[i]];
            for (int j = i; j < rems.length - 1; j++)
                poses[j] = poses[j + 1];
        }

        return result;
    }

    private List<T> decodeToList(long code) {
        return Arrays.asList(decodeToArray(code));
    }
}
