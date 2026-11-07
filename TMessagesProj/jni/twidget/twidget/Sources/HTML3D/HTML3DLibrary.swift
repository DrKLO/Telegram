import Foundation
import HTMLScript
@_exported import Twidget3D

extension HTML3DSession: HTMLScriptLibrary {
    public static var threeSource: () -> String? = {
        return Twidget3D.threeSource()
    }

    public static func register() {
        HTMLScript.libraryFactories["three"] = { HTML3DSession() }
    }

    public func install(in context: HTMLScriptContext, limit: TimeInterval) throws {
        guard let library = HTML3DSession.threeSource() else {
            return
        }
        if let jsc = context as? JSCScriptContext {
            let bridge = install(in: jsc.context)
            try context.evaluate(library, name: "three.js", timeLimit: limit)
            try jsc.callExpression(html3DThreeAdapter, [bridge])
            return
        }
        if let quick = context as? QuickJSScriptContext {
            installBridge(in: quick.context)
            try context.evaluate(library, name: "three.js", timeLimit: limit)
            try context.evaluate("(\(html3DThreeAdapter))(__h3dNative());", name: "three-adapter.js", timeLimit: limit)
            return
        }
        throw HTMLScriptError(.exception, "app:three is not available on this JavaScript runtime")
    }

    private static func array(_ argument: QuickJSArgument?) -> HTML3DArray? {
        guard case let .bytes(buffer, kind)? = argument, let base = buffer.baseAddress else {
            return nil
        }
        let mapped: HTML3DArray.Kind = kind == QuickJSArgument.float32 ? .float32 : kind == QuickJSArgument.uint16 ? .uint16 : kind == QuickJSArgument.uint32 ? .uint32 : .other
        return HTML3DArray(pointer: base, length: buffer.count, kind: mapped)
    }

    private static func int(_ argument: QuickJSArgument?) -> Int {
        guard case let .number(value)? = argument, value.isFinite else {
            return 0
        }
        return Int(max(-1e9, min(1e9, value)))
    }

    private func installBridge(in js: QuickJSContext) {
        js.registerArgumentFunction("__h3d_size") { [weak self] _ in
            return .string((self?.viewportInfo() ?? [0, 0, 2]).map { String($0) }.joined(separator: ","))
        }
        js.registerArgumentFunction("__h3d_configure") { [weak self] args in
            if case let .number(radius)? = args.first {
                self?.configure(radius: radius)
            }
            return .undefined
        }
        js.registerArgumentFunction("__h3d_geometry") { [weak self] args in
            let arg = { (i: Int) -> QuickJSArgument? in i < args.count ? args[i] : nil }
            self?.defineGeometry(id: HTML3DSession.int(arg(0)), positions: HTML3DSession.array(arg(1)), normals: HTML3DSession.array(arg(2)), colors: HTML3DSession.array(arg(3)), colorSize: HTML3DSession.int(arg(4)), index: HTML3DSession.array(arg(5)))
            return .undefined
        }
        js.registerArgumentFunction("__h3d_release") { [weak self] args in
            self?.releaseGeometry(id: HTML3DSession.int(args.first))
            return .undefined
        }
        js.registerArgumentFunction("__h3d_instances") { [weak self] args in
            let arg = { (i: Int) -> QuickJSArgument? in i < args.count ? args[i] : nil }
            self?.defineInstances(id: HTML3DSession.int(arg(0)), matrices: HTML3DSession.array(arg(1)), count: HTML3DSession.int(arg(2)), colors: HTML3DSession.array(arg(3)))
            return .undefined
        }
        js.registerArgumentFunction("__h3d_releaseInstances") { [weak self] args in
            self?.releaseInstances(id: HTML3DSession.int(args.first))
            return .undefined
        }
        js.registerArgumentFunction("__h3d_frame") { [weak self] args in
            self?.submit(data: HTML3DSession.array(args.first), count: HTML3DSession.int(args.count > 1 ? args[1] : nil))
            return .undefined
        }
        _ = try? js.evaluate("""
        globalThis.__h3dNative = (() => {
          const fns = {};
          for (const name of ['size', 'configure', 'geometry', 'release', 'instances', 'releaseInstances', 'frame']) {
            fns[name] = globalThis['__h3d_' + name];
            delete globalThis['__h3d_' + name];
          }
          return () => {
            delete globalThis.__h3dNative;
            return {
              supported: \(isSupported),
              size: () => fns.size().split(',').map(Number),
              configure: r => fns.configure(r),
              geometry: (id, p, n, c, s, i) => fns.geometry(id, p, n, c, s, i),
              release: id => fns.release(id),
              instances: (id, m, n, c) => fns.instances(id, m, n, c),
              releaseInstances: id => fns.releaseInstances(id),
              frame: (d, n) => fns.frame(d, n),
            };
          };
        })();
        """, filename: "three-bridge.js")
    }
}

extension HTMLScript {
    public var scene3D: HTML3DSession? {
        return library(HTML3DSession.self)
    }
}
