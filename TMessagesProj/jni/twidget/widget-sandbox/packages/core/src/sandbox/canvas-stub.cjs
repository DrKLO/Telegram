// linkedom optionally requires the native `canvas` package. Inside the sandbox
// canvas is provided by our recording proxy instead (see canvas.ts).
module.exports = { createCanvas: () => ({ width: 300, height: 150, getContext: () => null, toDataURL: () => '' }) };
