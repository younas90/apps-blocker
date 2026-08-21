# ---------------------------------------------------------------- CameraX
# CameraX resolves its default configuration reflectively. PushGateApp implements
# CameraXConfig.Provider so that path is no longer taken, but the extender/converter classes
# below are still looked up by name at runtime and must survive minification.
-keep class androidx.camera.camera2.Camera2Config { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.core.CameraXConfig { *; }
-keep class androidx.camera.core.impl.** { *; }
-keep class androidx.camera.camera2.internal.** { *; }
-keep class * implements androidx.camera.core.CameraXConfig$Provider { *; }
-dontwarn androidx.camera.**

# ---------------------------------------------------------------- MediaPipe
# Tasks-Vision is JNI + AutoValue + protobuf; almost all of it is reached reflectively.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class com.google.common.** { *; }
-keepclassmembers class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**
-dontwarn javax.lang.model.**

# ---------------------------------------------------------------- Room / WorkManager
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ---------------------------------------------------------------- PushGate entry points
# Declared in the manifest, so the OS instantiates them by name.
-keep class com.pushgate.app.PushGateApp { *; }
-keep class com.pushgate.app.block.BlockerAccessibilityService { *; }
-keep class com.pushgate.app.block.AdminReceiver { *; }
-keep class com.pushgate.app.block.BootReceiver { *; }
-keep class com.pushgate.app.block.WatchdogAlarmReceiver { *; }
-keep class com.pushgate.app.block.BlockerForegroundService { *; }
-keep class com.pushgate.app.block.BlockScreenActivity { *; }
-keep class com.pushgate.app.ui.challenge.ChallengeActivity { *; }
-keep class com.pushgate.app.MainActivity { *; }

# Keep line numbers so a crash report from a shared APK is still readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
