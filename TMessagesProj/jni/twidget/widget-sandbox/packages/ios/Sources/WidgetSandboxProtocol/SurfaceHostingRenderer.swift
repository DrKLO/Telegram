import Foundation
import QuartzCore

public protocol SurfaceHostingRenderer: AnyObject {
    var surfaceLayer: ((String) -> CALayer?)? { get set }
    var surfaceLayout: ((String, CGSize, CGFloat, Bool) -> Void)? { get set }
}
