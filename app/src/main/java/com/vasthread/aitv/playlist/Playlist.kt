/*
 * Copyright (c) 2025 WebViewTvLive
 *
 * 文件: Playlist.kt
 * 描述: 播放列表数据模型，包含多个频道分组
 */

package com.vasthread.aitv.playlist

/**
 * Playlist - 播放列表数据类
 *
 * 表示一个完整的播放列表，包含多个频道分组。
 * 每个分组包含多个频道，形成三级结构：
 * Playlist -> ChannelGroup -> Channel
 *
 * @property title 播放列表标题
 * @property groups 频道分组列表
 */
data class Playlist(
    var title: String = "",
    val groups: MutableList<ChannelGroup> = mutableListOf()
) {

    companion object {
        /**
         * 从频道列表创建播放列表
         *
         * 将扁平的频道列表按分组名称组织成分组结构
         * 相同分组名的频道会被放入同一个ChannelGroup中
         *
         * @param title 播放列表标题
         * @param allChannels 所有频道的集合
         * @return 组织好的Playlist对象
         */
        fun createFromAllChannels(title: String, allChannels: Collection<Channel>?): Playlist {
            val groups: MutableList<ChannelGroup> = mutableListOf()

            // 遍历所有频道，按分组归类
            allChannels?.forEach { channel ->
                // 查找或创建对应的频道分组
                val channelGroup = groups.firstOrNull { group -> group.name == channel.groupName }
                    ?: ChannelGroup(channel.groupName).apply { groups.add(this) }

                // 将频道添加到分组中
                channelGroup.channels.add(channel)
            }

            return Playlist(title, groups)
        }

        /**
         * 获取播放列表中的第一个频道
         *
         * 用于在启动时选择一个默认频道
         *
         * @receiver 播放列表对象（可为null）
         * @return 第一个频道，如果列表为空则返回null
         */
        fun Playlist?.firstChannel(): Channel? {
            // 检查播放列表是否为null
            if (this == null) return null

            // 检查是否有分组
            if (groups.isEmpty()) return null

            // 遍历分组，返回第一个非空分组的第一个频道
            groups.forEach { group ->
                if (group.channels.isNotEmpty()) {
                    return group.channels[0]
                }
            }

            return null
        }
    }

    /**
     * 获取播放列表中的所有频道
     *
     * 将所有分组中的频道合并成一个扁平列表
     *
     * @return 包含所有频道的列表
     */
    fun getAllChannels(): List<Channel> {
        val allChannels = mutableListOf<Channel>()

        // 遍历所有分组，收集频道
        groups.forEach { group ->
            allChannels.addAll(group.channels)
        }

        return allChannels
    }

    /**
     * 查找频道在播放列表中的位置
     *
     * 返回频道所在的分组索引和在分组内的索引
     *
     * @param c 要查找的频道
     * @return Pair(分组索引, 频道索引)，如果未找到则返回null
     */
    fun indexOf(c: Channel): Pair<Int, Int>? {
        // 遍历所有分组
        for ((i, group) in groups.withIndex()) {
            // 检查分组名称是否匹配
            if (group.name == c.groupName) {
                // 在分组内查找频道的索引
                val j = groups[i].channels.indexOf(c)
                if (j >= 0) {
                    return Pair(i, j)
                }
            }
        }

        // 未找到
        return null
    }
}
