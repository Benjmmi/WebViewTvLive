/*
 * Copyright (c) 2025 AITVLive
 *
 * 文件: UiLayout.kt
 * 描述: 自适应UI布局容器，处理屏幕刘海（异形屏）的适配
 */

package com.vasthread.aitv.widget

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.WindowInsets
import android.widget.FrameLayout

/**
 * UiLayout - 自适应UI布局容器
 *
 * 这个自定义FrameLayout主要用于处理现代Android设备的异形屏（刘海屏、挖孔屏）适配。
 *
 * 功能：
 * - 自动检测屏幕的安全区域（DisplayCutout）
 * - 调整padding以避免内容被刘海遮挡
 * - 只在Android P（API 28）及以上版本生效
 *
 * 使用场景：
 * - 作为Activity的根布局
 * - 包裹需要避开刘海区域的UI内容
 *
 * 工作原理：
 * 当系统发送WindowInsets时，自动读取DisplayCutout信息，
 * 并将安全区域的边距设置为padding，确保内容显示在安全区域内。
 *
 * @param context Android上下文
 * @param attrs XML属性集
 * @param defStyleAttr 默认样式属性
 */
class UiLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /**
     * 应用窗口插入（Window Insets）
     *
     * 当系统传递窗口插入信息时调用，用于处理：
     * - 状态栏高度
     * - 导航栏高度
     * - 屏幕刘海（DisplayCutout）区域
     *
     * 这个方法会根据DisplayCutout的安全区域自动调整padding，
     * 确保UI内容不会被刘海遮挡。
     *
     * @param insets 系统传递的窗口插入信息
     * @return 处理后的窗口插入对象
     */
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        // 只在Android P（API 28）及以上版本处理DisplayCutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 获取DisplayCutout对象，包含刘海区域的信息
            val cutout = insets.displayCutout

            // 设置padding以避开刘海区域
            // 如果cutout为null（没有刘海），使用0作为默认值
            setPadding(
                cutout?.safeInsetLeft ?: 0,    // 左边安全边距
                cutout?.safeInsetTop ?: 0,     // 上边安全边距
                cutout?.safeInsetRight ?: 0,   // 右边安全边距
                cutout?.safeInsetBottom ?: 0   // 下边安全边距
            )
        }

        // 调用父类方法，继续传递insets给子视图
        return super.onApplyWindowInsets(insets)
    }
}
