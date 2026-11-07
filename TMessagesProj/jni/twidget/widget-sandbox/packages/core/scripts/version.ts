// VERSION.json at the repository root: the framework release and the integer versions a
// client's glue and the JS bundles must agree on. build-native-bundles.ts stamps them into
// the first line of every bundle and writes the platform constants the glue compares the stamp
// with; tests/unit/version.test.ts checks that everything that repeats a number agrees.
//
//   version   the release, semver; the tag is `v<version>`; also root package.json,
//             packages/core/package.json and the Android library's build.gradle.kts
//   protocol  the wire between the glue and the bundles (docs/protocol.md: the config, the
//             host calls, the messages, the validator's API): changes when a bundle needs a
//             glue it cannot run with
//   profile   the widget profile (packages/core/profile.json `profile`, PROFILE_VERSION,
//             TelegramWidget.profile)
//   sdk       the widget SDK (docs/sdk-v1.md)
//
// A native glue refuses a bundle that carries no stamp or whose protocol, profile or sdk
// differs from its own constants (the release is not compared: a bundle of another release with the same three numbers runs).

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/** The repository root. */
export const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..');

export interface FrameworkVersion {
  version: string;
  protocol: number;
  profile: number;
  sdk: number;
}

const SEMVER = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-[0-9A-Za-z.-]+)?$/;
const INTEGERS = ['protocol', 'profile', 'sdk'] as const;

/** VERSION.json, checked: a semver release and three positive integers, nothing else. */
export function readVersion(file = path.join(root, 'VERSION.json')): FrameworkVersion {
  const v = JSON.parse(fs.readFileSync(file, 'utf8')) as Record<string, unknown>;
  const keys = Object.keys(v).sort().join(',');
  if (keys !== 'profile,protocol,sdk,version') throw new Error(`${file}: expected exactly version, protocol, profile and sdk, found ${keys}`);
  if (typeof v.version !== 'string' || !SEMVER.test(v.version)) throw new Error(`${file}: version ${JSON.stringify(v.version)} is not semver`);
  for (const k of INTEGERS) {
    if (!Number.isInteger(v[k]) || (v[k] as number) < 1 || (v[k] as number) > 999_999) throw new Error(`${file}: ${k} ${JSON.stringify(v[k])} is not a positive integer`);
  }
  return v as unknown as FrameworkVersion;
}

/** The other files that repeat a number of VERSION.json, with what they say (null: not found). */
export function repeatedVersions(): { file: string; field: keyof FrameworkVersion; value: string | number | null }[] {
  const json = (f: string) => JSON.parse(fs.readFileSync(path.join(root, f), 'utf8'));
  const gradle = fs.readFileSync(path.join(root, 'packages/android/widget-sandbox/build.gradle.kts'), 'utf8');
  return [
    { file: 'package.json', field: 'version', value: json('package.json').version ?? null },
    { file: 'packages/core/package.json', field: 'version', value: json('packages/core/package.json').version ?? null },
    { file: 'packages/android/widget-sandbox/build.gradle.kts', field: 'version', value: /^version = "([^"]*)"$/m.exec(gradle)?.[1] ?? null },
    { file: 'packages/core/profile.json', field: 'profile', value: json('packages/core/profile.json').profile ?? null },
  ];
}

/** Throws when a file that repeats a number of VERSION.json says something else (bump them together). */
export function assertVersionsAgree(v: FrameworkVersion = readVersion()): void {
  const wrong = repeatedVersions().filter((r) => r.value !== v[r.field]);
  if (wrong.length) {
    throw new Error(`VERSION.json says ${JSON.stringify(v)}, but ${wrong.map((r) => `${r.file} has ${r.field} ${JSON.stringify(r.value)}`).join('; ')}: bump them together`);
  }
}

/**
 * The first line of every bundle the native packages ship. Each glue parses it before it
 * evaluates the bundle (BundleStamp.swift, BundleStamp.kt, bundle_stamp.cpp): the prefix,
 * then `key=value` pairs separated by one space; unknown keys are ignored, a missing one
 * makes the stamp invalid.
 */
export function bundleStamp(v: FrameworkVersion): string {
  return `/* widget-sandbox version=${v.version} protocol=${v.protocol} profile=${v.profile} sdk=${v.sdk} */`;
}

/** The stamp of a bundle's source, or null when its first line is not one (the twin of the glue's parser). */
export function parseStamp(source: string): FrameworkVersion | null {
  const nl = source.indexOf('\n');
  const line = nl < 0 ? source : source.slice(0, nl);
  const prefix = '/* widget-sandbox ', suffix = ' */';
  if (!line.startsWith(prefix) || !line.endsWith(suffix) || line.length < prefix.length + suffix.length) return null;
  const fields = new Map<string, string>();
  for (const pair of line.slice(prefix.length, line.length - suffix.length).split(' ')) {
    const eq = pair.indexOf('=');
    if (eq <= 0) return null;
    fields.set(pair.slice(0, eq), pair.slice(eq + 1));
  }
  const version = fields.get('version');
  if (!version) return null;
  const out: FrameworkVersion = { version, protocol: 0, profile: 0, sdk: 0 };
  for (const k of INTEGERS) {
    const s = fields.get(k);
    if (!s || !/^\d{1,6}$/.test(s)) return null;
    out[k] = Number(s);
  }
  return out;
}

const HEADER = 'Generated by packages/core/scripts/build-native-bundles.ts from VERSION.json. Do not edit:\n' +
  'change VERSION.json and run `corepack pnpm build:ios`, `build:android`, `build:desktop`.';

/** The constants each glue compares a bundle's stamp with, per native target: repository path → content. */
export function platformConstants(v: FrameworkVersion): Record<'ios' | 'android' | 'desktop', { path: string; text: string }> {
  const lines = (prefix: string) => HEADER.split('\n').map((l) => `${prefix} ${l}`).join('\n');
  return {
    ios: {
      path: 'packages/ios/Sources/WidgetSandbox/WidgetSandboxVersion.swift',
      text: `${lines('//')}

/// The framework release and the versions this glue and its bundles share (VERSION.json). A bundle stamped with
/// another protocol, profile or sdk is refused (\`bundle-mismatch\`, BundleStamp.swift).
public enum WidgetSandboxVersion {
    /// The release (semver; the tag is \`v\` + this).
    public static let version = "${v.version}"
    /// The wire between this glue and the bundles (docs/protocol.md).
    public static let protocolVersion = ${v.protocol}
    /// The widget profile this glue enforces (\`TelegramWidget.profile\`, docs/profile-v1.md).
    public static let profileVersion = ${v.profile}
    /// The widget SDK (docs/sdk-v1.md).
    public static let sdkVersion = ${v.sdk}
}
`,
    },
    android: {
      path: 'packages/android/widget-sandbox/src/main/java/org/telegram/widgetsandbox/WidgetSandboxVersion.kt',
      text: `${lines('//')}

package org.telegram.widgetsandbox

/**
 * The framework release and the versions this glue and its bundles share (VERSION.json). A bundle stamped with
 * another protocol, profile or sdk is refused (\`bundle-mismatch\`, BundleStamp.kt).
 */
object WidgetSandboxVersion {
    /** The release (semver; the tag is \`v\` + this). */
    const val VERSION = "${v.version}"

    /** The wire between this glue and the bundles (docs/protocol.md). */
    const val PROTOCOL = ${v.protocol}

    /** The widget profile this glue enforces (\`TelegramWidget.profile\`, docs/profile-v1.md). */
    const val PROFILE = ${v.profile}

    /** The widget SDK (docs/sdk-v1.md). */
    const val SDK = ${v.sdk}
}
`,
    },
    desktop: {
      path: 'packages/desktop/sandbox/include/twidget/version.h',
      text: `${lines('//')}
#pragma once

namespace twidget::version {

/// The framework release and the versions this glue and its bundles share (VERSION.json). A bundle stamped with
/// another protocol, profile or sdk is refused (\`bundle-mismatch\`, bundle_stamp.h).
inline constexpr const char* VERSION = "${v.version}";
/// The wire between this glue and the bundles (docs/protocol.md).
inline constexpr int PROTOCOL = ${v.protocol};
/// The widget profile this glue enforces (\`TelegramWidget.profile\`, docs/profile-v1.md).
inline constexpr int PROFILE = ${v.profile};
/// The widget SDK (docs/sdk-v1.md).
inline constexpr int SDK = ${v.sdk};

} // namespace twidget::version
`,
    },
  };
}
