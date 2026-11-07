import XCTest
@testable import WidgetSandbox

/// CpuBudget.swift against the fixture shared with cpu-budget.ts, CpuBudget.kt and cpu_budget.cpp
/// (tests/fixtures/cpu-budget.json, copied here by `pnpm build:ios`).
final class CpuBudgetTests: XCTestCase {
    func testSharedFixture() throws {
        let url = try XCTUnwrap(Bundle.module.url(forResource: "cpu-budget", withExtension: "json", subdirectory: "Fixtures"))
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let cases = try XCTUnwrap(root["cases"] as? [[String: Any]])
        XCTAssertGreaterThan(cases.count, 5)
        for c in cases {
            let name = c["name"] as? String ?? "?"
            let cfg = try XCTUnwrap(c["config"] as? [String: Double])
            var b = CpuBudget(cpuLimitMs: cfg["cpuLimitMs"]!, cpuQuotaMs: cfg["cpuQuotaMs"]!, cpuQuotaWindowMs: cfg["cpuQuotaWindowMs"]!, cpuKillAfterMs: cfg["cpuKillAfterMs"]!)
            for (i, step) in try XCTUnwrap(c["steps"] as? [[String: Any]]).enumerated() {
                let at = (step["at"] as? NSNumber)?.doubleValue ?? 0
                let where_ = "\(name): step \(i) @\(Int(at))"
                if let kind = step["decide"] as? String {
                    let expect = try XCTUnwrap(step["expect"] as? [String: Any])
                    let want: CpuDecision = (expect["run"] as? Bool) == true ? .run(sliceMs: (expect["sliceMs"] as! NSNumber).doubleValue) : .defer_(retryAt: (expect["retryAt"] as! NSNumber).doubleValue)
                    XCTAssertEqual(b.decide(kind, now: at), want, where_)
                } else if let kind = step["record"] as? String {
                    b.record(kind, cpuMs: (step["cpuMs"] as! NSNumber).doubleValue, now: at)
                } else if let kill = step["kill"] as? Bool {
                    XCTAssertEqual(b.shouldKill(at), kill, where_)
                } else if let used = step["used"] as? NSNumber {
                    XCTAssertEqual(b.used(at), used.doubleValue, accuracy: 1e-9, where_)
                } else if let q = step["queries"] as? [NSNumber] {
                    XCTAssertEqual(CpuBudget.queryLimitHit(queries: q[0].intValue, queryMs: q[1].doubleValue), step["expect"] as? String, where_)
                } else if let cost = step["opsCost"] as? [NSNumber] {
                    XCTAssertEqual(CpuBudget.opsCostMs(ops: cost[0].intValue, canvasCommands: cost[1].intValue), (step["expect"] as! NSNumber).doubleValue, accuracy: 1e-9, where_)
                } else {
                    XCTFail("\(where_): unknown step")
                }
            }
        }
    }
}
