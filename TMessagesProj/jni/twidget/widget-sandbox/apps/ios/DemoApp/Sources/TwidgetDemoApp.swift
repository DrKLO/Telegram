import SwiftUI

@main
struct TwidgetDemoApp: App {
    init() {
        DemoAutorun.applyLaunchArguments()
        #if os(iOS)
        // The chat-feed autorun with `prewarmAtLaunch`: pools fill now, the way a messenger would warm up at launch.
        if DemoAutorun.kind == "feed" {
            let config = FeedConfig.fromLaunchArguments()
            if config.prewarmAtLaunch { config.applyGlobals(widgetWidth: FeedConfig.defaultWidgetWidth, parkingView: nil) }
        }
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                #if os(macOS)
                .frame(minWidth: 840, minHeight: 640)
                #endif
        }
    }
}
