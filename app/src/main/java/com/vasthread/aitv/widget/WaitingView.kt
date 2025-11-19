/*
 * Copyright (c) 2025 WebViewTvLive
 *
 * 文件: WaitingView.kt
 * 描述: 等待提示视图，当视频加载超时时显示，并自动切换播放源
 */

package com.vasthread.aitv.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.Toast
import com.vasthread.aitv.R
import com.vasthread.aitv.misc.application
import com.vasthread.aitv.settings.SettingsManager

/**
 * WaitingView - 等待提示视图
 *
 * 当视频加载时间过长时，这个视图会显示"正在加载"的提示。
 * 如果超过设定的最大加载时间，会自动切换到下一个播放源重试。
 *
 * 功能：
 * - 显示加载提示UI
 * - 自动计时，超时后切换播放源
 * - 循环切换所有可用播放源
 *
 * 工作流程：
 * 1. 视频开始加载时，WaitingView变为可见
 * 2. 启动定时器（默认15秒）
 * 3. 如果15秒后视频还没播放，自动切换到下一个播放源
 * 4. 如果所有播放源都试过了，回到第一个源
 *
 * 使用场景：
 * - 网络不稳定，视频加载缓慢
 * - 某个播放源失效，自动切换到备用源
 * - 提升用户体验，无需手动切换
 *
 * @param context Android上下文
 * @param attrs XML属性集
 * @param defStyleAttr 默认样式属性
 */
class WaitingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /**
     * 关联的播放器视图
     * 需要外部设置，用于控制频道重新加载
     */
    var playerView: ChannelPlayerView? = null

    /**
     * 重新加载频道的定时任务
     *
     * 当这个任务被执行时：
     * 1. 显示"正在切换播放源"的提示
     * 2. 获取当前频道的下一个播放源索引
     * 3. 如果已经是最后一个源，回到第一个源
     * 4. 保存新的播放源索引
     * 5. 刷新频道，使用新的播放源重新加载
     */
    private val reloadAction = Runnable {
        // 显示切换提示
        Toast.makeText(application, R.string.toast_reload_channel, Toast.LENGTH_SHORT).show()

        // 检查是否有正在播放的频道
        if (playerView?.channel != null) {
            val channelName = playerView!!.channel!!.name

            // 获取当前播放源索引，并切换到下一个
            var index = SettingsManager.getChannelLastSourceIndex(channelName) + 1

            // 如果已经是最后一个源，回到第一个源（循环）
            if (index >= playerView!!.channel!!.urls.size) index = 0

            // 保存新的播放源索引
            SettingsManager.setChannelLastSourceIndex(channelName, index)

            // 刷新频道，使用新的播放源
            playerView!!.refreshChannel()
        }
    }

    /**
     * 初始化视图
     *
     * 设置：
     * - 可点击（阻止触摸事件穿透到后面的视图）
     * - 不可获得焦点（避免干扰遥控器操作）
     * - 背景样式
     * - 加载布局文件
     */
    init {
        isClickable = true   // 可点击，阻止事件穿透
        isFocusable = false  // 不可获得焦点
        setBackgroundResource(R.drawable.bg)

        // 加载等待提示的布局
        LayoutInflater.from(context).inflate(R.layout.widget_waiting, this)
    }

    /**
     * 设置视图可见性
     *
     * 重写此方法以实现自动定时切换播放源的功能：
     * - 当视图变为可见时，启动定时器
     * - 当视图变为不可见时，取消定时器
     *
     * @param visibility 可见性：VISIBLE、INVISIBLE 或 GONE
     */
    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)

        // 先移除之前的定时任务（如果有）
        removeCallbacks(reloadAction)

        // 如果视图变为可见，启动定时器
        if (visibility == VISIBLE) {
            // 从设置中获取最大加载时间（秒），转换为毫秒
            val maxLoadingTimeMs = SettingsManager.getMaxLoadingTime() * 1000L

            // 延迟执行重新加载任务
            postDelayed(reloadAction, maxLoadingTimeMs)
        }
    }
}
