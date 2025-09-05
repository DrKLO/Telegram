package org.telegram.ui.Components.Shamsi;

import java.util.Arrays;

/**
 * A stateless utility class for converting between Gregorian and Jalali calendars.
 * This class contains the core calendar logic.
 */
public final class JalaliCalendarUtil {

    private static final int[][] grgSumOfDays = {
            {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 365},
            {0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335, 366}};
    private static final int[][] hshSumOfDays = {
            {0, 31, 62, 93, 124, 155, 186, 216, 246, 276, 306, 336, 365},
            {0, 31, 62, 93, 124, 155, 186, 216, 246, 276, 306, 336, 366}};

    // Private constructor to prevent instantiation
    private JalaliCalendarUtil() {}

    /**
     * Converts a Gregorian date to a Jalali date.
     *
     * @param gregorianYear  the Gregorian year
     * @param gregorianMonth the Gregorian month (1-12)
     * @param gregorianDay   the Gregorian day (1-31)
     * @return a {@link JalaliDate} object representing the equivalent Jalali date.
     */
    public static JalaliDate toJalali(int gregorianYear, int gregorianMonth, int gregorianDay) {
        int hshDay = 1;
        int hshMonth = 1;
        int hshElapsed;
        int hshYear = gregorianYear - 621;
        boolean grgLeap = isGregorianLeap(gregorianYear);
        boolean hshLeap = isJalaliLeap(hshYear - 1);
        int grgElapsed = grgSumOfDays[(grgLeap ? 1 : 0)][gregorianMonth - 1] + gregorianDay;
        int XmasToNorooz = (hshLeap && grgLeap) ? 80 : 79;

        if (grgElapsed <= XmasToNorooz) {
            hshElapsed = grgElapsed + 286;
            hshYear--;
            if (hshLeap && !grgLeap) {
                hshElapsed++;
            }
        } else {
            hshElapsed = grgElapsed - XmasToNorooz;
            hshLeap = isJalaliLeap(hshYear);
        }

        if (gregorianYear >= 2029 && (gregorianYear - 2029) % 4 == 0) {
            hshElapsed++;
        }

        for (int i = 1; i <= 12; i++) {
            if (hshSumOfDays[(hshLeap ? 1 : 0)][i] >= hshElapsed) {
                hshMonth = i;
                hshDay = hshElapsed - hshSumOfDays[(hshLeap ? 1 : 0)][i - 1];
                break;
            }
        }
        return new JalaliDate(hshYear, hshMonth, hshDay);
    }

    /**
     * Converts a Jalali date to a Gregorian date.
     *
     * @param jalaliYear  the Jalali year
     * @param jalaliMonth the Jalali month (1-12)
     * @param jalaliDay   the Jalali day (1-31)
     * @return a {@link GregorianDate} object representing the equivalent Gregorian date.
     */
    public static GregorianDate toGregorian(int jalaliYear, int jalaliMonth, int jalaliDay) {
        int grgYear = jalaliYear + 621;
        int grgDay = 0;
        int grgMonth = 0;
        int grgElapsed;

        boolean hshLeap = isJalaliLeap(jalaliYear);
        boolean grgLeap = isGregorianLeap(grgYear);

        int hshElapsed = hshSumOfDays[hshLeap ? 1 : 0][jalaliMonth - 1] + jalaliDay;

        if (jalaliMonth > 10 || (jalaliMonth == 10 && hshElapsed > 286 + (grgLeap ? 1 : 0))) {
            grgElapsed = hshElapsed - (286 + (grgLeap ? 1 : 0));
            grgLeap = isGregorianLeap(++grgYear);
        } else {
            boolean prevHshLeap = isJalaliLeap(jalaliYear - 1);
            grgElapsed = hshElapsed + 79 + (prevHshLeap ? 1 : 0) - (isGregorianLeap(grgYear - 1) ? 1 : 0);
        }

        if (grgYear >= 2030 && (grgYear - 2030) % 4 == 0) {
            grgElapsed--;
        }
        if (grgYear == 1989) {
            grgElapsed++;
        }

        for (int i = 1; i <= 12; i++) {
            if (grgSumOfDays[grgLeap ? 1 : 0][i] >= grgElapsed) {
                grgMonth = i;
                grgDay = grgElapsed - grgSumOfDays[grgLeap ? 1 : 0][i - 1];
                break;
            }
        }
        return new GregorianDate(grgYear, grgMonth, grgDay);
    }

    /**
     * Checks if a Jalali year is a leap year.
     *
     * @param jalaliYear the Jalali year
     * @return true if the year is a leap year, false otherwise.
     */
    public static boolean isJalaliLeap(int jalaliYear) {
        // This is the algorithm from the original PersianDate class.
        double referenceYear = 1375;
        double startYear = 1375;
        double yearRes = jalaliYear - referenceYear;
        if (yearRes > 0) {
            if (yearRes >= 33) {
                double numb = yearRes / 33;
                startYear = referenceYear + Math.floor(numb) * 33;
            }
        } else {
            if (yearRes >= -33) {
                startYear = referenceYear - 33;
            } else {
                double numb = Math.abs(yearRes / 33);
                startYear = referenceYear - (Math.floor(numb) + 1) * 33;
            }
        }
        double[] leapYears = {startYear, startYear + 4, startYear + 8, startYear + 16, startYear + 20,
                startYear + 24, startYear + 28, startYear + 33};
        return (Arrays.binarySearch(leapYears, jalaliYear)) >= 0;
    }

    /**
     * Checks if a Gregorian year is a leap year.
     *
     * @param gregorianYear the Gregorian year
     * @return true if the year is a leap year, false otherwise.
     */
    private static boolean isGregorianLeap(int gregorianYear) {
        return ((gregorianYear % 4) == 0 && ((gregorianYear % 100) != 0 || (gregorianYear % 400) == 0));
    }
}
