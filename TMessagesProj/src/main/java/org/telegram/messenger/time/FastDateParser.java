/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.telegram.messenger.time;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>FastDateParser is a fast and thread-safe version of
 * {@link java.text.SimpleDateFormat}.</p>
 *
 * <p>This class can be used as a direct replacement for
 * <code>SimpleDateFormat</code> in most parsing situations.
 * This class is especially useful in multi-threaded server environments.
 * <code>SimpleDateFormat</code> is not thread-safe in any JDK version,
 * nor will it be as Sun has closed the
 * <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4228335">bug</a>/RFE.
 * </p>
 *
 * <p>Only parsing is supported, but all patterns are compatible with
 * SimpleDateFormat.</p>
 *
 * <p>Timing tests indicate this class is as about as fast as SimpleDateFormat
 * in single thread applications and about 25% faster in multi-thread applications.</p>
 *
 * @version $Id: FastDateParser.java 1572877 2014-02-28 08:42:25Z britter $
 * @since 3.2
 */
public class FastDateParser implements DateParser, Serializable {
    /**
     * Required for serialization support.
     *
     * @see java.io.Serializable
     */
    private static final long serialVersionUID = 2L;

    static final Locale JAPANESE_IMPERIAL = new Locale("ja", "JP", "JP");

    // defining fields
    private final String pattern;
    private final TimeZone timeZone;
    private final Locale locale;
    private final int century;
    private final int startYear;

    // derived fields
    private transient Pattern parsePattern;
    private transient Strategy[] strategies;

    // dynamic fields to communicate with Strategy
    private transient String currentFormatField;
    private transient Strategy nextStrategy;

    /**
     * <p>Constructs a new FastDateParser.</p>
     *
     * @param pattern  non-null {@link java.text.SimpleDateFormat} compatible
     *                 pattern
     * @param timeZone non-null time zone to use
     * @param locale   non-null locale
     */
    protected FastDateParser(final String pattern, final TimeZone timeZone, final Locale locale) {
        this(pattern, timeZone, locale, null);
    }

    /**
     * <p>Constructs a new FastDateParser.</p>
     *
     * @param pattern      non-null {@link java.text.SimpleDateFormat} compatible
     *                     pattern
     * @param timeZone     non-null time zone to use
     * @param locale       non-null locale
     * @param centuryStart The start of the century for 2 digit year parsing
     * @since 3.3
     */
    protected FastDateParser(final String pattern, final TimeZone timeZone, final Locale locale, final Date centuryStart) {
        this.pattern = pattern;
        this.timeZone = timeZone;
        this.locale = locale;

        final Calendar definingCalendar = Calendar.getInstance(timeZone, locale);
        int centuryStartYear;
        if (centuryStart != null) {
            definingCalendar.setTime(centuryStart);
            centuryStartYear = definingCalendar.get(Calendar.YEAR);
        } else if (locale.equals(JAPANESE_IMPERIAL)) {
            centuryStartYear = 0;
        } else {
            // from 80 years ago to 20 years from now
            definingCalendar.setTime(new Date());
            centuryStartYear = definingCalendar.get(Calendar.YEAR) - 80;
        }
        century = centuryStartYear / 100 * 100;
        startYear = centuryStartYear - century;

        init(definingCalendar);
    }

    /**
     * Initialize derived fields from defining fields.
     * This is called from constructor and from readObject (de-serialization)
     *
     * @param definingCalendar the {@link java.util.Calendar} instance used to initialize this FastDateParser
     */
    private void init(Calendar definingCalendar) {

        final StringBuilder regex = new StringBuilder();
        final List<Strategy> collector = new ArrayList<Strategy>();

        final Matcher patternMatcher = formatPattern.matcher(pattern);
        if (!patternMatcher.lookingAt()) {
            throw new IllegalArgumentException(
                    "Illegal pattern character '" + pattern.charAt(patternMatcher.regionStart()) + "'");
        }

        currentFormatField = patternMatcher.group();
        Strategy currentStrategy = getStrategy(currentFormatField, definingCalendar);
        for (; ; ) {
            patternMatcher.region(patternMatcher.end(), patternMatcher.regionEnd());
            if (!patternMatcher.lookingAt()) {
                nextStrategy = null;
                break;
            }
            final String nextFormatField = patternMatcher.group();
            nextStrategy = getStrategy(nextFormatField, definingCalendar);
            if (currentStrategy.addRegex(this, regex)) {
                collector.add(currentStrategy);
            }
            currentFormatField = nextFormatField;
            currentStrategy = nextStrategy;
        }
        if (patternMatcher.regionStart() != patternMatcher.regionEnd()) {
            throw new IllegalArgumentException("Failed to parse \"" + pattern + "\" ; gave up at index " + patternMatcher.regionStart());
        }
        if (currentStrategy.addRegex(this, regex)) {
            collector.add(currentStrategy);
        }
        currentFormatField = null;
        strategies = collector.toArray(new Strategy[collector.size()]);
        parsePattern = Pattern.compile(regex.toString());
    }

    // Accessors
    //-----------------------------------------------------------------------
    /* (non-Javadoc)
     * @see org.apache.commons.lang3.time.DateParser#getPattern()
     */
    @Override
    public String getPattern() {
        return pattern;
    }

    /* (non-Javadoc)
     * @see org.apache.commons.lang3.time.DateParser#getTimeZone()
     */
    @Override
    public TimeZone getTimeZone() {
        return timeZone;
    }

    /* (non-Javadoc)
     * @see org.apache.commons.lang3.time.DateParser#getLocale()
     */
    @Override
    public Locale getLocale() {
        return locale;
    }

    /**
     * Returns the generated pattern (for testing purposes).
     *
     * @return the generated pattern
     */
    Pattern getParsePattern() {
        return parsePattern;
    }

    // Basics
    //-----------------------------------------------------------------------

    /**
     * <p>Compare another object for equality with this object.</p>
     *
     * @param obj the object to compare to
     * @return <code>true</code>if equal to this instance
     */
    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof FastDateParser)) {
            return false;
        }
        final FastDateParser other = (FastDateParser) obj;
        return pattern.equals(other.pattern)
                && timeZone.equals(other.timeZone)
                && locale.equals(other.locale);
    }

    /**
     * <p>Return a hashcode compatible with equals.</p>
     *
     * @return a hashcode compatible with equals
     */
    @Override
    public int hashCode() {
        return pattern.hashCode() + 13 * (timeZone.hashCode() + 13 * locale.hashCode());
    }

    /**
     * <p>Get a string version of this formatter.</p>
     *
     * @return a debugging string
     */
    @Override
    public String toString() {
        return "FastDateParser[" + pattern + "," + locale + "," + timeZone.getID() + "]";
    }

    // Serializing
    //-----------------------------------------------------------------------

    /**
     * Create the object after serialization. This implementation reinitializes the
     * transient properties.
     *
     * @param in ObjectInputStream from which the object is being deserialized.
     * @throws IOException            if there is an IO issue.
     * @throws ClassNotFoundException if a class cannot be found.
     */
    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        final Calendar definingCalendar = Calendar.getInstance(timeZone, locale);
        init(definingCalendar);
    }

    /* (non-Javadoc)
     * @see org.apache.commons.lang3.time.DateParser#parseObject(java.lang.String)
     */
    @Override
    public Object parseObject(final String source) throws ParseException {
        return parse(source);
    }

    /* (non-Javadoc)
     * @see org.apache.commons.lang3.time.DateParser#parse(java.lang.String)
     */
    @Override
    public Date parse(final String source) throws ParseException {
        final Date date = parse(source, new ParsePosition(0));
        if (date == null) {
            // Add a note re supported date range
            if (locale.equals(JAPANESE_IMPERIAL)) {
                throw new ParseException(
                        "(The " + locale + " locale does not support dates before 1868 AD)\n" +
                                "Unparseable date: \"" + source + "\" does not match " + parsePattern.pattern(), 0);
            }
            throw new ParseException("Unparseable date: \"" + source + "\" does not match " + parsePattern.pattern(), 0);
        }
        return date;
    }

    /* (non-Javadoc)
     * @see org.apache.commons.lang3.time.DateParser#parseObject(java.lang.String, java.text.ParsePosition)
     */
    @Override
    public Object parseObject(final String source, final ParsePosition pos) {
        return parse(source, pos);
    }

    /* (non-Javadoc)
     * @see org.apache.commons.lang3.time.DateParser#parse(java.lang.String, java.text.ParsePosition)
     */
    @Override
    public Date parse(final String source, final ParsePosition pos) {
        final int offset = pos.getIndex();
        final Matcher matcher = parsePattern.matcher(source.substring(offset));
        if (!matcher.lookingAt()) {
            return null;
        }
        // timing tests indicate getting new instance is 19% faster than cloning
        final Calendar cal = Calendar.getInstance(timeZone, locale);
        cal.clear();

        for (int i = 0; i < strategies.length; ) {
            final Strategy strategy = strategies[i++];
            strategy.setCalendar(this, cal, matcher.group(i));
        }
        pos.setIndex(offset + matcher.end());
        return cal.getTime();
    }

    // Support for strategies
    //-----------------------------------------------------------------------

    /**
     * Escape constant fields into regular expression
     *
     * @param regex   The destination regex
     * @param value   The source field
     * @param unquote If true, replace two success quotes ('') with single quote (')
     * @return The <code>StringBuilder</code>
     */
    private static StringBuilder escapeRegex(final StringBuilder regex, final String value, final boolean unquote) {
        regex.append("\\Q");
        for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            switch (c) {
                case '\'':
                    if (unquote) {
                        if (++i == value.length()) {
                            return regex;
                        }
                        c = value.charAt(i);
                    }
                    break;
                case '\\':
                    if (++i == value.length()) {
                        break;
                    }
                /*
                 * If we have found \E, we replace it with \E\\E\Q, i.e. we stop the quoting,
                 * quote the \ in \E, then restart the quoting.
                 *
                 * Otherwise we just output the two characters.
                 * In each case the initial \ needs to be output and the final char is done at the end
                 */
                    regex.append(c); // we always want the original \
                    c = value.charAt(i); // Is it followed by E ?
                    if (c == 'E') { // \E detected
                        regex.append("E\\\\E\\"); // see comment above
                        c = 'Q'; // appended below
                    }
                    break;
                default:
                    break;
            }
            regex.append(c);
        }
        regex.append("\\E");
        return regex;
    }

    private static String[] getDisplayNameArray(int field, boolean isLong, Locale locale) {
        DateFormatSymbols dfs = new DateFormatSymbols(locale);
        switch (field) {
            case Calendar.AM_PM:
                return dfs.getAmPmStrings();
            case Calendar.DAY_OF_WEEK:
                return isLong ? dfs.getWeekdays() : dfs.getShortWeekdays();
            case Calendar.ERA:
                return dfs.getEras();
            case Calendar.MONTH:
                return isLong ? dfs.getMonths() : dfs.getShortMonths();
        }
        return null;
    }

    private static void insertValuesInMap(Map<String, Integer> map, String[] values) {
        if (values == null) {
            return;
        }
        for (int i = 0; i < values.length; ++i) {
            if (values[i] != null && values[i].length() > 0) {
                map.put(values[i], i);
            }
        }
    }

    private static Map<String, Integer> getDisplayNames(int field, Locale locale) {
        Map<String, Integer> result = new HashMap<String, Integer>();
        insertValuesInMap(result, getDisplayNameArray(field, false, locale));
        insertValuesInMap(result, getDisplayNameArray(field, true, locale));
        return result.isEmpty() ? null : result;
    }

    /**
     * Get the short and long values displayed for a field
     *
     * @param field            The field of interest
     * @param definingCalendar The calendar to obtain the short and long values
     * @param locale           The locale of display names
     * @return A Map of the field key / value pairs
     */
    private static Map<String, Integer> getDisplayNames(final int field, final Calendar definingCalendar, final Locale locale) {
        return getDisplayNames(field, locale);
    }

    /**
     * Adjust dates to be within appropriate century
     *
     * @param twoDigitYear The year to adjust
     * @return A value between centuryStart(inclusive) to centuryStart+100(exclusive)
     */
    private int adjustYear(final int twoDigitYear) {
        int trial = century + twoDigitYear;
        return twoDigitYear >= startYear ? trial : trial + 100;
    }

    /**
     * Is the next field a number?
     *
     * @return true, if next field will be a number
     */
    boolean isNextNumber() {
        return nextStrategy != null && nextStrategy.isNumber();
    }

    /**
     * What is the width of the current field?
     *
     * @return The number of characters in the current format field
     */
    int getFieldWidth() {
        return currentFormatField.length();
    }

    /**
     * A strategy to parse a single field from the parsing pattern
     */
    private static abstract class Strategy {
        /**
         * Is this field a number?
         * The default implementation returns false.
         *
         * @return true, if field is a number
         */
        boolean isNumber() {
            return false;
        }

        /**
         * Set the Calendar with the parsed field.
         * <p/>
         * The default implementation does nothing.
         *
         * @param parser The parser calling this strategy
         * @param cal    The <code>Calendar</code> to set
         * @param value  The parsed field to translate and set in cal
         */
        void setCalendar(final FastDateParser parser, final Calendar cal, final String value) {

        }

        /**
         * Generate a <code>Pattern</code> regular expression to the <code>StringBuilder</code>
         * which will accept this field
         *
         * @param parser The parser calling this strategy
         * @param regex  The <code>StringBuilder</code> to append to
         * @return true, if this field will set the calendar;
         * false, if this field is a constant value
         */
        abstract boolean addRegex(FastDateParser parser, StringBuilder regex);
    }

    /**
     * A <code>Pattern</code> to parse the user supplied SimpleDateFormat pattern
     */
    private static final Pattern formatPattern = Pattern.compile(
            "D+|E+|F+|G+|H+|K+|M+|S+|W+|Z+|a+|d+|h+|k+|m+|s+|w+|y+|z+|''|'[^']++(''[^']*+)*+'|[^'A-Za-z]++");

    /**
     * Obtain a Strategy given a field from a SimpleDateFormat pattern
     *
     * @param formatField      A sub-sequence of the SimpleDateFormat pattern
     * @param definingCalendar The calendar to obtain the short and long values
     * @return The Strategy that will handle parsing for the field
     */
    private Strategy getStrategy(final String formatField, final Calendar definingCalendar) {
        switch (formatField.charAt(0)) {
            case '\'':
                if (formatField.length() > 2) {
                    return new CopyQuotedStrategy(formatField.substring(1, formatField.length() - 1));
                }
                //$FALL-THROUGH$
            default:
                return new CopyQuotedStrategy(formatField);
            case 'D':
                return DAY_OF_YEAR_STRATEGY;
            case 'E':
                return getLocaleSpecificStrategy(Calendar.DAY_OF_WEEK, definingCalendar);
            case 'F':
                return DAY_OF_WEEK_IN_MONTH_STRATEGY;
            case 'G':
                return getLocaleSpecificStrategy(Calendar.ERA, definingCalendar);
            case 'H':
                return MODULO_HOUR_OF_DAY_STRATEGY;
            case 'K':
                return HOUR_STRATEGY;
            case 'M':
                return formatField.length() >= 3 ? getLocaleSpecificStrategy(Calendar.MONTH, definingCalendar) : NUMBER_MONTH_STRATEGY;
            case 'S':
                return MILLISECOND_STRATEGY;
            case 'W':
                return WEEK_OF_MONTH_STRATEGY;
            case 'a':
                return getLocaleSpecificStrategy(Calendar.AM_PM, definingCalendar);
            case 'd':
                return DAY_OF_MONTH_STRATEGY;
            case 'h':
                return MODULO_HOUR_STRATEGY;
            case 'k':
                return HOUR_OF_DAY_STRATEGY;
            case 'm':
                return MINUTE_STRATEGY;
            case 's':
                return SECOND_STRATEGY;
            case 'w':
                return WEEK_OF_YEAR_STRATEGY;
            case 'y':
                return formatField.length() > 2 ? LITERAL_YEAR_STRATEGY : ABBREVIATED_YEAR_STRATEGY;
            case 'Z':
            case 'z':
                return getLocaleSpecificStrategy(Calendar.ZONE_OFFSET, definingCalendar);
        }
    }

    @SuppressWarnings("unchecked") // OK because we are creating an array with no entries
    private static final ConcurrentMap<Locale, Strategy>[] caches = new ConcurrentMap[Calendar.FIELD_COUNT];

    /**
     * Get a cache of Strategies for a particular field
     *
     * @param field The Calendar field
     * @return a cache of Locale to Strategy
     */
    private static ConcurrentMap<Locale, Strategy> getCache(final int field) {
        synchronized (caches) {
            if (caches[field] == null) {
                caches[field] = new ConcurrentHashMap<Locale, Strategy>(3);
            }
            return caches[field];
        }
    }

    /**
     * Construct a Strategy that parses a Text field
     *
     * @param field            The Calendar field
     * @param definingCalendar The calendar to obtain the short and long values
     * @return a TextStrategy for the field and Locale
     */
    private Strategy getLocaleSpecificStrategy(final int field, final Calendar definingCalendar) {
        final ConcurrentMap<Locale, Strategy> cache = getCache(field);
        Strategy strategy = cache.get(locale);
        if (strategy == null) {
            strategy = field == Calendar.ZONE_OFFSET
                    ? new TimeZoneStrategy(locale)
                    : new TextStrategy(field, definingCalendar, locale);
            final Strategy inCache = cache.putIfAbsent(locale, strategy);
            if (inCache != null) {
                return inCache;
            }
        }
        return strategy;
    }

    /**
     * A strategy that copies the static or quoted field in the parsing pattern
     */
    private static class CopyQuotedStrategy extends Strategy {
        private final String formatField;

        /**
         * Construct a Strategy that ensures the formatField has literal text
         *
         * @param formatField The literal text to match
         */
        CopyQuotedStrategy(final String formatField) {
            this.formatField = formatField;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        boolean isNumber() {
            char c = formatField.charAt(0);
            if (c == '\'') {
                c = formatField.charAt(1);
            }
            return Character.isDigit(c);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        boolean addRegex(final FastDateParser parser, final StringBuilder regex) {
            escapeRegex(regex, formatField, true);
            return false;
        }
    }

    /**
     * A strategy that handles a text field in the parsing pattern
     */
    private static class TextStrategy extends Strategy {
        private final int field;
        private final Map<String, Integer> keyValues;

        /**
         * Construct a Strategy that parses a Text field
         *
         * @param field            The Calendar field
         * @param definingCalendar The Calendar to use
         * @param locale           The Locale to use
         */
        TextStrategy(final int field, final Calendar definingCalendar, final Locale locale) {
            this.field = field;
            this.keyValues = getDisplayNames(field, definingCalendar, locale);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        boolean addRegex(final FastDateParser parser, final StringBuilder regex) {
            regex.append('(');
            for (final String textKeyValue : keyValues.keySet()) {
                escapeRegex(regex, textKeyValue, false).append('|');
            }
            regex.setCharAt(regex.length() - 1, ')');
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void setCalendar(final FastDateParser parser, final Calendar cal, final String value) {
            final Integer iVal = keyValues.get(value);
            if (iVal == null) {
                final StringBuilder sb = new StringBuilder(value);
                sb.append(" not in (");
                for (final String textKeyValue : keyValues.keySet()) {
                    sb.append(textKeyValue).append(' ');
                }
                sb.setCharAt(sb.length() - 1, ')');
                throw new IllegalArgumentException(sb.toString());
            }
            cal.set(field, iVal.intValue());
        }
    }


    /**
     * A strategy that handles a number field in the parsing pattern
     */
    private static class NumberStrategy extends Strategy {
        private final int field;

        /**
         * Construct a Strategy that parses a Number field
         *
         * @param field The Calendar field
         */
        NumberStrategy(final int field) {
            this.field = field;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        boolean isNumber() {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        boolean addRegex(final FastDateParser parser, final StringBuilder regex) {
            // See LANG-954: We use {Nd} rather than {IsNd} because Android does not support the Is prefix
            if (parser.isNextNumber()) {
                regex.append("(\\p{Nd}{").append(parser.getFieldWidth()).append("}+)");
            } else {
                regex.append("(\\p{Nd}++)");
            }
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void setCalendar(final FastDateParser parser, final Calendar cal, final String value) {
            cal.set(field, modify(Integer.parseInt(value)));
        }

        /**
         * Make any modifications to parsed integer
         *
         * @param iValue The parsed integer
         * @return The modified value
         */
        int modify(final int iValue) {
            return iValue;
        }
    }

    private static final Strategy ABBREVIATED_YEAR_STRATEGY = new NumberStrategy(Calendar.YEAR) {
        /**
         * {@inheritDoc}
         */
        @Override
        void setCalendar(final FastDateParser parser, final Calendar cal, final String value) {
            int iValue = Integer.parseInt(value);
            if (iValue < 100) {
                iValue = parser.adjustYear(iValue);
            }
            cal.set(Calendar.YEAR, iValue);
        }
    };

    /**
     * A strategy that handles a timezone field in the parsing pattern
     */
    private static class TimeZoneStrategy extends Strategy {

        private final String validTimeZoneChars;
        private final SortedMap<String, TimeZone> tzNames = new TreeMap<String, TimeZone>(String.CASE_INSENSITIVE_ORDER);

        /**
         * Index of zone id
         */
        private static final int ID = 0;
        /**
         * Index of the long name of zone in standard time
         */
        private static final int LONG_STD = 1;
        /**
         * Index of the short name of zone in standard time
         */
        private static final int SHORT_STD = 2;
        /**
         * Index of the long name of zone in daylight saving time
         */
        private static final int LONG_DST = 3;
        /**
         * Index of the short name of zone in daylight saving time
         */
        private static final int SHORT_DST = 4;

        /**
         * Construct a Strategy that parses a TimeZone
         *
         * @param locale The Locale
         */
        TimeZoneStrategy(final Locale locale) {
            final String[][] zones = DateFormatSymbols.getInstance(locale).getZoneStrings();
            for (String[] zone : zones) {
                if (zone[ID].startsWith("GMT")) {
                    continue;
                }
                final TimeZone tz = TimeZone.getTimeZone(zone[ID]);
                if (!tzNames.containsKey(zone[LONG_STD])) {
                    tzNames.put(zone[LONG_STD], tz);
                }
                if (!tzNames.containsKey(zone[SHORT_STD])) {
                    tzNames.put(zone[SHORT_STD], tz);
                }
                if (tz.useDaylightTime()) {
                    if (!tzNames.containsKey(zone[LONG_DST])) {
                        tzNames.put(zone[LONG_DST], tz);
                    }
                    if (!tzNames.containsKey(zone[SHORT_DST])) {
                        tzNames.put(zone[SHORT_DST], tz);
                    }
                }
            }

            final StringBuilder sb = new StringBuilder();
            sb.append("(GMT[+\\-]\\d{0,1}\\d{2}|[+\\-]\\d{2}:?\\d{2}|");
            for (final String id : tzNames.keySet()) {
                escapeRegex(sb, id, false).append('|');
            }
            sb.setCharAt(sb.length() - 1, ')');
            validTimeZoneChars = sb.toString();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        boolean addRegex(final FastDateParser parser, final StringBuilder regex) {
            regex.append(validTimeZoneChars);
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void setCalendar(final FastDateParser parser, final Calendar cal, final String value) {
            TimeZone tz;
            if (value.charAt(0) == '+' || value.charAt(0) == '-') {
                tz = TimeZone.getTimeZone("GMT" + value);
            } else if (value.startsWith("GMT")) {
                tz = TimeZone.getTimeZone(value);
            } else {
                tz = tzNames.get(value);
                if (tz == null) {
                    throw new IllegalArgumentException(value + " is not a supported timezone name");
                }
            }
            cal.setTimeZone(tz);
        }
    }

    private static final Strategy NUMBER_MONTH_STRATEGY = new NumberStrategy(Calendar.MONTH) {
        @Override
        int modify(final int iValue) {
            return iValue - 1;
        }
    };
    private static final Strategy LITERAL_YEAR_STRATEGY = new NumberStrategy(Calendar.YEAR);
    private static final Strategy WEEK_OF_YEAR_STRATEGY = new NumberStrategy(Calendar.WEEK_OF_YEAR);
    private static final Strategy WEEK_OF_MONTH_STRATEGY = new NumberStrategy(Calendar.WEEK_OF_MONTH);
    private static final Strategy DAY_OF_YEAR_STRATEGY = new NumberStrategy(Calendar.DAY_OF_YEAR);
    private static final Strategy DAY_OF_MONTH_STRATEGY = new NumberStrategy(Calendar.DAY_OF_MONTH);
    private static final Strategy DAY_OF_WEEK_IN_MONTH_STRATEGY = new NumberStrategy(Calendar.DAY_OF_WEEK_IN_MONTH);
    private static final Strategy HOUR_OF_DAY_STRATEGY = new NumberStrategy(Calendar.HOUR_OF_DAY);
    private static final Strategy MODULO_HOUR_OF_DAY_STRATEGY = new NumberStrategy(Calendar.HOUR_OF_DAY) {
        @Override
        int modify(final int iValue) {
            return iValue % 24;
        }
    };
    private static final Strategy MODULO_HOUR_STRATEGY = new NumberStrategy(Calendar.HOUR) {
        @Override
        int modify(final int iValue) {
            return iValue % 12;
        }
    };
    private static final Strategy HOUR_STRATEGY = new NumberStrategy(Calendar.HOUR);
    private static final Strategy MINUTE_STRATEGY = new NumberStrategy(Calendar.MINUTE);
    private static final Strategy SECOND_STRATEGY = new NumberStrategy(Calendar.SECOND);
    private static final Strategy MILLISECOND_STRATEGY = new NumberStrategy(Calendar.MILLISECOND);
}
