package org.telegram.ui.Components.Shamsi;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import org.telegram.messenger.SharedConfig;


public class PersianDate {

  private String[] dayNames = {"شنبه", "یک‌شنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه",
      "جمعه"};
  private String[] monthNames =
      {"فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی",
          "بهمن", "اسفند"};

  private int shYear;
  private int shMonth;
  private int shDay;
  private int grgYear;
  private int grgMonth;
  private int grgDay;
  private int hour;
  private int minute;
  private int second;
  private int dayOfWeek;

  private long timeInMilliSecond;

  public PersianDate() {
    this.initGrgDate(new Date().getYear() + 1900, new Date().getMonth() + 1, new Date().getDate(),
        new Date().getHours(), new Date().getMinutes(), new Date().getSeconds());
  }

  public PersianDate(Long timeInMilliSecond) {
    this.timeInMilliSecond = timeInMilliSecond;
    this.initGrgDate(new Date(timeInMilliSecond));
  }

  public PersianDate(Date date) {
    this.initGrgDate(date);
  }

  public PersianDate(int year, int month, int day, int hour, int minute, int second) {
    this.initJalaliDate(year, month, day, hour, minute, second);
  }

  public int getShYear() {
    return shYear;
  }

  public PersianDate setShYear(int shYear) {
    this.shYear = shYear;
    return this;
  }

  public int getShMonth() {
    return shMonth;
  }

  public PersianDate setShMonth(int shMonth) {
    this.shMonth = shMonth;
    return this;
  }

  public int getShDay() {
    return shDay;
  }

  public PersianDate setShDay(int shDay) {
    this.shDay = shDay;
    return this;
  }

  public int getGrgYear() {
    return grgYear;
  }

  public PersianDate setGrgYear(int grgYear) {
    this.grgYear = grgYear;
    return this;
  }

  public int getGrgMonth() {
    return grgMonth;
  }

  public PersianDate setGrgMonth(int grgMonth) {
    this.grgMonth = grgMonth;
    return this;
  }

  public int getGrgDay() {
    return grgDay;
  }

  public PersianDate setGrgDay(int grgDay) {
    this.grgDay = grgDay;
    return this;
  }

  public int getHour() {
    return hour;
  }

  public PersianDate setHour(int hour) {
    this.hour = hour;
    return this;
  }

  public int getMinute() {
    return minute;
  }

  public PersianDate setMinute(int minute) {
    this.minute = minute;
    return this;
  }

  public int getSecond() {
    return second;
  }

  public PersianDate setSecond(int second) {
    this.second = second;
    return this;
  }

  public PersianDate initGrgDate(Date date) {
    return this.initGrgDate(date.getYear() + 1900, date.getMonth() + 1, date.getDate(),
        date.getHours(), date.getMinutes(), date.getSeconds());
  }

  public PersianDate initGrgDate(int year, int month, int day, int hour, int minute, int second) {
    this.setGrgYear(year)
        .setGrgMonth(month)
        .setGrgDay(day)
        .setHour(hour)
        .setMinute(minute)
        .setSecond(second);
    JalaliDate jalaliDate = JalaliCalendarUtil.toJalali(year, month, day);
    this.shYear = jalaliDate.getYear();
    this.shMonth = jalaliDate.getMonth();
    this.shDay = jalaliDate.getDay();
    this.setShYear(jalaliDate.getYear())
        .setShMonth(jalaliDate.getMonth())
        .setShDay(jalaliDate.getDay());
    return this;
  }

  public PersianDate initJalaliDate(int year, int month, int day, int hour, int minute, int second) {
    this.setShYear(year)
        .setShMonth(month)
        .setShDay(day)
        .setHour(hour)
        .setMinute(minute)
        .setSecond(second);
    GregorianDate gregorianDate = JalaliCalendarUtil.toGregorian(year, month, day);
    this.setGrgYear(gregorianDate.getYear())
        .setGrgMonth(gregorianDate.getMonth())
        .setGrgDay(gregorianDate.getDay());
    return this;
  }

  public String format(String format) {
    return format.replace("yyyy", "" + this.getShYear())
        .replace("MM", this.textNumberFilter("" + this.getShMonth()))
        .replace("dd", this.textNumberFilter("" + this.getShDay()))
        .replace("M", "" + this.getMonthName())
        .replace("ww", "" + this.dayName())
        .replace("w", "" + this.dayShortName())
        .replace("HH", this.textNumberFilter("" + this.getHour()))
        .replace("H", "" + this.getHour())
        .replace("mm", this.textNumberFilter("" + this.getMinute()))
        .replace("m", "" + this.getMinute())
        .replace("ss", "" + this.getSecond())
        .replace("s", "" + this.getSecond())
        .replace("?", this.textNumberFilter(""));
  }

  public Long getTime() {
    return this.timeInMilliSecond;
  }

  public boolean isLeap() {
    return JalaliCalendarUtil.isJalaliLeap(this.shYear);
  }

  public boolean isLeap(int year) {
    return JalaliCalendarUtil.isJalaliLeap(year);
  }

  public static boolean isJalaliLeap(int year) {
    return JalaliCalendarUtil.isJalaliLeap(year);
  }

  public String dayName() {
    this.prepareDate();
    return this.dayNames[this.dayOfWeek];
  }

  public String dayShortName() {
    this.prepareDate();
    return this.dayNames[this.dayOfWeek].substring(0, 1);
  }

  public String monthName() {
    return this.monthNames[this.getShMonth() - 1];
  }

  private PersianDate prepareDate() {
    String dtStart = this.getGrgYear() + "-" + this.getGrgMonth() + "-" + this.getGrgDay() + "T"
        + this.textNumberFilter("" + this.getHour()) + ":" + this
        .textNumberFilter("" + this.getMinute()) + ":" + this
        .textNumberFilter("" + this.getSecond()) + "Z";
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    JalaliDate jalaliDate = JalaliCalendarUtil.toJalali(this.getGrgYear(), this.getGrgMonth(), this.getGrgDay());
    this.shYear = jalaliDate.getYear();
    this.shMonth = jalaliDate.getMonth();
    this.shDay = jalaliDate.getDay();
    Date date = null;
    try {
      date = format.parse(dtStart);
    } catch (ParseException e) {
      e.printStackTrace();
    }
    Calendar c = Calendar.getInstance();
    c.setTime(date);
    this.dayOfWeek = c.get(Calendar.DAY_OF_WEEK) - 1;
    return this;
  }

  private String textNumberFilter(String date) {
      return date.length() < 2 ? "0" + date : date;
  }
}
