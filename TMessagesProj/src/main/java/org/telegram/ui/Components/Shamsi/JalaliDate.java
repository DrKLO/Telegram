package org.telegram.ui.Components.Shamsi;

/**
 * A simple immutable data class to represent a Jalali date.
 */
public class JalaliDate {
    private final int year;
    private final int month;
    private final int day;

    public JalaliDate(int year, int month, int day) {
        this.year = year;
        this.month = month;
        this.day = day;
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public int getDay() {
        return day;
    }
}
