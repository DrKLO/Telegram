import XCTest
import HTMLSandbox

final class JSThreadTimerTests: XCTestCase {
    private func pump(_ seconds: TimeInterval, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
    }

    func testCancelledTimersLeaveTheThreadAndStoppedThreadsFreeTheirTimers() {
        final class Token {}
        weak var cancelled: Token?
        weak var pending: Token?
        var thread: JSThread? = JSThread(name: "memory-test")
        autoreleasepool {
            let a = Token(), b = Token()
            cancelled = a
            pending = b
            let handle = thread!.after(3600) { _ = a }
            _ = thread!.after(3600) { _ = b }
            thread!.sync { }
            handle.cancel()
            thread!.sync { }
            thread!.sync { }
        }
        XCTAssertNil(cancelled)
        XCTAssertNotNil(pending)
        thread?.stop()
        thread = nil
        pump(5, until: { pending == nil })
        XCTAssertNil(pending)
    }
}
