import Foundation
import Twidget3D
import WidgetSandbox

public enum Twidget3DLibrary {
    public static let threePatterns = [
        "^app:three$",
        "^https?:\\/\\/cdn\\.jsdelivr\\.net\\/npm\\/three@0\\.1[5-6]\\d(?:\\.\\d+)?\\/build\\/three(?:\\.min)?\\.js$",
        "^https?:\\/\\/unpkg\\.com\\/three@0\\.1[5-6]\\d(?:\\.\\d+)?\\/build\\/three(?:\\.min)?\\.js$",
        "^https?:\\/\\/cdnjs\\.cloudflare\\.com\\/ajax\\/libs\\/three\\.js\\/r?1[5-6]\\d(?:\\.\\d+)?\\/three(?:\\.min)?\\.js$",
    ]

    @discardableResult
    public static func provideThree(to resources: SandboxResources = .shared) -> Bool {
        return false
    }
}
