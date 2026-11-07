import XCTest
@testable import WidgetSandbox

final class MappedArgumentsGCTests: XCTestCase {
    func testGCWhileMappedArgumentsAreLiveDoesNotCorruptTheHeap() throws {
        let source = """
        function wrap(c){return function(a,b){let n=arguments.length;return n===0?c.call(this):n===1?c.call(this,a):c.call(this,a,b)}}
        var keep=0;
        var f=wrap(function(x,y){var t=[];for(var k=0;k<2000;k++){var o={k,x,y};o.self=o;t.push(o);}keep+=t.length;return [x,y]});
        for(let i=0;i<300;i++){f({i});f({i},{j:i});}
        keep
        """
        let js = QuickJSContext(memoryLimitBytes: 256 << 20, maxStackBytes: JSThread.jsStackLimit)
        let result = try JSThread(name: "mapped-arguments").sync { Result { try js.evaluate(source, filename: "mapped.js") } }.get()
        XCTAssertEqual(result.number, 1_200_000)
        JSThread(name: "mapped-arguments-gc").sync { js.collectGarbage() }
    }
}
