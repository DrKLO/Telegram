/* eslint-disable no-undef */
quickjsEmscriptenInit.inits.push((extension) => {
  extension["getWasmMemory"] = function () {
    return wasmMemory
  }
})
