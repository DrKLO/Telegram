import Foundation

enum WidgetThemeStyle {
    static let keys = [
        "bg_color", "text_color", "hint_color", "link_color", "button_color", "button_text_color",
        "secondary_bg_color", "accent_text_color", "subtitle_text_color", "destructive_text_color",
        "section_bg_color", "section_header_text_color", "section_separator_color", "header_bg_color", "bottom_bar_bg_color",
    ]

    static let defaults: [String: [String: String]] = [
        "light": [
            "bg_color": "#ffffff", "text_color": "#000000", "hint_color": "#707579", "link_color": "#3390ec",
            "button_color": "#3390ec", "button_text_color": "#ffffff", "secondary_bg_color": "#f4f4f5",
            "accent_text_color": "#3390ec", "subtitle_text_color": "#707579", "destructive_text_color": "#e53935",
            "section_bg_color": "#ffffff", "section_header_text_color": "#3390ec", "section_separator_color": "#e7e7e7",
            "header_bg_color": "#ffffff", "bottom_bar_bg_color": "#ffffff",
        ],
        "dark": [
            "bg_color": "#212121", "text_color": "#ffffff", "hint_color": "#aaaaaa", "link_color": "#8774e1",
            "button_color": "#8774e1", "button_text_color": "#ffffff", "secondary_bg_color": "#181818",
            "accent_text_color": "#8774e1", "subtitle_text_color": "#aaaaaa", "destructive_text_color": "#ff595a",
            "section_bg_color": "#212121", "section_header_text_color": "#8774e1", "section_separator_color": "#3d3d3d",
            "header_bg_color": "#212121", "bottom_bar_bg_color": "#212121",
        ],
    ]

    static let profileUAStylesheet = "@layer tw-ua{:where(html){color:var(--tg-theme-text-color);font:var(--tg-text-font-size,15px)/var(--tg-text-line-height,1.35) system-ui,sans-serif;-webkit-text-size-adjust:none;text-size-adjust:none;overflow-wrap:break-word;-webkit-user-select:none;user-select:none;-webkit-touch-callout:none}:where(body){margin:var(--tg-text-padding,8px 12px);padding:0}:where(h1,h2,h3,h4,h5,h6){margin:.75em 0 .35em;font-weight:600;line-height:1.25}:where(h1){font-size:1.6em}:where(h2){font-size:1.35em}:where(h3){font-size:1.2em}:where(h4){font-size:1.07em}:where(h5){font-size:1em}:where(h6){font-size:.87em}:where(p,ul,ol,menu,dl,pre,blockquote,figure,table){margin:.6em 0}:where(ul,ol,menu){padding:0 0 0 1.5em;padding-inline-start:1.5em;padding-inline-end:0}:where(ul,menu){list-style-type:disc}:where(ol){list-style-type:decimal}:where(li ul,li menu){list-style-type:circle}:where(li ul,li ol,li menu,li dl){margin:0}:where(dt){font-weight:600}:where(dd){margin:0 0 0 1.5em;margin-inline-start:1.5em;margin-inline-end:0}:where(table){border-collapse:collapse;border-spacing:0}:where(caption){padding:0 0 .3em;color:var(--tg-theme-hint-color);font-size:.87em;text-align:left;text-align:start}:where(th,td){padding:.3em .6em;border-bottom:1px solid var(--tg-theme-section-separator-color);text-align:left;text-align:start;vertical-align:top}:where(th){font-weight:600}:where(th:last-child,td:last-child){padding-right:0;padding-inline-end:0;padding-inline-start:.6em}:where(th:first-child,td:first-child){padding-left:0;padding-inline-start:0;padding-inline-end:.6em}:where(hr){height:0;margin:.6em 0;border:0;border-top:1px solid var(--tg-theme-section-separator-color)}:where(pre,code,kbd,samp){font-family:ui-monospace,monospace;font-size:.9em}:where(pre code,pre kbd,pre samp){font-size:1em}:where(pre){padding:.5em .7em;border-radius:6px;background:rgba(128,128,128,.12);white-space:pre-wrap}:where(blockquote){padding:0 0 0 .75em;padding-inline-start:.75em;padding-inline-end:0;border-left:3px solid var(--tg-theme-accent-text-color);border-inline-start:3px solid var(--tg-theme-accent-text-color);border-inline-end:0}:where(figcaption){margin:.3em 0 0;color:var(--tg-theme-hint-color);font-size:.87em}:where(img){max-width:100%}:where(a[href]){color:var(--tg-theme-link-color);text-decoration:none}:where(b,strong){font-weight:600}:where(i,em,cite,dfn,var){font-style:italic}:where(small){font-size:.85em}:where(sub,sup){position:relative;font-size:.75em;line-height:0;vertical-align:baseline}:where(sup){top:-.5em}:where(sub){bottom:-.25em}:where(mark){background:rgba(255,204,0,.4);color:inherit}:where(body>:first-child){margin-top:0}:where(body>:last-child){margin-bottom:0}:where(button){appearance:none;-webkit-appearance:none;margin:0;border:0;border-radius:var(--tg-button-radius,8px);padding:var(--tg-button-padding,8px 14px);min-height:var(--tg-button-height,auto);font:var(--tg-button-font,500 14px/1.2 system-ui,sans-serif);background:var(--tg-button-bg,rgba(51,144,236,.12));color:var(--tg-button-text,inherit);cursor:pointer;-webkit-tap-highlight-color:transparent}:where(button:not([disabled])):active{opacity:var(--tg-button-pressed-opacity,.7)}:where(button[disabled]){opacity:var(--tg-button-disabled-opacity,.5);cursor:default}}"

    static let textDefaults: [String: String] = ["fontSize": "15px", "lineHeight": "1.35", "padding": "8px 12px"]

    static let uaStylesheet = masterCSS(variables(nil))

    private static let unwrappedUAStylesheet = unwrappingWhere(profileUAStylesheet)
    private static let masterLock = NSLock()
    private static var masterCache: [String: String] = [:]

    private static let color = regex("#[0-9a-fA-F]{6}")
    private static let length = regex("(0|[0-9]{1,4}(\\.[0-9]{1,3})?(px|em|rem))")
    private static let font = regex("(?:(normal|italic) )?(?:(normal|bold|[1-9]00) )?[0-9]{1,3}(?:\\.[0-9]{1,2})?(?:px|em|rem)(?:/[0-9]{1,3}(?:\\.[0-9]{1,2})?(?:px|em|rem)?)? ([a-z-]+(?:, ?[a-z-]+)*)")
    private static let opacityValue = regex("(0(\\.[0-9]{1,3})?|1(\\.0{1,3})?|\\.[0-9]{1,3})")
    private static let padding = regex("\\S+( \\S+){0,3}")
    private static let generic: Set<String> = ["system-ui", "sans-serif", "serif", "monospace", "ui-sans-serif", "ui-serif", "ui-monospace", "ui-rounded"]

    private static let buttonVariables: [(field: String, name: String, check: (Any?) -> String?)] = [
        ("bg", "--tg-button-bg", { hexColor($0) }),
        ("text", "--tg-button-text", { hexColor($0) }),
        ("radius", "--tg-button-radius", { ($0 as? String).flatMap { matches(length, $0) ? $0 : nil } }),
        ("font", "--tg-button-font", { fontToken($0) }),
        ("padding", "--tg-button-padding", { paddingToken($0) }),
        ("height", "--tg-button-height", { ($0 as? String).flatMap { matches(length, $0) ? $0 : nil } }),
        ("pressedOpacity", "--tg-button-pressed-opacity", { opacity($0) }),
        ("disabledOpacity", "--tg-button-disabled-opacity", { opacity($0) }),
    ]

    private static let textVariables: [(field: String, name: String, check: (Any?) -> String?)] = [
        ("fontSize", "--tg-text-font-size", { bounded($0, min: 8, max: 64, decimals: 2, unit: "px") }),
        ("lineHeight", "--tg-text-line-height", { bounded($0, min: 1, max: 3, decimals: 3, unit: "") }),
        ("padding", "--tg-text-padding", { paddingToken($0) }),
    ]

    static let variableNames: Set<String> = Set(keys.map(variableName) + ["--tg-color-scheme"] + buttonVariables.map { $0.name } + textVariables.map { $0.name })

    static func masterCSS(_ variables: [(name: String, value: String)]) -> String {
        let key = variables.map { $0.name + ":" + $0.value }.joined(separator: ";")
        masterLock.lock()
        defer { masterLock.unlock() }
        if let cached = masterCache[key] {
            return cached
        }
        let css = substitute(unwrappedUAStylesheet, variables)
        if masterCache.count >= 16 {
            masterCache.removeAll()
        }
        masterCache[key] = css
        return css
    }

    static func checkParams(_ params: Any?) -> [String: String] {
        guard let params = params as? [String: Any] else {
            return [:]
        }
        var out: [String: String] = [:]
        for key in keys {
            if let value = params[key] as? String, matches(color, value) {
                out[key] = value.lowercased()
            }
        }
        return out
    }

    static func variables(_ theme: Any?) -> [(name: String, value: String)] {
        let theme = (theme as? [String: Any]) ?? [:]
        let scheme = (theme["colorScheme"] as? String) == "dark" ? "dark" : "light"
        let params = checkParams(theme["params"])
        let fallback = defaults[scheme] ?? [:]
        var out = keys.map { key -> (name: String, value: String) in (variableName(key), params[key] ?? fallback[key] ?? "") }
        out.append(("--tg-color-scheme", scheme))
        let button = (theme["button"] as? [String: Any]) ?? [:]
        for variable in buttonVariables {
            if let value = variable.check(button[variable.field]) {
                out.append((variable.name, value))
            }
        }
        let text = (theme["text"] as? [String: Any]) ?? [:]
        for variable in textVariables {
            out.append((variable.name, variable.check(text[variable.field]) ?? textDefaults[variable.field] ?? ""))
        }
        return out
    }

    private static func variableName(_ key: String) -> String {
        return "--tg-theme-" + key.replacingOccurrences(of: "_", with: "-")
    }

    private static func hexColor(_ value: Any?) -> String? {
        guard let value = value as? String, matches(color, value) else {
            return nil
        }
        return value.lowercased()
    }

    private static func fontToken(_ value: Any?) -> String? {
        guard let value = value as? String, let match = font.firstMatch(in: value, range: NSRange(value.startIndex..., in: value)), let range = Range(match.range(at: 3), in: value) else {
            return nil
        }
        return value[range].split(separator: ",", omittingEmptySubsequences: false).allSatisfy { generic.contains($0.trimmingCharacters(in: .whitespaces)) } ? value : nil
    }

    private static func paddingToken(_ value: Any?) -> String? {
        guard let value = value as? String, matches(padding, value), value.components(separatedBy: " ").allSatisfy({ matches(length, $0) }) else {
            return nil
        }
        return value
    }

    private static func bounded(_ value: Any?, min: Double, max: Double, decimals: Int, unit: String) -> String? {
        let number: Double
        if let value = value as? NSNumber, CFGetTypeID(value) != CFBooleanGetTypeID() {
            number = value.doubleValue
        } else if let value = value as? String, matches(regex("[0-9]{1,2}(\\.[0-9]{1,\(decimals)})?" + unit), value) {
            number = Double(value.dropLast(unit.count)) ?? .nan
        } else {
            return nil
        }
        guard number.isFinite, number >= min, number <= max else {
            return nil
        }
        let factor = pow(10, Double(decimals))
        return shortest((number * factor).rounded() / factor) + unit
    }

    private static func shortest(_ value: Double) -> String {
        if value == value.rounded(), abs(value) < 1e15 {
            return String(Int64(value))
        }
        return String(value)
    }

    private static func substitute(_ css: String, _ variables: [(name: String, value: String)]) -> String {
        let values = Dictionary(variables.map { ($0.name, $0.value) }, uniquingKeysWith: { first, _ in first })
        var out = ""
        var rest = Substring(css)
        while let start = rest.range(of: "var(") {
            var depth = 1
            var comma: Substring.Index?
            var index = start.upperBound
            while index < rest.endIndex, depth > 0 {
                let character = rest[index]
                if character == "(" {
                    depth += 1
                } else if character == ")" {
                    depth -= 1
                } else if character == ",", depth == 1, comma == nil {
                    comma = index
                }
                if depth > 0 {
                    index = rest.index(after: index)
                }
            }
            guard depth == 0 else {
                break
            }
            let name = rest[start.upperBound..<(comma ?? index)].trimmingCharacters(in: .whitespaces)
            out += rest[..<start.lowerBound]
            if let value = values[name] {
                out += value
            } else {
                out += rest[start.lowerBound...index]
            }
            rest = rest[rest.index(after: index)...]
        }
        return out + rest
    }

    private static func opacity(_ value: Any?) -> String? {
        let text: String
        if let string = value as? String {
            text = string
        } else if let number = value as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID() {
            let double = number.doubleValue
            text = double == double.rounded() && abs(double) < 1e15 ? String(Int64(double)) : "\(double)"
        } else {
            return nil
        }
        return matches(opacityValue, text) ? text : nil
    }

    private static func regex(_ pattern: String) -> NSRegularExpression {
        return try! NSRegularExpression(pattern: "^(?:" + pattern + ")\\z")
    }

    private static func matches(_ regex: NSRegularExpression, _ value: String) -> Bool {
        return regex.firstMatch(in: value, range: NSRange(value.startIndex..., in: value)) != nil
    }

    private static func unwrappingWhere(_ sheet: String) -> String {
        var out = ""
        var rest = Substring(sheet)
        while let start = rest.range(of: ":where(") {
            out += rest[..<start.lowerBound]
            var depth = 1
            var index = start.upperBound
            while index < rest.endIndex {
                if rest[index] == "(" {
                    depth += 1
                } else if rest[index] == ")" {
                    depth -= 1
                    if depth == 0 {
                        break
                    }
                }
                index = rest.index(after: index)
            }
            out += rest[start.upperBound..<index]
            rest = index < rest.endIndex ? rest[rest.index(after: index)...] : ""
        }
        return out + rest
    }
}
