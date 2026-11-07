import type { SandboxPolicy } from '@telegram/widget-sandbox/shared/protocol';

const fromQuery = new URLSearchParams(location.search).get('policy');
const fromEnv = (import.meta as unknown as { env?: Record<string, string | undefined> }).env?.VITE_TW_POLICY;

export const WIDGET_POLICY: SandboxPolicy = (fromQuery ?? fromEnv) === 'compat' ? 'compat' : 'strict';

/** Opt-in sandbox features for every widget the dev app creates: `?features=webAnimations` or VITE_TW_FEATURES (comma-separated). */
const featuresQuery = new URLSearchParams(location.search).get('features');
const featuresEnv = (import.meta as unknown as { env?: Record<string, string | undefined> }).env?.VITE_TW_FEATURES;
export const WIDGET_FEATURES: string[] = (featuresQuery ?? featuresEnv ?? '').split(',').map((f) => f.trim()).filter(Boolean);
