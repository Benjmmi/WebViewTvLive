/*
 * Copyright (c) 2025 AITVLive
 *
 * 文件: WebpageAdapterWebView.kt
 * 描述: 自定义WebView组件，基于腾讯X5内核，用于加载和显示电视直播页面
 *
 * 主要功能:
 * 1. 加载各种电视台的直播网页
 * 2. 处理视频全屏显示和画面比例调整
 * 3. 通过JavaScript注入实现自动播放和控制
 * 4. 监控页面加载状态和视频播放状态
 * 5. 管理等待提示的显示和隐藏
 */

package com.vasthread.aitv.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.widget.FrameLayout
import com.tencent.smtt.export.external.extension.interfaces.IX5WebSettingsExtension
import com.tencent.smtt.export.external.extension.proxy.ProxyWebChromeClientExtension
import com.tencent.smtt.export.external.extension.proxy.ProxyWebViewClientExtension
import com.tencent.smtt.export.external.interfaces.ConsoleMessage
import com.tencent.smtt.export.external.interfaces.IX5WebChromeClient
import com.tencent.smtt.export.external.interfaces.JsResult
import com.tencent.smtt.export.external.interfaces.MediaAccessPermissionsCallback
import com.tencent.smtt.export.external.interfaces.PermissionRequest
import com.tencent.smtt.export.external.interfaces.SslError
import com.tencent.smtt.export.external.interfaces.SslErrorHandler
import com.tencent.smtt.export.external.interfaces.WebResourceRequest
import com.tencent.smtt.export.external.interfaces.WebResourceResponse
import com.tencent.smtt.sdk.WebChromeClient
import com.tencent.smtt.sdk.WebSettings
import com.tencent.smtt.sdk.WebView
import com.tencent.smtt.sdk.WebViewClient
import com.vasthread.aitv.adapter.WebpageAdapterManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.commons.lang3.math.Fraction
import kotlin.system.measureTimeMillis

/**
 * LayoutParams 类型别名，简化布局参数的使用
 */
typealias LP = FrameLayout.LayoutParams

/**
 * WebpageAdapterWebView - 自定义的WebView控件
 *
 * 这个类继承自腾讯X5内核的WebView，专门用于加载和显示电视直播页面。
 * 它提供了以下核心功能：
 *
 * 1. **网页加载管理**：
 *    - 智能加载网页，支持URL切换
 *    - 页面重置机制，确保每次加载都是干净的状态
 *    - 加载进度监控
 *
 * 2. **视频全屏控制**：
 *    - 自动检测视频并进入全屏
 *    - 支持多种画面比例（16:9, 4:3等）
 *    - 动态调整视频尺寸以适应屏幕
 *
 * 3. **JavaScript交互**：
 *    - 注入自定义JavaScript代码控制网页行为
 *    - 提供Java方法供JavaScript调用（通过@JavascriptInterface）
 *    - 实现双向通信
 *
 * 4. **播放状态监控**：
 *    - 检测视频是否正在播放
 *    - 自动显示/隐藏等待提示
 *    - 处理播放异常
 *
 * 5. **适配器集成**：
 *    - 与WebpageAdapterManager配合，为不同的电视台网站提供定制化适配
 *    - 每个网站可以有自己的UserAgent和JavaScript脚本
 *
 * @param context Android上下文
 * @param attrs XML属性集
 * @param defStyleAttr 默认样式属性
 */
@Suppress("unused", "DEPRECATION")
@SuppressLint("SetJavaScriptEnabled")
class WebpageAdapterWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    companion object {
        /** 日志标签 */
        private const val TAG = "WebpageAdapterWebView"

        /** 空白页URL */
        const val URL_BLANK = "about:blank"

        /** 显示等待提示的延迟时间（毫秒）- 3秒后如果视频还没播放就显示等待提示 */
        private const val SHOW_WAITING_VIEW_DELAY = 3000L

        /** 最大缩小级别 - 控制页面缩放的最大次数 */
        private const val MAX_ZOOM_OUT_LEVEL = 3

        /** 检查页面加载状态的间隔时间（毫秒） */
        private const val CHECK_PAGE_LOADING_INTERVAL = 50L

        /** 加载空白页后的等待时间（毫秒） - 确保页面完全清空 */
        private const val BLANK_PAGE_WAIT = 800L

        /** 16:9画面比例 - 最常见的视频比例 */
        val RATIO_16_9 = Fraction.getFraction(16, 9)!!

        /** 4:3画面比例 - 传统电视比例 */
        val RATIO_4_3 = Fraction.getFraction(4, 3)!!
    }

    // ========== 私有状态变量 ==========

    /** 当前请求加载的URL */
    private var requestedUrl = ""

    /** 视频的实际尺寸（宽度和高度） */
    private val videoSize = Point()

    /** 是否处于全屏状态 */
    private var isInFullscreen = false

    /** 页面加载信息，记录当前加载的URL和加载状态 */
    private val loadingInfo = PageLoadingInfo("", false)

    /** 显示等待提示的延迟任务 */
    private val showWaitingViewAction = Runnable { onWaitingStateChanged?.invoke(true) }

    /** 隐藏等待提示的任务 */
    private val dismissWaitingViewAction = Runnable { onWaitingStateChanged?.invoke(false) }

    // ========== 公共属性和回调 ==========

    /** 全屏容器，用于显示全屏视频 */
    lateinit var fullscreenContainer: FrameLayout

    /** 等待状态变化的回调 - 当需要显示或隐藏等待提示时调用 */
    var onWaitingStateChanged: ((Boolean) -> Unit)? = null

    /** 页面加载完成的回调 - 当页面加载到100%时调用 */
    var onPageFinished: ((String) -> Unit)? = null

    /** 加载进度变化的回调 - 页面加载进度0-100 */
    var onProgressChanged: ((Int) -> Unit)? = null

    /** 全屏状态变化的回调 - 进入或退出全屏时调用 */
    var onFullscreenStateChanged: ((Boolean) -> Unit)? = null

    /** 视频比例变化的回调 - 当视频比例改变时调用 */
    var onVideoRatioChanged: ((Fraction) -> Unit)? = null

    // ========== WebViewClient ==========
    // 处理页面加载过程中的各种事件

    /**
     * WebViewClient - 处理WebView的页面加载事件
     *
     * 主要职责：
     * - 拦截URL加载请求
     * - 监控页面加载状态
     * - 处理SSL错误
     * - 记录HTTP错误
     */
    private val client = object : WebViewClient() {

        /**
         * 是否拦截URL加载请求
         * 返回true表示拦截，不让WebView自动加载
         */
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            // 返回true阻止WebView加载，防止页面跳转
            return true
        }

        /**
         * 是否拦截按键事件
         * 返回true表示WebView不处理该按键
         */
        override fun shouldOverrideKeyEvent(view: WebView, event: KeyEvent): Boolean {
            return super.shouldOverrideKeyEvent(view, event)
        }

        /**
         * 处理未被WebView消费的按键事件
         */
        override fun onUnhandledKeyEvent(view: WebView, event: KeyEvent) {
            super.onUnhandledKeyEvent(view, event)
        }

        /**
         * 当WebView加载资源时调用（图片、CSS、JS等）
         */
        override fun onLoadResource(view: WebView, url: String) {
            super.onLoadResource(view, url)
        }

        /**
         * 接收到SSL错误时的处理
         * 这里选择继续加载（proceed），忽略SSL证书错误
         * 注意：在生产环境中，这可能存在安全风险
         */
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.proceed()  // 继续加载，忽略SSL错误
        }

        /**
         * 页面开始加载时调用
         *
         * @param url 正在加载的URL
         * @param favicon 网站图标（可能为空）
         */
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            Log.i(TAG, "onPageStarted, $url")

            // 更新加载信息，标记为正在加载
            loadingInfo.set(url, true)

            // 禁用播放检查，因为页面刚开始加载，视频还没准备好
            disablePlayCheck()
        }

        /**
         * 页面加载完成时调用
         *
         * @param url 已完成加载的URL
         */
        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            Log.i(TAG, "onPageFinished, $url")

            // 更新加载信息，标记为加载完成
            loadingInfo.set(url, false)
        }

        /**
         * 接收到HTTP错误时调用
         * 记录错误信息以便调试
         */
        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            response: WebResourceResponse
        ) {
            super.onReceivedHttpError(view, request, response)
            Log.i(TAG, "Http error: ${response.statusCode} ${request.url}")
        }
    }

    /**
     * WebViewClient扩展 - 腾讯X5内核的扩展接口
     * 目前使用默认实现
     */
    private val clientExtension = object : ProxyWebViewClientExtension() {
        // 使用默认实现
    }

    // ========== WebChromeClient ==========
    // 处理JavaScript对话框、全屏请求、进度变化等

    /**
     * WebChromeClient - 处理WebView的UI事件
     *
     * 主要职责：
     * - 处理JavaScript对话框（alert、confirm等）
     * - 监控页面加载进度
     * - 处理视频全屏请求
     * - 管理控制台日志输出
     * - 控制视频画面比例
     */
    private val chromeClient = object : WebChromeClient() {

        /** 全屏视图 - 当视频进入全屏时，这个view会被添加到fullscreenContainer */
        private var view: View? = null

        /** 全屏回调 - 用于通知系统全屏状态变化 */
        private var callback: IX5WebChromeClient.CustomViewCallback? = null

        /** 上次加载的URL - 用于检测是否是新页面 */
        private var lastUrl = ""

        /** 上次的加载进度 - 避免重复处理相同进度 */
        private var lastProgress = 0

        /**
         * 视频画面比例
         * 当比例改变时，会触发 onVideoRatioChanged 回调
         * 如果正在全屏播放，还会重新调整全屏视图的布局参数
         */
        var videoRatio = RATIO_16_9
            set(value) {
                if (field == value) return
                field = value

                // 通知外部视频比例已改变
                onVideoRatioChanged?.invoke(value)

                // 如果正在全屏，重新调整布局以适应新比例
                if (isInFullscreen) {
                    view?.layoutParams = generateLayoutParams()
                }
            }

        /**
         * 标记为新页面
         * 重置上次URL，用于检测页面是否真正发生了变化
         */
        fun markNewPage() {
            lastUrl = ""
        }

        /**
         * 处理JavaScript的alert对话框
         * 直接取消，不显示对话框，避免干扰用户观看
         */
        override fun onJsAlert(
            view: WebView,
            url: String,
            message: String?,
            result: JsResult
        ): Boolean {
            result.cancel()  // 取消alert对话框
            return true
        }

        /**
         * 页面加载进度改变时调用
         *
         * 这个方法会在页面加载过程中多次调用，progress从0到100
         * 当progress达到100时，会注入自定义JavaScript代码
         *
         * @param progress 加载进度 0-100
         */
        override fun onProgressChanged(view: WebView, progress: Int) {
            super.onProgressChanged(view, progress)
            Log.i(TAG, "$url, progress=$progress")

            // 如果是空白页，不需要处理
            if (url == URL_BLANK) {
                disablePlayCheck()
                return
            }

            // 只有当URL变化或进度增加时才处理
            if (view.url != lastUrl || progress > lastProgress) {
                // 通知外部进度变化
                onProgressChanged?.invoke(progress)

                // 禁用播放检查，等待页面完全加载
                disablePlayCheck()

                // 调整页面缩放以获得最佳显示效果
                adjustWideViewPort()

                // 页面加载完成
                if (progress == 100) {
                    // 注入适配器的JavaScript代码
                    // 这些代码会控制视频自动播放、全屏等行为
                    evaluateJavascript(WebpageAdapterManager.get(url).javascript(), null)

                    // 通知外部页面加载完成
                    onPageFinished?.invoke(url)
                }

                // 更新记录
                lastUrl = view.url
                lastProgress = progress
            }
        }

        /**
         * 处理控制台消息
         * 将网页的console.log等输出重定向到Android日志系统
         *
         * @param msg 控制台消息
         * @return true表示已处理
         */
        override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
            when (msg.messageLevel()) {
                ConsoleMessage.MessageLevel.DEBUG, null -> Log.d(TAG, msg.message())
                ConsoleMessage.MessageLevel.LOG,
                ConsoleMessage.MessageLevel.TIP -> Log.i(TAG, msg.message())
                ConsoleMessage.MessageLevel.WARNING -> Log.w(TAG, msg.message())
                ConsoleMessage.MessageLevel.ERROR -> Log.e(TAG, msg.message())
            }
            return true
        }

        /**
         * 生成全屏视频的布局参数
         *
         * 根据屏幕比例和视频比例，计算出最佳的显示尺寸
         * 确保视频完整显示且尽可能填满屏幕
         *
         * 算法：
         * 1. 如果屏幕比例 = 视频比例，完全填满
         * 2. 如果屏幕比例 > 视频比例（屏幕更宽），以高度为准，宽度等比缩放
         * 3. 如果屏幕比例 < 视频比例（屏幕更窄），以宽度为准，高度等比缩放
         *
         * @return 计算好的布局参数
         */
        @Suppress("RemoveRedundantQualifierName")
        fun generateLayoutParams(): FrameLayout.LayoutParams {
            // 计算屏幕的宽高比
            val screenRadio = Fraction.getFraction(
                fullscreenContainer.width,
                fullscreenContainer.height
            )

            // 比较屏幕比例和视频比例
            val compare = screenRadio.compareTo(videoRatio)

            return if (compare == 0) {
                // 比例相同，完全填满
                LP(screenRadio.numerator, screenRadio.denominator, Gravity.CENTER)
            } else if (compare > 0) {
                // 屏幕更宽，以高度为准
                LP(
                    screenRadio.denominator * videoRatio.numerator / videoRatio.denominator,
                    screenRadio.denominator,
                    Gravity.CENTER
                )
            } else {
                // 屏幕更窄，以宽度为准
                LP(
                    screenRadio.numerator,
                    screenRadio.numerator * videoRatio.denominator / videoRatio.numerator,
                    Gravity.CENTER
                )
            }
        }

        /**
         * 显示自定义全屏视图
         * 当网页中的视频请求全屏时调用
         *
         * @param view 全屏视图（通常是video元素）
         * @param callback 全屏状态回调
         */
        override fun onShowCustomView(view: View, callback: IX5WebChromeClient.CustomViewCallback) {
            Log.i(TAG, "onShowCustomView")

            this.view = view
            this.callback = callback

            // 将全屏视图添加到全屏容器，使用计算好的布局参数
            fullscreenContainer.addView(view, generateLayoutParams())

            // 更新全屏状态
            isInFullscreen = true
            onFullscreenStateChanged?.invoke(true)
        }

        /**
         * 隐藏自定义全屏视图
         * 当视频退出全屏时调用
         */
        override fun onHideCustomView() {
            Log.i(TAG, "onHideCustomView")

            // 从全屏容器中移除视图
            fullscreenContainer.removeView(view)

            // 通知系统全屏已隐藏
            callback?.onCustomViewHidden()

            // 清理引用
            view = null
            callback = null

            // 更新全屏状态
            isInFullscreen = false
            onFullscreenStateChanged?.invoke(false)
        }

        /**
         * 处理权限请求（如摄像头、麦克风权限）
         * 目前使用默认实现
         */
        override fun onPermissionRequest(request: PermissionRequest) {
            super.onPermissionRequest(request)
        }
    }

    /**
     * WebChromeClient扩展 - 腾讯X5内核的扩展接口
     *
     * 处理X5内核特有的功能，如全屏请求和权限管理
     */
    private val chromeClientExtension = object : ProxyWebChromeClientExtension() {

        /**
         * JavaScript请求全屏时调用
         */
        override fun jsRequestFullScreen() {
            Log.i(TAG, "jsRequestFullScreen")
        }

        /**
         * HTML5视频请求全屏时调用
         */
        override fun h5videoRequestFullScreen(s: String) {
            Log.i(TAG, "h5videoRequestFullScreen")
        }

        /**
         * 处理媒体访问权限请求
         * 自动授予权限，允许网页访问媒体资源
         *
         * @param origin 请求来源
         * @param resources 请求的资源类型
         * @param callback 权限回调
         * @return true表示已处理
         */
        override fun onPermissionRequest(
            origin: String,
            resources: Long,
            callback: MediaAccessPermissionsCallback
        ): Boolean {
            Log.i(TAG, "onPermissionRequest, origin=$origin, resources=$resources")
            // 授予权限
            callback.invoke(origin, 0, true)
            return true
        }
    }

    // ========== 初始化块 ==========

    /**
     * 初始化WebView设置和客户端
     *
     * 这个初始化块在对象创建时执行，配置WebView的各种行为
     */
    init {
        // 配置WebView设置
        settings.apply {
            javaScriptEnabled = true                    // 启用JavaScript
            domStorageEnabled = true                    // 启用DOM存储
            mediaPlaybackRequiresUserGesture = false    // 允许视频自动播放，无需用户手势
            useWideViewPort = true                      // 使用宽视口
            loadWithOverviewMode = true                 // 以概览模式加载
            setAppCacheEnabled(true)                    // 启用应用缓存
            cacheMode = WebSettings.LOAD_DEFAULT        // 使用默认缓存策略
        }

        // 配置WebView客户端和界面
        apply {
            webViewClient = client                      // 设置WebViewClient
            webViewClientExtension = clientExtension    // 设置X5扩展
            webChromeClient = chromeClient              // 设置WebChromeClient
            webChromeClientExtension = chromeClientExtension  // 设置Chrome扩展
            setBackgroundColor(Color.BLACK)             // 设置背景为黑色
            addJavascriptInterface(this, "main")        // 添加JavaScript接口，名称为"main"
        }
    }

    // ========== 公共方法 ==========
    // 这些方法可以被外部调用，也可以被JavaScript调用（标记@JavascriptInterface的方法）

    /**
     * 加载指定URL
     *
     * 这个方法会执行以下步骤：
     * 1. 先加载空白页，清空之前的内容
     * 2. 等待空白页加载完成
     * 3. 配置适合目标网站的设置（UserAgent、图片加载等）
     * 4. 加载目标URL
     *
     * 使用协程异步执行，避免阻塞UI线程
     *
     * @param url 要加载的URL
     */
    @JavascriptInterface
    override fun loadUrl(url: String) {
        requestedUrl = url
        super.loadUrl(url)

        CoroutineScope(Dispatchers.Main).launch {
            // 重置页面，确保干净的加载环境
            resetPage(url)

            // 重置视频尺寸
            setVideoSize(0, 0)

            // 检查URL是否在等待期间被更改
            if (requestedUrl == url) {
                // 配置网站特定的设置
                settings.apply {
                    loadsImagesAutomatically = false    // 不自动加载图片，节省流量
                    blockNetworkImage = true            // 阻止网络图片
                    // 设置UserAgent，适配不同网站
                    userAgentString = WebpageAdapterManager.get(url).userAgent()
                }

                // 重置视频比例为默认的16:9
                chromeClient.apply {
                    videoRatio = RATIO_16_9
                }

                // 配置X5扩展设置
                settingsExtension?.apply {
                    // 设置为无图模式，进一步节省流量
                    setPicModel(IX5WebSettingsExtension.PicModel_NoPic)
                }

                // 禁用播放检查
                disablePlayCheck()

                // 标记为新页面
                chromeClient.markNewPage()

                Log.i(TAG, "Load url $url")

                // 真正开始加载目标URL
                super.loadUrl(url)
            } else {
                Log.i(TAG, "New url requested, ignore.")
            }
        }
    }

    /**
     * 加载带有基础URL的数据
     * 保留父类实现
     */
    override fun loadDataWithBaseURL(
        p0: String?,
        p1: String?,
        p2: String?,
        p3: String?,
        p4: String?
    ) {
        super.loadDataWithBaseURL(p0, p1, p2, p3, p4)
    }

    /**
     * 重置页面
     *
     * 在加载新URL之前，先加载空白页清空内容
     * 这样可以避免旧页面的JavaScript干扰新页面的加载
     *
     * 这是一个挂起函数（suspend），会等待空白页完全加载
     *
     * @param destUrl 目标URL，用于检测是否在等待期间请求了新URL
     */
    private suspend fun resetPage(destUrl: String) {
        Log.i(TAG, "Resetting page...")

        val cost = measureTimeMillis {
            // 加载空白页
            super.loadUrl(URL_BLANK)

            // 等待空白页开始加载或加载完成
            while (loadingInfo.url != URL_BLANK && !loadingInfo.isPageLoading) {
                // 如果在等待期间URL被更改，取消重置
                if (destUrl != requestedUrl) {
                    Log.i(TAG, "Requested url changed, cancel.")
                    break
                }
                delay(CHECK_PAGE_LOADING_INTERVAL)
            }

            // 额外等待一段时间，确保页面完全清空
            if (destUrl == requestedUrl) {
                val endTime = SystemClock.uptimeMillis() + BLANK_PAGE_WAIT
                while (SystemClock.uptimeMillis() < endTime) {
                    // 检查是否有新的URL请求
                    if (destUrl != requestedUrl) {
                        Log.i(TAG, "Requested url changed, cancel.")
                        break
                    }
                    delay(CHECK_PAGE_LOADING_INTERVAL)
                }
            }
        }

        Log.i(TAG, "Done Resetting, cost ${cost}ms.")
    }

    /**
     * 重新加载当前页面
     */
    override fun reload() {
        chromeClient.markNewPage()
        super.reload()
    }

    /**
     * 停止加载页面
     */
    override fun stopLoading() {
        super.stopLoading()
        disablePlayCheck()
    }

    /**
     * 检查是否处于全屏状态
     *
     * @return true表示正在全屏，false表示非全屏
     */
    fun isInFullscreen() = isInFullscreen

    /**
     * 设置视频画面比例
     *
     * @param ratio 画面比例（如16:9, 4:3）
     */
    fun setVideoRatio(ratio: Fraction) {
        chromeClient.videoRatio = ratio
    }

    /**
     * 获取当前视频画面比例
     *
     * @return 当前的画面比例
     */
    fun getVideoRatio() = chromeClient.videoRatio

    /**
     * JavaScript调用此方法请求进入全屏
     *
     * 注入的JavaScript代码会调用这个方法来触发全屏
     * 使用@JavascriptInterface注解使其可以被JavaScript访问
     */
    @JavascriptInterface
    fun schemeEnterFullscreen() {
        // 如果已经是全屏，不再处理
        if (isInFullscreen()) return

        Log.i(TAG, "schemeEnterFullscreen")

        // 在主线程中执行全屏操作
        CoroutineScope(Dispatchers.Main).launch {
            // 调用适配器的全屏方法，不同网站可能有不同的全屏实现
            WebpageAdapterManager.get(url).tryEnterFullscreen(this@WebpageAdapterWebView)
        }
    }

    /**
     * JavaScript调用此方法通知视频正在播放
     *
     * 当视频开始播放时，注入的JavaScript会调用这个方法
     * 这会重置播放检查计时器
     */
    @JavascriptInterface
    fun notifyVideoPlaying() {
        disablePlayCheck()  // 先禁用之前的检查
        enablePlayCheck()   // 重新启用检查
    }

    /**
     * 启用播放检查
     *
     * 3秒后如果没有收到视频播放的通知，就显示等待提示
     * JavaScript会定期调用notifyVideoPlaying来表示视频正在播放
     */
    @JavascriptInterface
    fun enablePlayCheck() {
        postDelayed(showWaitingViewAction, SHOW_WAITING_VIEW_DELAY)
    }

    /**
     * 禁用播放检查
     *
     * 移除等待提示的定时任务，并立即隐藏等待提示
     */
    @JavascriptInterface
    fun disablePlayCheck() {
        removeCallbacks(showWaitingViewAction)      // 取消显示等待提示的定时任务
        post(dismissWaitingViewAction)              // 立即隐藏等待提示
    }

    /**
     * JavaScript调用此方法设置视频的实际尺寸
     *
     * @param width 视频宽度（像素）
     * @param height 视频高度（像素）
     */
    @JavascriptInterface
    fun setVideoSize(width: Int, height: Int) {
        videoSize.set(width, height)
    }

    /**
     * 获取视频尺寸
     *
     * @return 包含视频宽度和高度的Point对象
     */
    fun getVideoSize() = videoSize

    // ========== 私有辅助方法 ==========

    /**
     * 调整宽视口
     *
     * 尝试缩小页面以获得更好的显示效果
     * 最多缩小3次
     */
    private fun adjustWideViewPort() {
        var level = 0
        while (canZoomOut() && level < MAX_ZOOM_OUT_LEVEL) {
            zoomOut()
            ++level
        }
    }

    /**
     * 页面加载信息 - 内部数据类
     *
     * 用于跟踪当前加载的页面信息
     *
     * @property url 正在加载的URL
     * @property isPageLoading 是否正在加载
     */
    private class PageLoadingInfo(var url: String, var isPageLoading: Boolean) {
        /**
         * 更新加载信息
         *
         * @param url 新的URL
         * @param isPageLoading 新的加载状态
         */
        fun set(url: String, isPageLoading: Boolean) {
            this.url = url
            this.isPageLoading = isPageLoading
        }
    }
}
