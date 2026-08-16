# Neko Video — release R8 / ProGuard

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keep class com.neko.video.NekoVideoApp { *; }
-keep class com.neko.video.MainActivity { *; }
-keep class com.neko.video.player.service.PlaybackService { *; }
-keep class com.neko.video.database.** { *; }

# Media3 / ExoPlayer
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# Coil
-dontwarn coil.**
-keep class coil.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# Remote protocol stacks — keep entry surface; R8 still trees unused crypto/impls
-dontwarn com.hierynomus.**
-dontwarn net.schmizz.sshj.**
-dontwarn org.apache.commons.net.**
-dontwarn org.bouncycastle.**
-dontwarn net.i2p.crypto.eddsa.**
-dontwarn org.slf4j.**
-keep class com.hierynomus.** { *; }
-keep class net.schmizz.sshj.** { *; }
-keep class org.apache.commons.net.** { *; }
-keep class net.i2p.crypto.eddsa.** { *; }

# Miuix
-dontwarn top.yukonga.miuix.**
-keep class top.yukonga.miuix.** { *; }

-dontwarn com.materialkolor.**

# Optional EL API referenced by MBassador (smbj) — not used on Android
-dontwarn javax.el.**
-dontwarn jakarta.el.**
-dontwarn net.engio.mbassy.**
