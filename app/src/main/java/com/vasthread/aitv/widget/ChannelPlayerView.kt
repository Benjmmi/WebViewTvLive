/*
 * Copyright (c) 2025 AITVLive
 *
 * 文件: ChannelPlayerView.kt
 * 描述: 频道播放器视图，封装WebView和相关UI组件，提供完整的播放功能
 */

package com.vasthread.aitv.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.FrameLayout
import com.vasthread.aitv.R
import com.vasthread.aitv.playlist.Channel
import com.vasthread.aitv.settings.SettingsManager

/**
 * ChannelPlayerView - 频道播放器视图
 *
 * 这是一个组合视图，包含了播放频道所需的所有UI组件：
 * - WebpageAdapterWebView: 核心播放器，加载电视台网页
 * - ChannelBarView: 顶部信息栏，显示频道名和加载进度
 * - WaitingView: 等待提示，加载超时时自动切换播放源
 *
 * 主要职责：
 * - 管理频道的加载和播放
 * - 协调各个子视图的显示和隐藏
 * - 处理用户触摸手势
 * - 提供刷新频道和调整画面比例的接口
 *
 * 使用方式：
 * ```kotlin
 * playerView.channel = selectedChannel  // 设置频道，自动开始加载和播放
 * playerView.refreshChannel()           // 刷新当前频道
 * playerView.setVideoRatio(true)        // 设置16:9画面比例
 * ```
 *
 * @param context Android上下文
 * @param attrs XML属性集
 * @param defStyleAttr 默认样式属性
 */
class ChannelPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        /** 日志标签 */
        private const val TAG = "ChannelPlayer"
    }

    // ========== UI组件 ==========

    /** 核心WebView播放器 */
    private val webView: WebpageAdapterWebView

    /** 等待提示视图 */
    private val waitingView: WaitingView

    /** 频道信息栏 */
    private val channelBarView: ChannelBarView

    // ========== 公共属性和回调 ==========

    /**
     * 当前播放的频道
     *
     * 设置此属性会自动触发频道加载：
     * - 如果设置为null，停止播放并显示空白页
     * - 如果设置为有效频道，加载该频道的播放URL
     *
     * 自定义setter确保：
     * - 避免重复加载相同频道
     * - 自动更新频道信息栏
     * - 根据频道状态显示或隐藏UI
     */
    var channel: Channel? = null
        set(value) {
            // 如果频道没有变化，不重复加载
            if (field == value) return

            field = value

            if (value == null) {
                // 设置为null，停止播放
                webView.loadUrl(WebpageAdapterWebView.URL_BLANK)
                channelBarView.dismiss()
            } else if (!value.urls.isEmpty()) {
                // 有效频道，开始加载
                webView.loadUrl(value.url)

                // 显示频道信息栏
                channelBarView.setCurrentChannelAndShow(value)
            }
        }

    /** 点击事件回调，传递点击位置的X和Y坐标 */
    var clickCallback: ((Float, Float) -> Unit)? = null

    /** 关闭所有视图的回调，通知外部隐藏其他UI */
    var dismissAllViewCallback: (() -> Unit)? = null

    /** 频道重新加载的回调，用于通知外部更新UI */
    var onChannelReload: ((Channel) -> Unit)? = null

    /** 视频比例改变的回调，true表示16:9，false表示4:3 */
    var onVideoRatioChanged: ((Boolean) -> Unit)? = null

    // ========== 手势检测器 ==========

    /**
     * 手势检测器
     *
     * 当WebView不可触摸时，使用手势检测器处理触摸事件：
     * - 检测单击事件
     * - 触发clickCallback回调
     *
     * 这样可以在不让用户直接操作WebView的情况下，
     * 仍然能响应点击事件用于显示/隐藏UI
     */
    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.OnGestureListener {

            /**
             * 手指按下事件
             * 返回true表示继续处理后续事件
             */
            override fun onDown(e: MotionEvent) = true

            /**
             * 手指按下但还未移动或抬起
             */
            override fun onShowPress(e: MotionEvent) = Unit

            /**
             * 单击事件
             * 触发clickCallback，传递点击位置
             */
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                clickCallback?.invoke(e.x, e.y)
                return true
            }

            /**
             * 滑动事件
             * 不处理滑动，返回false
             */
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ) = false

            /**
             * 长按事件
             * 不处理长按
             */
            override fun onLongPress(e: MotionEvent) = Unit

            /**
             * 快速滑动事件
             * 不处理快速滑动，返回false
             */
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ) = false
        })

    /**
     * 生成简单的HTML视频播放页面（备用方案）
     *
     * 这个方法可以用于直接播放视频URL，而不是加载电视台网页
     * 目前未使用，保留作为备用方案
     *
     * @param url 视频流URL
     * @return 完整的HTML页面代码
     */
    private fun html(url: String): String {
        return """
<html style="width:100;height:100%">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta http-equiv="Cache-Control" content="no-siteapp">
    <meta name="renderer" content="webkit">
    <meta name="viewport" content="width=device-width,initial-scale=1.0,minimum-scale=1.0,maximum-scale=1.0,user-scalable=no">
</head>
<body style="width:100;height:100%">
<video class="dplayer-video dplayer-video-current"
       webkit-playsinline=""
       x-webkit-airplay="allow"
       playsinline=""
       preload="metadata"
       id="aitvlive"
       width="100%"
       height="100%"
       src="$url">
               您的浏览器不支持视频播放
</video>
</body>
</html>
        """.trimIndent()
    }

    /**
     * 初始化视图
     *
     * 设置：
     * - 禁用焦点高亮（Android O及以上）
     * - 加载布局文件
     * - 设置黑色背景
     * - 初始化子视图
     * - 配置WebView的各种回调
     */
    init {
        // Android O (API 26) 及以上禁用默认的焦点高亮
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }

        // 加载布局文件
        LayoutInflater.from(context).inflate(R.layout.widget_channel_player, this)

        // 设置黑色背景
        setBackgroundColor(Color.BLACK)

        // 获取子视图引用
        webView = findViewById(R.id.webView)
        channelBarView = findViewById(R.id.channelBarView)
        waitingView = findViewById(R.id.waitingView)

        // 将播放器视图引用传递给等待视图，用于自动切换播放源
        waitingView.playerView = this

        // 配置WebView的各种回调
        webView.apply {
            // 设置全屏容器
            fullscreenContainer = this@ChannelPlayerView.findViewById(R.id.fullscreenContainer)

            // 页面加载完成回调（当前为空实现）
            onPageFinished = {}

            // 加载进度变化回调，更新频道信息栏的进度显示
            onProgressChanged = { progress ->
                channelBarView.setProgress(progress)
            }

            // 全屏状态变化回调（当前为空实现）
            onFullscreenStateChanged = {}

            // 等待状态变化回调，控制等待视图的显示和隐藏
            onWaitingStateChanged = { isWaiting ->
                waitingView.visibility = if (isWaiting) VISIBLE else GONE
            }

            // 视频比例变化回调，传递给外部
            onVideoRatioChanged = { ratio ->
                this@ChannelPlayerView.onVideoRatioChanged?.invoke(
                    ratio == WebpageAdapterWebView.RATIO_16_9
                )
            }
        }
    }

    /**
     * 请求焦点
     *
     * 将焦点请求传递给WebView，确保WebView能够接收按键事件
     *
     * @param direction 焦点移动方向
     * @param previouslyFocusedRect 之前获得焦点的视图区域
     * @return true表示成功获得焦点
     */
    override fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        return webView.requestFocus(direction, previouslyFocusedRect)
    }

    /**
     * 分发触摸事件
     *
     * 根据设置决定触摸事件的处理方式：
     * - 如果WebView可触摸：传递给子视图，允许用户直接操作网页
     * - 如果WebView不可触摸：使用手势检测器，只处理点击事件
     *
     * @param event 触摸事件
     * @return true表示事件已处理
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return if (SettingsManager.isWebViewTouchable()) {
            // WebView可触摸模式：关闭所有覆盖UI，传递事件给子视图
            dismissAllViewCallback?.invoke()
            super.dispatchTouchEvent(event)
        } else {
            // WebView不可触摸模式：使用手势检测器处理
            gestureDetector.onTouchEvent(event)
        }
    }

    /**
     * 分发通用运动事件
     *
     * 返回false，不处理运动事件（如鼠标移动）
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        return false
    }

    /**
     * 分发按键事件
     *
     * 返回false，让按键事件传递给父视图处理
     * 这样遥控器按键由MainActivity统一处理
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return false
    }

    // ========== 公共方法 ==========

    /**
     * 刷新当前频道
     *
     * 重新加载当前频道的播放URL
     * 用于：
     * - 播放失败时重试
     * - 切换播放源后重新加载
     */
    fun refreshChannel() {
        webView.loadUrl(channel!!.url)
    }

    /**
     * 设置视频画面比例
     *
     * @param is_16_9 true表示16:9比例，false表示4:3比例
     */
    fun setVideoRatio(is_16_9: Boolean) {
        webView.setVideoRatio(
            if (is_16_9) WebpageAdapterWebView.RATIO_16_9
            else WebpageAdapterWebView.RATIO_4_3
        )
    }

    /**
     * 获取视频的实际尺寸
     *
     * @return Point对象，包含视频的宽度和高度
     */
    fun getVideoSize() = webView.getVideoSize()
}
