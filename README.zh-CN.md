# Neko Video

本地与网络一体的 Android 视频播放器。界面走 MIUI / Miuix 风格，播放器参考 AVKit 的控制体验。

[English](README.md)

- 作者：起汐顺子
- 仓库：https://github.com/qixishunzi/NekoVideo

## 功能

- **本地媒体库**：扫描设备视频，按最近播放 / 文件夹 / 全部浏览，支持从系统选择文件或文件夹
- **网络源**：WebDAV、SMB、SFTP、FTP，以及直接打开 HTTP(S) 地址（含 HLS / DASH）
- **播放**：Media3 ExoPlayer，手势快进/调音量亮度、长按倍速、续播、外挂字幕、画中画、后台播放
- **资料**：播放历史、收藏；服务器密码用 Android Keystore 加密保存
- **外观**：浅色 / 深色 / 跟随系统，Material You 取色

## 环境要求

- Android 14+（`minSdk 34`）
- Android Studio Ladybug 或更新版本
- JDK 17（Android Studio 自带 JBR 即可）

## 构建

```bash
./gradlew :app:assembleDebug
```

Windows：

```bat
gradlew.bat :app:assembleDebug
```

产物在 `app/build/outputs/apk/debug/`。

发布包请用你自己的签名配置，不要把 keystore 提交进仓库。

## 技术栈

| 模块 | 选型 |
|------|------|
| UI | Jetpack Compose、[Miuix](https://github.com/miuix-kotlin-multiplatform/miuix) |
| 播放 | AndroidX Media3 |
| 存储 | Room、DataStore、Android Keystore |
| 网络 | OkHttp、SMBJ、SSHJ、Apache Commons Net |

## 隐私

密码只存在本机，经 Keystore AES-GCM 加密。应用不上传播放记录或账号信息。局域网 NAS 可能使用 HTTP，请只连你信任的网络。

## 许可

[MIT](LICENSE) © 起汐顺子
