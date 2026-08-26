# HyperLockMusic

基于 [LSPosed](https://github.com/LSPosed/LSPosed) 的 **HyperOS 4** 锁屏音乐模块：在锁屏与息屏（AOD）界面重绘专辑壁纸、大封面与歌词，并扩展媒体控件行为。

> **English:** LSPosed module for **HyperOS 4** that redraws lock screen & AOD with album art wallpapers, large cover overlays, synced lyrics, and enhanced media controls. **MIUI and older HyperOS versions are not supported.**

## 功能

- **音乐锁屏壁纸** — 将当前播放专辑模糊合成锁屏背景，支持强度与暗色遮罩调节
- **大专辑封面** — 锁屏 overlay 显示方形封面，可配置大小、位置、圆角；支持沉浸专辑模式
- **锁屏歌词** — 毛玻璃歌词条、翻译互换、颜色/字号/位置/对齐；支持沉浸歌词模式
- **AOD 歌词** — 息屏界面同步显示歌词（需勾选 `com.miui.aod` 作用域）
- **媒体控件** — AOD 下保持媒体卡片展开并实时更新进度条；歌名括号样式可定制
- **通知过滤** — 音乐锁屏激活时在锁屏隐藏无关通知，保留媒体控件
- **应用白名单** — 可选限定哪些音乐应用可触发音乐锁屏
- **网易云 HD 封面** — 可选拉取更高分辨率专辑图（需勾选对应音乐应用作用域）

## 环境要求

| 项目 | 说明 |
|------|------|
| 系统 | **HyperOS 4**（以 `4.0.0.14` 为开发与验证基准） |
| Android | **Android 17** |
| 框架 | LSPosed（API 102+） |
| Root | 需要（LSPosed 依赖；应用内「重启界面」亦需 `su`） |

**参考实机：** Xiaomi `23127PN0CC`，HyperOS 4 `4.0.0.14`，Android 17。

> **不支持 MIUI。** 本模块钩子针对 HyperOS 4 锁屏与 SystemUI 结构编写，MIUI 及 HyperOS 1/2/3 无法正常使用。其他机型与系统版本未测试，不保证可用。

## 安装

1. 从 [Releases](https://github.com/leowalk0613/HyperLockMusic/releases) 下载 APK，或自行编译 debug 包
2. 在 LSPosed 中启用 **HyperLockMusic**
3. 勾选作用域（见下表），**至少包含 `com.android.systemui`**
4. 重启系统界面（LSPosed 内操作，或应用右上角「重启界面」）

### LSPosed 作用域

| 包名 | 用途 |
|------|------|
| `com.android.systemui` | 锁屏壁纸、歌词、大封面、媒体控件（**必选**） |
| `com.miui.aod` | 息屏 AOD 歌词 |
| `miui.systemui.plugin` | SystemUI 插件层兼容 |
| 各音乐应用包名 | 白名单/歌词通知监听（按需勾选，如 `com.netease.cloudmusic`） |

## 使用

1. 播放音乐并进入锁屏
2. 在锁屏**媒体控件左侧**自定义按钮开启/关闭**音乐锁屏**
3. **右侧**按钮控制歌词显示
4. 打开 HyperLockMusic 应用调整样式；修改后点击右上角 **重启界面** 使 Xposed 钩子生效

应用内设置入口：

- **专辑封面** — 大封面、沉浸专辑、位置与圆角
- **模糊背景** — 模糊半径、暗色遮罩
- **媒体控件** — AOD 完整媒体控件、歌名括号处理
- **歌词样式** — 字号、颜色、毛玻璃、沉浸歌词、对齐与位置
- **音乐应用白名单** — 限制触发来源

### 其他

主菜单之外另有少量辅助能力，需自行在应用内探索发现。🥚

## 从源码构建

```bash
# Windows
.\gradlew.bat assembleDebug

# Linux / macOS
./gradlew assembleDebug
```

构建产物：`app/build/outputs/apk/debug/app-debug.apk`

安装到已连接设备：

```bash
.\gradlew.bat :app:installDebug   # Windows
./gradlew :app:installDebug       # Linux / macOS
```

需要 Android SDK（`compileSdk 34`）与 JDK 17。可通过环境变量 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 指定 SDK 路径。

## 调试

Logcat 过滤标签前缀：

```
HyperLockMusic
```

示例：

```bash
adb logcat -s HyperLockMusic HyperLockMusic_Lyric HyperLockMusic_Wallpaper
```

## 免责声明

- 本模块通过 Xposed 钩子修改 SystemUI 行为，**仅供学习与个人使用**
- 修改系统界面存在风险，请在了解后果后使用；作者不对数据丢失、系统异常或保修问题负责
- HyperLockMusic 与小米、HyperOS 及任何音乐应用官方无关
- **仅面向 HyperOS 4；MIUI 不在支持范围内**
- 「音乐锁屏」指模块提供的锁屏壁纸功能，不是系统自带能力

## 许可证

本项目采用 [MIT License](LICENSE)。

- 可自由使用、修改、合并、发布与商用
- 需在副本中保留版权声明与许可全文
- 本软件按「原样」提供，不提供任何担保

## 作者

[leowalk](https://github.com/leowalk0613)
