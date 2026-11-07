import XCTest
import JavaScriptCore
@testable import WidgetSandbox

final class IntlBridgeTests: XCTestCase {
    private func request(_ options: [String: Any], timestamp: Double, locale: String = "en-GB", method: String? = nil) throws -> String {
        var request: [String: Any] = ["kind": method == nil ? "date" : "dateMethod", "locale": locale, "opts": options, "value": timestamp]
        if let method = method { request["method"] = method }
        return String(decoding: try JSONSerialization.data(withJSONObject: request, options: [.sortedKeys]), as: UTF8.self)
    }

    private func check(_ options: [String: Any], timestamp: Double, locale: String = "en-GB", method: String? = nil, file: StaticString = #filePath, line: UInt = #line) throws {
        let json = try request(options, timestamp: timestamp, locale: locale, method: method)
        let context = try XCTUnwrap(JSContext())
        let expected = context.evaluateScript("var r = \(json); r.method ? new Date(r.value)[r.method](r.locale, r.opts) : new Intl.DateTimeFormat(r.locale, r.opts).format(r.value)")?.toString()
        XCTAssertNil(context.exception, file: file, line: line)
        XCTAssertEqual(IntlBridge.format(requestJson: json).replacingOccurrences(of: "\u{202f}", with: " "), expected?.replacingOccurrences(of: "\u{202f}", with: " "), "\(json)", file: file, line: line)
    }

    func testClockTimeZonesAcrossSeasons() throws {
        for timestamp in [1736942400000.0, 1752580800000.0, 1736971200000.0] {
            for zone in ["UTC", "Europe/Lisbon", "America/New_York", "Asia/Tokyo"] {
                try check(["timeZone": zone, "hour": "2-digit", "minute": "2-digit", "second": "2-digit", "hourCycle": "h23"], timestamp: timestamp)
                try check(["timeZone": zone, "weekday": "long", "day": "numeric", "month": "short"], timestamp: timestamp)
            }
        }
    }

    func testHourCyclesAtMidnightAndNoon() throws {
        for timestamp in [1736899200000.0, 1736942400000.0] {
            for cycle in ["h11", "h12", "h23", "h24"] {
                try check(["timeZone": "UTC", "hour": "2-digit", "minute": "2-digit", "hourCycle": cycle], timestamp: timestamp)
                try check(["timeZone": "UTC", "timeStyle": "short", "hourCycle": cycle], timestamp: timestamp)
            }
        }
    }

    func testFormattingOptionsRemainIndependent() throws {
        for timestamp in [1736899200000.0, 1736931600000.0, 1736942400000.0] {
            for locale in ["en-GB", "en-US", "de-DE"] {
                try check(["timeZone": "UTC", "hour": "numeric", "minute": "2-digit", "hour12": false], timestamp: timestamp, locale: locale)
                try check(["timeZone": "Asia/Tokyo", "hour": "numeric", "minute": "2-digit", "hour12": true], timestamp: timestamp, locale: locale)
                try check(["timeZone": "UTC", "year": "2-digit", "month": "2-digit", "day": "2-digit"], timestamp: timestamp, locale: locale)
                try check(["timeZone": "UTC", "dateStyle": "full"], timestamp: timestamp, locale: locale)
                try check(["timeZone": "UTC", "timeStyle": "short", "hour12": false], timestamp: timestamp, locale: locale)
                try check(["timeZone": "UTC", "timeStyle": "short", "hour12": true], timestamp: timestamp, locale: locale)
                try check(["timeZone": "UTC", "hour": "2-digit", "minute": "2-digit"], timestamp: timestamp, locale: locale, method: "toLocaleTimeString")
            }
        }
    }

    func testRepeatedClockFormattingCost() throws {
        let requests = try (0..<1000).map { index in
            try request(["timeZone": ["Europe/Lisbon", "America/New_York", "Asia/Tokyo"][index % 3], "hour": "2-digit", "minute": "2-digit", "second": "2-digit", "hourCycle": "h23"], timestamp: 1736942400000 + Double(index * 1000))
        }
        let start = Date()
        for request in requests { XCTAssertFalse(IntlBridge.format(requestJson: request).isEmpty) }
        print("IntlBridge 1000 clock formats: \(Date().timeIntervalSince(start) * 1000) ms")
    }

    func testConcurrentClockFormatting() throws {
        let requests = try ["UTC", "Europe/Lisbon", "America/New_York", "Asia/Tokyo"].map { zone in
            try request(["timeZone": zone, "hour": "2-digit", "minute": "2-digit", "hourCycle": "h23"], timestamp: 1736942400000)
        }
        let expected = ["12:00", "12:00", "07:00", "21:00"]
        DispatchQueue.concurrentPerform(iterations: 8) { worker in
            for iteration in 0..<100 {
                let index = (worker + iteration) % requests.count
                XCTAssertEqual(IntlBridge.format(requestJson: requests[index]), expected[index])
            }
        }
    }

    /// formatToParts is in the widget profile: the parts join to what `format` gives and name the fields.
    func testFormatToPartsJoinsToTheFormattedString() throws {
        func parts(_ request: [String: Any]) throws -> [(String, String)] {
            let json = String(decoding: try JSONSerialization.data(withJSONObject: request, options: [.sortedKeys]), as: UTF8.self)
            let list = try XCTUnwrap(try JSONSerialization.jsonObject(with: Data(IntlBridge.format(requestJson: json).utf8)) as? [[String: String]])
            return list.map { ($0["type"] ?? "", $0["value"] ?? "") }
        }
        func formatted(_ request: [String: Any]) throws -> String {
            IntlBridge.format(requestJson: String(decoding: try JSONSerialization.data(withJSONObject: request, options: [.sortedKeys]), as: UTF8.self))
        }
        let number: [String: Any] = ["kind": "numberParts", "locale": "en-US", "opts": [String: Any](), "value": -1234.5]
        let n = try parts(number)
        XCTAssertEqual(n.map(\.0), ["minusSign", "integer", "group", "integer", "decimal", "fraction"], "\(n)")
        XCTAssertEqual(n.map(\.1).joined(), try formatted(number.merging(["kind": "number"]) { $1 }))
        let currency: [String: Any] = ["kind": "numberParts", "locale": "en-US", "opts": ["style": "currency", "currency": "USD"], "value": 12.5]
        XCTAssertTrue(try parts(currency).contains { $0 == ("currency", "$") })
        let date: [String: Any] = ["kind": "dateParts", "locale": "en-US", "opts": ["timeZone": "UTC", "year": "numeric", "month": "long", "day": "numeric"], "value": 0]
        let d = try parts(date)
        XCTAssertTrue(d.contains { $0 == ("year", "1970") } && d.contains { $0 == ("month", "January") } && d.contains { $0 == ("day", "1") }, "\(d)")
        XCTAssertEqual(d.map(\.1).joined(), try formatted(date.merging(["kind": "date"]) { $1 }))
    }

    private func format(_ request: [String: Any]) throws -> String {
        IntlBridge.format(requestJson: String(decoding: try JSONSerialization.data(withJSONObject: request, options: [.sortedKeys]), as: UTF8.self))
    }

    /// ECMA-402 ToDateTimeOptions is the shim's (globals.ts `dateTimeOptions`): a format without a field or a style
    /// reaches the glue with numeric year/month/day (or the time, or both) and comes out as JavaScriptCore formats it.
    func testDefaultsWithoutFieldsAreNumeric() throws {
        let script = """
            var u={timeZone:'UTC'},out=[];
            ['en-US','en-GB','de-DE','ru-RU'].forEach(function(l){[0,1736942400000].forEach(function(t){var d=new Date(t);
              out.push(new Intl.DateTimeFormat(l,u).format(d),d.toLocaleString(l,u),d.toLocaleDateString(l,u),d.toLocaleTimeString(l,u),
                d.toLocaleDateString(l,{timeZone:'UTC',hour:'numeric'}),new Intl.DateTimeFormat(l,{timeZone:'UTC',weekday:'long'}).format(d));});});
            var r=new Intl.DateTimeFormat('en-US',{timeZone:'Asia/Tokyo'}).resolvedOptions();out.push(r.year+','+r.month+','+r.day+','+r.timeZone);
            """
        let context = try XCTUnwrap(JSContext())
        let expected = try XCTUnwrap(context.evaluateScript(script + "out")?.toArray() as? [String])
        XCTAssertNil(context.exception)
        let h = UserActivationTests.Harness("<script>\(script)out.forEach(function(s,i){console.log('intl'+i+'='+s);});</script>")
        h.pump { h.logs.contains { $0.hasPrefix("intl\(expected.count - 1)=") } }
        let got = (0..<expected.count).map { i in h.logs.first { $0.hasPrefix("intl\(i)=") }.map { String($0.dropFirst("intl\(i)=".count)) } ?? "?" }
        let narrow = { (s: String) in s.replacingOccurrences(of: "\u{202f}", with: " ") }
        XCTAssertEqual(got.map(narrow), expected.map(narrow))
        XCTAssertEqual(got.first, "1/1/1970")
        XCTAssertEqual(got.last, "numeric,numeric,numeric,Asia/Tokyo")
    }

    /// A locale tag with a Unicode extension: Locale(identifier:) misreads "ar-EG-u-nu-arab" as "ar_EG@numbers=yes",
    /// whose formatters have nil patterns and symbols and trapped the host. Each request must answer, with the extension applied.
    func testUnicodeExtensionTagsFormatInsteadOfTrapping() throws {
        let requests: [[String: Any]] = [
            ["kind": "dateMethod", "method": "toLocaleDateString", "locale": "ar-EG-u-nu-arab", "opts": ["dateStyle": "full"], "value": 0],
            ["kind": "date", "locale": "hi-IN-u-nu-deva", "opts": ["hour": "numeric", "minute": "numeric"], "value": 0],
            ["kind": "dateParts", "locale": "ar-EG-u-nu-arab", "opts": ["year": "numeric", "month": "long", "day": "numeric"], "value": 0],
            ["kind": "dateMethod", "method": "toLocaleString", "locale": "en-u-nu-latn-ca-gregory", "opts": ["year": "numeric", "month": "numeric", "day": "numeric", "hour": "numeric", "minute": "numeric", "second": "numeric"], "value": 0],
            ["kind": "numberParts", "locale": "ar-EG-u-nu-arab", "opts": ["style": "percent"], "value": 0.25],
            ["kind": "relative", "locale": "fa-u-nu-arab", "opts": ["numeric": "auto"], "value": 1, "unit": "quarter"],
            ["kind": "number", "locale": "ar-EG-u-nu-arab", "opts": ["style": "currency", "currency": "EGP"], "value": 1234.5],
            ["kind": "number", "locale": "hi-u-nu-deva-x-y", "opts": [:], "value": 42],
            ["kind": "number", "locale": "en-u-attr-nu-thai", "opts": [:], "value": 42],
        ]
        for request in requests {
            let json = String(decoding: try JSONSerialization.data(withJSONObject: request, options: [.sortedKeys]), as: UTF8.self)
            XCTAssertFalse(IntlBridge.format(requestJson: json).isEmpty, json)
        }
        let date = String(decoding: try JSONSerialization.data(withJSONObject: requests[0], options: [.sortedKeys]), as: UTF8.self)
        let currency = String(decoding: try JSONSerialization.data(withJSONObject: requests[6], options: [.sortedKeys]), as: UTF8.self)
        if #available(macOS 13, iOS 16, *) {
            XCTAssertTrue(IntlBridge.format(requestJson: date).contains("١٩٧٠"), IntlBridge.format(requestJson: date))
            XCTAssertNotEqual(IntlBridge.format(requestJson: currency), "1234.5")
        }
    }

    func testUnicodeExtensionIsDroppedWhereComponentsAreUnavailable() {
        XCTAssertEqual(IntlBridge.withoutUnicodeExtension("ar-EG-u-nu-arab"), "ar-EG")
        XCTAssertEqual(IntlBridge.withoutUnicodeExtension("en-u-nu-latn-ca-gregory"), "en")
        XCTAssertEqual(IntlBridge.withoutUnicodeExtension("hi-u-nu-deva-x-y"), "hi-x-y")
        XCTAssertEqual(IntlBridge.withoutUnicodeExtension("de-t-en-u-co-phonebk"), "de-t-en")
        XCTAssertEqual(IntlBridge.withoutUnicodeExtension("zh-Hant-TW"), "zh-Hant-TW")
    }

    func testResolvedLocaleKeepsTheTag() throws {
        guard #available(macOS 13, iOS 16, *) else {
            throw XCTSkip("Locale.Components needs macOS 13")
        }
        XCTAssertEqual(IntlBridge.bcp47Locale("ar-EG-u-nu-arab").identifier(.bcp47), "ar-EG-u-nu-arab")
        XCTAssertEqual(IntlBridge.bcp47Locale("zh-Hant-TW").identifier(.bcp47), "zh-Hant-TW")
    }

    /// The cases shared with intl.ts, IntlBridge.kt and intl.cpp: V8's answers (tests/fixtures/intl-cases.json,
    /// copied here by `pnpm build:ios`).
    func testSharedFixtureMatchesV8() throws {
        let url = try XCTUnwrap(Bundle.module.url(forResource: "intl-cases", withExtension: "json", subdirectory: "Fixtures"))
        let root = try XCTUnwrap(try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let cases = try XCTUnwrap(root["cases"] as? [[String: Any]])
        var mismatches: [String] = []
        for c in cases {
            let request = try XCTUnwrap(c["request"] as? [String: Any])
            let json = String(decoding: try JSONSerialization.data(withJSONObject: request, options: [.sortedKeys]), as: UTF8.self)
            let got = IntlBridge.format(requestJson: json)
            if !Self.matches(got, c["expected"]) { mismatches.append("\(json): \(got) != \(c["expected"] ?? "nil")") }
        }
        XCTAssertGreaterThan(cases.count, 100)
        XCTAssertEqual(mismatches, [], mismatches.joined(separator: "\n"))
    }

    private static func spaces(_ s: String) -> String {
        s.replacingOccurrences(of: "\u{a0}", with: " ").replacingOccurrences(of: "\u{202f}", with: " ").replacingOccurrences(of: "\u{2009}", with: " ")
    }

    /// Strings with the three spaces as one; parts as [type, value] lists; options on the expected keys.
    private static func matches(_ got: String, _ expected: Any?) -> Bool {
        if let s = expected as? String { return spaces(got) == spaces(s) }
        let parsed = try? JSONSerialization.jsonObject(with: Data(got.utf8))
        if let list = expected as? [[String: String]] {
            guard let g = parsed as? [[String: Any]] else { return false }
            return g.map { [$0["type"] as? String ?? "", spaces($0["value"] as? String ?? "")] } == list.map { [$0["type"] ?? "", spaces($0["value"] ?? "")] }
        }
        if let object = expected as? [String: Any] {
            guard let g = parsed as? [String: Any] else { return false }
            return object.allSatisfy { (g[$0.key] as? NSObject)?.isEqual($0.value) == true }
        }
        return false
    }

    /// The CLDR rules match the system ICU (JavaScriptCore's Intl.PluralRules) for every language they list.
    func testPluralRulesMatchICU() throws {
        let context = try XCTUnwrap(JSContext())
        let locales = ["en", "de", "nl", "sv", "fi", "it", "ca", "es", "fr", "pt-BR", "pt-PT", "ru", "uk", "be", "pl", "cs", "sk", "hr", "sr", "bs", "ro", "lt", "lv",
                       "ar", "he", "sl", "tr", "az", "kk", "uz", "hu", "el", "bg", "ka", "hy", "fa", "hi", "bn", "da", "is", "mk", "si", "fil", "id", "ja", "ko", "zh",
                       "ms", "th", "vi", "nb", "et", "sq", "ta", "te", "mr", "ne", "gu", "ky", "mn", "tk", "am", "pa"]
        var numbers: [Double] = Array(0...30).map(Double.init) + [100, 101, 102, 103, 104, 105, 111, 112, 114, 121, 1000, 1001, 1_000_000, 2_000_000, 1e20]
        numbers += [0.1, 0.5, 1.5, 2.1, 2.5, 3.2, 5.5, 11.1, 21.3, 0.01, 1.25, 1.0005]
        var mismatches: [String] = []
        for locale in locales {
            for type in ["cardinal", "ordinal"] {
                for options in [["type": type], ["type": type, "minimumFractionDigits": 1]] as [[String: Any]] {
                    let optionsJson = String(decoding: try JSONSerialization.data(withJSONObject: options, options: [.sortedKeys]), as: UTF8.self)
                    for n in numbers {
                        let expected = context.evaluateScript("new Intl.PluralRules(\"\(locale)\", \(optionsJson)).select(\(n))")?.toString() ?? ""
                        let got = try format(["kind": "plural", "locale": locale, "opts": options, "value": n])
                        if got != expected { mismatches.append("\(locale) \(optionsJson) \(n): \(got) != \(expected)") }
                    }
                }
            }
        }
        XCTAssertNil(context.exception)
        XCTAssertEqual(mismatches, [], mismatches.prefix(40).joined(separator: "\n"))
        XCTAssertEqual(try format(["kind": "plural", "locale": "en-US", "value": 1]) + "," + format(["kind": "plural", "locale": "ru-RU", "value": 3]), "one,few")
    }

    func testRelativeTimeAndListFollowOptionsAndLocale() throws {
        let context = try XCTUnwrap(JSContext())
        for locale in ["en", "de", "ru"] {
            for options in [[:], ["numeric": "auto"], ["numeric": "always"], ["style": "short"]] as [[String: Any]] {
                let optionsJson = String(decoding: try JSONSerialization.data(withJSONObject: options, options: [.sortedKeys]), as: UTF8.self)
                for (value, unit) in [(-1.0, "day"), (1.0, "day"), (0.0, "day"), (-7.0, "day"), (3.0, "hours"), (-2.0, "week"), (1.0, "month"), (-1.0, "year"), (0.0, "second"), (45.0, "minute")] {
                    let expected = context.evaluateScript("new Intl.RelativeTimeFormat(\"\(locale)\", \(optionsJson)).format(\(value), \"\(unit)\")")?.toString()
                    let got = try format(["kind": "relative", "locale": locale, "opts": options, "value": value, "unit": unit])
                    XCTAssertEqual(got.replacingOccurrences(of: "\u{a0}", with: " "), expected?.replacingOccurrences(of: "\u{a0}", with: " "), "\(locale) \(optionsJson) \(value) \(unit)")
                }
            }
        }
        XCTAssertEqual(try format(["kind": "relative", "locale": "en", "opts": ["numeric": "auto"], "value": -1, "unit": "day"]), "yesterday")
        XCTAssertEqual(try format(["kind": "list", "locale": "de", "opts": ["type": "conjunction"], "value": ["a", "b", "c"]]), "a, b und c")
        XCTAssertEqual(try format(["kind": "list", "locale": "en", "opts": ["type": "conjunction"], "value": ["a", "b", "c"]]), "a, b, and c")
        XCTAssertLessThan(Int(try format(["kind": "compare", "locale": "en", "a": "a", "b": "B"])) ?? 0, 0)
        XCTAssertEqual(try format(["kind": "compare", "locale": "en", "opts": ["sensitivity": "base"], "a": "a", "b": "\u{e1}"]), "0")
        XCTAssertEqual(try format(["kind": "compare", "locale": "en", "opts": ["numeric": true], "a": "2", "b": "10"]), "-1")
        let resolved = try XCTUnwrap(try JSONSerialization.jsonObject(with: Data(try format(["kind": "dateOptions", "locale": "en-US", "opts": ["timeZone": "Asia/Tokyo", "year": "numeric"]]).utf8)) as? [String: Any])
        XCTAssertEqual(resolved["timeZone"] as? String, "Asia/Tokyo")
        XCTAssertEqual(resolved["year"] as? String, "numeric")
    }
}
