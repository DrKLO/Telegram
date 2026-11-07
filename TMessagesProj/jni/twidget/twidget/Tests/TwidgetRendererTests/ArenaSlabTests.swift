import XCTest
import Darwin
import CQuickJS

final class ArenaSlabTests: XCTestCase {
    private func evaluate(_ ctx: OpaquePointer, _ code: String) {
        var bytes = Array(code.utf8)
        bytes.append(0)
        let value = bytes.withUnsafeBufferPointer { buffer in
            buffer.baseAddress!.withMemoryRebound(to: CChar.self, capacity: buffer.count) { qjs_eval(ctx, $0, buffer.count - 1, "memory.js", false) }
        }
        XCTAssertFalse(qjs_is_exception(value))
        qjs_free_value(ctx, value)
    }

    private func arenaBytes() -> Int {
        var address: mach_vm_address_t = 0
        var total = 0
        while true {
            var size: mach_vm_size_t = 0
            var info = vm_region_extended_info_data_t()
            var count = mach_msg_type_number_t(MemoryLayout<vm_region_extended_info_data_t>.size / MemoryLayout<natural_t>.size)
            var object: mach_port_t = 0
            let result = withUnsafeMutablePointer(to: &info) { $0.withMemoryRebound(to: Int32.self, capacity: Int(count)) { mach_vm_region(mach_task_self_, &address, &size, VM_REGION_EXTENDED_INFO, $0, &count, &object) } }
            if result != KERN_SUCCESS {
                break
            }
            if info.user_tag == UInt32(VM_MEMORY_APPLICATION_SPECIFIC_1) {
                total += Int(info.pages_dirtied) * Int(vm_page_size)
            }
            address += size
        }
        return total
    }

    func testCollectedAndFreedRuntimesGiveTheirArenasBack() {
        let start = arenaBytes()
        for _ in 0..<3 {
            let runtime = JS_NewRuntime()!
            let context = JS_NewContext(runtime)!
            evaluate(context, "globalThis.keep = []; for (let i = 0; i < 300000; i++) keep.push({ a: i, b: 'v' + i, c: [i] });")
            let peak = arenaBytes()
            XCTAssertGreaterThan(peak - start, 24 << 20)
            evaluate(context, "globalThis.keep = null;")
            JS_RunGC(runtime)
            XCTAssertLessThan(arenaBytes() - start, (peak - start) / 20)
            JS_FreeContext(context)
            JS_FreeRuntime(runtime)
            XCTAssertEqual(arenaBytes(), start)
        }
    }
}
