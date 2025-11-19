/*
 * Copyright (c) 2025 WebViewTvLive
 *
 * 文件: ChannelSettingsView.kt
 * 描述: 频道设置视图，显示和调整当前频道的各种设置
 */

package com.vasthread.aitv.widget

import android.content.Context
import android.graphics.Point
import android.os.SystemClock
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.vasthread.aitv.R
import com.vasthread.aitv.misc.getTrafficBytes
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.system.measureTimeMillis

/**
 * ChannelSettingsView - 频道设置视图
 *
 * 这个视图显示在屏幕右侧，提供当前频道的设置选项和实时信息：
 *
 * 设置选项：
 * - 播放源切换：选择主源或备用源
 * - 画面比例：16:9 或 4:3
 *
 * 实时信息：
 * - 视频分辨率：如 1920x1080
 * - 当前时间：HH:mm:ss 格式
 * - 网络速度：实时显示下载速度
 *
 * 特点：
 * - 每秒自动更新实时信息
 * - 自动计算网络速度（KB/s 或 MB/s）
 * - 根据播放源数量动态显示或隐藏相关UI
 *
 * @param context Android上下文
 * @param attrs XML属性集
 * @param defStyleAttr 默认样式属性
 */
@Suppress("PrivatePropertyName", "LocalVariableName")
class ChannelSettingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        /** 信息更新周期（毫秒） - 每秒更新一次 */
        private const val UPDATE_PERIOD = 1000L
    }

    // ========== UI组件 ==========

    /** 播放源标签 */
    private val tvSource: TextView

    /** 播放源按钮容器 */
    private val llSource: LinearLayout

    /** 播放源1按钮 */
    private val btnSource1: Button

    /** 播放源2按钮 */
    private val btnSource2: Button

    /** 16:9画面比例按钮 */
    private val rbAspectRatio_16_9: Button

    /** 4:3画面比例按钮 */
    private val rbAspectRatio_4_3: Button

    /** 显示视频分辨率的TextView */
    private val tvVideoSize: TextView

    /** 显示当前时间的TextView */
    private val tvCurrentTime: TextView

    /** 显示网络速度的TextView */
    private val tvCurrentNetworkSpeed: TextView

    // ========== 回调 ==========

    /** 播放源选择回调，传递选中的源索引（0或1） */
    var onChannelSourceSelected: ((Int) -> Unit)? = null

    /** 画面比例选择回调，true表示16:9，false表示4:3 */
    var onAspectRatioSelected: ((Boolean) -> Unit)? = null

    /** 获取视频尺寸的回调 */
    var onGetVideoSize: (() -> Point)? = null

    // ========== 网络速度计算相关 ==========

    /** 时间格式化器，格式：HH:mm:ss */
    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

    /** 上次的流量字节数（用于计算速度） */
    private var lastTrafficBytes = 0L

    /** 上次更新流量的时间（用于计算速度） */
    private var lastTrafficBytesUpdateTime = 0L

    /**
     * 定时更新任务
     * 每秒执行一次，更新实时信息
     */
    private lateinit var updateAction: Runnable

    /**
     * 初始化视图
     *
     * 设置：
     * - 加载布局文件
     * - 初始化UI组件
     * - 设置按钮点击事件
     * - 配置定时更新任务
     */
    init {
        isClickable = true   // 可点击，阻止事件穿透
        isFocusable = false  // 不可获得焦点

        // 加载布局文件
        LayoutInflater.from(context).inflate(R.layout.widget_channel_settings, this)

        // 获取UI组件引用
        tvSource = findViewById(R.id.tvSource)
        llSource = findViewById(R.id.llSource)
        btnSource1 = findViewById(R.id.btnSource1)
        btnSource2 = findViewById(R.id.btnSource2)
        rbAspectRatio_16_9 = findViewById(R.id.rbAspectRatio_16_9)
        rbAspectRatio_4_3 = findViewById(R.id.rbAspectRatio_4_3)
        tvVideoSize = findViewById(R.id.tvVideoSize)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvCurrentNetworkSpeed = findViewById(R.id.tvCurrentNetworkSpeed)

        // 设置按钮点击事件
        btnSource1?.setOnClickListener {
            onChannelSourceSelected?.invoke(0)  // 选择播放源1
        }
        btnSource2?.setOnClickListener {
            onChannelSourceSelected?.invoke(1)  // 选择播放源2
        }
        rbAspectRatio_16_9.setOnClickListener {
            onAspectRatioSelected?.invoke(true)  // 选择16:9比例
        }
        rbAspectRatio_4_3.setOnClickListener {
            onAspectRatioSelected?.invoke(false)  // 选择4:3比例
        }

        // 配置定时更新任务
        updateAction = Runnable {
            // 测量更新操作耗时，确保精确的更新周期
            val time = measureTimeMillis {
                // 更新网络速度
                updateNetworkSpeed()

                // 更新当前时间
                tvCurrentTime.text = sdf.format(System.currentTimeMillis())

                // 更新视频分辨率
                onGetVideoSize?.invoke()?.let { videoSize ->
                    tvVideoSize.text =
                        if (videoSize.x == 0 || videoSize.y == 0) {
                            context.getString(R.string.unknown)  // 未知
                        } else {
                            "${videoSize.x}x${videoSize.y}"  // 如 1920x1080
                        }
                }
            }

            // 安排下一次更新，减去本次执行耗时以保持精确的更新周期
            postDelayed(updateAction, UPDATE_PERIOD - time)
        }

        // 默认选中16:9画面比例
        setSelectedAspectRatio(true)
    }

    /**
     * 更新网络速度显示
     *
     * 计算方法：
     * 1. 获取当前累计流量字节数
     * 2. 计算与上次的流量差值
     * 3. 除以时间间隔，得到速度
     * 4. 根据速度大小选择单位（KB/s 或 MB/s）
     *
     * 格式：
     * - 小于1000 KB/s：显示为 "xxx KB/s"
     * - 大于等于1000 KB/s：显示为 "x.x MB/s" 或 "x MB/s"
     */
    private fun updateNetworkSpeed() {
        // 获取当前流量字节数
        val trafficBytes = getTrafficBytes()

        // 如果不是第一次更新，计算速度
        if (lastTrafficBytes != 0L) {
            // 计算时间间隔（秒）
            val duration = (SystemClock.uptimeMillis() - lastTrafficBytesUpdateTime) / 1000f

            // 计算速度（KB/s）
            var speed = (trafficBytes - lastTrafficBytes) / duration / 1024

            if (speed >= 1000F) {
                // 速度大于等于1000 KB/s，转换为 MB/s
                speed /= 1024
                var speedString = "%.1f".format(speed)

                // 如果是整数（如 5.0），去掉小数部分
                if (speedString.endsWith(".0")) {
                    speedString = speedString.substring(0, speedString.length - 2)
                }

                tvCurrentNetworkSpeed.text = "$speedString MB/s"
            } else {
                // 速度小于1000 KB/s，显示为 KB/s
                tvCurrentNetworkSpeed.text = "%d KB/s".format(speed.toInt())
            }
        }

        // 保存当前流量和时间，用于下次计算
        lastTrafficBytes = trafficBytes
        lastTrafficBytesUpdateTime = SystemClock.uptimeMillis()
    }

    /**
     * 设置选中的画面比例
     *
     * 更新按钮的选中状态，只有一个按钮会被选中
     *
     * @param is_16_9 true表示16:9，false表示4:3
     */
    fun setSelectedAspectRatio(is_16_9: Boolean) {
        rbAspectRatio_16_9.isSelected = is_16_9
        rbAspectRatio_4_3.isSelected = !is_16_9
    }

    /**
     * 设置选中的播放源
     *
     * 更新播放源按钮的选中状态，并根据源数量显示或隐藏相关UI
     *
     * 逻辑：
     * - 如果只有1个播放源，隐藏播放源选择UI
     * - 如果有2个或更多播放源，显示播放源选择UI
     *
     * @param sourceIndex 当前选中的源索引（0或1）
     * @param sourceSize 播放源总数
     */
    fun setSelectedChannelSource(sourceIndex: Int, sourceSize: Int) {
        // 更新按钮选中状态
        btnSource1.isSelected = sourceIndex == 0
        btnSource2.isSelected = sourceIndex == 1

        // 根据源数量决定是否显示播放源选择UI
        val hasMultipleSources = sourceSize >= 2
        btnSource2.isVisible = hasMultipleSources
        tvSource.isVisible = hasMultipleSources
        llSource.isVisible = hasMultipleSources
    }

    /**
     * 设置视图可见性
     *
     * 重写此方法以实现：
     * - 视图显示时：启动定时更新任务，让第一个按钮获得焦点
     * - 视图隐藏时：停止定时更新任务
     *
     * @param visibility 可见性：VISIBLE、INVISIBLE 或 GONE
     */
    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)

        if (visibility == VISIBLE) {
            // 视图变为可见，启动定时更新
            post(updateAction)

            // 让当前选中的画面比例按钮获得焦点
            post {
                (if (rbAspectRatio_16_9.isSelected) rbAspectRatio_16_9 else rbAspectRatio_4_3)
                    .requestFocus()
            }
        } else {
            // 视图隐藏，停止定时更新
            removeCallbacks(updateAction)
        }
    }

    /**
     * 分发按键事件
     *
     * 特殊处理菜单键：
     * - 按下菜单键时，确认当前选中的画面比例并关闭设置界面
     *
     * @param event 按键事件
     * @return true表示事件已处理
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_UP) {
            // 菜单键抬起，触发画面比例选择回调
            onAspectRatioSelected?.invoke(rbAspectRatio_16_9.isSelected)
        }
        return super.dispatchKeyEvent(event)
    }
}
