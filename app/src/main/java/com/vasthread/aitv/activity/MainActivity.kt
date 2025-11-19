/*
 * Copyright (c) 2025 AITVLive
 *
 * 文件: MainActivity.kt
 * 描述: 应用的主Activity，负责管理整个应用的UI界面和用户交互逻辑
 *
 * 主要功能:
 * 1. 管理多个UI视图组件（播放器、频道列表、设置等）
 * 2. 处理用户输入事件（按键、触摸、遥控器）
 * 3. 控制UI模式切换（标准模式、频道选择、设置等）
 * 4. 管理频道播放状态和用户偏好设置
 */

package com.vasthread.aitv.activity

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.vasthread.aitv.R
import com.vasthread.aitv.misc.preference
import com.vasthread.aitv.playlist.Channel
import com.vasthread.aitv.playlist.Playlist.Companion.firstChannel
import com.vasthread.aitv.playlist.PlaylistManager
import com.vasthread.aitv.settings.SettingsManager
import com.vasthread.aitv.widget.AppSettingsView
import com.vasthread.aitv.widget.ChannelPlayerView
import com.vasthread.aitv.widget.ChannelSettingsView
import com.vasthread.aitv.widget.PlaylistView
import me.jessyan.autosize.AutoSize

/**
 * MainActivity - 应用主界面Activity
 *
 * 这是整个应用的核心控制器，负责：
 * - 初始化和管理所有UI组件
 * - 处理用户的各种输入事件（遥控器、触摸、按键）
 * - 控制不同UI模式之间的切换
 * - 保存和恢复用户上次观看的频道
 * - 与播放列表管理器和设置管理器进行交互
 *
 * UI模式说明：
 * - STANDARD: 标准播放模式，只显示视频播放器
 * - CHANNELS: 频道列表模式，显示所有可用频道供用户选择
 * - EXIT_CONFIRM: 退出确认模式，询问用户是否真的要退出应用
 * - APP_SETTINGS: 应用设置模式，显示应用级别的设置选项
 * - CHANNEL_SETTINGS: 频道设置模式，显示当前频道的设置（画面比例、播放源等）
 */
class MainActivity : AppCompatActivity() {

    companion object {
        /**
         * SharedPreferences中保存上次观看频道的键名
         * 存储格式: "频道组名, 频道名"，例如 "CCTV, CCTV-1"
         */
        private const val LAST_CHANNEL = "last_channel"

        /**
         * UI模式枚举
         * 定义了应用中所有可能的界面状态
         */
        private enum class UiMode {
            STANDARD,           // 标准播放模式
            CHANNELS,           // 频道列表选择模式
            APP_SETTINGS,       // 应用设置模式
            CHANNEL_SETTINGS    // 频道设置模式
        }

        /**
         * 用户无操作自动返回标准模式的超时时间（毫秒）
         * 当用户5秒内没有任何操作时，自动回到标准播放模式
         */
        private const val OPERATION_TIMEOUT = 5000L

        /**
         * 需要重置超时计时器的操作按键列表
         * 这些按键被认为是有效的用户操作
         */
        private val OPERATION_KEYS = arrayOf(
            KeyEvent.KEYCODE_DPAD_UP,       // 方向键上
            KeyEvent.KEYCODE_DPAD_DOWN,     // 方向键下
            KeyEvent.KEYCODE_DPAD_LEFT,     // 方向键左
            KeyEvent.KEYCODE_DPAD_RIGHT,    // 方向键右
            KeyEvent.KEYCODE_DPAD_CENTER,   // 方向键中间（确认）
            KeyEvent.KEYCODE_ENTER,         // 回车键
            KeyEvent.KEYCODE_MENU,          // 菜单键
            KeyEvent.KEYCODE_BACK           // 返回键
        )
    }

    // ========== UI组件 ==========
    // 这些是界面上的各个视图组件，通过 findViewById 在 setupUi() 中初始化

    /** 频道播放器视图，显示视频内容 */
    private lateinit var playerView: ChannelPlayerView

    /** 主布局容器 */
    private lateinit var mainLayout: FrameLayout

    /** UI覆盖层布局，用于显示各种设置界面 */
    private lateinit var uiLayout: FrameLayout

    /** 频道列表视图，显示所有可用频道 */
    private lateinit var playlistView: PlaylistView

    /** 频道设置视图，用于调整当前频道的设置 */
    private lateinit var channelSettingsView: ChannelSettingsView

    /** 应用设置视图，用于调整应用级别的设置 */
    private lateinit var appSettingsView: AppSettingsView

    // ========== 状态变量 ==========

    /**
     * 上次观看的频道
     * 用于在应用暂停和恢复时保持用户的观看状态
     */
    private var lastChannel: Channel? = null

    /**
     * 当前的UI模式
     * 通过自定义setter实现模式切换时自动更新UI显示状态
     */
    private var uiMode = UiMode.STANDARD
        set(value) {
            // 如果模式没有变化，不执行任何操作
            if (field == value) return

            field = value

            // 根据当前模式显示或隐藏相应的视图
            playlistView.visibility = if (value == UiMode.CHANNELS) View.VISIBLE else View.GONE
            channelSettingsView.visibility = if (value == UiMode.CHANNEL_SETTINGS) View.VISIBLE else View.GONE
            appSettingsView.visibility = if (value == UiMode.APP_SETTINGS) View.VISIBLE else View.GONE

            // 切换回标准模式时，让播放器获得焦点
            if (value == UiMode.STANDARD) {
                playerView.requestFocus()
            }
        }

    /**
     * 超时后自动返回标准模式的回调任务
     * 通过 postDelayed 延迟执行，实现无操作自动隐藏UI的功能
     */
    private val backToStandardModeAction = Runnable { uiMode = UiMode.STANDARD }

    // ========== Activity生命周期方法 ==========

    /**
     * Activity创建时的回调方法
     *
     * 执行初始化流程：
     * 1. 设置UI界面
     * 2. 设置各种事件监听器
     * 3. 初始化频道列表
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUi()         // 初始化UI组件
        setupListener()   // 设置事件监听器
        initChannels()    // 初始化频道列表
    }

    /**
     * 设置UI界面
     *
     * 功能：
     * 1. 加载布局文件
     * 2. 初始化所有视图组件
     * 3. 设置全屏和保持屏幕常亮
     */
    @Suppress("DEPRECATION")
    private fun setupUi() {
        // 加载主布局文件
        setContentView(R.layout.activity_main)

        // 通过ID获取所有视图组件的引用
        mainLayout = findViewById(R.id.mainLayout)
        uiLayout = findViewById(R.id.uiLayout)
        playerView = findViewById(R.id.player)
        playlistView = findViewById(R.id.playlist)
        channelSettingsView = findViewById(R.id.channelSettings)
        appSettingsView = findViewById(R.id.appSettings)

        // 设置系统UI标志，实现沉浸式全屏体验
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or      // 隐藏导航栏
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or  // 布局延伸到状态栏下方
                View.SYSTEM_UI_FLAG_FULLSCREEN or         // 隐藏状态栏
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or   // 沉浸式粘性模式
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE         // 保持布局稳定

        // 设置窗口标志
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or        // 保持屏幕常亮
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM          // 替代输入法焦点
        )
    }

    /**
     * 设置各个UI组件的事件监听器
     *
     * 这个方法建立了各个UI组件之间的通信机制，
     * 当用户在某个视图上进行操作时，相应的回调会被触发
     */
    private fun setupListener() {
        // 频道列表视图的频道选择回调
        // 当用户选择一个频道时触发
        playlistView.onChannelSelectCallback = { selectedChannel ->
            // 保存用户选择的频道到SharedPreferences，下次启动时恢复
            preference.edit().putString(LAST_CHANNEL, "${selectedChannel.groupName}, ${selectedChannel.name}").apply()

            // 设置播放器播放选中的频道
            playerView.channel = selectedChannel

            // 更新频道设置视图，显示当前频道的播放源选择
            channelSettingsView.setSelectedChannelSource(
                SettingsManager.getChannelLastSourceIndex(selectedChannel.name),
                selectedChannel.urls.size
            )

            // 切换回标准播放模式
            playlistView.post { uiMode = UiMode.STANDARD }
        }

        // 频道设置视图的画面比例选择回调
        channelSettingsView.onAspectRatioSelected = { ratio ->
            // 设置播放器的视频画面比例
            playerView.setVideoRatio(ratio)
            // 返回标准模式
            uiMode = UiMode.STANDARD
        }

        // 频道设置视图的播放源选择回调
        // 每个频道可能有多个播放源URL，用户可以切换
        channelSettingsView.onChannelSourceSelected = { sourceIndex ->
            val channel = playerView.channel!!
            val currentSource = SettingsManager.getChannelLastSourceIndex(channel.name)

            // 只有当选择的源与当前源不同时才进行切换
            if (currentSource != sourceIndex) {
                // 保存用户选择的播放源索引
                SettingsManager.setChannelLastSourceIndex(channel.name, sourceIndex)
                // 刷新频道以使用新的播放源
                playerView.refreshChannel()
                // 更新UI显示
                channelSettingsView.setSelectedChannelSource(sourceIndex, channel.urls.size)
            }

            // 返回标准模式
            uiMode = UiMode.STANDARD
        }

        // 频道设置视图获取视频尺寸的回调
        channelSettingsView.onGetVideoSize = { playerView.getVideoSize() }

        // 播放器视图的关闭所有UI回调
        playerView.dismissAllViewCallback = { uiMode = UiMode.STANDARD }

        // 播放器视图的点击事件回调
        playerView.clickCallback = { x, _ ->
            // 当前未使用点击事件
        }

        // 播放器视图的视频比例改变回调
        playerView.onVideoRatioChanged = { ratio ->
            // 同步更新频道设置视图中的画面比例选择
            channelSettingsView.setSelectedAspectRatio(ratio)
        }

        // 播放器视图的频道重载回调
        playerView.onChannelReload = { channel ->
            // 频道重新加载时，更新频道设置视图的播放源显示
            channelSettingsView.setSelectedChannelSource(
                SettingsManager.getChannelLastSourceIndex(channel.name),
                channel.urls.size
            )
        }

        // 播放列表管理器的播放列表变化回调
        // 当从网络下载到新的频道列表时触发
        PlaylistManager.onPlaylistChange = { newPlaylist ->
            runOnUiThread {
                playlistView.playlist = newPlaylist
            }
        }

        // 播放列表管理器的更新任务状态变化回调
        // 用于显示"正在更新频道列表"的提示
        PlaylistManager.onUpdatePlaylistJobStateChange = { isUpdating ->
            runOnUiThread {
                playlistView.updating = isUpdating
            }
        }
    }

    /**
     * 初始化频道列表
     *
     * 功能：
     * 1. 从本地加载播放列表
     * 2. 恢复用户上次观看的频道
     * 3. 如果没有上次观看记录，则选择第一个频道
     */
    private fun initChannels() {
        // 从PlaylistManager加载播放列表（优先从缓存，如果没有则从网络下载）
        playlistView.playlist = PlaylistManager.loadPlaylist()

        // 尝试恢复用户上次观看的频道
        runCatching {
            // 从SharedPreferences读取上次观看的频道信息
            val savedChannelInfo = preference.getString(LAST_CHANNEL, "")!!

            // 解析频道信息，格式为 "频道组名, 频道名"
            val pair = savedChannelInfo.split(", ").let { Pair(it[0], it[1]) }

            // 在播放列表中查找匹配的频道
            lastChannel = playlistView.playlist!!.getAllChannels()
                .firstOrNull { channel ->
                    channel.groupName == pair.first && channel.name == pair.second
                }
        }

        // 如果没有找到上次观看的频道，则使用播放列表中的第一个频道
        if (lastChannel == null) {
            lastChannel = playlistView.playlist.firstChannel()
        }
    }

    /**
     * 创建视图时的回调
     *
     * 使用 AutoSize 库自动适配不同屏幕尺寸
     * 这对于电视应用尤其重要，因为电视屏幕尺寸差异很大
     */
    override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? {
        AutoSize.autoConvertDensityOfGlobal(this)
        return super.onCreateView(parent, name, context, attrs)
    }

    /**
     * Activity恢复时的回调
     *
     * 当Activity从暂停状态恢复时，重新开始播放上次的频道
     */
    override fun onResume() {
        super.onResume()

        // 如果有上次观看的频道，且播放器当前没有播放内容，则恢复播放
        if (lastChannel != null && playerView.channel == null) {
            playlistView.currentChannel = lastChannel
        }
    }

    /**
     * Activity暂停时的回调
     *
     * 当用户切换到其他应用或按下Home键时调用
     * 保存当前状态并停止播放以节省资源
     */
    override fun onPause() {
        super.onPause()

        // 切换回标准模式，隐藏所有设置界面
        uiMode = UiMode.STANDARD

        // 保存当前播放的频道
        lastChannel = playerView.channel

        // 停止播放以释放资源
        playerView.channel = null
    }

    /**
     * Activity销毁时的回调
     *
     * 清理资源，防止内存泄漏
     */
    override fun onDestroy() {
        // 停止播放
        playerView.channel = null

        // 清除播放列表管理器的回调，防止内存泄漏
        PlaylistManager.onPlaylistChange = null

        super.onDestroy()
    }

    /**
     * 处理返回键按下事件
     *
     * 行为：直接退出应用
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
        System.exit(0)
    }

    /**
     * 窗口焦点改变时的回调
     *
     * 当窗口获得焦点时，让播放器视图获得焦点以接收按键事件
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            playerView.requestFocus()
        }
    }

    // ========== 事件分发方法 ==========
    // 这些方法拦截和处理各种用户输入事件

    /**
     * 分发通用运动事件（如遥控器的移动）
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        onMotionEvent(event)
        return super.dispatchGenericMotionEvent(event)
    }

    /**
     * 分发无障碍事件
     */
    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
        repostBackToStandardModeAction()
        return super.dispatchPopulateAccessibilityEvent(event)
    }

    /**
     * 分发轨迹球事件
     */
    override fun dispatchTrackballEvent(event: MotionEvent): Boolean {
        onMotionEvent(event)
        return super.dispatchTrackballEvent(event)
    }

    /**
     * 分发触摸事件
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        onMotionEvent(event)
        return super.dispatchTouchEvent(event)
    }

    /**
     * 分发按键事件
     *
     * 这是处理遥控器和键盘输入的核心方法
     * 根据当前的UI模式，将按键事件路由到相应的视图组件
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        onKeyEvent(event)

        val keyCode = event.keyCode

        // 如果不是我们关心的操作按键，不处理
        if (!OPERATION_KEYS.contains(keyCode)) {
            return false
        }

        // 返回键特殊处理，交给父类的 onBackPressed 方法
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return super.dispatchKeyEvent(event)
        }

        // 根据当前UI模式分发按键事件
        when (uiMode) {
            UiMode.CHANNELS -> {
                // 频道列表模式：将按键交给频道列表视图处理
                if (playlistView.dispatchKeyEvent(event)) return true
            }
            UiMode.CHANNEL_SETTINGS -> {
                // 频道设置模式：将按键交给频道设置视图处理
                if (channelSettingsView.dispatchKeyEvent(event)) return true
            }
            UiMode.APP_SETTINGS -> {
                // 应用设置模式：将按键交给应用设置视图处理
                if (appSettingsView.dispatchKeyEvent(event)) return true
            }
            else -> {
                // 标准模式：处理频道切换和模式切换
                if (event.action == KeyEvent.ACTION_UP) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            // 上键：切换到上一个频道
                            playlistView.previousChannel()
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            // 下键：切换到下一个频道
                            playlistView.nextChannel()
                        }
                        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                            // 确认键：显示频道列表
                            uiMode = UiMode.CHANNELS
                        }
                        KeyEvent.KEYCODE_MENU -> {
                            // 菜单键：显示频道设置
                            uiMode = UiMode.CHANNEL_SETTINGS
                        }
                    }
                }
                return true
            }
        }

        return super.dispatchKeyEvent(event)
    }

    /**
     * 按键事件处理
     *
     * 检测长按操作，并重置自动返回标准模式的计时器
     */
    private fun onKeyEvent(event: KeyEvent) {
        // 如果是长按（按下时间超过1秒）或按键抬起事件，重置计时器
        if (event.eventTime - event.downTime >= 1000L || event.action == KeyEvent.ACTION_UP) {
            repostBackToStandardModeAction()
        }
    }

    /**
     * 运动事件处理
     *
     * 检测长按操作，并重置自动返回标准模式的计时器
     */
    private fun onMotionEvent(event: MotionEvent) {
        // 如果是长按（按下时间超过1秒）或事件结束，重置计时器
        if (event.eventTime - event.downTime >= 1000L || event.action == KeyEvent.ACTION_UP) {
            repostBackToStandardModeAction()
        }
    }

    /**
     * 重新安排返回标准模式的定时任务
     *
     * 每次用户有操作时，都会调用此方法重置计时器
     * 如果用户在5秒内没有任何操作，自动返回标准播放模式
     *
     * 这个设计是为了在用户查看频道列表或设置后，
     * 如果忘记返回，能够自动返回播放界面
     */
    private fun repostBackToStandardModeAction() {
        // 先移除之前的定时任务
        playerView.removeCallbacks(backToStandardModeAction)
        // 重新安排5秒后执行的定时任务
        playerView.postDelayed(backToStandardModeAction, OPERATION_TIMEOUT)
    }
}
