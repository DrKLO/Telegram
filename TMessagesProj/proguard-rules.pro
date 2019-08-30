-keep public class com.google.android.gms.* { public *; }
-keepnames @com.google.android.gms.common.annotation.KeepName class *
-keepclassmembernames class * {
    @com.google.android.gms.common.annotation.KeepName *;
}
-keep class org.telegram.** { *; }
-keep class com.google.android.exoplayer2.ext.** { *; }
-keep class com.google.android.exoplayer2.util.** { *; }
-dontwarn com.coremedia.**
-dontwarn org.telegram.**
-dontwarn com.google.android.exoplayer2.ext.**
-dontwarn com.google.android.exoplayer2.util.**
-dontwarn com.google.android.gms.**
-dontwarn com.google.common.cache.**
-dontwarn com.google.common.primitives.**
-dontwarn com.googlecode.mp4parser.**
# Use -keep to explicitly keep any other classes shrinking would remove
-dontoptimize
-dontobfuscate