#if DEBUG
import Foundation

extension LiteHTMLWidgetRenderer {
    public func debugClick(attributes: [String: String]) -> Bool {
        guard !attributes.isEmpty, let node = dom.nodes.values.first(where: { node in attributes.allSatisfy { node.attribute($0.key) == $0.value } }) else {
            return false
        }
        noteUserInput()
        onMessage?("{\"type\":\"event\",\"trusted\":true,\"ev\":{\"type\":\"click\",\"targetId\":\(dom.publicId(node)),\"bubbles\":true,\"button\":0}}")
        return true
    }
}
#endif
