import Foundation

/// `Intl.PluralRules.select` on CLDR's plural rules (cardinal and ordinal). Foundation has no
/// API for plural categories and libicucore's `uplrules_*` is not public on iOS, so the rules
/// of the languages the Telegram clients ship are written out here, as Telegram's own
/// `NumberPluralizationForm` does. A language without rules here gets English's, which is
/// what a browser does for an unsupported locale.
enum CLDRPluralRules {
    struct Operands {
        /// The absolute value, rounded as shown.
        let n: Double
        /// The last 18 integer digits, as ICU keeps them.
        let i: Int
        let v: Int
        let f: Int
        let t: Int
        var integral: Bool { t == 0 }
    }

    static func select(_ value: Double, locale: Locale, options: [String: Any]) -> String {
        guard value.isFinite else { return "other" }
        let o = operands(value, options: options)
        let language = languageKey(locale)
        return options["type"] as? String == "ordinal" ? ordinal(language, o) : cardinal(language, o)
    }

    private static let posix = Locale(identifier: "en_US_POSIX")

    static func languageKey(_ locale: Locale) -> String {
        let parts = locale.identifier.replacingOccurrences(of: "_", with: "-").split(separator: "-").map { $0.lowercased() }
        guard let language = parts.first, !language.isEmpty else { return "en" }
        if language == "pt", let region = parts.dropFirst().first(where: { $0.count == 2 }), region != "br" { return "pt-pt" }
        return language
    }

    /// The number as `Intl.NumberFormat` would show it (default 0–3 fraction digits), read as CLDR operands.
    static func operands(_ value: Double, options: [String: Any]) -> Operands {
        let minFraction = min(max((options["minimumFractionDigits"] as? NSNumber)?.intValue ?? 0, 0), 15)
        let maxFraction = min(max((options["maximumFractionDigits"] as? NSNumber)?.intValue ?? max(minFraction, 3), minFraction), 15)
        var exact = Decimal(string: "\(abs(value))", locale: posix) ?? Decimal(abs(value))
        var rounded = Decimal()
        NSDecimalRound(&rounded, &exact, maxFraction, .plain)
        let text = NSDecimalNumber(decimal: rounded).description(withLocale: posix)
        let dot = text.firstIndex(of: ".")
        let integer = dot.map { String(text[..<$0]) } ?? text
        var fraction = dot.map { String(text[text.index(after: $0)...]) } ?? ""
        while fraction.count > minFraction && fraction.hasSuffix("0") { fraction.removeLast() }
        while fraction.count < minFraction { fraction += "0" }
        var significant = fraction
        while significant.hasSuffix("0") { significant.removeLast() }
        return Operands(n: Double(text) ?? abs(value), i: Int(integer.suffix(18)) ?? 0, v: fraction.count, f: Int(fraction) ?? 0, t: Int(significant) ?? 0)
    }

    private static func cardinal(_ language: String, _ o: Operands) -> String {
        let i = o.i, v = o.v, f = o.f, t = o.t
        let n = o.n, n1 = n == 1
        let millions = i != 0 && i % 1_000_000 == 0 && v == 0
        switch language {
        case "id", "in", "ja", "ko", "ms", "th", "vi", "zh", "lo", "km", "my", "jv", "yue", "su", "bo", "dz", "ig", "kea", "sah", "ses", "sg", "to", "wo", "yo", "ii", "nqo", "osa", "kde":
            return "other"
        case "af", "an", "asa", "az", "bal", "bem", "bez", "bg", "brx", "ce", "cgg", "chr", "ckb", "dv", "ee", "el", "eo", "eu", "fo", "fur", "gsw", "ha", "haw", "hu",
             "jgo", "jmc", "ka", "kaj", "kcg", "kk", "kkj", "kl", "ks", "ksb", "ku", "ky", "lb", "lg", "mas", "mgo", "ml", "mn", "mr", "nah", "nb", "nd", "ne", "nn",
             "nnh", "no", "nr", "ny", "nyn", "om", "or", "os", "pap", "ps", "rm", "rof", "rwk", "saq", "sd", "sdh", "seh", "sn", "so", "sq", "ss", "ssy", "st", "syr",
             "ta", "te", "teo", "tig", "tk", "tn", "tr", "ts", "ug", "uz", "ve", "vo", "vun", "wae", "xh", "xog":
            return n1 ? "one" : "other"
        case "es":
            return n1 ? "one" : millions ? "many" : "other"
        case "ca", "it", "pt-pt":
            return i == 1 && v == 0 ? "one" : millions ? "many" : "other"
        case "fr", "pt":
            return i == 0 || i == 1 ? "one" : millions ? "many" : "other"
        case "ff", "hy", "kab":
            return i == 0 || i == 1 ? "one" : "other"
        case "am", "as", "bn", "doi", "fa", "gu", "hi", "kn", "pcm", "zu":
            return i == 0 || n1 ? "one" : "other"
        case "ak", "bho", "guw", "ln", "mg", "nso", "pa", "ti", "wa":
            return n == 0 || n == 1 ? "one" : "other"
        case "da":
            return n1 || (t != 0 && (i == 0 || i == 1)) ? "one" : "other"
        case "is":
            return (t == 0 && i % 10 == 1 && i % 100 != 11) || (t % 10 == 1 && t % 100 != 11) ? "one" : "other"
        case "mk":
            return (v == 0 && i % 10 == 1 && i % 100 != 11) || (f % 10 == 1 && f % 100 != 11) ? "one" : "other"
        case "si":
            return n == 0 || n == 1 || (i == 0 && f == 1) ? "one" : "other"
        case "fil", "tl":
            return (v == 0 && (1...3).contains(i)) || (v == 0 && ![4, 6, 9].contains(i % 10)) || (v != 0 && ![4, 6, 9].contains(f % 10)) ? "one" : "other"
        case "ru", "uk":
            guard v == 0 else { return "other" }
            if i % 10 == 1 && i % 100 != 11 { return "one" }
            if (2...4).contains(i % 10) && !(12...14).contains(i % 100) { return "few" }
            return "many"
        case "be":
            guard o.integral else { return "other" }
            if i % 10 == 1 && i % 100 != 11 { return "one" }
            if (2...4).contains(i % 10) && !(12...14).contains(i % 100) { return "few" }
            return "many"
        case "pl":
            guard v == 0 else { return "other" }
            if i == 1 { return "one" }
            if (2...4).contains(i % 10) && !(12...14).contains(i % 100) { return "few" }
            return "many"
        case "cs", "sk":
            if i == 1 && v == 0 { return "one" }
            if (2...4).contains(i) && v == 0 { return "few" }
            return v != 0 ? "many" : "other"
        case "hr", "sr", "bs", "sh":
            if (v == 0 && i % 10 == 1 && i % 100 != 11) || (f % 10 == 1 && f % 100 != 11) { return "one" }
            if (v == 0 && (2...4).contains(i % 10) && !(12...14).contains(i % 100)) || ((2...4).contains(f % 10) && !(12...14).contains(f % 100)) { return "few" }
            return "other"
        case "ro", "mo":
            if i == 1 && v == 0 { return "one" }
            return v != 0 || n == 0 || (!n1 && o.integral && (1...19).contains(i % 100)) ? "few" : "other"
        case "lt":
            if o.integral && i % 10 == 1 && !(11...19).contains(i % 100) { return "one" }
            if o.integral && (2...9).contains(i % 10) && !(11...19).contains(i % 100) { return "few" }
            return f != 0 ? "many" : "other"
        case "lv":
            if (o.integral && (i % 10 == 0 || (11...19).contains(i % 100))) || (v == 2 && (11...19).contains(f % 100)) { return "zero" }
            if (o.integral && i % 10 == 1 && i % 100 != 11) || (v == 2 && f % 10 == 1 && f % 100 != 11) || (v != 2 && f % 10 == 1) { return "one" }
            return "other"
        case "ar", "ars":
            guard o.integral else { return "other" }
            if n == 0 { return "zero" }
            if n == 1 { return "one" }
            if n == 2 { return "two" }
            if (3...10).contains(i % 100) { return "few" }
            if (11...99).contains(i % 100) { return "many" }
            return "other"
        case "he", "iw":
            if (i == 1 && v == 0) || (i == 0 && v != 0) { return "one" }
            if i == 2 && v == 0 { return "two" }
            return "other"
        case "sl":
            if v == 0 && i % 100 == 1 { return "one" }
            if v == 0 && i % 100 == 2 { return "two" }
            if (v == 0 && (3...4).contains(i % 100)) || v != 0 { return "few" }
            return "other"
        default:
            return i == 1 && v == 0 ? "one" : "other"
        }
    }

    private static func ordinal(_ language: String, _ o: Operands) -> String {
        let n = o.i
        switch language {
        case "ka":
            if n == 1 { return "one" }
            return n == 0 || (2...20).contains(n % 100) || [40, 60, 80].contains(n % 100) ? "many" : "other"
        case "az":
            if [1, 2, 5, 7, 8].contains(n % 10) || [20, 50, 70, 80].contains(n % 100) { return "one" }
            if [3, 4].contains(n % 10) || [100, 200, 300, 400, 500, 600, 700, 800, 900].contains(n % 1000) { return "few" }
            return n == 0 || n % 10 == 6 || [40, 60, 90].contains(n % 100) ? "many" : "other"
        case "mk":
            if n % 10 == 1 && n % 100 != 11 { return "one" }
            if n % 10 == 2 && n % 100 != 12 { return "two" }
            return [7, 8].contains(n % 10) && ![17, 18].contains(n % 100) ? "many" : "other"
        default:
            break
        }
        guard o.integral else { return "other" }
        switch language {
        case "en":
            if n % 10 == 1 && n % 100 != 11 { return "one" }
            if n % 10 == 2 && n % 100 != 12 { return "two" }
            if n % 10 == 3 && n % 100 != 13 { return "few" }
            return "other"
        case "fr", "ro", "mo", "ms", "hy", "fil", "tl", "vi", "ga", "lo":
            return n == 1 ? "one" : "other"
        case "ca":
            return n == 1 || n == 3 ? "one" : n == 2 ? "two" : n == 4 ? "few" : "other"
        case "it":
            return [8, 11, 80, 800].contains(n) ? "many" : "other"
        case "sv":
            return [1, 2].contains(n % 10) && ![11, 12].contains(n % 100) ? "one" : "other"
        case "hu":
            return n == 1 || n == 5 ? "one" : "other"
        case "kk":
            return n % 10 == 6 || n % 10 == 9 || (n % 10 == 0 && o.n != 0) ? "many" : "other"
        case "uk":
            return n % 10 == 3 && n % 100 != 13 ? "few" : "other"
        case "be":
            return [2, 3].contains(n % 10) && ![12, 13].contains(n % 100) ? "few" : "other"
        case "sq":
            return n == 1 ? "one" : n % 10 == 4 && n % 100 != 14 ? "many" : "other"
        case "tk":
            return [6, 9].contains(n % 10) || n == 10 ? "few" : "other"
        case "hi", "gu":
            return n == 1 ? "one" : n == 2 || n == 3 ? "two" : n == 4 ? "few" : n == 6 ? "many" : "other"
        case "bn", "as":
            return [1, 5, 7, 8, 9, 10].contains(n) ? "one" : n == 2 || n == 3 ? "two" : n == 4 ? "few" : n == 6 ? "many" : "other"
        case "mr":
            return n == 1 ? "one" : n == 2 || n == 3 ? "two" : n == 4 ? "few" : "other"
        case "ne":
            return (1...4).contains(n) ? "one" : "other"
        default:
            return "other"
        }
    }
}
