# Called from JNI (quickjs_jni.c) by name; @Keep covers them too, this is the belt.
-keepclassmembers class org.telegram.widgetsandbox.QuickJSContext {
    private java.lang.Object dispatch(int, byte[][]);
    private void raise(byte[]);
}
-keep class org.telegram.widgetsandbox.HostReturn$ArrayBuffer { byte[] bytes; }
-keep class org.telegram.widgetsandbox.HostReturn$Null { *; }
-keepclassmembers class org.telegram.widgetsandbox.RendererWebView$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
