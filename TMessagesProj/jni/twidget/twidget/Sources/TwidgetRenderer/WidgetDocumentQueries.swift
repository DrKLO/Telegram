import Foundation
import LiteHTML

extension LHDocument {
    func idBounds(transformed: Bool = false, lineBoxes: Bool = false) -> [Int: CGRect] {
        let data = keyedBounds(byAttribute: WidgetDOM.idAttribute, transformed: transformed, lineBoxes: lineBoxes)
        return data.withUnsafeBytes { raw in
            let records = raw.bindMemory(to: LHKeyedRect.self)
            var result = [Int: CGRect](minimumCapacity: records.count)
            for record in records {
                result[record.key] = record.rect
            }
            return result
        }
    }
}
