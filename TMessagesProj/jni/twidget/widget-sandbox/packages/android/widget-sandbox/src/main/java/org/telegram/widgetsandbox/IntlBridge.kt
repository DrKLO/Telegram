// Intl delegation (pure formatting, no I/O): the sandbox shim forwards
// Intl.* / toLocale*String calls as `__host('intl', json)`; the request shape
// is `{kind, locale, opts, value, method, unit, a, b}` (see src/sandbox).
// Same kinds and fallbacks as the iOS IntlBridge; `numberParts` / `dateParts`
// (formatToParts, in the widget profile) come from ICU's field iterators.

package org.telegram.widgetsandbox

import android.icu.text.Collator
import android.icu.text.DateFormat
import android.icu.text.DecimalFormat
import android.icu.text.ListFormatter
import android.icu.text.NumberFormat
import android.icu.text.PluralFormat
import android.icu.text.PluralRules
import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.RuleBasedCollator
import android.icu.util.Currency
import android.icu.util.TimeZone
import android.icu.util.ULocale
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.AttributedCharacterIterator
import java.text.CharacterIterator
import java.util.Date
import java.util.Locale

object IntlBridge {
    fun format(requestJson: String): String {
        val req = try { JSONObject(requestJson) } catch (e: Exception) { return "" }
        val kind = req.optString("kind")
        val locale = localeOf(req.opt("locale"))
        val opts = req.optJSONObject("opts") ?: JSONObject()
        val value = req.optDouble("value", 0.0)
        return try {
            when (kind) {
                "number" -> numberFormat(locale, opts, value).format(value)
                "numberParts" -> parts(numberFormat(locale, opts, value).formatToCharacterIterator(value), ::numberPart).toString()
                "numberOptions" -> {
                    val f = numberFormat(locale, opts)
                    val out = JSONObject().put("locale", locale.toLanguageTag()).put("numberingSystem", "latn").put("style", opts.optString("style").ifEmpty { "decimal" })
                        .put("minimumIntegerDigits", f.minimumIntegerDigits).put("minimumFractionDigits", f.minimumFractionDigits)
                        .put("maximumFractionDigits", f.maximumFractionDigits).put("useGrouping", if (f.isGroupingUsed) "auto" else false)
                        .put("signDisplay", opts.optString("signDisplay").ifEmpty { "auto" })
                    opts.optString("currency").takeIf { it.isNotEmpty() }?.let { out.put("currency", it.uppercase(Locale.ROOT)) }
                    out.toString()
                }
                "date", "dateMethod" -> dateFormat(req, locale, opts).format(Date(value.toLong()))
                "dateParts" -> parts(dateFormat(req, locale, opts).formatToCharacterIterator(Date(value.toLong())), ::datePart).toString()
                "plural" -> plural(locale, opts, value)
                "dateOptions" -> {
                    val f = dateFormat(req, locale, opts)
                    val out = JSONObject()
                    for (key in opts.keys()) opts.opt(key).takeIf { it is String || it is Number || it is Boolean }?.let { out.put(key, it) }
                    out.put("locale", locale.toLanguageTag()).put("calendar", "gregory").put("numberingSystem", "latn").put("timeZone", f.timeZone.id).toString()
                }
                "relative" -> relative(locale, opts, value, req.optString("unit", "second"), Build.VERSION.SDK_INT >= 28)
                "list" -> {
                    val arr = req.optJSONArray("value") ?: JSONArray()
                    val items = (0 until arr.length()).map { arr.opt(it).toString() }
                    if (Build.VERSION.SDK_INT >= 33) {
                        val type = when (opts.optString("type")) { "disjunction" -> ListFormatter.Type.OR; "unit" -> ListFormatter.Type.UNITS; else -> ListFormatter.Type.AND }
                        val width = when (opts.optString("style")) { "short" -> ListFormatter.Width.SHORT; "narrow" -> ListFormatter.Width.NARROW; else -> ListFormatter.Width.WIDE }
                        ListFormatter.getInstance(locale, type, width).format(items)
                    } else {
                        // API 26–32 have only the wide conjunction.
                        ListFormatter.getInstance(locale).format(items)
                    }
                }
                "compare" -> Integer.signum(collator(locale, opts).compare(req.optString("a"), req.optString("b"))).toString()
                "upper" -> req.optString("value").uppercase(locale)
                "lower" -> req.optString("value").lowercase(locale)
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun numberFormat(locale: Locale, opts: JSONObject, value: Double? = null): NumberFormat {
        val f = when (opts.optString("style")) {
            "currency" -> {
                val kind = when (opts.optString("currencyDisplay")) { "code" -> NumberFormat.ISOCURRENCYSTYLE; "name" -> NumberFormat.PLURALCURRENCYSTYLE; else -> NumberFormat.CURRENCYSTYLE }
                NumberFormat.getInstance(locale, kind).also { c ->
                    opts.optString("currency").takeIf { it.isNotEmpty() }?.let { code -> runCatching { c.currency = Currency.getInstance(code.uppercase(Locale.ROOT)) } }
                }
            }
            "percent" -> NumberFormat.getPercentInstance(locale)
            else -> NumberFormat.getNumberInstance(locale)
        }
        // ECMA-402 rounds half away from zero (halfExpand); ICU's default is half-even.
        f.roundingMode = BigDecimal.ROUND_HALF_UP
        if (opts.has("minimumIntegerDigits")) f.minimumIntegerDigits = opts.optInt("minimumIntegerDigits")
        if (opts.has("minimumFractionDigits")) f.minimumFractionDigits = opts.optInt("minimumFractionDigits")
        if (opts.has("maximumFractionDigits")) f.maximumFractionDigits = opts.optInt("maximumFractionDigits")
        if (opts.has("useGrouping")) f.isGroupingUsed = opts.optBoolean("useGrouping")
        if (f is DecimalFormat) minimumGrouping(f, locale)
        if (f is DecimalFormat && (opts.has("minimumSignificantDigits") || opts.has("maximumSignificantDigits"))) {
            f.setSignificantDigitsUsed(true)
            f.minimumSignificantDigits = opts.optInt("minimumSignificantDigits", 1)
            f.maximumSignificantDigits = opts.optInt("maximumSignificantDigits", 21)
        }
        val sign = opts.optString("signDisplay")
        if (value != null && f is DecimalFormat && sign.isNotEmpty() && sign != "auto") signDisplay(f, sign, value)
        return f
    }

    /**
     * CLDR's minimum grouping digits (es, it, pl, pt-PT… leave four-digit integers ungrouped, as V8 does): the
     * locale's own from API 33, read off NumberFormatter on 31–32; DecimalFormat cannot take it before API 31.
     */
    private fun minimumGrouping(f: DecimalFormat, locale: Locale) {
        if (Build.VERSION.SDK_INT >= 33) {
            f.minimumGroupingDigits = DecimalFormat.MINIMUM_GROUPING_DIGITS_AUTO
        } else if (Build.VERSION.SDK_INT >= 31) {
            val thousand = android.icu.number.NumberFormatter.withLocale(locale).format(1000).toString()
            f.minimumGroupingDigits = if (thousand.all { Character.isDigit(it) }) 2 else 1
        }
    }

    /**
     * ECMA-402's signDisplay: "always" signs everything, "exceptZero" what does not round to zero, "negative" puts a
     * minus only on negatives that do not round to zero, "never" shows none ("auto" is the default).
     */
    private fun signDisplay(f: DecimalFormat, sign: String, value: Double) {
        val zero = f.format(Math.abs(value)).none { Character.isDigit(it) && Character.getNumericValue(it) != 0 }
        val negative = value < 0 || (value == 0.0 && 1.0 / value < 0)
        val plus = (sign == "always" && !negative) || (sign == "exceptZero" && !negative && !zero)
        val noMinus = sign == "never" || ((sign == "exceptZero" || sign == "negative") && zero)
        if (plus) f.positivePrefix = f.decimalFormatSymbols.plusSign + f.positivePrefix
        if (noMinus) {
            f.negativePrefix = f.positivePrefix
            f.negativeSuffix = f.positiveSuffix
        }
    }

    private const val PLURAL_KEYWORDS = "zero{zero} one{one} two{two} few{few} many{many} other{other}"

    /**
     * `Intl.PluralRules.select` on the number as NumberFormat would show it (0–3 fraction digits by default):
     * PluralFormat reads CLDR's v, f and t operands from its number format, PluralRules.select(double) does not
     * (`{minimumFractionDigits: 1}.select(1)` is "other", not "one").
     */
    private fun plural(locale: Locale, opts: JSONObject, value: Double): String {
        if (!value.isFinite()) return "other"
        val type = if (opts.optString("type") == "ordinal") PluralRules.PluralType.ORDINAL else PluralRules.PluralType.CARDINAL
        val digits = NumberFormat.getNumberInstance(Locale.ROOT) as DecimalFormat
        digits.isGroupingUsed = false
        digits.roundingMode = BigDecimal.ROUND_HALF_UP
        val minFraction = opts.optInt("minimumFractionDigits", 0).coerceIn(0, 20)
        digits.minimumFractionDigits = minFraction
        digits.maximumFractionDigits = opts.optInt("maximumFractionDigits", maxOf(minFraction, 3)).coerceIn(minFraction, 20)
        if (opts.has("minimumSignificantDigits") || opts.has("maximumSignificantDigits")) {
            digits.setSignificantDigitsUsed(true)
            digits.minimumSignificantDigits = opts.optInt("minimumSignificantDigits", 1)
            digits.maximumSignificantDigits = opts.optInt("maximumSignificantDigits", 21)
        }
        val f = PluralFormat(ULocale.forLocale(locale), type, PLURAL_KEYWORDS)
        f.setNumberFormat(digits)
        return f.format(value)
    }

    /** `Intl.Collator`: sensitivity (base, accent, case, variant) and numeric ordering. */
    private fun collator(locale: Locale, opts: JSONObject): Collator {
        val c = Collator.getInstance(locale)
        when (opts.optString("sensitivity")) {
            "base" -> c.strength = Collator.PRIMARY
            "accent" -> c.strength = Collator.SECONDARY
            "case" -> { c.strength = Collator.PRIMARY; (c as? RuleBasedCollator)?.isCaseLevel = true }
        }
        if (opts.optBoolean("numeric")) (c as? RuleBasedCollator)?.numericCollation = true
        return c
    }

    /**
     * `Intl.RelativeTimeFormat`. `numeric: "auto"` words the offsets CLDR has a phrase for ("yesterday", "next
     * week", "now"); API 28 formats them for every unit, the [modern] = false path (API 26–27) for days, weeks,
     * months and years (with "the day before yesterday" where the language has it) and "now", and counts quarters
     * in months.
     */
    internal fun relative(locale: Locale, opts: JSONObject, value: Double, rawUnit: String, modern: Boolean): String {
        val style = when (opts.optString("style")) {
            "short" -> RelativeDateTimeFormatter.Style.SHORT
            "narrow" -> RelativeDateTimeFormatter.Style.NARROW
            else -> RelativeDateTimeFormatter.Style.LONG
        }
        val f = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale), null, style, android.icu.text.DisplayContext.CAPITALIZATION_NONE)
        val unit = rawUnit.removeSuffix("s")
        val auto = opts.optString("numeric") == "auto"
        if (modern) {
            val ru = when (unit) {
                "minute" -> RelativeDateTimeFormatter.RelativeDateTimeUnit.MINUTE
                "hour" -> RelativeDateTimeFormatter.RelativeDateTimeUnit.HOUR
                "day" -> RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY
                "week" -> RelativeDateTimeFormatter.RelativeDateTimeUnit.WEEK
                "month" -> RelativeDateTimeFormatter.RelativeDateTimeUnit.MONTH
                "quarter" -> RelativeDateTimeFormatter.RelativeDateTimeUnit.QUARTER
                "year" -> RelativeDateTimeFormatter.RelativeDateTimeUnit.YEAR
                else -> RelativeDateTimeFormatter.RelativeDateTimeUnit.SECOND
            }
            return if (auto) f.format(value, ru) else f.formatNumeric(value, ru)
        }
        if (unit == "quarter") return relative(locale, opts, value * 3, "month", false)
        if (auto && value == Math.rint(value) && Math.abs(value) <= 2) {
            val absolute = when (unit) {
                "day" -> RelativeDateTimeFormatter.AbsoluteUnit.DAY
                "week" -> RelativeDateTimeFormatter.AbsoluteUnit.WEEK
                "month" -> RelativeDateTimeFormatter.AbsoluteUnit.MONTH
                "year" -> RelativeDateTimeFormatter.AbsoluteUnit.YEAR
                else -> null
            }
            val direction = when (value.toInt()) {
                -2 -> RelativeDateTimeFormatter.Direction.LAST_2
                -1 -> RelativeDateTimeFormatter.Direction.LAST
                0 -> RelativeDateTimeFormatter.Direction.THIS
                1 -> RelativeDateTimeFormatter.Direction.NEXT
                else -> RelativeDateTimeFormatter.Direction.NEXT_2
            }
            val phrase = when {
                absolute != null -> f.format(direction, absolute)
                unit == "second" && value == 0.0 -> f.format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW)
                else -> null
            }
            if (!phrase.isNullOrEmpty()) return phrase
        }
        val u = when (unit) {
            "minute" -> RelativeDateTimeFormatter.RelativeUnit.MINUTES
            "hour" -> RelativeDateTimeFormatter.RelativeUnit.HOURS
            "day" -> RelativeDateTimeFormatter.RelativeUnit.DAYS
            "week" -> RelativeDateTimeFormatter.RelativeUnit.WEEKS
            "month" -> RelativeDateTimeFormatter.RelativeUnit.MONTHS
            "year" -> RelativeDateTimeFormatter.RelativeUnit.YEARS
            else -> RelativeDateTimeFormatter.RelativeUnit.SECONDS
        }
        return f.format(Math.abs(value), if (value < 0) RelativeDateTimeFormatter.Direction.LAST else RelativeDateTimeFormatter.Direction.NEXT, u)
    }

    /** JS `formatToParts`: one `{type, value}` per run of characters the iterator gives the same field. */
    private fun parts(it: AttributedCharacterIterator, type: (Set<AttributedCharacterIterator.Attribute>, Char) -> String): JSONArray {
        val out = JSONArray()
        var current: String? = null
        val text = StringBuilder()
        var c = it.first()
        while (c != CharacterIterator.DONE) {
            val t = type(it.attributes.keys, c)
            if (t != current && current != null) {
                out.put(JSONObject().put("type", current).put("value", text.toString()))
                text.setLength(0)
            }
            current = t
            text.append(c)
            c = it.next()
        }
        if (current != null) out.put(JSONObject().put("type", current).put("value", text.toString()))
        return out
    }

    private fun numberPart(keys: Set<AttributedCharacterIterator.Attribute>, c: Char): String = when {
        NumberFormat.Field.GROUPING_SEPARATOR in keys -> "group"
        NumberFormat.Field.DECIMAL_SEPARATOR in keys -> "decimal"
        NumberFormat.Field.FRACTION in keys -> "fraction"
        NumberFormat.Field.EXPONENT_SYMBOL in keys -> "exponentSeparator"
        NumberFormat.Field.EXPONENT_SIGN in keys -> "exponentMinusSign"
        NumberFormat.Field.EXPONENT in keys -> "exponentInteger"
        NumberFormat.Field.INTEGER in keys -> "integer"
        NumberFormat.Field.SIGN in keys -> if (c == '+') "plusSign" else "minusSign"
        NumberFormat.Field.CURRENCY in keys -> "currency"
        NumberFormat.Field.PERCENT in keys -> "percentSign"
        else -> "literal"
    }

    private fun datePart(keys: Set<AttributedCharacterIterator.Attribute>, c: Char): String = when {
        DateFormat.Field.ERA in keys -> "era"
        DateFormat.Field.YEAR in keys || DateFormat.Field.EXTENDED_YEAR in keys -> "year"
        DateFormat.Field.MONTH in keys -> "month"
        DateFormat.Field.DAY_OF_MONTH in keys -> "day"
        DateFormat.Field.DAY_OF_WEEK in keys -> "weekday"
        DateFormat.Field.AM_PM in keys -> "dayPeriod"
        DateFormat.Field.HOUR0 in keys || DateFormat.Field.HOUR1 in keys || DateFormat.Field.HOUR_OF_DAY0 in keys || DateFormat.Field.HOUR_OF_DAY1 in keys -> "hour"
        DateFormat.Field.MINUTE in keys -> "minute"
        DateFormat.Field.SECOND in keys -> "second"
        DateFormat.Field.MILLISECOND in keys -> "fractionalSecond"
        DateFormat.Field.TIME_ZONE in keys -> "timeZoneName"
        else -> "literal"
    }

    private fun dateFormat(req: JSONObject, locale: Locale, opts: JSONObject): DateFormat {
        val method = req.optString("method")
        val wantsDate = listOf("era", "weekday", "year", "month", "day", "dateStyle").any { opts.has(it) }
        val wantsTime = listOf("hour", "minute", "second", "fractionalSecondDigits", "timeZoneName", "timeStyle").any { opts.has(it) }
        val f: DateFormat = if (wantsDate || wantsTime) {
            val skeleton = StringBuilder()
            opts.optString("era").takeIf { it.isNotEmpty() }?.let { skeleton.append(when (it) { "long" -> "GGGG"; "narrow" -> "GGGGG"; else -> "G" }) }
            opts.optString("weekday").takeIf { it.isNotEmpty() }?.let { skeleton.append(when (it) { "long" -> "EEEE"; "narrow" -> "EEEEE"; else -> "EEE" }) }
            opts.optString("month").takeIf { it.isNotEmpty() }?.let { skeleton.append(when (it) { "long" -> "MMMM"; "short" -> "MMM"; "narrow" -> "MMMMM"; "numeric" -> "M"; else -> "MM" }) }
            if (opts.has("day")) skeleton.append(if (opts.optString("day") == "2-digit") "dd" else "d")
            if (opts.has("year")) skeleton.append(if (opts.optString("year") == "2-digit") "yy" else "y")
            if (opts.has("hour")) {
                val cycle = if (opts.has("hour12")) (if (opts.optBoolean("hour12")) "h" else "H") else when (opts.optString("hourCycle")) { "h11" -> "K"; "h12" -> "h"; "h23" -> "H"; "h24" -> "k"; else -> "j" }
                skeleton.append(if (opts.optString("hour") == "2-digit") cycle + cycle else cycle)
            }
            if (opts.has("minute")) skeleton.append("mm")
            if (opts.has("second")) skeleton.append("ss")
            opts.optInt("fractionalSecondDigits", 0).takeIf { it in 1..3 }?.let { skeleton.append("S".repeat(it)) }
            opts.optString("timeZoneName").takeIf { it.isNotEmpty() }?.let {
                skeleton.append(when (it) { "long" -> "zzzz"; "shortOffset" -> "O"; "longOffset" -> "OOOO"; "shortGeneric" -> "v"; "longGeneric" -> "vvvv"; else -> "z" })
            }
            if (skeleton.isEmpty()) {
                val ds = style(opts.optString("dateStyle"))
                val ts = style(opts.optString("timeStyle"))
                when {
                    ds != null && ts != null -> DateFormat.getDateTimeInstance(ds, ts, locale)
                    ds != null -> DateFormat.getDateInstance(ds, locale)
                    ts != null -> DateFormat.getTimeInstance(ts, locale)
                    else -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale)
                }
            } else {
                DateFormat.getInstanceForSkeleton(skeleton.toString(), locale)
            }
        } else {
            when (method) {
                "toLocaleTimeString" -> DateFormat.getTimeInstance(DateFormat.MEDIUM, locale)
                "toLocaleDateString" -> DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
                else -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale)
            }
        }
        opts.optString("timeZone").takeIf { it.isNotEmpty() }?.let { f.timeZone = TimeZone.getTimeZone(it) }
        return f
    }

    private fun localeOf(v: Any?): Locale {
        val tag = when (v) {
            is String -> v
            is JSONArray -> v.optString(0, "")
            else -> ""
        }
        return if (tag.isEmpty()) Locale.getDefault() else Locale.forLanguageTag(tag)
    }

    private fun style(s: String): Int? = when (s) {
        "full" -> DateFormat.FULL
        "long" -> DateFormat.LONG
        "medium" -> DateFormat.MEDIUM
        "short" -> DateFormat.SHORT
        else -> null
    }
}
