// SwiftUI wrapper: a chat-bubble-sized widget whose height follows the content.
// One struct per UI framework; the model below is shared.

import SwiftUI

#if canImport(UIKit)
@available(iOS 13.0, *)
public struct WidgetView: UIViewRepresentable {
    public let host: WidgetHost

    public init(host: WidgetHost) { self.host = host }

    public func makeUIView(context: Context) -> UIView { host.view }
    public func updateUIView(_ uiView: UIView, context: Context) {}
}
#elseif canImport(AppKit)
@available(macOS 10.15, *)
public struct WidgetView: NSViewRepresentable {
    public let host: WidgetHost

    public init(host: WidgetHost) { self.host = host }

    public func makeNSView(context: Context) -> NSView { host.view }
    public func updateNSView(_ nsView: NSView, context: Context) {}
}
#endif

/// Observable façade over WidgetHost for SwiftUI screens.
@available(macOS 10.15, iOS 13.0, *)
public final class WidgetModel: ObservableObject {
    @Published public private(set) var diagnostics = WidgetDiagnostics()
    public let host: WidgetHost

    /// `rendererFactory`: the renderer; the WebView renderer when nil (see Renderer.swift).
    public init(html: String, theme: WidgetTheme, width: CGFloat, options: WidgetHost.Options = WidgetModel.defaultOptions, rendererFactory: RendererFactory? = nil) throws {
        host = try WidgetHost(html: html, theme: theme, width: width, options: options, rendererFactory: rendererFactory)
        host.onChange = { [weak self] d in self?.diagnostics = d }
    }

    /// The API before SDK v1: a plain colour scheme, `"dark"` or `"light"`.
    @available(*, deprecated, message: "pass a WidgetTheme (SDK v1)")
    public convenience init(html: String, theme: String, width: CGFloat, options: WidgetHost.Options = WidgetModel.defaultOptions, rendererFactory: RendererFactory? = nil) throws {
        try self.init(html: html, theme: WidgetTheme(colorScheme: theme), width: width, options: options, rendererFactory: rendererFactory)
    }

    /// A debug build of quickjs-ng is several times slower than release, so the
    /// demo and tests give the CPU quota more room than the product default (300 ms/s).
    public static var defaultOptions: WidgetHost.Options {
        var o = WidgetHost.Options()
        #if DEBUG
        o.cpuQuotaMs = 1000
        #endif
        return o
    }

    public func setTheme(_ theme: WidgetTheme) { host.setTheme(theme) }
    @available(*, deprecated, message: "pass a WidgetTheme (SDK v1)")
    public func setTheme(_ theme: String) { setTheme(WidgetTheme(colorScheme: theme)) }
    deinit { host.destroy() }
}
