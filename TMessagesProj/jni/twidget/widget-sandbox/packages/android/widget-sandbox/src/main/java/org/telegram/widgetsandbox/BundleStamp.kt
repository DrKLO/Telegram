// The stamp packages/core/scripts/build-native-bundles.ts writes on the first line of every
// bundle, from VERSION.json (packages/core/scripts/version.ts): this glue runs only bundles of
// its own protocol, profile and sdk (WidgetSandboxVersion, generated from the same file), the
// way it runs only a `.qbc` compiled from the source next to it. The release itself is not
// compared. Twins: BundleStamp.swift, bundle_stamp.cpp; the format's tests: version.test.ts.

package org.telegram.widgetsandbox

data class BundleStamp(val version: String, val protocol: Int, val profile: Int, val sdk: Int) {
    override fun toString() = "widget-sandbox $version (protocol $protocol, profile $profile, sdk $sdk)"

    /** The line itself, as build-native-bundles.ts writes it. */
    val line: String get() = "/* widget-sandbox version=$version protocol=$protocol profile=$profile sdk=$sdk */"

    companion object {
        /** This glue's own. */
        val GLUE = BundleStamp(WidgetSandboxVersion.VERSION, WidgetSandboxVersion.PROTOCOL, WidgetSandboxVersion.PROFILE, WidgetSandboxVersion.SDK)

        private const val PREFIX = "/* widget-sandbox "
        private const val SUFFIX = " */"

        /**
         * The stamp on the first line of a bundle's source; null when there is none (a bundle built before
         * VERSION.json) or it is malformed: the prefix, then `key=value` pairs separated by one space, unknown keys
         * ignored, `version` non-empty and the three numbers 1 to 6 ASCII digits.
         */
        fun parse(source: String): BundleStamp? {
            val nl = source.indexOf('\n')
            val line = if (nl < 0) source else source.substring(0, nl)
            if (line.length < PREFIX.length + SUFFIX.length || !line.startsWith(PREFIX) || !line.endsWith(SUFFIX)) return null
            val fields = HashMap<String, String>()
            for (pair in line.substring(PREFIX.length, line.length - SUFFIX.length).split(' ')) {
                val eq = pair.indexOf('=')
                if (eq <= 0) return null
                fields[pair.substring(0, eq)] = pair.substring(eq + 1)
            }
            fun number(key: String): Int? {
                val s = fields[key] ?: return null
                if (s.length !in 1..6 || !s.all { it in '0'..'9' }) return null
                return s.toInt()
            }
            val version = fields["version"]?.takeIf { it.isNotEmpty() } ?: return null
            return BundleStamp(version, number("protocol") ?: return null, number("profile") ?: return null, number("sdk") ?: return null)
        }

        /** Why this glue refuses a bundle (`bundle-mismatch`), or null when it runs it. `name` is the bundle's file name. */
        fun refusal(source: String, name: String): String? {
            val fix = "the bundles and the glue must come from one release (rebuild the bundles with pnpm build:android)"
            val stamp = parse(source) ?: return "$name has no version stamp, this glue is $GLUE: $fix"
            if (stamp.protocol == GLUE.protocol && stamp.profile == GLUE.profile && stamp.sdk == GLUE.sdk) return null
            return "$name is $stamp, this glue is $GLUE: $fix"
        }
    }
}
