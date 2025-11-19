/*
 * Copyright (c) 2025 WebViewTvLive
 *
 * 文件: Channel.kt
 * 描述: 频道数据模型，表示一个电视频道
 */

package com.vasthread.aitv.playlist

import com.google.gson.annotations.SerializedName
import com.vasthread.aitv.settings.SettingsManager

/**
 * Channel - 频道数据类
 *
 * 表示一个电视频道，包含频道名称、所属分组和播放源URL列表。
 * 一个频道可以有多个播放源，用户可以在不同源之间切换。
 *
 * @property name 频道名称（如"CCTV-1 综合"）
 * @property groupName 频道所属分组（如"CCTV"），在JSON中序列化为"group"
 * @property urls 播放源URL列表，支持多个备用源
 */
data class Channel @JvmOverloads constructor(
    var name: String = "",
    @SerializedName("group")
    var groupName: String = "",
    var urls: List<String> = emptyList(),
) {

    /**
     * 当前播放源URL
     *
     * 根据用户上次选择的播放源索引，返回对应的URL
     * 如果索引无效，返回第一个URL
     */
    val url: String
        get() {
            // 获取用户上次选择的播放源索引
            var index = SettingsManager.getChannelLastSourceIndex(name)

            // 确保索引在有效范围内
            if (index >= urls.size || index < 0) index = 0

            // 返回对应索引的URL
            return urls[index]
        }

    /**
     * 判断两个频道是否相等
     * 使用默认的对象相等性比较
     */
    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    /**
     * 返回频道的字符串表示
     * 用于调试和日志输出
     */
    override fun toString(): String {
        return "name=$name, groupName=$groupName, urls=$urls"
    }

    /**
     * 计算频道的哈希码
     * 基于频道名称和分组名称
     */
    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + groupName.hashCode()
        return result
    }
}
