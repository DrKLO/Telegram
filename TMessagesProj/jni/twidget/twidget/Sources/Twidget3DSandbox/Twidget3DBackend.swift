import Foundation
import QuartzCore
import Twidget3D
import WidgetSandbox

public final class Twidget3DBackend: Widget3DBackend {
    public static var limits: HTML3DLimits = {
        var limits = HTML3DLimits()
        limits.maxGeometryBytes = 48 << 20
        limits.maxDrawablePixels = 2048 * 2048
        return limits
    }()

    public init() {}

    public var isAvailable: Bool {
        HTML3DSession.isAvailable
    }

    public var adapterSource: String? {
        Twidget3D.sandboxAdapterSource
    }

    public func makeSurface() -> Widget3DSurface {
        Twidget3DSurface(session: HTML3DSession(limits: Self.limits))
    }

    public static func channel() -> Widget3DChannel {
        Widget3DChannel(backend: Twidget3DBackend())
    }
}

public final class Twidget3DSurface: Widget3DSurface {
    public let session: HTML3DSession
    public var onDiag: ((String, String) -> Void)?

    init(session: HTML3DSession) {
        self.session = session
        session.onStop = { [weak self] reason in
            self?.onDiag?("3d-stopped", reason)
        }
    }

    private static func array(_ argument: Widget3DArgument) -> HTML3DArray? {
        guard let bytes = argument.bytes else {
            return nil
        }
        let kind: HTML3DArray.Kind
        switch bytes.kind {
        case .float32: kind = .float32
        case .uint16: kind = .uint16
        case .uint32: kind = .uint32
        case .other: kind = .other
        }
        return HTML3DArray(pointer: bytes.borrowedPointer, length: bytes.length, kind: kind)
    }

    public func call(_ op: String, _ args: [Widget3DArgument]) -> HostReturn {
        let arg = { (i: Int) -> Widget3DArgument in i < args.count ? args[i] : .none }
        switch op {
        case "size":
            return .string(session.viewportInfo().map { String($0) }.joined(separator: ","))
        case "configure":
            session.configure(radius: arg(2).number ?? 0)
        case "geometry":
            if !session.defineGeometry(id: arg(2).int, positions: Self.array(arg(3)), normals: Self.array(arg(4)), colors: Self.array(arg(5)), colorSize: arg(6).int, index: Self.array(arg(7))) {
                onDiag?("3d-rejected", "geometry \(arg(2).int)")
            }
        case "release":
            session.releaseGeometry(id: arg(2).int)
        case "instances":
            if !session.defineInstances(id: arg(2).int, matrices: Self.array(arg(3)), count: arg(4).int, colors: Self.array(arg(5))) {
                onDiag?("3d-rejected", "instances \(arg(2).int)")
            }
        case "releaseInstances":
            session.releaseInstances(id: arg(2).int)
        case "frame":
            session.submit(data: Self.array(arg(2)), count: arg(3).int)
        default:
            break
        }
        return .undefined
    }

    public func makeLayer() -> CALayer {
        session.makeLayer() ?? CALayer()
    }

    public func layout(_ layer: CALayer, size: CGSize, scale: CGFloat) {
        guard let metal = layer as? CAMetalLayer else {
            return
        }
        let drawable = session.drawableSize(for: size, scale: scale)
        if metal.drawableSize != drawable {
            metal.drawableSize = drawable
        }
        let radius = session.cornerRadius
        if metal.cornerRadius != radius {
            metal.cornerRadius = radius
            metal.masksToBounds = radius > 0
        }
        session.attach(metal, size: size, scale: scale)
    }

    public func setPaused(_ paused: Bool) {
        session.setPaused(paused)
    }

    public func stop(_ reason: String) {
        session.stop(reason)
    }
}
