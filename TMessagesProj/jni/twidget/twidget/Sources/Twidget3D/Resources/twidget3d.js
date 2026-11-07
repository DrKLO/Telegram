(function (host) {
  'use strict';
  if (typeof host !== 'function' || typeof document === 'undefined') return;
  const HEADER = 160, RECORD = 40, MAX_LIGHTS = 4;
  const surfaces = new WeakMap();
  let nextToken = 1;

  const surfaceOf = element => {
    let surface = surfaces.get(element);
    if (surface) return surface;
    const token = nextToken++;
    const ok = host('bind', token) === 1;
    surface = { token, ok, element, size: [0, 0, 1], sizeAt: -1 };
    surfaces.set(element, surface);
    if (ok) element.setAttribute('data-tw3d', String(token));
    return surface;
  };

  const retag = (surface, element) => {
    if (surface.element === element) return;
    if (surface.element && surface.element.getAttribute && surface.element.getAttribute('data-tw3d') === String(surface.token)) surface.element.removeAttribute('data-tw3d');
    surface.element = element;
    surfaces.set(element, surface);
    if (surface.ok) element.setAttribute('data-tw3d', String(surface.token));
  };

  const connected = element => {
    if (!element) return false;
    if (typeof element.isConnected === 'boolean') return element.isConnected;
    return document.documentElement ? document.documentElement.contains(element) : false;
  };

  const measure = surface => {
    const now = typeof performance !== 'undefined' ? performance.now() : Date.now();
    if (surface.sizeAt >= 0 && now - surface.sizeAt < 100) return surface.size;
    surface.sizeAt = now;
    const reported = String(host('size', surface.token) || '').split(',').map(Number);
    if (reported.length === 3 && reported[0] > 0 && reported[1] > 0) {
      surface.size = reported;
      return reported;
    }
    const dpr = (typeof devicePixelRatio === 'number' && devicePixelRatio > 0) ? devicePixelRatio : 1;
    let width = 0, height = 0;
    try {
      const rect = surface.element.getBoundingClientRect();
      width = rect.width; height = rect.height;
    } catch (e) {}
    if (!(width > 0 && height > 0)) {
      width = Number(surface.element.width) || 300;
      height = Number(surface.element.height) || 150;
    }
    surface.size = [width, height, dpr];
    return surface.size;
  };

  const header = new Float32Array(HEADER);

  const GL = {
    DEPTH_BUFFER_BIT: 0x100, STENCIL_BUFFER_BIT: 0x400, COLOR_BUFFER_BIT: 0x4000,
    POINTS: 0, LINES: 1, LINE_LOOP: 2, LINE_STRIP: 3, TRIANGLES: 4, TRIANGLE_STRIP: 5, TRIANGLE_FAN: 6,
    ARRAY_BUFFER: 0x8892, ELEMENT_ARRAY_BUFFER: 0x8893, STATIC_DRAW: 0x88E4, DYNAMIC_DRAW: 0x88E8, STREAM_DRAW: 0x88E0,
    FLOAT: 0x1406, UNSIGNED_BYTE: 0x1401, UNSIGNED_SHORT: 0x1403, UNSIGNED_INT: 0x1405, BYTE: 0x1400, SHORT: 0x1402, INT: 0x1404,
    VERTEX_SHADER: 0x8B31, FRAGMENT_SHADER: 0x8B30, COMPILE_STATUS: 0x8B81, LINK_STATUS: 0x8B82, DELETE_STATUS: 0x8B80,
    DEPTH_TEST: 0x0B71, BLEND: 0x0BE2, CULL_FACE: 0x0B44, SCISSOR_TEST: 0x0C11, STENCIL_TEST: 0x0B90,
    TEXTURE_2D: 0x0DE1, TEXTURE0: 0x84C0, RGBA: 0x1908, RGB: 0x1907, NEAREST: 0x2600, LINEAR: 0x2601,
    TEXTURE_MIN_FILTER: 0x2801, TEXTURE_MAG_FILTER: 0x2800, TEXTURE_WRAP_S: 0x2802, TEXTURE_WRAP_T: 0x2803, CLAMP_TO_EDGE: 0x812F, REPEAT: 0x2901,
    SRC_ALPHA: 0x0302, ONE_MINUS_SRC_ALPHA: 0x0303, ONE: 1, ZERO: 0, LEQUAL: 0x0203, LESS: 0x0201,
    FRAMEBUFFER: 0x8D40, RENDERBUFFER: 0x8D41, FRAMEBUFFER_COMPLETE: 0x8CD5, NO_ERROR: 0, CONTEXT_LOST_WEBGL: 0x9242,
    VERSION: 0x1F02, VENDOR: 0x1F00, RENDERER: 0x1F01, SHADING_LANGUAGE_VERSION: 0x8B8C, MAX_TEXTURE_SIZE: 0x0D33,
    MAX_VERTEX_ATTRIBS: 0x8869, MAX_TEXTURE_IMAGE_UNITS: 0x8872, MAX_VIEWPORT_DIMS: 0x0D3A, VIEWPORT: 0x0BA2,
    HIGH_FLOAT: 0x8DF2, MEDIUM_FLOAT: 0x8DF1, UNPACK_FLIP_Y_WEBGL: 0x9240, UNPACK_PREMULTIPLY_ALPHA_WEBGL: 0x9241,
  };
  const unsupported = 'Telegram widgets draw 3D with the bundled three.js; custom WebGL shaders are not supported.';
  const noops = ('activeTexture attachShader bindAttribLocation bindBuffer bindFramebuffer bindRenderbuffer bindTexture blendColor blendEquation blendEquationSeparate blendFunc blendFuncSeparate bufferData bufferSubData clearDepth clearStencil colorMask compressedTexImage2D compressedTexSubImage2D copyTexImage2D copyTexSubImage2D cullFace deleteBuffer deleteFramebuffer deleteProgram deleteRenderbuffer deleteShader deleteTexture depthFunc depthMask depthRange detachShader disable disableVertexAttribArray drawArrays drawElements enable enableVertexAttribArray finish flush framebufferRenderbuffer framebufferTexture2D frontFace generateMipmap hint lineWidth pixelStorei polygonOffset renderbufferStorage sampleCoverage scissor stencilFunc stencilFuncSeparate stencilMask stencilMaskSeparate stencilOp stencilOpSeparate texImage2D texParameterf texParameteri texSubImage2D uniform1f uniform1fv uniform1i uniform1iv uniform2f uniform2fv uniform2i uniform2iv uniform3f uniform3fv uniform3i uniform3iv uniform4f uniform4fv uniform4i uniform4iv uniformMatrix2fv uniformMatrix3fv uniformMatrix4fv useProgram validateProgram vertexAttrib1f vertexAttrib1fv vertexAttrib2f vertexAttrib2fv vertexAttrib3f vertexAttrib3fv vertexAttrib4f vertexAttrib4fv vertexAttribPointer vertexAttribDivisor drawArraysInstanced drawElementsInstanced bindVertexArray deleteVertexArray drawBuffers').split(' ');

  class WebGLSubsetContext {
    constructor(canvas, type, attributes) {
      this.canvas = canvas;
      this._surface = surfaceOf(canvas);
      this._type = type;
      this._attributes = Object.assign({ alpha: true, antialias: true, depth: true, stencil: false, premultipliedAlpha: true, preserveDrawingBuffer: false }, attributes || {});
      this._clear = [0, 0, 0, 0];
      this._viewport = [0, 0, 0, 0];
      this._lastError = 0;
    }
    get drawingBufferWidth() { const s = measure(this._surface); return Math.round(s[0] * s[2]); }
    get drawingBufferHeight() { const s = measure(this._surface); return Math.round(s[1] * s[2]); }
    getContextAttributes() { return Object.assign({}, this._attributes); }
    isContextLost() { return false; }
    getError() { const e = this._lastError; this._lastError = 0; return e; }
    getExtension() { return null; }
    getSupportedExtensions() { return []; }
    getParameter(name) {
      switch (name) {
        case GL.VERSION: return this._type === 'webgl2' ? 'WebGL 2.0 (Telegram subset)' : 'WebGL 1.0 (Telegram subset)';
        case GL.SHADING_LANGUAGE_VERSION: return 'WebGL GLSL ES 1.0 (Telegram subset)';
        case GL.VENDOR: return 'Telegram';
        case GL.RENDERER: return 'Telegram Metal';
        case GL.MAX_TEXTURE_SIZE: return 4096;
        case GL.MAX_VERTEX_ATTRIBS: return 16;
        case GL.MAX_TEXTURE_IMAGE_UNITS: return 16;
        case GL.MAX_VIEWPORT_DIMS: return new Int32Array([4096, 4096]);
        case GL.VIEWPORT: return new Int32Array(this._viewport);
        default: return null;
      }
    }
    getShaderPrecisionFormat() { return { rangeMin: 127, rangeMax: 127, precision: 23 }; }
    clearColor(r, g, b, a) { this._clear = [Number(r) || 0, Number(g) || 0, Number(b) || 0, Number(a) || 0]; }
    clear(mask) {
      if (!(Number(mask) & GL.COLOR_BUFFER_BIT) || !this._surface.ok) return;
      header.fill(0);
      header[52] = this._clear[0]; header[53] = this._clear[1]; header[54] = this._clear[2]; header[55] = this._clear[3];
      header[94] = 0;
      header[95] = measure(this._surface)[2];
      host('frame', this._surface.token, header, HEADER);
    }
    viewport(x, y, w, h) { this._viewport = [x | 0, y | 0, w | 0, h | 0]; }
    createBuffer() { return {}; }
    createFramebuffer() { return {}; }
    createRenderbuffer() { return {}; }
    createTexture() { return {}; }
    createVertexArray() { return {}; }
    createShader(type) { return { type, source: '' }; }
    shaderSource(shader, source) { if (shader) shader.source = String(source); }
    compileShader() { if (typeof console !== 'undefined') console.warn(unsupported); }
    getShaderParameter(shader, name) { return name === GL.COMPILE_STATUS ? false : null; }
    getShaderInfoLog() { return unsupported; }
    getShaderSource(shader) { return shader ? shader.source : null; }
    createProgram() { return {}; }
    linkProgram() {}
    getProgramParameter(program, name) { return name === GL.LINK_STATUS ? false : null; }
    getProgramInfoLog() { return unsupported; }
    getAttribLocation() { return -1; }
    getUniformLocation() { return null; }
    checkFramebufferStatus() { return GL.FRAMEBUFFER_COMPLETE; }
    isEnabled() { return false; }
    readPixels(x, y, w, h, format, type, pixels) { if (pixels && typeof pixels.fill === 'function') pixels.fill(0); }
  }
  for (const key of Object.keys(GL)) WebGLSubsetContext.prototype[key] = GL[key];
  for (const name of noops) if (!(name in WebGLSubsetContext.prototype)) WebGLSubsetContext.prototype[name] = function () {};
  globalThis.WebGLRenderingContext = WebGLSubsetContext;
  globalThis.WebGL2RenderingContext = WebGLSubsetContext;

  const contexts = new WeakMap();
  const sample = document.createElement('canvas');
  let owner = sample;
  while (owner && !Object.prototype.hasOwnProperty.call(owner, 'getContext')) owner = Object.getPrototypeOf(owner);
  if (owner) {
    const original = owner.getContext;
    Object.defineProperty(owner, 'getContext', {
      configurable: true, writable: true,
      value(type, attributes) {
        const kind = String(type).toLowerCase();
        const existing = contexts.get(this);
        if (kind === 'webgl' || kind === 'webgl2' || kind === 'experimental-webgl') {
          if (existing) return existing.gl || null;
          const gl = new WebGLSubsetContext(this, kind, attributes);
          contexts.set(this, { gl });
          return gl;
        }
        if (existing && (existing.gl || existing.three)) return null;
        const result = original.call(this, type, attributes);
        if (result) contexts.set(this, { other: true });
        return result;
      },
    });
  }

  const installThree = T => {
    if (!T || typeof T !== 'object' || T.__tw3d || typeof T.Frustum !== 'function') return;
    T.__tw3d = true;
    const geometryIds = new WeakMap(), instanceIds = new WeakMap();
    let nextGeometry = 1, nextInstanceSet = 1;
    const shadowViewProjection = new T.Matrix4();
    const v1 = new T.Vector3(), v2 = new T.Vector3();

    const version = attribute => attribute ? attribute.version : -1;
    const floats = attribute => {
      if (!attribute) return null;
      if (!attribute.isInterleavedBufferAttribute && attribute.array instanceof Float32Array && attribute.array.length === attribute.count * attribute.itemSize) return attribute.array;
      const size = attribute.itemSize, out = new Float32Array(attribute.count * size);
      for (let i = 0; i < attribute.count; i++) for (let k = 0; k < size; k++) out[i * size + k] = attribute.getComponent(i, k);
      return out;
    };
    const indices = attribute => {
      if (!attribute) return null;
      const a = attribute.array;
      if (a instanceof Uint16Array || a instanceof Uint32Array) return a;
      return attribute.count > 65535 ? Uint32Array.from(a) : Uint16Array.from(a);
    };

    const materialType = m => m.isMeshBasicMaterial || m.isLineBasicMaterial || m.isLineDashedMaterial || m.isPointsMaterial || m.isSpriteMaterial ? 0
      : m.isMeshLambertMaterial ? 1 : m.isMeshPhongMaterial ? 2 : m.isMeshStandardMaterial ? 3 : m.isMeshNormalMaterial ? 4 : m.isMeshToonMaterial ? 5 : 1;

    const packMaterial = (m, out, o) => {
      const c = m.color, e = m.emissive, s = m.specular, ei = m.emissiveIntensity === undefined ? 1 : m.emissiveIntensity;
      out[o] = c ? c.r : 1; out[o + 1] = c ? c.g : 1; out[o + 2] = c ? c.b : 1; out[o + 3] = m.opacity;
      out[o + 4] = e ? e.r * ei : 0; out[o + 5] = e ? e.g * ei : 0; out[o + 6] = e ? e.b * ei : 0; out[o + 7] = materialType(m);
      out[o + 8] = s ? s.r : 0.0666; out[o + 9] = s ? s.g : 0.0666; out[o + 10] = s ? s.b : 0.0666; out[o + 11] = m.shininess === undefined ? 30 : m.shininess;
      out[o + 12] = m.roughness === undefined ? 1 : m.roughness;
      out[o + 13] = m.metalness === undefined ? 0 : m.metalness;
      out[o + 14] = (m.transparent ? 1 : 0) | (m.vertexColors ? 2 : 0) | (m.flatShading ? 4 : 0) | (m.wireframe ? 8 : 0) | ((m.side & 3) << 4) | (m.fog !== false ? 64 : 0) | (m.depthWrite ? 128 : 0) | (m.depthTest ? 256 : 0);
      out[o + 15] = m.size === undefined ? 1 : m.size;
    };

    class MetalRenderer {
      constructor(parameters) {
        const p = parameters || {};
        this.isWebGLRenderer = true;
        this.isMetalRenderer = true;
        this._explicit = !!p.canvas;
        this.domElement = p.canvas || document.createElement('canvas');
        if (!this._explicit) { try { this.domElement.style.display = 'block'; } catch (e) {} }
        if (!contexts.get(this.domElement)) contexts.set(this.domElement, { three: true });
        this._surface = surfaceOf(this.domElement);
        this.isSupported = this._surface.ok;
        this.shadowMap = { enabled: false, autoUpdate: true, needsUpdate: false, type: T.PCFShadowMap };
        this.info = { autoReset: true, render: { frame: 0, calls: 0, triangles: 0, points: 0, lines: 0 }, memory: { geometries: 0, textures: 0 }, programs: [], reset() {} };
        this.capabilities = { isWebGL2: true, maxTextures: 16, maxTextureSize: 4096, precision: 'highp', logarithmicDepthBuffer: false, getMaxAnisotropy: () => 1 };
        this.extensions = { get: () => null, has: () => false };
        this.properties = { get: () => ({}) };
        this.xr = { enabled: false, isPresenting: false, addEventListener() {}, removeEventListener() {}, setAnimationLoop() {} };
        this.outputColorSpace = T.SRGBColorSpace;
        this.toneMapping = T.NoToneMapping;
        this.toneMappingExposure = 1;
        this.useLegacyLights = false;
        this.autoClear = true; this.autoClearColor = true; this.autoClearDepth = true; this.autoClearStencil = true;
        this.sortObjects = true;
        this._clearColor = new T.Color(0, 0, 0);
        this._clearAlpha = p.alpha ? 0 : 1;
        this._buffer = new Float32Array(HEADER + RECORD * 256);
        this._pixelRatio = null;
        this._loop = null;
        this._looping = false;
        if (p.cornerRadius !== undefined) host('configure', this._surface.token, Number(p.cornerRadius) || 0);
      }
      _target() {
        if (!this._explicit && !connected(this.domElement)) {
          const stage = document.querySelector('[data-3d]');
          if (stage) { retag(this._surface, stage); return stage; }
        }
        if (this._surface.element !== this.domElement && connected(this.domElement)) retag(this._surface, this.domElement);
        return this._surface.element;
      }
      getContext() { return null; }
      setCornerRadius(radius) { host('configure', this._surface.token, Number(radius) || 0); }
      getPixelRatio() { return this._pixelRatio || measure(this._surface)[2]; }
      setPixelRatio(value) { this._pixelRatio = Number(value) || null; }
      getSize(target) {
        this._target();
        const s = measure(this._surface);
        return (target || new T.Vector2()).set(s[0], s[1]);
      }
      setSize(width, height, updateStyle) {
        const ratio = this.getPixelRatio();
        try {
          this.domElement.width = Math.floor(width * ratio);
          this.domElement.height = Math.floor(height * ratio);
          if (updateStyle !== false) { this.domElement.style.width = width + 'px'; this.domElement.style.height = height + 'px'; }
        } catch (e) {}
        this._surface.sizeAt = -1;
      }
      getDrawingBufferSize(target) { const s = measure(this._surface); return (target || new T.Vector2()).set(Math.round(s[0] * s[2]), Math.round(s[1] * s[2])); }
      setViewport() {}
      getViewport(target) { const s = measure(this._surface); return (target || new T.Vector4()).set(0, 0, s[0], s[1]); }
      setScissor() {}
      setScissorTest() {}
      setClearColor(color, alpha) { this._clearColor.set(color); if (alpha !== undefined) this._clearAlpha = alpha; }
      getClearColor(target) { return target.copy(this._clearColor); }
      getClearAlpha() { return this._clearAlpha; }
      setClearAlpha(alpha) { this._clearAlpha = alpha; }
      clear() {}
      clearColor() {}
      clearDepth() {}
      clearStencil() {}
      compile() {}
      compileAsync() { return Promise.resolve(); }
      initTexture() {}
      resetState() {}
      forceContextLoss() {}
      getActiveCubeFace() { return 0; }
      getActiveMipmapLevel() { return 0; }
      dispose() { this.setAnimationLoop(null); }
      setRenderTarget() {}
      getRenderTarget() { return null; }
      setAnimationLoop(callback) {
        this._loop = callback;
        if (!callback || this._looping) return;
        this._looping = true;
        const step = time => {
          if (!this._loop) { this._looping = false; return; }
          this._loop(time);
          requestAnimationFrame(step);
        };
        requestAnimationFrame(step);
      }
      _reserve(records) {
        const needed = HEADER + RECORD * records;
        if (this._buffer.length < needed) {
          const next = new Float32Array(Math.max(needed, this._buffer.length * 2));
          next.set(this._buffer);
          this._buffer = next;
        }
        return this._buffer;
      }
      _syncGeometry(geometry) {
        let entry = geometryIds.get(geometry);
        const token = this._surface.token;
        if (!entry) {
          entry = { id: nextGeometry++, tokens: new Map() };
          geometryIds.set(geometry, entry);
          geometry.addEventListener('dispose', () => { for (const t of entry.tokens.keys()) host('release', t, entry.id); geometryIds.delete(geometry); });
        }
        const a = geometry.attributes, p = a.position, n = a.normal, c = a.color, i = geometry.index;
        if (!p) return null;
        const known = entry.tokens.get(token);
        if (!known || known.p !== p || known.n !== n || known.c !== c || known.i !== i || known.pv !== p.version || known.nv !== version(n) || known.cv !== version(c) || known.iv !== version(i)) {
          entry.tokens.set(token, { p, n, c, i, pv: p.version, nv: version(n), cv: version(c), iv: version(i) });
          host('geometry', token, entry.id, floats(p), floats(n), floats(c), c ? c.itemSize : 0, indices(i));
        }
        return entry;
      }
      _syncInstances(mesh) {
        let entry = instanceIds.get(mesh);
        const token = this._surface.token;
        if (!entry) {
          entry = { id: nextInstanceSet++, tokens: new Map() };
          instanceIds.set(mesh, entry);
          mesh.addEventListener('dispose', () => { for (const t of entry.tokens.keys()) host('releaseInstances', t, entry.id); instanceIds.delete(mesh); });
        }
        const m = mesh.instanceMatrix, c = mesh.instanceColor;
        const known = entry.tokens.get(token);
        if (!known || known.m !== m || known.c !== c || known.mv !== m.version || known.cv !== version(c) || known.count !== mesh.count) {
          entry.tokens.set(token, { m, c, mv: m.version, cv: version(c), count: mesh.count });
          host('instances', token, entry.id, floats(m), mesh.count, floats(c));
        }
        return entry;
      }
      render(scene, camera) {
        if (!this.isSupported || !scene || !camera) return;
        this._target();
        if (scene.matrixWorldAutoUpdate === true) scene.updateMatrixWorld();
        if (camera.parent === null && camera.matrixWorldAutoUpdate === true) camera.updateMatrixWorld();

        let out = this._buffer, count = 0, triangles = 0;
        let ambientR = 0, ambientG = 0, ambientB = 0, hemi = null, directional = null;
        const points = [];
        const shadows = this.shadowMap.enabled;
        const layers = camera.layers;

        const emit = (object, geometry, material, start, drawCount, primitive, instances) => {
          if (!material || material.visible === false || drawCount <= 0) return;
          const entry = this._syncGeometry(geometry);
          if (!entry) return;
          out = this._reserve(count + 1);
          const o = HEADER + count * RECORD;
          out[o] = entry.id; out[o + 1] = material.id; out[o + 2] = start; out[o + 3] = drawCount;
          out[o + 4] = (object.castShadow ? 1 : 0) | (object.receiveShadow ? 2 : 0) | (primitive << 4) | (object.frustumCulled !== false ? 64 : 0);
          out[o + 5] = instances ? instances.id : 0;
          out[o + 6] = instances ? object.count : 1;
          out[o + 7] = 0;
          out.set(object.matrixWorld.elements, o + 8);
          packMaterial(material, out, o + 24);
          count++;
          if (primitive === 0) triangles += (drawCount / 3) * (instances ? object.count : 1);
        };

        const drawObject = (object, primitive) => {
          const geometry = object.geometry, material = object.material;
          if (!geometry || !geometry.attributes || !geometry.attributes.position) return;
          const instances = object.isInstancedMesh ? this._syncInstances(object) : null;
          const total = geometry.index ? geometry.index.count : geometry.attributes.position.count;
          const range = geometry.drawRange;
          const rangeStart = Math.max(0, range.start), rangeEnd = Math.min(total, range.start + range.count);
          if (Array.isArray(material)) {
            for (const group of geometry.groups) {
              const start = Math.max(rangeStart, group.start), end = Math.min(rangeEnd, group.start + group.count);
              emit(object, geometry, material[group.materialIndex], start, end - start, primitive, instances);
            }
          } else {
            emit(object, geometry, material, rangeStart, rangeEnd - rangeStart, primitive, instances);
          }
        };

        const visit = object => {
          if (object.visible === false || !object.layers.test(layers)) return;
          if (object.isMesh) drawObject(object, 0);
          else if (object.isLineSegments) drawObject(object, 1);
          else if (object.isLine) drawObject(object, 2);
          else if (object.isPoints) drawObject(object, 3);
          else if (object.isAmbientLight) { const c = object.color, k = object.intensity; ambientR += c.r * k; ambientG += c.g * k; ambientB += c.b * k; }
          else if (object.isHemisphereLight) { if (!hemi) hemi = object; }
          else if (object.isDirectionalLight) { if (!directional) directional = object; }
          else if (object.isPointLight || object.isSpotLight) { if (points.length < MAX_LIGHTS) points.push(object); }
          const children = object.children;
          for (let i = 0; i < children.length; i++) visit(children[i]);
        };
        visit(scene);

        out = this._buffer;
        out.fill(0, 0, HEADER);
        out.set(camera.matrixWorldInverse.elements, 0);
        out.set(camera.projectionMatrix.elements, 16);
        const cw = camera.matrixWorld.elements;
        out[48] = cw[12]; out[49] = cw[13]; out[50] = cw[14];
        const background = scene.background;
        if (background && background.isColor) { out[52] = background.r; out[53] = background.g; out[54] = background.b; out[55] = 1; }
        else { out[52] = this._clearColor.r; out[53] = this._clearColor.g; out[54] = this._clearColor.b; out[55] = this._clearAlpha; }
        const fog = scene.fog;
        if (fog && fog.color) {
          out[56] = fog.color.r; out[57] = fog.color.g; out[58] = fog.color.b;
          if (fog.isFogExp2) { out[59] = 2; out[62] = fog.density; } else { out[59] = 1; out[60] = fog.near; out[61] = fog.far; }
        }
        out[64] = ambientR; out[65] = ambientG; out[66] = ambientB;
        if (hemi) {
          const k = hemi.intensity;
          out[68] = hemi.color.r * k; out[69] = hemi.color.g * k; out[70] = hemi.color.b * k; out[71] = 1;
          out[72] = hemi.groundColor.r * k; out[73] = hemi.groundColor.g * k; out[74] = hemi.groundColor.b * k;
          v1.setFromMatrixPosition(hemi.matrixWorld).normalize();
          out[76] = v1.x; out[77] = v1.y; out[78] = v1.z;
        }
        if (directional) {
          const k = directional.intensity;
          out[80] = directional.color.r * k; out[81] = directional.color.g * k; out[82] = directional.color.b * k; out[83] = 1;
          v1.setFromMatrixPosition(directional.matrixWorld);
          v2.setFromMatrixPosition(directional.target.matrixWorld);
          v1.sub(v2).normalize();
          out[84] = v1.x; out[85] = v1.y; out[86] = v1.z;
          if (shadows && directional.castShadow && directional.shadow) {
            const shadow = directional.shadow;
            shadow.updateMatrices(directional);
            shadowViewProjection.multiplyMatrices(shadow.camera.projectionMatrix, shadow.camera.matrixWorldInverse);
            out.set(shadowViewProjection.elements, 32);
            out[88] = shadow.bias; out[89] = shadow.normalBias; out[90] = shadow.radius; out[91] = 1;
            out[92] = shadow.mapSize.x;
          }
        }
        out[93] = points.length;
        out[94] = count;
        out[95] = this.getPixelRatio();
        for (let i = 0; i < points.length; i++) {
          const light = points[i], o = 96 + i * 16, k = light.intensity;
          v1.setFromMatrixPosition(light.matrixWorld);
          out[o] = v1.x; out[o + 1] = v1.y; out[o + 2] = v1.z; out[o + 3] = light.distance;
          out[o + 4] = light.color.r * k; out[o + 5] = light.color.g * k; out[o + 6] = light.color.b * k; out[o + 7] = light.decay;
          if (light.isSpotLight) {
            v2.setFromMatrixPosition(light.target.matrixWorld);
            v1.sub(v2).normalize();
            out[o + 8] = v1.x; out[o + 9] = v1.y; out[o + 10] = v1.z; out[o + 11] = 1;
            out[o + 12] = Math.cos(light.angle); out[o + 13] = Math.cos(light.angle * (1 - light.penumbra));
          }
        }
        const info = this.info.render;
        info.frame++; info.calls = count; info.triangles = triangles;
        host('frame', this._surface.token, out, HEADER + count * RECORD);
      }
    }

    T.MetalRenderer = MetalRenderer;
    T.WebGLRenderer = MetalRenderer;
    T.WebGL1Renderer = MetalRenderer;
  };

  let threeValue;
  Object.defineProperty(globalThis, 'THREE', {
    configurable: true, enumerable: false,
    get() { return threeValue; },
    set(value) { threeValue = value; try { installThree(value); } catch (e) {} },
  });

  const legacy = () => {
    if (typeof globalThis.render !== 'function' || !threeValue || !document.querySelector('[data-3d]')) return;
    const hooks = {
      render: globalThis.render,
      onAction: typeof globalThis.onAction === 'function' ? globalThis.onAction : null,
      onPointer: typeof globalThis.onPointer === 'function' ? globalThis.onPointer : null,
      onKey: typeof globalThis.onKey === 'function' ? globalThis.onKey : null,
      onFocus: typeof globalThis.onFocus === 'function' ? globalThis.onFocus : null,
    };
    let root = document.querySelector('[data-3d]');
    while (root && root.parentNode && root.parentNode !== document.body) root = root.parentNode;
    if (!root || root.parentNode !== document.body) return;
    let last = null, queued = false, focused = false;
    const refresh = () => {
      let html;
      try { html = hooks.render(); } catch (e) { return; }
      if (typeof html !== 'string' || html === last) return;
      last = html;
      const holder = document.createElement('div');
      holder.innerHTML = html;
      const next = holder.firstElementChild;
      if (!next) return;
      const oldStage = root.querySelector('[data-3d]');
      const newStage = next.querySelector('[data-3d]');
      const surface = oldStage ? surfaces.get(oldStage) : null;
      root.parentNode.replaceChild(next, root);
      root = next;
      if (surface && newStage) retag(surface, newStage);
    };
    const schedule = () => {
      if (queued) return;
      queued = true;
      Promise.resolve().then(() => { queued = false; refresh(); });
    };
    const nativeFrame = globalThis.requestAnimationFrame;
    globalThis.requestAnimationFrame = fn => nativeFrame(t => { fn(t); schedule(); });
    const focus = () => { if (!focused && hooks.onFocus) { focused = true; hooks.onFocus(true); } };
    document.addEventListener('pointerdown', e => {
      const stage = e.target && e.target.closest ? e.target.closest('[data-3d]') : null;
      if (!stage) return;
      focus();
      if (hooks.onPointer) {
        const r = stage.getBoundingClientRect();
        hooks.onPointer(e.clientX - r.left, e.clientY - r.top);
      }
      schedule();
    });
    document.addEventListener('click', e => {
      const target = e.target && e.target.closest ? e.target.closest('[data-action]') : null;
      if (!target || !hooks.onAction) return;
      hooks.onAction(target.getAttribute('data-action'), target.hasAttribute('data-value') ? target.getAttribute('data-value') : undefined);
      schedule();
    });
    const keys = new Set(['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'w', 'a', 's', 'd', 'W', 'A', 'S', 'D', ' ', 'Enter', 'Escape']);
    document.addEventListener('keydown', e => {
      if (!hooks.onKey || e.metaKey || e.ctrlKey || e.altKey || !keys.has(e.key)) return;
      focus();
      hooks.onKey(e.key.length === 1 ? e.key.toLowerCase() : e.key);
      schedule();
    });
    refresh();
  };
  if (document.readyState === 'complete') setTimeout(legacy, 0);
  else globalThis.addEventListener('load', () => legacy());
})
