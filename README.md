# HyperLockMusic

基于 LSPosed（libxposed API 102）的 **HyperOS 4** 锁屏音乐模块：在锁屏与息屏（AOD）重绘专辑壁纸、大封面 / 沉浸封面与歌词，并扩展媒体控件与息屏行为。

> **English:** LSPosed module for **HyperOS 4** that redraws the lock screen & AOD with album-art wallpapers, large / immersive covers, synced lyrics, expanded AOD media controls, and a minimal clock. **MIUI and older HyperOS versions are not supported.**

当前版本：**0.1.0**

项目地址：`https://github.com/leowalk0613/HyperLockMusic`

## 功能

- **音乐锁屏壁纸** — 按当前曲目合成模糊专辑背景；切歌走串行壁纸管线（`TrackWallpaperCoordinator`），减少回滚与闪烁
- **大专辑 / 沉浸封面** — 方形大封面（大小、底边位置、圆角）或 Monet 取色沉浸封面；专辑样式与歌词样式自动绑定
- **沉浸暗角渐变** — 沉浸封面壁纸上下沿暗色渐变，范围随专辑位置动态伸缩
- **歌词优先** — 开启歌词时，有歌词（或切歌等待新词）优先占位，无歌词才显示方形专辑
- **锁屏歌词** — 毛玻璃 / MiBlur 取色歌词条、翻译互换（仅在有翻译时生效）、字号 / 宽度 / 位置 / 对齐；支持沉浸歌词（大字当前行）
- **AOD 歌词** — 息屏同步歌词；切歌按版本 / 内容快照刷新，Provider 冷启动可从磁盘恢复
- **AOD 媒体控件** — 可选息屏保持展开并实时进度条，减少压缩再展开的动画
- **简洁时钟** — 音乐锁屏时隐藏系统大时钟，顶部显示可调字号 / 位置的时间日期
- **通知过滤** — 音乐锁屏激活时隐藏无关通知，保留媒体控件与歌词数据源进程
- **媒体退出** — 划掉媒体、杀播放器或无活跃会话时自动退出音乐锁屏（建议开启通知使用权）
- **应用白名单** — 可选限定哪些音乐应用可触发音乐锁屏
- **网易云高清封面** — 仅在网易云音乐播放时，经媒体会话识别曲目后拉取官方高清图替换前景大专辑（无需 hook 网易云；其他播放器不匹配）；数据归平台所有，图像版权归原作者所有
- **锁屏常亮** — 可选音乐锁屏时保持屏幕常亮
- **禁用息屏壁纸缩放** — 音乐锁屏息屏时去掉 HyperOS 壁纸缩放动画，仅保留压暗
- **歌名括号** — 媒体标题括号：原样 / 右侧缩小 / 隐藏 / 分行

锁屏 / AOD 歌词数据由 **LyricFocus** 的外部渲染功能推送到本模块 ContentProvider；本模块在 SystemUI / AOD 内消费并上屏。歌词与配置落盘，应用进程被杀后冷启动仍可恢复。

## 环境要求

| 项目 | 说明 |
|------|------|
| 系统 | **HyperOS 4**（以 `4.0.0.14` 为开发与验证基准） |
| Android | **Android 17**（开发机） |
| 框架 | LSPosed（API **102+**，libxposed） |
| Root | 需要（LSPosed；应用内「重启界面」亦需 `su`） |
| 歌词 | 建议安装并启用 **LyricFocus**（外部渲染） |

**测试机型：** Xiaomi 14，HyperOS 4 `4.0.0.14`，Android 17。

> **不支持 MIUI。** 钩子针对 HyperOS 4 锁屏 / SystemUI / AOD 编写，MIUI 及 HyperOS 1/2/3 无法正常使用。其他机型与版本未充分测试。

## 安装

1. 从 Releases（`https://github.com/leowalk0613/HyperLockMusic/releases`）下载 APK，或自行编译
2. 安装 **LyricFocus**，并开启其外部渲染，作为锁屏 / AOD 歌词数据源
3. 在 LSPosed 中启用 **HyperLockMusic**
4. 勾选作用域（见下表），**至少包含 `com.android.systemui`**
5. 在应用内开启 **通知使用权**（更准确检测播放与自动退出）
6. 重启系统界面（LSPosed，或应用右上角「重启界面」）

### LSPosed 作用域

| 包名 | 用途 |
|------|------|
| `com.android.systemui` | 锁屏壁纸、歌词、大封面、媒体控件、时钟、通知栈（**必选**） |
| `com.miui.aod` | 息屏 AOD 歌词 |
| `miui.systemui.plugin` | SystemUI 插件层兼容 |

模块声明作用域见 `app/src/main/resources/META-INF/xposed/scope.list`（无需勾选音乐应用包名）。

## 使用

1. 播放音乐并进入锁屏
2. 在锁屏**媒体控件左侧**自定义按钮开启 / 关闭**音乐锁屏**
3. **右侧**按钮控制歌词显示（需主界面「歌词」总开关已开启）
4. 打开 HyperLockMusic 调整样式；多数配置经 ContentProvider 即时下发，钩子结构变更时可点右上角 **重启界面**

### 应用内设置

| 入口 | 内容 |
|------|------|
| **主界面** | 专辑封面 / 歌词总开关（点标题进子页）、其他设置、音乐应用白名单、权限状态、关于 |
| **专辑封面** | 大封面 / 沉浸封面（含效果图预览）、大小位置圆角、上下暗角渐变、网易云高清封面 |
| **歌词样式** | 锁屏显示开关、普通 / 沉浸歌词（含效果图预览）、毛玻璃、翻译互换等 |
| **其他设置** | 壁纸模糊、简洁时钟、禁用息屏壁纸缩放、锁屏常亮、AOD 完整媒体控件、歌名括号 |
| **音乐应用白名单** | 启用后仅白名单应用可开音乐锁屏 |
| **权限** | 通知使用权、Root 权限（重启 SystemUI） |
| **关于** | 版本、项目地址、开源致谢与说明 |

主界面「歌词」总开关关闭后，整个歌词功能不可用；歌词样式页「显示歌词」仅控制锁屏是否展示。

### 其他

主菜单之外另有少量辅助能力，需自行在应用内探索发现。🥚

## 从源码构建

一律使用项目 Wrapper，**不要依赖 Android Studio 同步 / 编译**：

```bash
# Windows
.\gradlew.bat assembleDebug
.\gradlew.bat :app:installDebug
.\gradlew.bat testDebugUnitTest

# Linux / macOS
./gradlew assembleDebug
./gradlew :app:installDebug
./gradlew testDebugUnitTest
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

需要 Android SDK（`compileSdk 34`）与 **JDK 17**。可通过 `.env` 或环境变量 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 指定 SDK。

核心策略类（壁纸管线、歌词门闩、专辑取色恢复等）配有 JVM 单元测试，位于 `app/src/test/java/`。协作者约定见 [`AGENTS.md`](AGENTS.md)。

## 调试

```bash
adb logcat -s HyperLockMusic HyperLockMusic_Lyric HyperLockMusic_Wallpaper HyperLockMusic_Config HyperLockMusic_AlbumArt HyperLockMusic_Provider
```

改动 SystemUI / AOD 钩子后，通常需重启 SystemUI；AOD 歌词异常时也可重启 `com.miui.aod`。

## 致谢

感谢以下开源项目：

- **LSPosed / libxposed API** — 提供 Xposed 模块运行时与挂钩能力，本模块以此注入 SystemUI / AOD（https://github.com/LSPosed/LSPosed）
- **Android Jetpack（AndroidX）** — 应用层基础组件（Core KTX、AppCompat 等）（https://developer.android.com/jetpack）
- **Material Components for Android** — 设置页 Material 3 控件与主题（https://github.com/material-components/material-components-android）
- **Material Color Utilities** — 沉浸封面等场景的 Monet / HCT 取色（https://github.com/material-foundation/material-color-utilities）
- **StackBlur（Mario Klingemann）** — 专辑壁纸模糊所用的高效盒式模糊算法（http://www.quasimondo.com/StackBlurForCanvas/StackBlurDemo.html）
- **HyperLyric** — AOD「完整媒体控件」参考其禁用媒体卡片折叠的实现思路（https://github.com/limczhh/HyperLyric）

## 免责声明

- 本模块通过 Xposed 钩子修改 SystemUI / AOD 行为，**仅供学习与个人使用**
- 修改系统界面存在风险；作者不对数据丢失、系统异常或保修问题负责
- 与小米、HyperOS 及任何音乐应用官方无关
- **仅面向 HyperOS 4；MIUI 不在支持范围内**
- 「音乐锁屏」指本模块功能，不是系统自带能力
- 「网易云高清封面」仅在网易云音乐播放时生效（无需 hook 网易云）：**相关数据归平台所有，图像版权归原作者所有**；仅供个人学习与本机显示，不会用其他播放器去匹配网易云封面

## 许可证

MIT License（见 `LICENSE`）。

## 作者

leowalk（`https://github.com/leowalk0613`）
