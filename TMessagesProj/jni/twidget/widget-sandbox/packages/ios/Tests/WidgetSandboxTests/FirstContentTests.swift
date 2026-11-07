import XCTest
@testable import WidgetSandbox

final class FirstContentTests: XCTestCase {
    func testSizeReportedWhileApplyingOpsCountsAsFirstContent() {
        let host = try! WidgetHost(html: "<div style='height:120px'>Hello</div>", theme: WidgetTheme(), width: 300, rendererFactory: { _, _, _ in SynchronousSizeRenderer() })
        defer { host.destroy() }
        let deadline = Date().addingTimeInterval(5)
        while host.diagnostics.firstContentMs == nil && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
        XCTAssertNil(host.view.window)
        XCTAssertNotNil(host.diagnostics.firstContentMs)
        XCTAssertEqual(host.diagnostics.height, 120)
    }
}

private final class SynchronousSizeRenderer: Renderer {
    let view = PlatformView(frame: CGRect(x: 0, y: 0, width: 300, height: 100))
    var onMessage: ((String) -> Void)?
    var onDiag: ((String, String) -> Void)?

    func load() {
        onMessage?("{\"type\":\"ready\"}")
    }

    func send(_ json: String) {
        guard let data = json.data(using: .utf8), let message = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any], let type = message["type"] as? String else {
            return
        }
        if type == "ops" {
            onMessage?("{\"type\":\"size\",\"width\":300,\"height\":120}")
        } else if type == "query", let qid = message["qid"] as? Int {
            DispatchQueue.main.async { [weak self] in
                self?.onMessage?("{\"type\":\"result\",\"qid\":\(qid),\"result\":null}")
            }
        }
    }

    func setTheme(_ theme: String) {}
    func evaluate(_ script: String, completion: @escaping (String?) -> Void) { completion(nil) }
    func destroy() {}
}
