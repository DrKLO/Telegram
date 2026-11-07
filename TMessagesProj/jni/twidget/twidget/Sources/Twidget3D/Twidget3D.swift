import Foundation

public enum Twidget3D {
    public static var threeSource: () -> String? = {
        guard let url = Bundle.module.url(forResource: "three", withExtension: "js") else {
            return nil
        }
        return try? String(contentsOf: url, encoding: .utf8)
    }

    public static var sandboxAdapterSource: String? = {
        guard let url = Bundle.module.url(forResource: "twidget3d", withExtension: "js") else {
            return nil
        }
        return try? String(contentsOf: url, encoding: .utf8)
    }()
}
