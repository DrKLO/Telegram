import Foundation
import WidgetSandboxProtocol

enum WidgetCanvasOrder {
    static func ordered(_ ops: [WidgetOp]) -> [WidgetOp] {
        var lastDraw: [Int: Int] = [:]
        for (index, op) in ops.enumerated() where isCanvas(op) {
            lastDraw[target(op)] = index
        }
        guard lastDraw.count > 1 else {
            return ops
        }
        var deferred = Set<Int>()
        var head: [WidgetOp] = []
        var tail: [WidgetOp] = []
        head.reserveCapacity(ops.count)
        for (index, op) in ops.enumerated() {
            let id = target(op)
            let canvas = isCanvas(op)
            if deferred.contains(id), canvas || isAttribute(op) {
                tail.append(op)
                continue
            }
            if canvas, references(op).contains(where: { $0 != id && (deferred.contains($0) || (lastDraw[$0] ?? -1) > index) }) {
                deferred.insert(id)
                tail.append(op)
                continue
            }
            head.append(op)
        }
        return deferred.isEmpty ? ops : head + tail
    }

    private static func isCanvas(_ op: WidgetOp) -> Bool {
        return op.kind == .canvas || (op.kind == .other && op.op == "canvas")
    }

    private static func isAttribute(_ op: WidgetOp) -> Bool {
        return op.kind == .attribute || op.kind == .removeAttribute || (op.kind == .other && op.op == "attr")
    }

    private static func target(_ op: WidgetOp) -> Int {
        if op.kind == .other {
            return (op["id"] as? NSNumber)?.intValue ?? 0
        }
        return op.id
    }

    static func references(_ op: WidgetOp) -> Set<Int> {
        let stream: CanvasStream
        if let batch = op.canvas {
            stream = CanvasStream(batch: batch)
        } else if let commands = op["cmds"] as? [Any] {
            stream = CanvasStream(commands: commands)
        } else {
            return []
        }
        var out = Set<Int>()
        for arg in stream.args {
            if case let .node(id) = arg {
                out.insert(id)
            }
        }
        return out
    }
}
