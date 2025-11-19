/*
 * Copyright (c) 2025 WebViewTvLive
 *
 * 文件: ExitConfirmView.kt
 * 描述: 退出确认对话框，提供退出应用或进入设置的选项
 */

package com.vasthread.aitv.widget

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.tencent.smtt.sdk.QbSdk
import com.vasthread.aitv.R

/**
 * ExitConfirmView - 退出确认对话框视图
 *
 * 当用户按返回键时显示这个对话框，防止误操作退出应用。
 *
 * 功能：
 * - 提供两个按钮：退出应用 / 进入设置
 * - 显示应用版本和X5内核版本信息
 * - 显示Android系统版本信息
 *
 * 设计理念：
 * 在电视应用中，用户可能会误按返回键，直接退出会影响体验。
 * 这个对话框给用户一个确认的机会，同时也提供了快速进入设置的入口。
 *
 * 使用流程：
 * 1. 用户按返回键
 * 2. MainActivity 显示这个对话框
 * 3. 用户选择"退出"或"设置"
 * 4. 触发 onUserSelection 回调
 * 5. MainActivity 执行相应操作
 *
 * @param context Android上下文
 * @param attrs XML属性集
 * @param defStyleAttr 默认样式属性
 */
@SuppressLint("SetTextI18n")
class ExitConfirmView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /**
     * 用户选择枚举
     *
     * EXIT: 用户选择退出应用
     * SETTINGS: 用户选择进入设置
     */
    enum class Selection {
        EXIT,      // 退出应用
        SETTINGS   // 进入设置
    }

    /** 设置按钮 */
    private val btnSettings: Button

    /**
     * 用户选择的回调
     * 当用户点击按钮时，会调用这个回调传递用户的选择
     */
    var onUserSelection: ((Selection) -> Unit)? = null

    /**
     * 初始化视图
     *
     * 设置：
     * - 布局方向和对齐方式
     * - 背景样式
     * - 按钮点击事件
     * - 版本信息显示
     */
    init {
        isClickable = true   // 可点击，阻止事件穿透到后面的视图
        isFocusable = false  // 不可获得焦点

        // 设置布局属性
        gravity = Gravity.CENTER     // 内容居中显示
        orientation = VERTICAL       // 垂直排列

        // 设置背景
        setBackgroundResource(R.drawable.bg)

        // 加载布局文件
        LayoutInflater.from(context).inflate(R.layout.widget_exit_confirm, this)

        // 获取设置按钮并设置点击事件
        btnSettings = findViewById(R.id.btnSettings)
        btnSettings.setOnClickListener {
            // 用户点击设置按钮，触发回调
            onUserSelection?.invoke(Selection.SETTINGS)
        }

        // 获取退出按钮并设置点击事件
        findViewById<Button>(R.id.btnExit).setOnClickListener {
            // 用户点击退出按钮，触发回调
            onUserSelection?.invoke(Selection.EXIT)
        }

        // 显示应用版本和X5内核版本信息
        // 格式: "App: 1.0 | X5: 43900"
        findViewById<TextView>(R.id.tvAppInfo).text =
            "App: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName} | " +
            "X5: ${QbSdk.getTbsVersion(context)}"

        // 显示Android系统版本信息
        // 格式: "Android 10 (API 29)"
        findViewById<TextView>(R.id.tvSystemInfo).text =
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    /**
     * 设置视图可见性
     *
     * 重写此方法以实现：
     * - 当对话框显示时，自动让"设置"按钮获得焦点
     * - 这样用户可以直接用遥控器选择
     *
     * @param visibility 可见性：VISIBLE、INVISIBLE 或 GONE
     */
    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)

        // 如果对话框变为可见，让设置按钮获得焦点
        if (visibility == VISIBLE) {
            post { btnSettings.requestFocus() }
        }
    }
}
