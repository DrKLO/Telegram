import Foundation

public enum WidgetTeardown {
    private static let queue = DispatchQueue(label: "twidget.teardown", qos: .utility)

    public static func release(_ objects: [AnyObject]) {
        guard !objects.isEmpty else {
            return
        }
        let box = Box(objects)
        queue.asyncAfter(deadline: .now() + 0.05) {
            box.objects.removeAll()
        }
    }

    public static func flush() {
        queue.sync {}
    }

    private final class Box {
        var objects: [AnyObject]

        init(_ objects: [AnyObject]) {
            self.objects = objects
        }
    }
}
