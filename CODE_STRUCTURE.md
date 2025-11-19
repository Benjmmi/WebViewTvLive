# AITVLive 代码结构说明

## 项目简介

AITVLive 是一个基于 Android 的电视直播应用，使用腾讯 X5 WebView 内核加载各大电视台的直播页面，实现自动播放和全屏控制。

## 核心技术栈

- **语言**: Kotlin
- **WebView**: 腾讯 X5 内核
- **网络**: OkHttp3
- **JSON**: Gson
- **异步**: Kotlin Coroutines
- **UI**: Android View System

---

## 项目架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    MainActivity (主Activity)                 │
│                   管理UI、事件、生命周期                       │
└────────┬──────────────────────┬──────────────────────────────┘
         │                      │
         │                      │
    ┌────▼─────┐         ┌─────▼──────┐
    │ Widget   │         │ Playlist   │
    │  视图组件 │         │  播放列表   │
    └────┬─────┘         └─────┬──────┘
         │                      │
         │                      │
┌────────▼──────────┐    ┌──────▼─────────────────┐
│ WebpageAdapter    │    │ PlaylistManager        │
│ WebView           │    │ 网络下载和缓存          │
│ (核心播放器)       │    │                        │
└────────┬──────────┘    └────────────────────────┘
         │
         │
    ┌────▼──────────────┐
    │ WebpageAdapter    │
    │ (适配器模式)       │
    │ 18个不同电视台适配 │
    └───────────────────┘
```

---

## 目录结构

```
app/src/main/
├── java/com/vasthread/aitv/
│   ├── activity/          # Activity层
│   │   └── MainActivity.kt         ⭐ 主Activity，应用的核心控制器
│   │
│   ├── adapter/           # WebView适配器层
│   │   ├── WebpageAdapter.kt       # 适配器抽象基类
│   │   ├── WebpageAdapterManager.kt # 适配器管理器
│   │   ├── CommonWebpageAdapter.kt # 通用适配器
│   │   └── [15个电视台特定适配器]  # CCTV, TVB, Youtube等
│   │
│   ├── app/               # 应用层
│   │   └── LiveApplication.kt      # Application类，应用启动入口
│   │
│   ├── misc/              # 工具类
│   │   ├── Global.kt              ⭐ 全局Context和SharedPreferences
│   │   └── Utils.kt                # 工具函数
│   │
│   ├── playlist/          # 播放列表层
│   │   ├── Channel.kt             ⭐ 频道数据模型
│   │   ├── ChannelGroup.kt        # 频道分组模型
│   │   ├── Playlist.kt            ⭐ 播放列表模型
│   │   └── PlaylistManager.kt     ⭐ 播放列表管理器（网络下载）
│   │
│   ├── settings/          # 设置层
│   │   ├── SettingItem.kt         # 设置项模型
│   │   └── SettingsManager.kt     # 设置管理器
│   │
│   └── widget/            # UI组件层
│       ├── WebpageAdapterWebView.kt    ⭐ 核心WebView组件
│       ├── ChannelPlayerView.kt        # 频道播放器视图
│       ├── PlaylistView.kt             # 播放列表视图
│       ├── ChannelSettingsView.kt      # 频道设置视图
│       ├── AppSettingsView.kt          # 应用设置视图
│       ├── ChannelBarView.kt           # 频道信息栏
│       ├── ExitConfirmView.kt          # 退出确认对话框
│       ├── WaitingView.kt              # 等待提示视图
│       └── UiLayout.kt                 # UI布局管理
│
├── assets/
│   ├── web/
│   │   └── index.html                  # WebView加载的HTML页面
│   └── default_js_template.js          ⭐ JavaScript注入模板
│
└── res/                   # Android资源文件
    ├── layout/            # 布局文件
    ├── drawable/          # 图片资源
    └── values/            # 颜色、字符串等
```

**注**: ⭐ 标记的文件是已添加详细注释的核心文件

---

## 核心模块详解

### 1. MainActivity（主Activity）

**文件**: `activity/MainActivity.kt`
**作用**: 应用的核心控制器

#### 主要职责

- 初始化和管理所有UI组件
- 处理用户输入事件（遥控器、按键、触摸）
- 管理UI模式切换
- 保存和恢复用户观看状态

#### UI模式

```kotlin
enum class UiMode {
    STANDARD,           // 标准播放模式
    CHANNELS,           // 频道列表模式
    EXIT_CONFIRM,       // 退出确认模式
    APP_SETTINGS,       // 应用设置模式
    CHANNEL_SETTINGS    // 频道设置模式
}
```

#### 关键方法

| 方法 | 说明 |
|------|------|
| `onCreate()` | 初始化UI、监听器、频道列表 |
| `setupUi()` | 设置全屏、保持屏幕常亮 |
| `setupListener()` | 建立各组件之间的通信 |
| `initChannels()` | 加载播放列表，恢复上次观看 |
| `dispatchKeyEvent()` | 处理遥控器按键事件 |

---

### 2. WebpageAdapterWebView（核心WebView）

**文件**: `widget/WebpageAdapterWebView.kt`
**作用**: 自定义WebView，加载和控制电视直播页面

#### 主要功能

1. **网页加载管理**
   - 智能URL切换
   - 页面重置机制
   - 加载进度监控

2. **视频全屏控制**
   - 自动检测视频并全屏
   - 支持多种画面比例（16:9, 4:3）
   - 动态调整视频尺寸

3. **JavaScript交互**
   - 注入自定义脚本
   - 双向通信（Java ↔ JavaScript）

4. **播放状态监控**
   - 检测视频是否播放
   - 自动显示/隐藏等待提示

#### JavaScript接口

```kotlin
@JavascriptInterface
fun schemeEnterFullscreen()      // 请求进入全屏
@JavascriptInterface
fun notifyVideoPlaying()         // 通知视频正在播放
@JavascriptInterface
fun enablePlayCheck()            // 启用播放检查
@JavascriptInterface
fun disablePlayCheck()           // 禁用播放检查
@JavascriptInterface
fun setVideoSize(w: Int, h: Int) // 设置视频尺寸
```

#### 关键回调

```kotlin
var onWaitingStateChanged: ((Boolean) -> Unit)?   // 等待状态变化
var onPageFinished: ((String) -> Unit)?           // 页面加载完成
var onProgressChanged: ((Int) -> Unit)?           // 加载进度变化
var onFullscreenStateChanged: ((Boolean) -> Unit)? // 全屏状态变化
var onVideoRatioChanged: ((Fraction) -> Unit)?    // 视频比例变化
```

---

### 3. PlaylistManager（播放列表管理器）

**文件**: `playlist/PlaylistManager.kt`
**作用**: 管理频道列表的下载、解析和缓存

#### 主要功能

1. **网络下载**
   - 从GitHub下载频道列表
   - 使用OkHttp3网络请求
   - 失败自动重试（10秒间隔）

2. **格式解析**
   - 解析自定义M3U格式
   - 转换为JSON格式
   - 支持频道分组

3. **缓存管理**
   - 保存到本地文件
   - 24小时缓存过期
   - 检测内容变化

#### M3U格式示例

```text
CCTV,#genre#
CCTV-1 综合,http://example.com/cctv1.m3u8
CCTV-2 财经,http://example.com/cctv2.m3u8
卫视,#genre#
湖南卫视,http://example.com/hunan.m3u8
```

#### 关键方法

| 方法 | 说明 |
|------|------|
| `loadPlaylist()` | 加载播放列表（缓存或网络） |
| `setPlaylistUrl()` | 设置新的播放列表URL |
| `parseM3UTextToJson()` | 解析M3U为JSON |
| `requestUpdatePlaylist()` | 后台更新播放列表 |

---

### 4. 数据模型

#### Channel（频道）

**文件**: `playlist/Channel.kt`

```kotlin
data class Channel(
    var name: String,           // 频道名称
    var groupName: String,      // 所属分组
    var urls: List<String>      // 播放源列表（支持多源）
)
```

- **多源支持**: 一个频道可以有多个播放URL
- **自动选择**: 根据用户上次选择的源索引播放
- **分组管理**: 按电视台类型分组（CCTV、卫视等）

#### Playlist（播放列表）

**文件**: `playlist/Playlist.kt`

```kotlin
data class Playlist(
    var title: String,                          // 播放列表标题
    val groups: MutableList<ChannelGroup>       // 频道分组列表
)
```

- **三级结构**: `Playlist → ChannelGroup → Channel`
- **扁平化**: 可以将所有频道提取为扁平列表
- **索引查找**: 快速定位频道在列表中的位置

---

### 5. JavaScript注入机制

**文件**: `assets/default_js_template.js`
**作用**: 控制网页中的视频播放

#### 工作流程

```
1. WebView加载网页
   ↓
2. 页面加载完成 (progress = 100)
   ↓
3. WebpageAdapterWebView 注入 JavaScript
   ↓
4. JavaScript 查找视频元素
   ↓
5. 监听视频事件 (play, pause, timeupdate)
   ↓
6. 调用 Android 方法 (window.main.xxx)
   ↓
7. Android 控制全屏和UI
```

#### 核心功能

| 功能 | 说明 |
|------|------|
| `wvt_loop()` | 主循环，定期检查视频元素 |
| `wvt_setupVideo()` | 设置视频属性和事件监听器 |
| `wvt_onTimeUpdate()` | 视频播放时通知Android |
| `wvt_fullscreenVideo()` | 触发全屏播放 |

#### 模板变量（运行时替换）

```javascript
var selector = '%selector%';                 // 视频元素选择器
var enterFullscreenButton = '%enter_fullscreen_button%';  // 全屏按钮
var playingCheckEnabled = %playing_check_enabled%;  // 是否启用播放检查
```

---

### 6. Adapter适配器模式

**文件**: `adapter/WebpageAdapter.kt` 及其子类

#### 设计模式

```
WebpageAdapter (抽象基类)
    ↓
    ├── CommonWebpageAdapter (通用适配器)
    ├── CctvWebpageAdapter (CCTV适配)
    ├── TvbWebpageAdapter (TVB适配)
    ├── YoutubeWebpageAdapter (Youtube适配)
    └── [其他12个电视台适配器]
```

#### 适配器职责

- 提供电视台特定的 **UserAgent**
- 提供电视台特定的 **JavaScript代码**
- 提供电视台特定的 **全屏方法**

#### 为什么需要适配器？

不同电视台的网页结构不同：

| 电视台 | 视频元素 | 全屏方式 | 特殊处理 |
|--------|----------|----------|----------|
| CCTV | `#player_video` | 点击全屏按钮 | 需要特定UserAgent |
| TVB | `video` | 直接全屏 | 需要禁用某些脚本 |
| Youtube | `.html5-main-video` | 点击全屏按钮 | 需要模拟移动设备 |

---

## 数据流图

### 频道列表更新流程

```
[GitHub服务器]
    ↓ (HTTP GET)
PlaylistManager.requestUpdatePlaylist()
    ↓ (下载M3U文本)
PlaylistManager.parseM3UTextToJson()
    ↓ (解析为JSON)
Playlist.createFromAllChannels()
    ↓ (创建Playlist对象)
MainActivity.playlistView.playlist = newPlaylist
    ↓ (更新UI)
[用户看到新的频道列表]
```

### 频道播放流程

```
[用户选择频道]
    ↓
PlaylistView.onChannelSelectCallback
    ↓
MainActivity 保存选择到 SharedPreferences
    ↓
ChannelPlayerView.channel = selectedChannel
    ↓
WebpageAdapterWebView.loadUrl(channel.url)
    ↓
WebView 加载电视台网页
    ↓
注入 JavaScript (progress = 100)
    ↓
JavaScript 查找视频元素
    ↓
JavaScript 调用 window.main.schemeEnterFullscreen()
    ↓
WebpageAdapter.tryEnterFullscreen()
    ↓
[视频全屏播放]
```

---

## 组件通信机制

### 回调模式

MainActivity 通过回调函数连接各个组件：

```kotlin
// 频道列表 → MainActivity
playlistView.onChannelSelectCallback = { channel ->
    playerView.channel = channel
}

// 播放器 → MainActivity
playerView.dismissAllViewCallback = {
    uiMode = UiMode.STANDARD
}

// 播放列表管理器 → MainActivity
PlaylistManager.onPlaylistChange = { newPlaylist ->
    playlistView.playlist = newPlaylist
}
```

### 事件分发

MainActivity 作为事件总线，分发按键事件：

```kotlin
override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    when (uiMode) {
        UiMode.CHANNELS -> playlistView.dispatchKeyEvent(event)
        UiMode.CHANNEL_SETTINGS -> channelSettingsView.dispatchKeyEvent(event)
        UiMode.STANDARD -> handleStandardModeKeys(event)
    }
}
```

---

## 用户交互流程

### 启动流程

1. **LiveApplication.onCreate()**
   - 初始化全局Context
   - 初始化腾讯X5内核

2. **MainActivity.onCreate()**
   - 设置全屏UI
   - 初始化所有视图组件
   - 加载播放列表

3. **MainActivity.onResume()**
   - 恢复上次观看的频道
   - 开始播放

### 切换频道流程

1. 用户按 **上/下键**
2. MainActivity 切换到下一个频道
3. PlayerView 加载新URL
4. WebView 加载网页并注入JavaScript
5. 视频自动全屏播放

### 查看频道列表流程

1. 用户按 **确认键/中间键**
2. UI切换到 `CHANNELS` 模式
3. 显示频道列表
4. 用户选择频道后返回播放

---

## 关键设计思想

### 1. 分离关注点

- **Activity**: 只负责UI和事件协调
- **Widget**: 独立的UI组件，可复用
- **Manager**: 业务逻辑和数据管理
- **Adapter**: 适配不同的电视台

### 2. 适配器模式

通过适配器处理不同电视台的差异，避免在主代码中写大量 if-else。

### 3. 协程异步

使用 Kotlin Coroutines 处理耗时操作：
- 网络请求不阻塞UI
- 页面加载延迟处理
- 平滑的用户体验

### 4. 缓存策略

- 播放列表缓存24小时
- 启动时优先加载缓存
- 后台自动更新

### 5. 状态管理

使用 SharedPreferences 保存：
- 上次观看的频道
- 用户选择的播放源
- 视频画面比例偏好

---

## 常见问题

### Q1: 为什么使用 WebView 而不是直接播放视频？

**A**: 因为很多电视台的直播流有防盗链机制，需要从官网页面中获取真实的播放地址和Cookie。使用WebView可以完整模拟浏览器环境。

### Q2: JavaScript 注入是如何工作的？

**A**: 当WebView加载完页面后（progress=100），会调用 `evaluateJavascript()` 执行我们的脚本。脚本通过 `window.main` 调用Android方法。

### Q3: 为什么需要这么多适配器？

**A**: 不同电视台的网页结构完全不同：
- 有的用 `<video>` 标签
- 有的用 Flash 播放器
- 有的需要点击按钮才能播放
- 有的有特殊的反爬虫机制

### Q4: 如何添加新的电视台？

1. 创建新的 Adapter 继承 `WebpageAdapter`
2. 重写 `javascript()` 方法，返回适合该站的JS代码
3. 重写 `userAgent()` 方法，返回合适的UA
4. 在 `WebpageAdapterManager` 中注册

---

## 入门学习路径

### 第一步：理解数据结构

1. 阅读 `Channel.kt` - 了解频道数据模型
2. 阅读 `Playlist.kt` - 了解播放列表结构
3. 查看 `data/` 目录下的频道列表文件

### 第二步：理解主流程

1. 阅读 `MainActivity.kt` 的 `onCreate()` 和 `setupListener()`
2. 理解UI模式切换机制
3. 跟踪一次频道切换的完整流程

### 第三步：理解WebView机制

1. 阅读 `WebpageAdapterWebView.kt` 的 `loadUrl()` 方法
2. 了解页面加载流程
3. 学习 JavaScript 注入机制

### 第四步：理解网络机制

1. 阅读 `PlaylistManager.kt` 的 `requestUpdatePlaylist()`
2. 了解M3U格式解析
3. 学习协程的使用

### 第五步：实践修改

1. 尝试修改JavaScript模板，添加自定义功能
2. 尝试添加一个新的电视台适配器
3. 尝试修改UI样式

---

## 代码规范

本项目遵循 **Google Kotlin 代码规范**：

### 命名规范

- **类名**: PascalCase (例如: `MainActivity`)
- **变量名**: camelCase (例如: `playerView`)
- **常量名**: UPPER_SNAKE_CASE (例如: `OPERATION_TIMEOUT`)
- **函数名**: camelCase (例如: `loadPlaylist()`)

### 注释规范

- **文件头**: 说明文件作用和主要功能
- **类注释**: 说明类的职责和使用方式
- **方法注释**: 说明参数、返回值和副作用
- **行内注释**: 解释复杂逻辑

### 代码组织

```kotlin
class Example {
    companion object {
        // 常量定义
    }

    // 私有属性

    // 公共属性

    // 初始化块

    // 公共方法

    // 私有方法

    // 内部类
}
```

---

## 总结

AITVLive 是一个设计良好的 Android 电视直播应用，具有：

✅ **清晰的架构** - 分层明确，职责单一
✅ **灵活的适配** - 支持多种电视台
✅ **良好的体验** - 自动播放，智能缓存
✅ **易于扩展** - 适配器模式，方便添加新站
✅ **详细的注释** - 方便初学者理解

希望这份文档能帮助你快速理解代码结构，顺利开始开发！
