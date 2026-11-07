import Foundation
import TwidgetAssets

public enum TwidgetIcons {
    public static func dataURL(_ icon: String) -> String? {
        guard icon.utf8.count <= 512, !icon.utf8.contains(0), let raw = twa_icon_data_url(icon) else {
            return nil
        }
        defer { twa_free(raw) }
        return String(cString: raw)
    }

    public static let resolver: (String) -> String? = { dataURL($0) }
}
