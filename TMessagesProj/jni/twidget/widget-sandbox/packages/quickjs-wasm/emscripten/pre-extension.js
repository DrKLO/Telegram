/* eslint-disable no-undef */
// quickjs-emscripten code injected into emscripten-module.js
// We use this to expose and patch up issues with Emscripten's source map handling...
function quickjsEmscriptenInit(debugLog) {
  const log = debugLog || function () {}
  // Everything goes in a function so we can defer running until other variables
  // are initialized in case they change.
  const extension = { log }
  for (const init of quickjsEmscriptenInit.inits) {
    init(extension)
  }
  Module["quickJSEmscriptenExtensions"] = extension
  return extension
}
quickjsEmscriptenInit.inits = []
Module["quickjsEmscriptenInit"] = quickjsEmscriptenInit
