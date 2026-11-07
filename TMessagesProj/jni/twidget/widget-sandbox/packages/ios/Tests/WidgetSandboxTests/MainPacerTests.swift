import XCTest
@testable import WidgetSandbox

final class MainPacerTests: XCTestCase {
    func testDeliveriesKeepTheirOrderAndLeaveTheMainRunLoopBetweenBudgets() {
        var order: [Int] = []
        var turns = 0
        let observer = CFRunLoopObserverCreateWithHandler(nil, CFRunLoopActivity.beforeTimers.rawValue, true, 0) { _, _ in turns += 1 }
        CFRunLoopAddObserver(CFRunLoopGetMain(), observer, .commonModes)
        defer { CFRunLoopRemoveObserver(CFRunLoopGetMain(), observer, .commonModes) }
        let done = expectation(description: "drained")
        DispatchQueue.global().async {
            for i in 0..<60 {
                MainPacer.shared.async {
                    XCTAssertTrue(Thread.isMainThread)
                    order.append(i)
                    Thread.sleep(forTimeInterval: 0.001)
                    if i == 59 { done.fulfill() }
                }
            }
        }
        wait(for: [done], timeout: 10)
        XCTAssertEqual(order, Array(0..<60))
        XCTAssertGreaterThan(turns, 6)
    }
}
