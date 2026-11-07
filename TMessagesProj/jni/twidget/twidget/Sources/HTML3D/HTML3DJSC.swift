import Foundation
import JavaScriptCore
import Twidget3D

extension HTML3DSession {
    func install(in context: JSContext) -> JSValue {
        let native = JSValue(newObjectIn: context)!
        native.setObject(isSupported, forKeyedSubscript: "supported" as NSString)
        let size: @convention(block) () -> [Double] = { [weak self] in
            return self?.viewportInfo() ?? [0, 0, 2]
        }
        let configure: @convention(block) (Double) -> Void = { [weak self] radius in
            self?.configure(radius: radius)
        }
        let geometry: @convention(block) (Int, JSValue, JSValue, JSValue, Int, JSValue) -> Void = { [weak self] id, positions, normals, colors, colorSize, index in
            self?.defineGeometry(id: id, positions: HTML3DSession.array(positions), normals: HTML3DSession.array(normals), colors: HTML3DSession.array(colors), colorSize: colorSize, index: HTML3DSession.array(index))
        }
        let release: @convention(block) (Int) -> Void = { [weak self] id in
            self?.releaseGeometry(id: id)
        }
        let instances: @convention(block) (Int, JSValue, Int, JSValue) -> Void = { [weak self] id, matrices, count, colors in
            self?.defineInstances(id: id, matrices: HTML3DSession.array(matrices), count: count, colors: HTML3DSession.array(colors))
        }
        let releaseInstances: @convention(block) (Int) -> Void = { [weak self] id in
            self?.releaseInstances(id: id)
        }
        let frame: @convention(block) (JSValue, Int) -> Void = { [weak self] data, count in
            self?.submit(data: HTML3DSession.array(data), count: count)
        }
        native.setObject(size, forKeyedSubscript: "size" as NSString)
        native.setObject(configure, forKeyedSubscript: "configure" as NSString)
        native.setObject(geometry, forKeyedSubscript: "geometry" as NSString)
        native.setObject(release, forKeyedSubscript: "release" as NSString)
        native.setObject(instances, forKeyedSubscript: "instances" as NSString)
        native.setObject(releaseInstances, forKeyedSubscript: "releaseInstances" as NSString)
        native.setObject(frame, forKeyedSubscript: "frame" as NSString)
        return native
    }

    static func array(_ value: JSValue) -> HTML3DArray? {
        guard let context = value.context?.jsGlobalContextRef, let ref = value.jsValueRef else {
            return nil
        }
        let type = JSValueGetTypedArrayType(context, ref, nil)
        if type == kJSTypedArrayTypeNone || type == kJSTypedArrayTypeArrayBuffer {
            return nil
        }
        guard let object = JSValueToObject(context, ref, nil), let pointer = JSObjectGetTypedArrayBytesPtr(context, object, nil) else {
            return nil
        }
        let kind: HTML3DArray.Kind = type == kJSTypedArrayTypeFloat32Array ? .float32 : type == kJSTypedArrayTypeUint16Array ? .uint16 : type == kJSTypedArrayTypeUint32Array ? .uint32 : .other
        return HTML3DArray(pointer: UnsafeRawPointer(pointer), length: JSObjectGetTypedArrayByteLength(context, object, nil), kind: kind)
    }
}
