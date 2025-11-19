/*
 * Copyright (c) 2025 AITVLive
 *
 * 文件: SettingsManager.kt
 * 描述: 应用设置管理器，负责管理和持久化各种用户设置
 *
 * 主要功能:
 * 1. 管理播放列表选择
 * 2. 管理WebView触摸设置
 * 3. 管理页面加载超时时间
 * 4. 管理频道播放源选择
 * 5. 管理用户唯一标识符
 */

package com.vasthread.aitv.settings

import com.vasthread.aitv.misc.preference
import com.vasthread.aitv.playlist.PlaylistManager
import java.util.UUID

/**
 * SettingsManager - 设置管理器（单例对象）
 *
 * 这是一个单例对象，负责管理应用的所有设置项。
 * 所有设置都通过 SharedPreferences 进行持久化存储。
 *
 * 管理的设置包括：
 * - **播放列表设置**: 当前选择的播放列表
 * - **WebView设置**: 是否允许触摸WebView
 * - **加载设置**: 页面最大加载时间
 * - **频道设置**: 每个频道上次选择的播放源
 * - **用户标识**: 唯一的用户ID（UUID）
 *
 * 使用方式：
 * ```kotlin
 * // 获取设置
 * val touchable = SettingsManager.isWebViewTouchable()
 *
 * // 保存设置
 * SettingsManager.setWebViewTouchable(true)
 * ```
 */
object SettingsManager {

    // ========== SharedPreferences 键名常量 ==========

    /** WebView是否可触摸的设置键 */
    private const val KEY_WEB_VIEW_TOUCHABLE = "web_view_touchable"

    /** 页面最大加载时间的设置键 */
    private const val KEY_MAX_LOADING_TIME = "max_loading_time"

    /** 用户唯一标识符（UUID）的设置键 */
    private const val KEY_UUID = "uuid"

    // ========== 播放列表相关设置 ==========

    /**
     * 获取所有可用播放列表的名称
     *
     * 从 PlaylistManager 获取内置播放列表，提取名称部分
     * 内置播放列表格式: Pair(名称, URL)
     *
     * @return 播放列表名称数组
     */
    fun getPlaylistNames(): Array<String> {
        // 获取内置播放列表
        val builtInPlaylists = PlaylistManager.getBuiltInPlaylists()

        // 创建名称数组
        val names = arrayOfNulls<String>(builtInPlaylists.size)

        // 提取每个播放列表的名称（Pair的第一个元素）
        for (i in names.indices) {
            names[i] = builtInPlaylists[i].first
        }

        // 确保没有null值并返回
        return names.requireNoNulls()
    }

    /**
     * 获取当前选中的播放列表位置
     *
     * 通过当前播放列表的URL在内置列表中查找对应的位置索引
     * 如果未找到匹配的URL，返回0（第一个位置）
     *
     * @return 当前选中播放列表的索引位置（从0开始）
     */
    fun getSelectedPlaylistPosition(): Int {
        // 获取当前播放列表的URL
        val playlistUrl = PlaylistManager.getPlaylistUrl()

        // 获取所有内置播放列表
        val builtInPlaylists = PlaylistManager.getBuiltInPlaylists()

        // 如果没有内置播放列表，返回0
        if (builtInPlaylists.isEmpty()) {
            return 0
        }

        // 在内置列表中查找匹配的URL
        for (i in builtInPlaylists.indices) {
            if (builtInPlaylists[i].second == playlistUrl) {
                return i  // 找到匹配的URL，返回索引
            }
        }

        // 未找到匹配，返回默认值0
        return 0
    }

    /**
     * 设置选中的播放列表位置
     *
     * 根据位置索引，从内置列表中获取对应的播放列表URL
     * 并通过 PlaylistManager 设置为当前播放列表
     *
     * @param position 播放列表的索引位置（从0开始）
     */
    fun setSelectedPlaylistPosition(position: Int) {
        // 获取内置播放列表
        val builtInPlaylists = PlaylistManager.getBuiltInPlaylists()

        // 根据位置获取URL（Pair的第二个元素）并设置
        PlaylistManager.setPlaylistUrl(builtInPlaylists[position].second)
    }

    // ========== WebView触摸设置 ==========

    /**
     * 设置WebView是否可触摸
     *
     * 控制用户是否可以直接触摸和操作WebView
     * - true: 允许触摸，用户可以与网页交互
     * - false: 禁用触摸，防止误操作
     *
     * @param touchable 是否允许触摸
     */
    fun setWebViewTouchable(touchable: Boolean) {
        preference.edit().putBoolean(KEY_WEB_VIEW_TOUCHABLE, touchable).apply()
    }

    /**
     * 获取WebView是否可触摸
     *
     * @return true表示允许触摸，false表示禁用触摸
     *         默认值为false（禁用触摸）
     */
    fun isWebViewTouchable(): Boolean {
        return preference.getBoolean(KEY_WEB_VIEW_TOUCHABLE, false)
    }

    // ========== 页面加载时间设置 ==========

    /**
     * 设置页面最大加载时间
     *
     * 控制WebView加载页面的最大等待时间
     * 超过这个时间后，可能会显示加载超时提示
     *
     * @param second 最大加载时间（秒）
     */
    fun setMaxLoadingTime(second: Int) {
        preference.edit().putInt(KEY_MAX_LOADING_TIME, second).apply()
    }

    /**
     * 获取页面最大加载时间
     *
     * @return 最大加载时间（秒），默认值为15秒
     */
    fun getMaxLoadingTime(): Int {
        return preference.getInt(KEY_MAX_LOADING_TIME, 15)
    }

    // ========== 频道播放源设置 ==========

    /**
     * 生成频道播放源索引的存储键
     *
     * 每个频道的播放源选择都单独保存
     * 键格式: "source_index[频道名]"
     *
     * 例如: "source_index[CCTV-1 综合]"
     *
     * @param channelName 频道名称
     * @return SharedPreferences中的存储键
     */
    private fun lastSourceIndexKey(channelName: String) = "source_index[$channelName]"

    /**
     * 设置频道上次选择的播放源索引
     *
     * 当用户为某个频道选择了特定的播放源时，保存这个选择
     * 下次播放该频道时，会自动使用上次选择的播放源
     *
     * 应用场景：
     * - 某个频道有多个播放源（主源、备用源1、备用源2...）
     * - 用户发现某个源更稳定，切换到该源
     * - 保存用户的选择，下次自动使用该源
     *
     * @param channelName 频道名称
     * @param index 播放源的索引位置（从0开始）
     */
    fun setChannelLastSourceIndex(channelName: String, index: Int) {
        preference.edit().putInt(lastSourceIndexKey(channelName), index).apply()
    }

    /**
     * 获取频道上次选择的播放源索引
     *
     * 读取用户上次为该频道选择的播放源
     * 如果是第一次播放该频道，返回默认值0（第一个播放源）
     *
     * @param channelName 频道名称
     * @return 播放源索引（从0开始），默认值为0
     */
    fun getChannelLastSourceIndex(channelName: String): Int {
        return preference.getInt(lastSourceIndexKey(channelName), 0)
    }

    // ========== 用户标识管理 ==========

    /**
     * 获取用户唯一标识符（UUID）
     *
     * 用途：
     * - 区分不同的用户设备
     * - 用于统计分析
     * - 用于个性化设置
     *
     * 特点：
     * - 第一次调用时自动生成UUID
     * - 生成后永久保存，不会改变
     * - 除非用户清除应用数据，否则UUID保持不变
     *
     * UUID格式示例: "550e8400-e29b-41d4-a716-446655440000"
     *
     * @return 用户的唯一标识符（UUID字符串）
     */
    fun getUserId(): String {
        // 尝试从SharedPreferences读取已保存的UUID
        var uuid = preference.getString(KEY_UUID, null)

        // 如果UUID不存在或为空，生成新的UUID
        if (uuid.isNullOrBlank()) {
            // 生成随机UUID
            uuid = UUID.randomUUID().toString()

            // 保存到SharedPreferences，以便下次使用
            preference.edit().putString(KEY_UUID, uuid).apply()
        }

        return uuid
    }
}
