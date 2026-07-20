package org.telegram.ui.Components;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Converts a LaTeX (math) source string into a compact, readable single-line
 * preview using Unicode where possible. This is intentionally lossy: it is meant
 * for inline previews, not faithful rendering. When there is no good Unicode
 * representation, things degrade to a plain function-like form, e.g. sqrt(x).
 */
public class LatexInliner {

    // \command -> unicode symbol
    private static final HashMap<String, String> SYMBOLS = new HashMap<>();
    // \command that should keep its name (with parentheses preserved), e.g. \sin -> sin
    private static final HashSet<String> FUNCTIONS = new HashSet<>();
    // wrappers whose content is kept but the command itself dropped
    private static final HashSet<String> KEEP_CONTENT = new HashSet<>();
    // per-character maps for ^ and _
    private static final HashMap<Character, Character> SUPER = new HashMap<>();
    private static final HashMap<Character, Character> SUB = new HashMap<>();
    // \mathbb letters that exist as single BMP codepoints
    private static final HashMap<Character, Character> BLACKBOARD = new HashMap<>();

    // lazily populated on first inlineLatex() call so the (large) tables cost
    // nothing at app startup unless LaTeX is actually inlined.
    private static volatile boolean initialized;

    private static void ensureInit() {
        if (initialized) {
            return;
        }
        synchronized (LatexInliner.class) {
            if (initialized) {
                return;
            }
            buildTables();
            initialized = true;
        }
    }

    private static void buildTables() {
        // --- lowercase greek ---
        SYMBOLS.put("alpha", "α"); SYMBOLS.put("beta", "β"); SYMBOLS.put("gamma", "γ");
        SYMBOLS.put("delta", "δ"); SYMBOLS.put("epsilon", "ε"); SYMBOLS.put("varepsilon", "ε");
        SYMBOLS.put("zeta", "ζ"); SYMBOLS.put("eta", "η"); SYMBOLS.put("theta", "θ");
        SYMBOLS.put("vartheta", "ϑ"); SYMBOLS.put("iota", "ι"); SYMBOLS.put("kappa", "κ");
        SYMBOLS.put("lambda", "λ"); SYMBOLS.put("mu", "μ"); SYMBOLS.put("nu", "ν");
        SYMBOLS.put("xi", "ξ"); SYMBOLS.put("omicron", "ο"); SYMBOLS.put("pi", "π");
        SYMBOLS.put("varpi", "ϖ"); SYMBOLS.put("rho", "ρ"); SYMBOLS.put("varrho", "ϱ");
        SYMBOLS.put("sigma", "σ"); SYMBOLS.put("varsigma", "ς"); SYMBOLS.put("tau", "τ");
        SYMBOLS.put("upsilon", "υ"); SYMBOLS.put("phi", "φ"); SYMBOLS.put("varphi", "ϕ");
        SYMBOLS.put("chi", "χ"); SYMBOLS.put("psi", "ψ"); SYMBOLS.put("omega", "ω");
        // --- uppercase greek ---
        SYMBOLS.put("Gamma", "Γ"); SYMBOLS.put("Delta", "Δ"); SYMBOLS.put("Theta", "Θ");
        SYMBOLS.put("Lambda", "Λ"); SYMBOLS.put("Xi", "Ξ"); SYMBOLS.put("Pi", "Π");
        SYMBOLS.put("Sigma", "Σ"); SYMBOLS.put("Upsilon", "Υ"); SYMBOLS.put("Phi", "Φ");
        SYMBOLS.put("Psi", "Ψ"); SYMBOLS.put("Omega", "Ω");

        // --- operators / relations ---
        SYMBOLS.put("times", "×"); SYMBOLS.put("div", "÷"); SYMBOLS.put("pm", "±");
        SYMBOLS.put("mp", "∓"); SYMBOLS.put("cdot", "·"); SYMBOLS.put("ast", "∗");
        SYMBOLS.put("star", "⋆"); SYMBOLS.put("circ", "∘"); SYMBOLS.put("bullet", "•");
        SYMBOLS.put("oplus", "⊕"); SYMBOLS.put("otimes", "⊗"); SYMBOLS.put("odot", "⊙");
        SYMBOLS.put("leq", "≤"); SYMBOLS.put("le", "≤"); SYMBOLS.put("geq", "≥");
        SYMBOLS.put("ge", "≥"); SYMBOLS.put("neq", "≠"); SYMBOLS.put("ne", "≠");
        SYMBOLS.put("approx", "≈"); SYMBOLS.put("equiv", "≡"); SYMBOLS.put("sim", "∼");
        SYMBOLS.put("simeq", "≃"); SYMBOLS.put("cong", "≅"); SYMBOLS.put("propto", "∝");
        SYMBOLS.put("ll", "≪"); SYMBOLS.put("gg", "≫"); SYMBOLS.put("doteq", "≐");

        // --- big operators ---
        SYMBOLS.put("sum", "∑"); SYMBOLS.put("prod", "∏"); SYMBOLS.put("coprod", "∐");
        SYMBOLS.put("int", "∫"); SYMBOLS.put("iint", "∬"); SYMBOLS.put("iiint", "∭");
        SYMBOLS.put("oint", "∮"); SYMBOLS.put("bigcup", "⋃"); SYMBOLS.put("bigcap", "⋂");
        SYMBOLS.put("bigvee", "⋁"); SYMBOLS.put("bigwedge", "⋀");

        // --- sets / logic ---
        SYMBOLS.put("in", "∈"); SYMBOLS.put("notin", "∉"); SYMBOLS.put("ni", "∋");
        SYMBOLS.put("subset", "⊂"); SYMBOLS.put("subseteq", "⊆"); SYMBOLS.put("supset", "⊃");
        SYMBOLS.put("supseteq", "⊇"); SYMBOLS.put("cup", "∪"); SYMBOLS.put("cap", "∩");
        SYMBOLS.put("setminus", "∖"); SYMBOLS.put("emptyset", "∅"); SYMBOLS.put("varnothing", "∅");
        SYMBOLS.put("forall", "∀"); SYMBOLS.put("exists", "∃"); SYMBOLS.put("nexists", "∄");
        SYMBOLS.put("neg", "¬"); SYMBOLS.put("lnot", "¬"); SYMBOLS.put("land", "∧");
        SYMBOLS.put("wedge", "∧"); SYMBOLS.put("lor", "∨"); SYMBOLS.put("vee", "∨");
        SYMBOLS.put("implies", "⟹"); SYMBOLS.put("impliedby", "⟸"); SYMBOLS.put("iff", "⟺");
        SYMBOLS.put("therefore", "∴"); SYMBOLS.put("because", "∵");

        // --- blackboard / common named sets ---
        SYMBOLS.put("Re", "ℜ"); SYMBOLS.put("Im", "ℑ"); SYMBOLS.put("aleph", "ℵ");
        SYMBOLS.put("hbar", "ℏ"); SYMBOLS.put("ell", "ℓ"); SYMBOLS.put("wp", "℘");

        // --- arrows ---
        SYMBOLS.put("rightarrow", "→"); SYMBOLS.put("to", "→"); SYMBOLS.put("gets", "←");
        SYMBOLS.put("leftarrow", "←"); SYMBOLS.put("leftrightarrow", "↔");
        SYMBOLS.put("Rightarrow", "⇒"); SYMBOLS.put("Leftarrow", "⇐");
        SYMBOLS.put("Leftrightarrow", "⇔"); SYMBOLS.put("mapsto", "↦");
        SYMBOLS.put("uparrow", "↑"); SYMBOLS.put("downarrow", "↓");
        SYMBOLS.put("longrightarrow", "⟶"); SYMBOLS.put("longleftarrow", "⟵");

        // --- calculus / misc symbols ---
        SYMBOLS.put("infty", "∞"); SYMBOLS.put("partial", "∂"); SYMBOLS.put("nabla", "∇");
        SYMBOLS.put("angle", "∠"); SYMBOLS.put("perp", "⊥"); SYMBOLS.put("parallel", "∥");
        SYMBOLS.put("prime", "′"); SYMBOLS.put("degree", "°");
        SYMBOLS.put("dots", "…"); SYMBOLS.put("ldots", "…"); SYMBOLS.put("cdots", "⋯");
        SYMBOLS.put("vdots", "⋮"); SYMBOLS.put("ddots", "⋱"); SYMBOLS.put("dagger", "†");
        SYMBOLS.put("ddagger", "‡"); SYMBOLS.put("triangle", "△"); SYMBOLS.put("square", "□");
        SYMBOLS.put("checkmark", "✓"); SYMBOLS.put("clubsuit", "♣"); SYMBOLS.put("diamondsuit", "♦");
        SYMBOLS.put("heartsuit", "♥"); SYMBOLS.put("spadesuit", "♠"); SYMBOLS.put("flat", "♭");
        SYMBOLS.put("sharp", "♯"); SYMBOLS.put("natural", "♮");

        // --- spacing / escapes that become nothing or a space ---
        SYMBOLS.put(",", " "); SYMBOLS.put(";", " "); SYMBOLS.put(":", " ");
        SYMBOLS.put("!", ""); SYMBOLS.put("quad", "  "); SYMBOLS.put("qquad", "    ");
        SYMBOLS.put("\\", "\n");
        SYMBOLS.put("%", "%"); SYMBOLS.put("&", "&"); SYMBOLS.put("#", "#");
        SYMBOLS.put("$", "$"); SYMBOLS.put("_", "_"); SYMBOLS.put("{", "{"); SYMBOLS.put("}", "}");

        FUNCTIONS.addAll(Arrays.asList(
            "sin", "cos", "tan", "cot", "sec", "csc",
            "sinh", "cosh", "tanh", "coth",
            "arcsin", "arccos", "arctan",
            "log", "ln", "lg", "exp",
            "lim", "limsup", "liminf", "max", "min", "sup", "inf",
            "det", "dim", "ker", "deg", "gcd", "hom", "arg", "mod", "bmod",
            "Pr"
        ));

        KEEP_CONTENT.addAll(Arrays.asList(
            "text", "textrm", "textbf", "textit", "texttt", "textsf",
            "mathrm", "mathbf", "mathit", "mathtt", "mathsf", "mathcal",
            "mathfrak", "operatorname", "boldsymbol", "bm",
            "left", "right", "big", "Big", "bigg", "Bigg", "displaystyle",
            "textstyle", "scriptstyle", "limits", "nolimits"
        ));

        putPairs(SUPER, "0123456789+-=()ni", "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ⁿⁱ");
        putPairs(SUPER, "abcdefghijklmnoprstuvwxyz", "ᵃᵇᶜᵈᵉᶠᵍʰⁱʲᵏˡᵐⁿᵒᵖʳˢᵗᵘᵛʷˣʸᶻ");
        putPairs(SUB, "0123456789+-=()aehijklmnoprstuvx", "₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎ₐₑₕᵢⱼₖₗₘₙₒₚᵣₛₜᵤᵥₓ");
        putPairs(BLACKBOARD, "CHNPQRZ", "ℂℍℕℙℚℝℤ");
    }

    private static void putPairs(HashMap<Character, Character> map, String from, String to) {
        for (int i = 0; i < from.length() && i < to.length(); i++) {
            map.put(from.charAt(i), to.charAt(i));
        }
    }

    public static String inlineLatex(String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        ensureInit();
        source = source.trim();
        return process(source, new int[]{0}, false);
    }

    /**
     * Processes the source starting at pos[0]. When stopAtBrace is true, stops
     * (and consumes) at the matching closing brace; otherwise runs to end.
     */
    private static String process(String s, int[] pos, boolean stopAtBrace) {
        final StringBuilder out = new StringBuilder();
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c == '}') {
                pos[0]++; // consume
                if (stopAtBrace) {
                    break;
                }
                // stray closing brace -> ignore
                continue;
            }
            if (c == '{') {
                pos[0]++;
                out.append(process(s, pos, true));
                continue;
            }
            if (c == '\\') {
                out.append(readCommand(s, pos));
                continue;
            }
            if (c == '^') {
                pos[0]++;
                out.append(applyScript(readArgument(s, pos), true));
                continue;
            }
            if (c == '_') {
                pos[0]++;
                out.append(applyScript(readArgument(s, pos), false));
                continue;
            }
            if (c == '&' || c == '~') { // alignment tab / nbsp
                out.append(' ');
                pos[0]++;
                continue;
            }
            out.append(c);
            pos[0]++;
        }
        return out.toString();
    }

    /** Reads one argument: a {group}, a \command, or a single character. */
    private static String readArgument(String s, int[] pos) {
        // skip whitespace
        while (pos[0] < s.length() && s.charAt(pos[0]) == ' ') pos[0]++;
        if (pos[0] >= s.length()) {
            return "";
        }
        char c = s.charAt(pos[0]);
        if (c == '{') {
            pos[0]++;
            return process(s, pos, true);
        }
        if (c == '\\') {
            return readCommand(s, pos);
        }
        pos[0]++;
        return String.valueOf(c);
    }

    /** At a backslash. Reads the command name and returns its inlined form. */
    private static String readCommand(String s, int[] pos) {
        pos[0]++; // skip '\'
        if (pos[0] >= s.length()) {
            return "";
        }
        char first = s.charAt(pos[0]);
        // non-letter command (escaped char / spacing), is exactly one char
        if (!Character.isLetter(first)) {
            String key = String.valueOf(first);
            pos[0]++;
            String sym = SYMBOLS.get(key);
            return sym != null ? sym : key;
        }
        // letter command name
        int start = pos[0];
        while (pos[0] < s.length() && Character.isLetter(s.charAt(pos[0]))) {
            pos[0]++;
        }
        String name = s.substring(start, pos[0]);

        switch (name) {
            case "frac":
            case "dfrac":
            case "tfrac": {
                String a = readArgument(s, pos);
                String b = readArgument(s, pos);
                return wrapIfNeeded(a) + "/" + wrapIfNeeded(b);
            }
            case "sqrt": {
                // optional [n] index
                String index = null;
                skipSpaces(s, pos);
                if (pos[0] < s.length() && s.charAt(pos[0]) == '[') {
                    int close = s.indexOf(']', pos[0]);
                    if (close > 0) {
                        index = inlineLatex(s.substring(pos[0] + 1, close));
                        pos[0] = close + 1;
                    }
                }
                String radicand = readArgument(s, pos);
                String prefix = index != null ? superscriptOrRaw(index) : "";
                return prefix + "√" + wrapIfNeeded(radicand);
            }
            case "overline":
            case "bar":
                return readArgument(s, pos); // best-effort: drop the bar
            case "hat":
                return readArgument(s, pos) + "̂"; // combining circumflex
            case "vec":
                return readArgument(s, pos) + "⃗"; // combining right arrow
            case "dot":
                return readArgument(s, pos) + "̇";
            case "tilde":
                return readArgument(s, pos) + "̃";
            case "begin":
            case "end":
                // consume the environment name argument and drop it
                readArgument(s, pos);
                return "";
            case "mathbb": {
                // blackboard-bold: map the letters that have a single Unicode codepoint
                String arg = readArgument(s, pos);
                StringBuilder bb = new StringBuilder();
                for (int i = 0; i < arg.length(); i++) {
                    char ch = arg.charAt(i);
                    Character bbc = BLACKBOARD.get(ch);
                    bb.append(bbc != null ? bbc : ch);
                }
                return bb.toString();
            }
        }

        if (KEEP_CONTENT.contains(name)) {
            // \left( \right) etc. take the next single token literally;
            // \text{...} takes a group. readArgument handles both.
            return readArgument(s, pos);
        }
        if (FUNCTIONS.contains(name)) {
            return name;
        }
        String sym = SYMBOLS.get(name);
        if (sym != null) {
            return sym;
        }
        // unknown command: drop the backslash, keep the name (less distracting)
        return name;
    }

    private static void skipSpaces(String s, int[] pos) {
        while (pos[0] < s.length() && s.charAt(pos[0]) == ' ') pos[0]++;
    }

    /** Tries to render text as super/subscript Unicode; falls back to ^(..)/_(..). */
    private static String applyScript(String text, boolean sup) {
        if (text.isEmpty()) {
            return "";
        }
        HashMap<Character, Character> map = sup ? SUPER : SUB;
        StringBuilder mapped = new StringBuilder();
        boolean ok = true;
        for (int i = 0; i < text.length(); i++) {
            Character m = map.get(text.charAt(i));
            if (m == null) {
                ok = false;
                break;
            }
            mapped.append((char) m);
        }
        if (ok) {
            return mapped.toString();
        }
        return (sup ? "^" : "_") + wrapIfNeeded(text);
    }

    private static String superscriptOrRaw(String text) {
        StringBuilder mapped = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            Character m = SUPER.get(text.charAt(i));
            if (m == null) {
                return ""; // give up on the index prefix rather than show noise
            }
            mapped.append((char) m);
        }
        return mapped.toString();
    }

    /** Wraps in parentheses when the content isn't a single visual unit. */
    private static String wrapIfNeeded(String s) {
        if (s.length() <= 1) {
            return s;
        }
        // already parenthesized / bracketed
        char f = s.charAt(0), l = s.charAt(s.length() - 1);
        if ((f == '(' && l == ')') || (f == '[' && l == ']')) {
            return s;
        }
        return "(" + s + ")";
    }
}
