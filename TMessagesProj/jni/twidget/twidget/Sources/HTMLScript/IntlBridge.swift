import Foundation

enum IntlBridge {
    static let polyfill = """
    (function () {
      const host = globalThis.__lhIntl;
      delete globalThis.__lhIntl;
      const call = request => host(JSON.stringify(request));
      const locale = l => Array.isArray(l) ? (l[0] || 'en-US') : (typeof l === 'string' && l ? l : 'en-US');
      const opts = o => (o && typeof o === 'object') ? JSON.parse(JSON.stringify(o)) : {};
      class NumberFormat {
        constructor(l, o) { this._l = locale(l); this._o = opts(o); }
        format(n) { return call({ kind: 'number', locale: this._l, opts: this._o, value: Number(n) }); }
        formatToParts(n) { return [{ type: 'literal', value: this.format(n) }]; }
        resolvedOptions() { return Object.assign({ locale: this._l }, this._o); }
      }
      class DateTimeFormat {
        constructor(l, o) { this._l = locale(l); this._o = opts(o); }
        format(d) { return call({ kind: 'date', locale: this._l, opts: this._o, value: d === undefined ? Date.now() : +new Date(d) }); }
        formatToParts(d) { return [{ type: 'literal', value: this.format(d) }]; }
        resolvedOptions() { return Object.assign({ locale: this._l, timeZone: this._o.timeZone || call({ kind: 'zone' }) }, this._o); }
      }
      class PluralRules {
        constructor(l, o) { this._l = locale(l); this._o = opts(o); }
        select(n) { return call({ kind: 'plural', locale: this._l, opts: this._o, value: Number(n) }); }
      }
      class Collator {
        constructor(l, o) { this._l = locale(l); this._o = opts(o); this.compare = this.compare.bind(this); }
        compare(a, b) { return Number(call({ kind: 'compare', locale: this._l, opts: this._o, a: String(a), b: String(b) })); }
      }
      class ListFormat {
        constructor(l, o) { this._l = locale(l); this._o = opts(o); }
        format(items) { return call({ kind: 'list', locale: this._l, opts: this._o, items: Array.from(items, String) }); }
      }
      class RelativeTimeFormat {
        constructor(l, o) { this._l = locale(l); this._o = opts(o); }
        format(v, unit) { return call({ kind: 'relative', locale: this._l, opts: this._o, value: Number(v), unit: String(unit) }); }
      }
      const define = (target, name, value) => Object.defineProperty(target, name, { value, writable: true, configurable: true });
      define(globalThis, 'Intl', { NumberFormat, DateTimeFormat, PluralRules, Collator, ListFormat, RelativeTimeFormat, getCanonicalLocales: l => [].concat(l ?? []) });
      define(Number.prototype, 'toLocaleString', function (l, o) { return new NumberFormat(l, o).format(this); });
      define(Date.prototype, 'toLocaleString', function (l, o) { return new DateTimeFormat(l, Object.assign({ year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric', minute: '2-digit', second: '2-digit' }, opts(o))).format(this); });
      define(Date.prototype, 'toLocaleDateString', function (l, o) { return new DateTimeFormat(l, Object.keys(opts(o)).length ? o : { year: 'numeric', month: 'numeric', day: 'numeric' }).format(this); });
      define(Date.prototype, 'toLocaleTimeString', function (l, o) { return new DateTimeFormat(l, Object.keys(opts(o)).length ? o : { hour: 'numeric', minute: '2-digit', second: '2-digit' }).format(this); });
      define(String.prototype, 'localeCompare', function (b, l, o) { return new Collator(l, o).compare(this, b); });
    })();
    """

    static func handle(_ json: String) -> String {
        guard json.utf8.count < 1 << 20, let data = json.data(using: .utf8), let request = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any], let kind = request["kind"] as? String else {
            return ""
        }
        let locale = Locale(identifier: (request["locale"] as? String).map { String($0.prefix(64)) } ?? "en-US")
        let opts = request["opts"] as? [String: Any] ?? [:]
        switch kind {
        case "number":
            return number(request["value"] as? Double ?? .nan, locale: locale, opts: opts)
        case "date":
            return date(request["value"] as? Double ?? 0, locale: locale, opts: opts)
        case "zone":
            return TimeZone.current.identifier
        case "plural":
            let value = request["value"] as? Double ?? 0
            if (opts["type"] as? String) == "ordinal" {
                let n = Int(abs(value).truncatingRemainder(dividingBy: 100))
                if (11...13).contains(n) {
                    return "other"
                }
                return [1: "one", 2: "two", 3: "few"][n % 10] ?? "other"
            }
            return value == 1 ? "one" : "other"
        case "compare":
            let a = (request["a"] as? String) ?? "", b = (request["b"] as? String) ?? ""
            var options: String.CompareOptions = []
            if (opts["sensitivity"] as? String) == "base" || (opts["sensitivity"] as? String) == "accent" {
                options.insert(.caseInsensitive)
            }
            if opts["numeric"] as? Bool == true {
                options.insert(.numeric)
            }
            switch a.compare(b, options: options, range: nil, locale: locale) {
            case .orderedAscending:
                return "-1"
            case .orderedDescending:
                return "1"
            case .orderedSame:
                return "0"
            }
        case "list":
            let items = (request["items"] as? [String] ?? []).prefix(1000)
            let joiner = (opts["type"] as? String) == "disjunction" ? "or" : "and"
            switch items.count {
            case 0:
                return ""
            case 1:
                return items[0]
            case 2:
                return "\(items[0]) \(joiner) \(items[1])"
            default:
                return items.dropLast().joined(separator: ", ") + ", \(joiner) " + items.last!
            }
        case "relative":
            let value = request["value"] as? Double ?? 0
            let unit = (request["unit"] as? String ?? "second").replacingOccurrences(of: "s$", with: "", options: .regularExpression)
            let amount = abs(value)
            let label = amount == 1 ? unit : unit + "s"
            let shown = amount == amount.rounded() ? String(Int(min(amount, 1e15))) : String(amount)
            if (opts["numeric"] as? String) == "auto" && amount == 1 && ["day", "year", "month", "week"].contains(unit) {
                if unit == "day" {
                    return value < 0 ? "yesterday" : "tomorrow"
                }
                return (value < 0 ? "last " : "next ") + unit
            }
            return value < 0 ? "\(shown) \(label) ago" : "in \(shown) \(label)"
        default:
            return ""
        }
    }

    private static func number(_ value: Double, locale: Locale, opts: [String: Any]) -> String {
        guard value.isFinite else {
            return value.isNaN ? "NaN" : (value < 0 ? "-∞" : "∞")
        }
        let formatter = NumberFormatter()
        formatter.locale = locale
        switch opts["style"] as? String {
        case "percent":
            formatter.numberStyle = .percent
        case "currency":
            formatter.numberStyle = .currency
            if let code = opts["currency"] as? String {
                formatter.currencyCode = String(code.prefix(8))
            }
        default:
            formatter.numberStyle = .decimal
        }
        if let grouping = opts["useGrouping"] as? Bool {
            formatter.usesGroupingSeparator = grouping
        }
        let clampDigits = { (v: Any?) -> Int? in (v as? Double).map { Int(max(0, min(20, $0))) } }
        if let minimum = clampDigits(opts["minimumIntegerDigits"]) {
            formatter.minimumIntegerDigits = minimum
        }
        if let minimum = clampDigits(opts["minimumFractionDigits"]) {
            formatter.minimumFractionDigits = minimum
            formatter.maximumFractionDigits = max(formatter.maximumFractionDigits, minimum)
        }
        if let maximum = clampDigits(opts["maximumFractionDigits"]) {
            formatter.maximumFractionDigits = maximum
            formatter.minimumFractionDigits = min(formatter.minimumFractionDigits, maximum)
        }
        if (opts["notation"] as? String) == "compact" {
            let units: [(Double, String)] = [(1e12, "T"), (1e9, "B"), (1e6, "M"), (1e3, "K")]
            for (size, suffix) in units where abs(value) >= size {
                formatter.maximumFractionDigits = abs(value) / size < 10 ? 1 : 0
                return (formatter.string(from: NSNumber(value: value / size)) ?? "") + suffix
            }
        }
        return formatter.string(from: NSNumber(value: value)) ?? String(value)
    }

    private static func date(_ milliseconds: Double, locale: Locale, opts: [String: Any]) -> String {
        guard milliseconds.isFinite, abs(milliseconds) < 8.64e15 else {
            return "Invalid Date"
        }
        let formatter = DateFormatter()
        formatter.locale = locale
        if let zone = (opts["timeZone"] as? String).flatMap({ TimeZone(identifier: $0) }) {
            formatter.timeZone = zone
        }
        let hour12: Bool
        if let cycle = opts["hourCycle"] as? String {
            hour12 = cycle == "h11" || cycle == "h12"
        } else if let flag = opts["hour12"] as? Bool {
            hour12 = flag
        } else {
            hour12 = (DateFormatter.dateFormat(fromTemplate: "j", options: 0, locale: locale) ?? "").contains("a")
        }
        var template = ""
        switch opts["era"] as? String {
        case "long": template += "GGGG"
        case "short": template += "G"
        default: break
        }
        switch opts["weekday"] as? String {
        case "long": template += "EEEE"
        case "short": template += "EEE"
        case "narrow": template += "EEEEE"
        default: break
        }
        switch opts["year"] as? String {
        case "numeric": template += "y"
        case "2-digit": template += "yy"
        default: break
        }
        switch opts["month"] as? String {
        case "long": template += "MMMM"
        case "short": template += "MMM"
        case "narrow": template += "MMMMM"
        case "numeric": template += "M"
        case "2-digit": template += "MM"
        default: break
        }
        switch opts["day"] as? String {
        case "numeric": template += "d"
        case "2-digit": template += "dd"
        default: break
        }
        if let hour = opts["hour"] as? String {
            template += hour12 ? (hour == "2-digit" ? "hh" : "h") : (hour == "2-digit" ? "HH" : "H")
        }
        if opts["minute"] is String {
            template += "mm"
        }
        if opts["second"] is String {
            template += "ss"
        }
        if let name = opts["timeZoneName"] as? String {
            template += name == "long" ? "zzzz" : "z"
        }
        if template.isEmpty {
            template = "yMd"
        }
        if let format = DateFormatter.dateFormat(fromTemplate: template, options: 0, locale: locale) {
            formatter.dateFormat = hour12 || !format.contains("a") ? format : format.replacingOccurrences(of: " a", with: "").replacingOccurrences(of: "a", with: "")
        }
        return formatter.string(from: Date(timeIntervalSince1970: milliseconds / 1000))
    }
}
