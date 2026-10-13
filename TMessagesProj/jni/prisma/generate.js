const fs = require('fs')
const isEqual = require('lodash.isequal')
const Prism = require('prismjs')
const components = require('prismjs/components.js')

global.Prism = Prism

const SCRIPTS = {}
const include = function (src) {
    // Some black magic of eval. Load the script from src to global scope. Source: https://stackoverflow.com/a/23699187/17140794
    (1, eval)(src.toString())
}

async function loadLanguages(lngs) {
    if (lngs) {
        lngs = Array.isArray(lngs) ? lngs : [lngs];

        for (const lng of lngs) {
            await loadLanguage(lng)
        }
    }
}
let langNumber = 0
async function loadLanguage(lng) {
    if (!components.languages[lng].title) {
        return
    }

    await loadLanguages(components.languages[lng].optional)
    await loadLanguages(components.languages[lng].require)
    await loadLanguages(components.languages[lng].modify)

    if (!SCRIPTS[lng]) {
        SCRIPTS[lng] = true

        langNumber += 1
        console.log(`${langNumber} | Loading ${lng}`);
        // TODO: version should probably not be hardcoded

        require(`prismjs/components/prism-${lng}.js`)
    }
}

function loadLocalLanguage(path, code, title, alias) {
    include(fs.readFileSync(path))

    components.languages[code] = {
        title: title,
        alias: alias
    }
}

// Spell out the constructs whose meaning differs between regex engines, so one table can
// serve all of them. java.util.regex reads a [ inside a character class as the start of a
// nested class, requires a { outside one to open a quantifier, reads \0 as the start of an
// octal escape, and reads \v as the whole vertical whitespace class rather than as U+000B.
// All four rewrites are no-ops for ECMAScript and for Boost, which is what libprisma
// compiles the patterns with.
const QUANTIFIER = /^\{\d+(?:,\d*)?\}/

function normalizeEscapes(pattern) {
    let result = ''
    let inClass = false

    for (let i = 0; i < pattern.length; i++) {
        const c = pattern[i]

        if (c === '\\') {
            if (pattern[i + 1] === '0' && !/[0-7]/.test(pattern[i + 2] || '')) {
                result += '\\x00'
            } else if (pattern[i + 1] === 'v') {
                result += '\\x0B'
            } else {
                result += pattern.substr(i, 2)
            }
            i++
            continue
        }

        if (inClass) {
            if (c === '[') {
                result += '\\['
                continue
            }
            // && is set intersection in java.util.regex
            if (c === '&' && pattern[i + 1] === '&') {
                result += '\\&\\&'
                i++
                continue
            }
            if (c === ']') {
                inClass = false
            }
        } else if (c === '[') {
            inClass = true
        } else if (c === '{' && !QUANTIFIER.test(pattern.substr(i))) {
            result += '\\{'
            continue
        }

        result += c
    }

    return result
}

// A character written literally in a grammar - BQN's •, APL's ⍵ - has to reach the readers as
// an escape rather than as itself: they disagree about what the bytes of a pattern mean, and
// \uXXXX is the one spelling all of them read as a code point. An astral character is two code
// units here, so it becomes a surrogate pair, which UnicodeEscapes.h puts back together.
function escapeNonAscii(pattern) {
    if (!/[^\x00-\x7F]/.test(pattern)) {
        return pattern
    }

    let result = ''

    for (let i = 0; i < pattern.length; i++) {
        const code = pattern.charCodeAt(i)
        result += code > 0x7F
            ? '\\u' + code.toString(16).padStart(4, '0')
            : pattern[i]
    }

    return result
}

function unique(a, fn) {
    if (a.length === 0 || a.length === 1) {
        return a;
    }
    if (!fn) {
        return a;
    }

    for (let i = 0; i < a.length; i++) {
        for (let j = i + 1; j < a.length; j++) {
            if (fn(a[i], a[j])) {
                a.splice(i, 1);
            }
        }
    }
    return a;
}

function uniqlo(a, fn) {
    var size = a.length;

    do {
        size = a.length
        a = unique(a, fn)
    }
    while (size > a.length)
    return a
}

async function generate() {

    var tempPatterns = []
    var tempLanguages = {}
    var tempTokens = []
    var tempGrammars = []
    var weak = new WeakMap

    function flatten(grammar) {
        var keys = {}

        var cache = weak.get(grammar)
        if (cache !== undefined) {
            return cache
        }

        weak.set(grammar, keys)

        var copy = grammar;
        var rest = copy.rest;
        if (rest) {
            copy = {}

            Object.keys(grammar).forEach(name => {
                copy[name] = grammar[name]
            })

            for (var token in rest) {
                copy[token] = rest[token];
            }

            delete copy.rest;
        }

        function sanitize(pattern) {
            // Unsupported:
            // UTF-16 ranges
            // [^]    => [\s\S] <- matches any character, including new line
            // []     =>   ??   <- matches _empty_ string

            // All the whitelisted languages have 0xFFFF as maximum range
            // This is not the case for all the grammars supported by Prisma.

            pattern = pattern.replaceAll("\\uFFFF", "\\xFF");
            pattern = pattern.replaceAll("[^]", "[\\s\\S]");

            // TODO: This just bruteforces the regex to work, but of course
            // result may vary from the original one.
            //static const boost::regex hex(R"(\\u([0-9a-fA-F]{4}))");
            //pattern = boost::regex_replace(pattern, hex, R"(\\xFF)");

            // TODO: Again, none of the whitelisted languages use [], but others do.
            // Howhever, it is unclear to me how [] is supposed to work.
            pattern = pattern.replaceAll("|[])", ")");
            pattern = pattern.replaceAll(":[]", ":");

            return escapeNonAscii(normalizeEscapes(pattern))
        }

        for (var token in copy) {
            if (!copy.hasOwnProperty(token) || !copy[token]) {
                continue;
            }

            var patterns = copy[token];
            patterns = Array.isArray(patterns) ? patterns : [patterns];

            var indexes = []

            for (var j = 0; j < patterns.length; ++j) {
                var patternObj = patterns[j];
                var inside = patternObj.inside;
                var lookbehind = !!patternObj.lookbehind;
                var greedy = !!patternObj.greedy;
                var alias = patternObj.alias;

                // Preserve every Prism CSS class in the existing alias string field.
                alias = Array.isArray(alias) ? alias.join(' ') : alias;

                var pattern = patternObj.pattern || patternObj;
                var patternStr = sanitize(pattern.toString())

                if (lookbehind) {
                    patternStr += "l"
                }
                if (greedy) {
                    patternStr += "y"
                }

                var np

                if (alias || inside) {
                    np = {
                        pattern: patternStr
                    }

                    if (alias) {
                        np.alias = alias
                    }
                    if (inside) {
                        np.inside = flatten(inside)
                    }

                } else if (pattern instanceof RegExp) {
                    np = patternStr
                } else {
                    debugger
                }

                tempPatterns.push(np)
                indexes.push(np)
            }

            keys[token] = indexes
            tempTokens.push(indexes)
        }

        tempGrammars.push(keys)
        return keys
    }

    var unsupported = [
        "bsl",
        "coq",
        "gherkin",
        "jexl",
        "kumir",
        "pure",
        "purescript",
        "turtle",
        "sparql" // requires turtle
    ]

    await loadLanguages(Object.keys(components.languages))
    console.log(`\nLoaded all ${langNumber} languages`)
    console.log("Processing...")

    // Manually add local definitions
    loadLocalLanguage('./components/prism-tl.js', 'typelanguage', 'TypeLanguage', 'tl')
    loadLocalLanguage('./components/prism-tlb.js', 'tlb', 'TypeLanguage-Binary', 'tlb')
    loadLocalLanguage('./components/prism-fift.js', 'fift', 'Fift', 'fift')
    loadLocalLanguage('./components/prism-func.js', 'func', 'FunC', ['func', 'fc'])
    loadLocalLanguage('./components/prism-tact.js', 'tact', 'Tact', 'tact')
    loadLocalLanguage('./components/prism-tolk.js', 'tolk', 'Tolk', 'tolk')

    Object.keys(Prism.languages).forEach(lng => {
        if (unsupported.includes(lng) || !components.languages[lng]) {
            return
        }

        tempLanguages[lng] = flatten(Prism.languages[lng])
    })

    var allTokens = uniqlo(tempTokens, isEqual)
    var allGrammars = uniqlo(tempGrammars, isEqual)
    var allPatterns = uniqlo(tempPatterns, isEqual)

    Object.keys(tempLanguages).forEach(name => {
        var find = allGrammars.find(x => isEqual(x, tempLanguages[name]))
        if (find === undefined) {
            debugger
        }

        tempLanguages[name] = find
    })

    for (var i = 0; i < allPatterns.length; i++) {
        if (allPatterns[i].inside) {
            var find = allGrammars.find(x => isEqual(x, allPatterns[i].inside))
            if (find === undefined) {
                debugger
            }

            allPatterns[i].inside = find
        }
    }

    for (var i = 0; i < allTokens.length; i++) {
        var token = allTokens[i]

        for (var j = 0; j < token.length; j++) {
            var find = allPatterns.find(x => isEqual(x, token[j]))
            if (find === undefined) {
                debugger
            }

            token[j] = find
        }
    }

    for (var i = 0; i < allGrammars.length; i++) {
        Object.keys(allGrammars[i]).forEach(name => {
            var find = allTokens.find(x => isEqual(x, allGrammars[i][name]))
            if (find === undefined) {
                debugger
            }

            allGrammars[i][name] = find
        })
    }

    for (var i = 0; i < allPatterns.length; i++) {
        if (allPatterns[i].inside) {
            allPatterns[i].inside = allGrammars.indexOf(allPatterns[i].inside)
        }
    }

    for (var i = 0; i < allTokens.length; i++) {
        var token = allTokens[i]

        for (var j = 0; j < token.length; j++) {
            token[j] = allPatterns.indexOf(token[j])
        }
    }

    /*for (var i = 0; i < allGrammars.length; i++) {
        Object.keys(allGrammars[i]).forEach(name => {
            if (allGrammars[i][name].length == 1) {
                allGrammars[i][name] = allGrammars[i][name][0]
            }
        })
    }*/

    for (var i = 0; i < allPatterns.length; i++) {
        if (allPatterns[i].pattern) {
            var patternStr = allPatterns[i].pattern + ",";
            if (allPatterns[i].alias) {
                patternStr += allPatterns[i].alias
            }
            patternStr += ","
            if (allPatterns[i].inside) {
                patternStr += allPatterns[i].inside
            }

            allPatterns[i] = patternStr
        } else {
            allPatterns[i] += ",,"
        }
    }

    var allLanguages = {}
    var languageNames = {}

    Object.keys(tempLanguages).forEach(name => {
        var find = allGrammars.find(x => isEqual(x, tempLanguages[name]))
        if (find === undefined) {
            debugger
        }

        allLanguages[name] = allGrammars.indexOf(find)
        languageNames[name] = components.languages[name].title

        var alias = components.languages[name].alias
        if (alias) {
            alias = Array.isArray(alias) ? alias : [alias];

            for (const lng of alias) {
                allLanguages[lng] = allGrammars.indexOf(find)

                // aliasTitles only names the aliases whose title differs from the language's,
                // so an alias missing from it takes the language title rather than no title:
                // an empty one drops the alias out of SyntaxHighlighter::languages().
                const aliasTitles = components.languages[name].aliasTitles
                languageNames[lng] = (aliasTitles && aliasTitles[lng]) || components.languages[name].title
            }
        }
    })

    var final = {
        patterns: allPatterns,
        grammars: allGrammars,
        languages: allLanguages
    }

    const chunks = [];

    const writeUint16 = i => chunks.push(new Uint16Array([i]))
    const writeUint8 = i => chunks.push(new Uint8Array([i]))
    const writeString = str => {
        // one byte per code unit truncated anything above U+00FF; the length is the byte
        // count, which is what the readers advance by
        const bytes = new TextEncoder().encode(str)
        if (bytes.length < 253) {
            writeUint8(bytes.length)
        } else {
            writeUint8(254 & 0xFF)
            writeUint8(bytes.length & 0xFF)
            writeUint8((bytes.length >> 8) & 0xFF)
            writeUint8((bytes.length >> 16) & 0xFF)
        }
        chunks.push(bytes)
    }

    // Patterns
    writeUint16(allPatterns.length)

    allPatterns.forEach(pattern => {
        writeString(pattern)
    })

    // Grammars
    writeUint16(allGrammars.length)

    for (var i = 0; i < allGrammars.length; i++) {
        writeUint8(Object.keys(allGrammars[i]).length)

        Object.keys(allGrammars[i]).forEach(name => {
            writeString(name)
            writeUint8(allGrammars[i][name].length)
            allGrammars[i][name].forEach(id => {
                writeUint16(id)
            })
        })
    }

    // Languages
    writeUint16(Object.keys(allLanguages).length)

    Object.keys(allLanguages).forEach(name => {
        writeString(name)
        if (languageNames[name]) {
            writeString(languageNames[name])
        } else {
            writeString("")
        }
        writeUint16(allLanguages[name])
    })

    const blob = new Blob(chunks, {type: 'application/octet-binary'});
    console.log(blob)
    return blob;
}

async function saveBlob(blob, filename) {
    console.log(`Saving ${blob} to ${filename}`)
    const buffer = Buffer.from(await blob.arrayBuffer())
    fs.writeFileSync(filename, buffer)
}

const filepath = "libprisma/grammars.dat";

generate().then(blob => {
    saveBlob(blob, filepath).then(() => {
        console.log("Done! Saved to " + filepath)
    })
})
