// -*- c-basic-offset: 4; indent-tabs-mode: nil -*-

/**
 * 
 */
package uk.ac.lancs.rest.service;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Provides static methods for augmenting user-supplied regular
 * expressions in a consistent way.
 * 
 * @author simpsons
 */
public final class Routes {
    /**
     * The string <samp>{@value}</samp> prefixed to patterns specified
     * by {@link Route &#64;Route}
     */
    public static final String PATTERN_PREFIX = "^";

    /**
     * The name of the capture in the regex pattern
     * {@link #OPT_SUBPATH_PATTERN_SUFFIX}
     */
    public static final String PATH_INFO_FIELD_NAME = "pathInfo";

    /**
     * The string <samp>{@value}</samp> suffixed to patterns specified
     * by {@link Route &#64;Route} with {@link Subpath#required()} set
     * to {@code false}
     */
    public static final String OPT_SUBPATH_PATTERN_SUFFIX =
        "(?<" + PATH_INFO_FIELD_NAME + ">/.*)?$";

    /**
     * The string <samp>{@value}</samp> suffixed to patterns specified
     * by {@link Route &#64;Route} with {@link Subpath#required()} set
     * to {@code true}
     */
    public static final String SUBPATH_PATTERN_SUFFIX =
        "(?<" + PATH_INFO_FIELD_NAME + ">/.*)$";

    /**
     * The string <samp>{@value}</samp> suffixed to patterns specified
     * by {@link Route &#64;Route} without {@link Subpath &#64;Subpath}
     */
    public static final String EXACT_PATTERN_SUFFIX = "$";

    private Routes() {}

    /**
     * Get the text of the regular expression matching paths strictly
     * under a given prefix pattern. The supplied pattern is prefixed
     * with <code>{@value Routes#PATTERN_PREFIX}</code> and suffixed
     * with <code>{@value Routes#SUBPATH_PATTERN_SUFFIX}</code> before
     * compilation.
     * 
     * @param pattern the pattern to match against
     * 
     * @return the uncompiled pattern with the described prefix and
     * suffix applied
     */
    public static String rawUnder(String pattern) {
        return Routes.PATTERN_PREFIX + pattern
            + Routes.SUBPATH_PATTERN_SUFFIX;
    }

    /**
     * Get the regular expression matching paths strictly under a given
     * prefix pattern. {@link #rawUnder(String)} is applied to the
     * pattern before compilation.
     * 
     * @param pattern the pattern to match against
     * 
     * @throws PatternSyntaxException if the pattern's syntax is invalid
     * 
     * @return the compiled pattern with the described prefix and suffix
     * applied
     */
    public static Pattern under(String pattern) {
        return Pattern.compile(rawUnder(pattern));
    }

    /**
     * Get the text of the regular expression matching an exact path.
     * The supplied pattern is prefixed with
     * <code>{@value Routes#PATTERN_PREFIX}</code> and suffixed with
     * <code>{@value Routes#EXACT_PATTERN_SUFFIX}</code> before
     * compilation.
     * 
     * @param pattern the pattern to match against
     * 
     * @return the uncompiled pattern with the described prefix and
     * suffix applied
     */
    public static String rawAt(String pattern) {
        return Routes.PATTERN_PREFIX + pattern + Routes.EXACT_PATTERN_SUFFIX;
    }

    /**
     * Get the regular expression matching an exact path.
     * {@link #rawAt(String)} is applied to the pattern before
     * compilation.
     * 
     * @param pattern the pattern to match against
     * 
     * @throws PatternSyntaxException if the pattern's syntax is invalid
     * 
     * @return the compiled pattern with the described prefix and suffix
     * applied
     */
    public static Pattern at(String pattern) {
        return Pattern.compile(rawAt(pattern));
    }

    /**
     * Get the text of the regular expression matching an exact path or
     * anything under it as a prefix. The supplied pattern is prefixed
     * with <code>{@value Routes#PATTERN_PREFIX}</code> and suffixed
     * with <code>{@value Routes#OPT_SUBPATH_PATTERN_SUFFIX}</code>
     * before compilation.
     * 
     * @param pattern the pattern to match against
     * 
     * @return the uncompiled pattern with the described prefix and
     * suffix applied
     */
    public static String rawAtOrUnder(String pattern) {
        return Routes.PATTERN_PREFIX + pattern
            + Routes.OPT_SUBPATH_PATTERN_SUFFIX;
    }

    /**
     * Get the regular expression matching an exact path or anything
     * under it as a prefix. {@link #rawAtOrUnder(String)} is applied to
     * the pattern before compilation.
     * 
     * @param pattern the pattern to match against
     * 
     * @throws PatternSyntaxException if the pattern's syntax is invalid
     * 
     * @return the compiled pattern with the described prefix and suffix
     * applied
     */
    public static Pattern atOrUnder(String pattern) {
        return Pattern.compile(rawAtOrUnder(pattern));
    }
}
