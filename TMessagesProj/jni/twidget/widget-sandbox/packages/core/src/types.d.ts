// Bundles produced by the Vite plugin in vite.ts (esbuild IIFE builds).
declare module 'virtual:sandbox-bundle' {
  const code: string;
  export default code;
}
declare module 'virtual:renderer-bundle' {
  const code: string;
  export default code;
}
// linkedom's selector engine, reached only through the build-time redirect in
// scripts/sandbox-build.ts (not a direct dependency, hence no types).
declare module 'css-select';

// Renderer transport, set by esbuild `define` (scripts/sandbox-build.ts).
declare const __TW_TRANSPORT__: 'port' | 'webkit';
