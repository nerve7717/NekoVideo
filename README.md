# Neko Video

An Android video player for local files and network shares. The UI follows a MIUI / Miuix look; playback controls are inspired by AVKit.

[中文说明](README.zh-CN.md)

- Author: 起汐顺子
- Repository: https://github.com/qixishunzi/NekoVideo

## Features

- **Local library**: scan device videos, browse by recently played / folders / all, or pick a file or folder from the system picker
- **Network sources**: WebDAV, SMB, SFTP, FTP, and direct HTTP(S) URLs (including HLS / DASH)
- **Playback**: Media3 ExoPlayer with seek / volume / brightness gestures, long-press speed-up, resume, external subtitles, picture-in-picture, and background play
- **Library extras**: watch history and favorites; server passwords stored with Android Keystore
- **Appearance**: light / dark / follow system, Material You colors

## Requirements

- Android 14+ (`minSdk 34`)
- Android Studio Ladybug or newer
- JDK 17 (the JBR bundled with Android Studio is fine)

## Build

```bash
./gradlew :app:assembleDebug
```

Windows:

```bat
gradlew.bat :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

Sign release builds with your own keystore. Do not commit signing material.

## Stack

| Area | Choice |
|------|--------|
| UI | Jetpack Compose, [Miuix](https://github.com/miuix-kotlin-multiplatform/miuix) |
| Playback | AndroidX Media3 |
| Storage | Room, DataStore, Android Keystore |
| Network | OkHttp, SMBJ, SSHJ, Apache Commons Net |

## Privacy

Passwords stay on the device and are encrypted with Keystore AES-GCM. The app does not upload watch history or account data. LAN NAS access may use HTTP — only connect on networks you trust.

## License

[MIT](LICENSE) © 起汐顺子
