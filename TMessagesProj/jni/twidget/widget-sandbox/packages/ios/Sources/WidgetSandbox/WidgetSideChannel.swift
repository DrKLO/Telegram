import Foundation

/// An optional channel next to the DOM protocol (the twidget kit's 3D surfaces, for
/// example): it installs its own trusted functions in the widget's runtime after the
/// sandbox bundle booted, talks to the renderer directly, and follows the host's
/// visibility and lifetime. Hosts get them from `WidgetHost.Options.sideChannels`.
public protocol WidgetSideChannel: AnyObject {
    /// Diagnostics, delivered on the host's executor.
    var onDiag: ((String, String) -> Void)? { get set }
    /// On the engine's thread, after the sandbox bundle and before widget code.
    func install(in js: QuickJSContext)
    /// The renderer the widget draws into; a channel ignores renderers it cannot use.
    func connect(_ renderer: Renderer)
    func setVisible(_ visible: Bool)
    func destroy()
}
