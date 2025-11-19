/*
 * Copyright (c) 2025 AITVLive
 *
 * 文件: Global.kt
 * 描述: 全局应用上下文和共享首选项管理
 *
 * 提供全局访问应用上下文和SharedPreferences的便捷方式
 */

package com.vasthread.aitv.misc

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * 私有的应用上下文变量
 * 在应用启动时通过 setApplication() 设置
 */
private var _application: Context? = null

/**
 * 全局应用上下文
 *
 * 提供对应用Context的全局访问
 * 必须在使用前调用 setApplication() 进行初始化
 *
 * 使用示例：
 * ```
 * val file = File(application.filesDir, "data.json")
 * ```
 */
val application: Context
    get() {
        return _application!!
    }

/**
 * 设置全局应用上下文
 *
 * 应该在Application的onCreate()方法中调用
 *
 * @param application 应用上下文
 */
fun setApplication(application: Context) {
    _application = application
}

/**
 * 全局SharedPreferences对象
 *
 * 使用延迟初始化，只在第一次访问时创建
 * 用于保存应用的设置和状态数据
 *
 * 使用示例：
 * ```
 * preference.edit()
 *     .putString("key", "value")
 *     .apply()
 * ```
 */
val preference: SharedPreferences by lazy {
    PreferenceManager.getDefaultSharedPreferences(application)
}
