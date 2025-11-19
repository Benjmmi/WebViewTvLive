/*
 * Copyright (c) 2025 WebViewTvLive
 *
 * 文件: ChannelBarView.kt
 * 描述: 频道信息栏视图，显示当前频道名称和加载进度
 */

package com.vasthread.aitv.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.vasthread.aitv.R
import com.vasthread.aitv.playlist.Channel

/**
 * ChannelBarView - 频道信息栏视图
 *
 * 这个视图显示在屏幕顶部，用于显示：
 * - 当前播放的频道名称
 * - 页面加载进度（0-100%）
 *
 * 行为：
 * - 当切换频道时自动显示
 * - 加载完成后3秒自动隐藏
 * - 用户可以通过这个视图了解当前频道和加载状态
 *
 * 生命周期：
 * 1. 用户切换频道
 * 2. 调用 setCurrentChannelAndShow() 显示频道名
 * 3. 随着页面加载，多次调用 setProgress() 更新进度
 * 4. 进度达到100%后，3秒后自动隐藏
 *
 * @param context Android上下文
 * @param attrs XML属性集
 * @param defStyleAttr 默认样式属性
 */
class ChannelBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        /** 加载完成后自动隐藏的延迟时间（毫秒） - 3秒 */
        private const val DISMISS_DELAY = 3000L
    }

    // ========== UI组件 ==========

    /** 显示频道名称的TextView */
    private val tvChannelName: TextView

    /** 显示加载进度的TextView */
    private val tvProgress: TextView

    // 注释掉的代码：显示频道URL的TextView
    // private val tvChannelUrl: TextView

    /**
     * 自动隐藏的定时任务
     * 当加载完成后，延迟3秒执行
     */
    private val dismissAction = Runnable { visibility = GONE }

    /**
     * 初始化视图
     *
     * 设置：
     * - 背景样式
     * - 加载布局文件
     * - 初始化TextView引用
     * - 初始状态为隐藏
     */
    init {
        isClickable = true   // 可点击，阻止事件穿透
        isFocusable = false  // 不可获得焦点

        // 设置背景
        setBackgroundResource(R.drawable.bg)

        // 加载布局文件
        LayoutInflater.from(context).inflate(R.layout.widget_channel_bar, this)

        // 获取TextView引用
        tvChannelName = findViewById(R.id.tvChannelName)
        tvProgress = findViewById(R.id.tvProgress)

        // 注释掉的代码：获取频道URL的TextView
        // tvChannelUrl = findViewById(R.id.tvChannelUrl)

        // 初始状态为隐藏
        visibility = GONE
    }

    /**
     * 设置当前频道并显示信息栏
     *
     * 当用户切换频道时调用此方法：
     * 1. 取消之前的自动隐藏任务
     * 2. 显示新频道的名称
     * 3. 重置进度为0
     * 4. 显示信息栏
     *
     * @param channel 要显示的频道对象
     */
    fun setCurrentChannelAndShow(channel: Channel) {
        // 移除之前的自动隐藏任务
        removeCallbacks(dismissAction)

        // 显示频道名称
        tvChannelName.text = channel.name

        // 注释掉的代码：显示频道URL
        // tvChannelUrl.text = channel.url

        // 重置进度为0
        setProgress(0)

        // 显示信息栏
        visibility = VISIBLE
    }

    /**
     * 立即隐藏信息栏
     *
     * 取消自动隐藏任务并立即隐藏视图
     */
    fun dismiss() {
        removeCallbacks(dismissAction)
        visibility = GONE
    }

    /**
     * 设置加载进度
     *
     * 随着页面加载进度更新，多次调用此方法更新显示
     *
     * 行为：
     * - 显示进度百分比（如"50%"）
     * - 每次更新都会重置自动隐藏的定时器
     * - 当进度达到100%时，启动3秒后自动隐藏的定时器
     *
     * @param progress 加载进度，范围0-100
     */
    fun setProgress(progress: Int) {
        // 移除之前的自动隐藏任务
        removeCallbacks(dismissAction)

        // 更新进度显示
        tvProgress.text = "$progress%"

        // 如果加载完成（100%），3秒后自动隐藏
        if (progress == 100) {
            postDelayed(dismissAction, DISMISS_DELAY)
        }
    }
}
