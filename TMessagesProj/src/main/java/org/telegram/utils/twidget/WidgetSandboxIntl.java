package org.telegram.utils.twidget;

import android.icu.text.Collator;
import android.icu.text.DateFormat;
import android.icu.text.ListFormatter;
import android.icu.text.NumberFormat;
import android.icu.text.PluralRules;
import android.icu.text.RelativeDateTimeFormatter;
import android.icu.util.Currency;
import android.icu.util.TimeZone;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Java port of packages/android/.../IntlBridge.kt; the same Android ICU behavior. android.icu is API 24
 * (ListFormatter 26): before, the answer is "" and the native bridge formats with the engine's BasicIntl.
 */
final class WidgetSandboxIntl {
    static String format(String json) {
        if (Build.VERSION.SDK_INT < 24) return "";
        try {
            JSONObject request = new JSONObject(json);
            JSONObject options = request.optJSONObject("opts");
            if (options == null) options = new JSONObject();
            Object language = request.opt("locale");
            String tag =
                    language instanceof String
                            ? (String) language
                            : language instanceof JSONArray
                                    ? ((JSONArray) language).optString(0, "")
                                    : "";
            Locale locale = tag.isEmpty() ? Locale.getDefault() : Locale.forLanguageTag(tag);
            double value = request.optDouble("value", 0);
            switch (request.optString("kind")) {
                case "number":
                    NumberFormat number;
                    switch (options.optString("style")) {
                        case "currency":
                            number = NumberFormat.getCurrencyInstance(locale);
                            String code = options.optString("currency");
                            if (!code.isEmpty()) {
                                try {
                                    number.setCurrency(Currency.getInstance(code));
                                } catch (Exception ignored) {
                                }
                            }
                            break;
                        case "percent":
                            number = NumberFormat.getPercentInstance(locale);
                            break;
                        default:
                            number = NumberFormat.getNumberInstance(locale);
                    }
                    if (options.has("minimumFractionDigits"))
                        number.setMinimumFractionDigits(options.optInt("minimumFractionDigits"));
                    if (options.has("maximumFractionDigits"))
                        number.setMaximumFractionDigits(options.optInt("maximumFractionDigits"));
                    if (options.has("useGrouping"))
                        number.setGroupingUsed(options.optBoolean("useGrouping"));
                    return number.format(value);
                case "date":
                case "dateMethod":
                    return dateFormat(options, request.optString("method"), locale)
                            .format(new Date((long) value));
                case "plural":
                    return PluralRules.forLocale(locale).select(value);
                case "relative":
                    String unit = request.optString("unit", "second");
                    if (unit.endsWith("s")) unit = unit.substring(0, unit.length() - 1);
                    RelativeDateTimeFormatter.RelativeUnit relative;
                    switch (unit) {
                        case "minute":
                            relative = RelativeDateTimeFormatter.RelativeUnit.MINUTES;
                            break;
                        case "hour":
                            relative = RelativeDateTimeFormatter.RelativeUnit.HOURS;
                            break;
                        case "day":
                            relative = RelativeDateTimeFormatter.RelativeUnit.DAYS;
                            break;
                        case "week":
                            relative = RelativeDateTimeFormatter.RelativeUnit.WEEKS;
                            break;
                        case "month":
                            relative = RelativeDateTimeFormatter.RelativeUnit.MONTHS;
                            break;
                        case "year":
                            relative = RelativeDateTimeFormatter.RelativeUnit.YEARS;
                            break;
                        default:
                            relative = RelativeDateTimeFormatter.RelativeUnit.SECONDS;
                    }
                    RelativeDateTimeFormatter formatter = RelativeDateTimeFormatter.getInstance(locale);
                    if (options.optString("numeric").equals("auto")) {
                        String named = namedRelative(formatter, value, unit);
                        if (named != null) return named;
                    }
                    return formatter
                            .format(
                                    Math.abs(value),
                                    value < 0
                                            ? RelativeDateTimeFormatter.Direction.LAST
                                            : RelativeDateTimeFormatter.Direction.NEXT,
                                    relative);
                case "list":
                    if (Build.VERSION.SDK_INT < 26) return "";
                    JSONArray array = request.optJSONArray("value");
                    ArrayList<String> items = new ArrayList<>();
                    if (array != null)
                        for (int i = 0; i < array.length(); i++)
                            items.add(String.valueOf(array.opt(i)));
                    return ListFormatter.getInstance(locale).format(items);
                case "compare":
                    return Integer.toString(
                            Integer.signum(
                                    Collator.getInstance(locale)
                                            .compare(
                                                    request.optString("a"),
                                                    request.optString("b"))));
                case "upper":
                    return request.optString("value").toUpperCase(locale);
                case "lower":
                    return request.optString("value").toLowerCase(locale);
                default:
                    return "";
            }
        } catch (Exception error) {
            return "";
        }
    }

    /**
     * numeric: "auto": the named day, week, month, year (yesterday, next week, this year…) and "now" for
     * 0 seconds, as Intl does; null where ICU has no name and the number is used.
     */
    private static String namedRelative(RelativeDateTimeFormatter formatter, double value, String unit) {
        if (value == 0 && unit.equals("second"))
            return formatter.format(
                    RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW);
        RelativeDateTimeFormatter.AbsoluteUnit absolute;
        switch (unit) {
            case "day":
                absolute = RelativeDateTimeFormatter.AbsoluteUnit.DAY;
                break;
            case "week":
                absolute = RelativeDateTimeFormatter.AbsoluteUnit.WEEK;
                break;
            case "month":
                absolute = RelativeDateTimeFormatter.AbsoluteUnit.MONTH;
                break;
            case "year":
                absolute = RelativeDateTimeFormatter.AbsoluteUnit.YEAR;
                break;
            default:
                return null;
        }
        RelativeDateTimeFormatter.Direction direction;
        if (value == -1) direction = RelativeDateTimeFormatter.Direction.LAST;
        else if (value == 0) direction = RelativeDateTimeFormatter.Direction.THIS;
        else if (value == 1) direction = RelativeDateTimeFormatter.Direction.NEXT;
        else return null;
        return formatter.format(direction, absolute);
    }

    /**
     * Intl.DateTimeFormat and Date's toLocale*String: the styles, or a skeleton of the components asked for.
     * Without either, the ECMA-402 defaults (ToDateTimeOptions): a numeric date for format() and
     * toLocaleDateString, a numeric time for toLocaleTimeString, both for toLocaleString. timeZone applies,
     * else the device's.
     */
    private static DateFormat dateFormat(JSONObject options, String method, Locale locale) {
        DateFormat format = null;
        int ds = style(options.optString("dateStyle")), ts = style(options.optString("timeStyle"));
        if (ds >= 0 && ts >= 0) format = DateFormat.getDateTimeInstance(ds, ts, locale);
        else if (ds >= 0) format = DateFormat.getDateInstance(ds, locale);
        else if (ts >= 0) format = DateFormat.getTimeInstance(ts, locale);
        if (format == null) {
            StringBuilder skeleton = new StringBuilder();
            String era = options.optString("era");
            if (!era.isEmpty()) skeleton.append(era.equals("long") ? "GGGG" : era.equals("narrow") ? "GGGGG" : "G");
            String year = options.optString("year");
            if (!year.isEmpty()) skeleton.append(year.equals("2-digit") ? "yy" : "y");
            String month = options.optString("month");
            if (!month.isEmpty())
                skeleton.append(
                        month.equals("long")
                                ? "MMMM"
                                : month.equals("short")
                                        ? "MMM"
                                        : month.equals("narrow") ? "MMMMM" : month.equals("2-digit") ? "MM" : "M");
            String weekday = options.optString("weekday");
            if (!weekday.isEmpty())
                skeleton.append(weekday.equals("long") ? "EEEE" : weekday.equals("narrow") ? "EEEEE" : "EEE");
            String day = options.optString("day");
            if (!day.isEmpty()) skeleton.append(day.equals("2-digit") ? "dd" : "d");
            String hour = options.optString("hour");
            if (!hour.isEmpty()) skeleton.append(hourField(options, hour.equals("2-digit")));
            if (options.has("minute")) skeleton.append("mm");
            if (options.has("second")) skeleton.append("ss");
            if (skeleton.length() == 0) {
                boolean date = !method.equals("toLocaleTimeString");
                boolean time = method.equals("toLocaleTimeString") || method.equals("toLocaleString");
                if (date) skeleton.append("yMd");
                if (time) skeleton.append(hourField(options, false)).append("mmss");
            }
            String zoneName = options.optString("timeZoneName");
            if (!zoneName.isEmpty()) skeleton.append(zoneName.equals("long") ? "zzzz" : "z");
            format = DateFormat.getInstanceForSkeleton(skeleton.toString(), locale);
        }
        String zone = options.optString("timeZone");
        if (!zone.isEmpty()) format.setTimeZone(TimeZone.getTimeZone(zone));
        return format;
    }

    /** The hour field: the locale's cycle, or 12/24 hours as hour12 or hourCycle ask. */
    private static String hourField(JSONObject options, boolean twoDigit) {
        String field = "j";
        if (options.has("hour12")) field = options.optBoolean("hour12") ? "h" : "H";
        else {
            String cycle = options.optString("hourCycle");
            if (cycle.equals("h11") || cycle.equals("h12")) field = "h";
            else if (cycle.equals("h23") || cycle.equals("h24")) field = "H";
        }
        return twoDigit ? field + field : field;
    }

    private static int style(String value) {
        switch (value) {
            case "full":
                return DateFormat.FULL;
            case "long":
                return DateFormat.LONG;
            case "medium":
                return DateFormat.MEDIUM;
            case "short":
                return DateFormat.SHORT;
            default:
                return -1;
        }
    }

    private WidgetSandboxIntl() {}
}
