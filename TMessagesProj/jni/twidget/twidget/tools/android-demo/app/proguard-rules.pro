# The native code looks these up by name and signature.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class org.telegram.widgetsandbox.litehtml.LitehtmlBackend {
    public <methods>;
}
-keep interface org.telegram.widgets.WidgetHostNative$RendererBridge { *; }
-keep interface org.telegram.widgets.WidgetHostNative$Listener { *; }
-keep class * implements org.telegram.widgets.WidgetHostNative$RendererBridge { public <methods>; }
-keep class * implements org.telegram.widgets.WidgetHostNative$Listener { public <methods>; }
