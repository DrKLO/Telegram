let html3DThreeAdapter = #"""
(function (native) {
  'use strict';
  const T = globalThis.THREE;
  if (!T) return;
  const HEADER = 160, RECORD = 40, MAX_LIGHTS = 4;
  const geometryIds = new WeakMap(), instanceIds = new WeakMap();
  let nextGeometry = 1, nextInstanceSet = 1;
  const frustum = new T.Frustum(), viewProjection = new T.Matrix4(), shadowViewProjection = new T.Matrix4();
  const v1 = new T.Vector3(), v2 = new T.Vector3(), clearColor = new T.Color(0, 0, 0);
  const listeners = {};

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

  function syncGeometry(geometry) {
    let entry = geometryIds.get(geometry);
    if (!entry) {
      entry = { id: nextGeometry++, p: null, n: null, c: null, i: null, pv: -2, nv: -2, cv: -2, iv: -2 };
      geometryIds.set(geometry, entry);
      geometry.addEventListener('dispose', () => { native.release(entry.id); geometryIds.delete(geometry); });
    }
    const a = geometry.attributes, p = a.position, n = a.normal, c = a.color, i = geometry.index;
    if (!p) return null;
    if (entry.p !== p || entry.n !== n || entry.c !== c || entry.i !== i || entry.pv !== p.version || entry.nv !== version(n) || entry.cv !== version(c) || entry.iv !== version(i)) {
      entry.p = p; entry.n = n; entry.c = c; entry.i = i;
      entry.pv = p.version; entry.nv = version(n); entry.cv = version(c); entry.iv = version(i);
      native.geometry(entry.id, floats(p), floats(n), floats(c), c ? c.itemSize : 0, indices(i));
    }
    return entry;
  }

  function syncInstances(mesh) {
    let entry = instanceIds.get(mesh);
    if (!entry) {
      entry = { id: nextInstanceSet++, m: -2, c: -2, ma: null, ca: null };
      instanceIds.set(mesh, entry);
      mesh.addEventListener('dispose', () => { native.releaseInstances(entry.id); instanceIds.delete(mesh); });
    }
    const m = mesh.instanceMatrix, c = mesh.instanceColor;
    if (entry.ma !== m || entry.ca !== c || entry.m !== m.version || entry.c !== version(c)) {
      entry.ma = m; entry.ca = c; entry.m = m.version; entry.c = version(c);
      native.instances(entry.id, floats(m), mesh.count, floats(c));
    }
    return entry;
  }

  const materialType = m => m.isMeshBasicMaterial || m.isLineBasicMaterial || m.isLineDashedMaterial || m.isPointsMaterial || m.isSpriteMaterial ? 0
    : m.isMeshLambertMaterial ? 1 : m.isMeshPhongMaterial ? 2 : m.isMeshStandardMaterial ? 3 : m.isMeshNormalMaterial ? 4 : m.isMeshToonMaterial ? 5 : 1;

  function packMaterial(m, out, o) {
    const c = m.color, e = m.emissive, s = m.specular, ei = m.emissiveIntensity === undefined ? 1 : m.emissiveIntensity;
    out[o] = c ? c.r : 1; out[o + 1] = c ? c.g : 1; out[o + 2] = c ? c.b : 1; out[o + 3] = m.opacity;
    out[o + 4] = e ? e.r * ei : 0; out[o + 5] = e ? e.g * ei : 0; out[o + 6] = e ? e.b * ei : 0; out[o + 7] = materialType(m);
    out[o + 8] = s ? s.r : 0.0666; out[o + 9] = s ? s.g : 0.0666; out[o + 10] = s ? s.b : 0.0666; out[o + 11] = m.shininess === undefined ? 30 : m.shininess;
    out[o + 12] = m.roughness === undefined ? 1 : m.roughness;
    out[o + 13] = m.metalness === undefined ? 0 : m.metalness;
    out[o + 14] = (m.transparent ? 1 : 0) | (m.vertexColors ? 2 : 0) | (m.flatShading ? 4 : 0) | (m.wireframe ? 8 : 0) | ((m.side & 3) << 4) | (m.fog !== false ? 64 : 0) | (m.depthWrite ? 128 : 0) | (m.depthTest ? 256 : 0);
    out[o + 15] = m.size === undefined ? 1 : m.size;
  }

  class MetalRenderer {
    constructor(parameters) {
      const p = parameters || {};
      this.isWebGLRenderer = true;
      this.isMetalRenderer = true;
      this.isSupported = !!native.supported;
      this.domElement = { style: {}, width: 0, height: 0, addEventListener: target.addEventListener, removeEventListener: target.removeEventListener, getBoundingClientRect: () => { const s = native.size(); return { left: 0, top: 0, x: 0, y: 0, width: s[0], height: s[1], right: s[0], bottom: s[1] }; } };
      this.shadowMap = { enabled: false, autoUpdate: true, needsUpdate: false, type: T.PCFShadowMap };
      this.info = { autoReset: true, render: { frame: 0, calls: 0, triangles: 0, points: 0, lines: 0 }, memory: { geometries: 0, textures: 0 }, reset() {} };
      this.capabilities = { isWebGL2: true, maxTextures: 0, precision: 'highp' };
      this.outputColorSpace = T.SRGBColorSpace;
      this.toneMapping = T.NoToneMapping;
      this.toneMappingExposure = 1;
      this.useLegacyLights = false;
      this.autoClear = true;
      this.sortObjects = true;
      this._alpha = !!p.alpha;
      this._clearAlpha = this._alpha ? 0 : 1;
      this._buffer = new Float32Array(HEADER + RECORD * 256);
      this._pixelRatio = 1;
      this._loop = null;
      this._looping = false;
      if (p.cornerRadius !== undefined) native.configure(Number(p.cornerRadius) || 0);
    }
    setCornerRadius(radius) { native.configure(Number(radius) || 0); }
    getContext() { return null; }
    getPixelRatio() { return native.size()[2]; }
    setPixelRatio(value) { this._pixelRatio = value; }
    getSize(target) { const s = native.size(); return (target || new T.Vector2()).set(s[0], s[1]); }
    setSize(width, height) { this.domElement.width = width; this.domElement.height = height; }
    getDrawingBufferSize(target) { const s = native.size(); return (target || new T.Vector2()).set(s[0] * s[2], s[1] * s[2]); }
    setClearColor(color, alpha) { clearColor.set(color); if (alpha !== undefined) this._clearAlpha = alpha; }
    getClearColor(target) { return target.copy(clearColor); }
    getClearAlpha() { return this._clearAlpha; }
    setClearAlpha(alpha) { this._clearAlpha = alpha; }
    clear() {}
    compile() {}
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

    render(scene, camera) {
      if (!this.isSupported || !scene || !camera) return;
      if (scene.matrixWorldAutoUpdate === true) scene.updateMatrixWorld();
      if (camera.parent === null && camera.matrixWorldAutoUpdate === true) camera.updateMatrixWorld();
      viewProjection.multiplyMatrices(camera.projectionMatrix, camera.matrixWorldInverse);
      frustum.setFromProjectionMatrix(viewProjection);

      let out = this._buffer, count = 0, triangles = 0;
      let ambientR = 0, ambientG = 0, ambientB = 0, hemi = null, directional = null;
      const points = [];
      const shadows = this.shadowMap.enabled;
      const layers = camera.layers;

      const emit = (object, geometry, material, start, drawCount, primitive, instances) => {
        if (!material || material.visible === false || drawCount <= 0) return;
        const entry = syncGeometry(geometry);
        if (!entry) return;
        out = this._reserve(count + 1);
        const o = HEADER + count * RECORD;
        out[o] = entry.id; out[o + 1] = material.id; out[o + 2] = start; out[o + 3] = drawCount;
        out[o + 4] = (object.castShadow ? 1 : 0) | (object.receiveShadow ? 2 : 0) | (primitive << 4);
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
        if (object.frustumCulled !== false && !object.isInstancedMesh && !frustum.intersectsObject(object)) return;
        const instances = object.isInstancedMesh ? syncInstances(object) : null;
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
      else { out[52] = clearColor.r; out[53] = clearColor.g; out[54] = clearColor.b; out[55] = this._clearAlpha; }
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
      out[95] = native.size()[2];
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
      native.frame(out, HEADER + count * RECORD);
    }
  }

  const target = {
    addEventListener(type, fn) { if (typeof fn === 'function') (listeners[type] || (listeners[type] = [])).push(fn); },
    removeEventListener(type, fn) { const list = listeners[type]; if (list) { const i = list.indexOf(fn); if (i >= 0) list.splice(i, 1); } },
  };
  const dispatch = (type, event) => { const list = listeners[type]; if (list) for (const fn of list.slice()) fn(event); };
  const codes = { ' ': 'Space', ArrowUp: 'ArrowUp', ArrowDown: 'ArrowDown', ArrowLeft: 'ArrowLeft', ArrowRight: 'ArrowRight', Enter: 'Enter', Escape: 'Escape' };

  globalThis.__htmlInput = (type, a, b) => {
    const noop = () => {};
    if (type === 'key') {
      const key = String(a);
      dispatch('keydown', { type: 'keydown', key, code: codes[key] || ('Key' + key.toUpperCase()), repeat: false, preventDefault: noop, stopPropagation: noop });
    } else if (type === 'pointer') {
      const event = { type: 'pointerdown', clientX: a, clientY: b, offsetX: a, offsetY: b, button: 0, pointerType: 'mouse', preventDefault: noop, stopPropagation: noop };
      dispatch('pointerdown', event);
      dispatch('click', Object.assign({}, event, { type: 'click' }));
    } else if (type === 'focus') {
      dispatch(a ? 'focus' : 'blur', { type: a ? 'focus' : 'blur' });
    }
  };

  if (typeof globalThis.window === 'undefined') {
    globalThis.window = globalThis;
    Object.defineProperty(globalThis, 'innerWidth', { get: () => native.size()[0] });
    Object.defineProperty(globalThis, 'innerHeight', { get: () => native.size()[1] });
    Object.defineProperty(globalThis, 'devicePixelRatio', { get: () => native.size()[2] });
    globalThis.addEventListener = target.addEventListener;
    globalThis.removeEventListener = target.removeEventListener;
  }
  if (typeof globalThis.document === 'undefined') {
    const element = () => ({ style: {}, appendChild() {}, removeChild() {}, addEventListener: target.addEventListener, removeEventListener: target.removeEventListener, getContext: () => null });
    globalThis.document = { body: element(), documentElement: element(), createElement: element, createElementNS: element, addEventListener: target.addEventListener, removeEventListener: target.removeEventListener };
  }

  T.MetalRenderer = MetalRenderer;
  T.WebGLRenderer = MetalRenderer;
  T.WebGL1Renderer = MetalRenderer;
})
"""#
