# MediaPipe Tasks uses reflection + JNI heavily.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**

# Room generated code
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Entry points declared in the manifest must survive shrinking.
-keep class com.pushgate.app.block.BlockerAccessibilityService { *; }
-keep class com.pushgate.app.block.AdminReceiver { *; }
-keep class com.pushgate.app.block.BootReceiver { *; }
-keep class com.pushgate.app.block.BlockerForegroundService { *; }
