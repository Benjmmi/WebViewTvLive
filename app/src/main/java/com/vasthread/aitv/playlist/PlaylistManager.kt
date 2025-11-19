/*
 * Copyright (c) 2025 WebViewTvLive
 *
 * 文件: PlaylistManager.kt
 * 描述: 播放列表管理器，负责从网络下载、解析和缓存电视频道列表
 *
 * 主要功能:
 * 1. 从远程服务器下载频道列表
 * 2. 解析M3U格式的频道列表文本
 * 3. 将频道列表缓存到本地文件
 * 4. 管理缓存过期时间（24小时）
 * 5. 提供频道列表更新的状态通知
 */

package com.vasthread.aitv.playlist

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.vasthread.aitv.misc.application
import com.vasthread.aitv.misc.preference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * PlaylistManager - 播放列表管理器（单例对象）
 *
 * 这是一个单例对象，负责管理整个应用的频道列表。
 * 它提供了以下核心功能：
 *
 * 1. **网络下载**：
 *    - 从远程URL下载频道列表
 *    - 使用OkHttp3进行网络请求
 *    - 支持失败重试机制
 *
 * 2. **格式解析**：
 *    - 解析M3U格式的频道列表
 *    - 将文本格式转换为JSON
 *    - 支持频道分组
 *
 * 3. **缓存管理**：
 *    - 将频道列表保存到本地文件
 *    - 24小时缓存过期策略
 *    - 检测本地和远程内容是否变化
 *
 * 4. **状态通知**：
 *    - 通知UI频道列表已更新
 *    - 通知UI更新任务的进行状态
 *
 * M3U格式说明：
 * 频道列表使用自定义的M3U格式，每行包含：
 * - 分组标记：频道组名,#genre#
 * - 频道信息：频道名,播放URL
 *
 * 示例：
 * ```
 * CCTV,#genre#
 * CCTV-1 综合,http://example.com/cctv1.m3u8
 * CCTV-2 财经,http://example.com/cctv2.m3u8
 * ```
 */
object PlaylistManager {

    // ========== 常量定义 ==========

    /** 日志标签 */
    private const val TAG = "PlaylistManager"

    /** 缓存过期时间（毫秒）- 24小时 */
    private const val CACHE_EXPIRATION_MS = 24 * 60 * 60 * 1000L

    /** SharedPreferences中保存播放列表URL的键名 */
    private const val KEY_PLAYLIST_URL = "playlist_url"

    /** SharedPreferences中保存上次更新时间的键名 */
    private const val KEY_LAST_UPDATE = "last_update"

    /** 更新失败后的重试延迟时间（毫秒）- 10秒 */
    private const val UPDATE_RETRY_DELAY = 10 * 1000L

    // ========== 工具对象 ==========

    /**
     * HTTP客户端，用于下载频道列表
     * 配置了5秒的连接超时和读取超时
     */
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)  // 连接超时5秒
        .readTimeout(5, TimeUnit.SECONDS)     // 读取超时5秒
        .build()

    /**
     * Gson对象，用于JSON序列化和反序列化
     * 使用漂亮打印格式，便于阅读和调试
     */
    private val gson = GsonBuilder().setPrettyPrinting().create()!!

    /**
     * JSON类型标记，用于将JSON解析为Channel列表
     */
    private val jsonTypeToken = object : TypeToken<List<Channel>>() {}

    /**
     * 播放列表缓存文件
     * 保存在应用的私有文件目录中
     */
    private val playlistFile = File(application.filesDir, "playlist.json")

    /**
     * 内置播放列表列表
     * 格式：Pair(名称, URL)
     * 当前为空，可以在这里添加默认的播放列表
     */
    private val builtInPlaylists = listOf<Pair<String, String>>()

    // ========== 回调和状态 ==========

    /**
     * 播放列表变化的回调
     * 当从网络下载到新的播放列表时调用
     */
    var onPlaylistChange: ((Playlist) -> Unit)? = null

    /**
     * 更新任务状态变化的回调
     * 当开始或结束更新任务时调用，用于显示/隐藏加载提示
     */
    var onUpdatePlaylistJobStateChange: ((Boolean) -> Unit)? = null

    /**
     * 更新播放列表的协程任务
     * 用于跟踪和取消正在进行的更新任务
     */
    private var updatePlaylistJob: Job? = null

    /**
     * 是否正在更新播放列表
     * 设置此值时会自动触发状态变化回调
     */
    private var isUpdating = false
        set(value) {
            field = value
            // 通知外部更新状态已改变
            onUpdatePlaylistJobStateChange?.invoke(value)
        }

    // ========== 公共方法 ==========

    /**
     * 获取内置播放列表
     *
     * @return 内置播放列表的列表
     */
    fun getBuiltInPlaylists() = builtInPlaylists

    /**
     * 设置播放列表URL
     *
     * 设置新的播放列表URL后，会立即请求更新
     * 同时会重置上次更新时间，强制重新下载
     *
     * @param url 新的播放列表URL
     */
    fun setPlaylistUrl(url: String) {
        // 保存URL到SharedPreferences
        preference.edit()
            .putString(KEY_PLAYLIST_URL, url)
            .putLong(KEY_LAST_UPDATE, 0)  // 重置更新时间，强制更新
            .apply()

        // 立即请求更新播放列表
        requestUpdatePlaylist()
    }

    /**
     * 获取当前播放列表的URL
     *
     * 返回固定的GitHub镜像URL
     * 注释掉的代码显示了从SharedPreferences读取URL的方式
     *
     * @return 播放列表的URL
     */
    fun getPlaylistUrl() =
        "https://hub.gitmirror.com/raw.githubusercontent.com/Benjmmi/iptv-api/refs/heads/master/output/user_result.txt"
//        preference.getString(KEY_PLAYLIST_URL, builtInPlaylists.firstOrNull()?.second ?: "")!!

    /**
     * 设置上次更新时间
     *
     * @param time 更新时间戳（毫秒）
     * @param requestUpdate 是否立即请求更新，默认为false
     */
    fun setLastUpdate(time: Long, requestUpdate: Boolean = false) {
        // 保存更新时间到SharedPreferences
        preference.edit().putLong(KEY_LAST_UPDATE, time).apply()

        // 如果需要，立即请求更新
        if (requestUpdate) requestUpdatePlaylist()
    }

    /**
     * 请求更新播放列表
     *
     * 这个方法会启动一个协程任务来下载和更新播放列表。
     * 更新流程：
     * 1. 检查是否有正在进行的更新任务，如果有则忽略
     * 2. 检查缓存是否过期（24小时）
     * 3. 如果过期，从网络下载新的播放列表
     * 4. 解析M3U格式为JSON
     * 5. 与本地缓存比较，如果有变化则保存并通知UI
     * 6. 如果下载失败，等待10秒后重试
     *
     * 注意：此方法不会阻塞调用线程，更新在后台IO线程中进行
     */
    private fun requestUpdatePlaylist() {
        // 检查是否有正在执行的更新任务
        val lastJobCompleted = updatePlaylistJob?.isCompleted
        if (lastJobCompleted != null && !lastJobCompleted) {
            Log.i(TAG, "A job is executing, ignore!")
            return
        }

        // 注释掉的代码：用于测试的示例播放列表
//        val playlistText = """
//            [{"name":"CCTV-1 综合","group":"China","urls":["https://stream1.freetv.fun/a4f6e6163319cb6ad59892a54343f514a9ff20917af903f2ee1818e005f43202.ctv"]},{"name":"CCTV-2 财经","group":"China","urls":["https://stream1.freetv.fun/1847276d6bc5debba388a2d32acc6ac34427b06d901dedb2d9678954b03ad752.ctv"]}]
//        """.trimIndent()
//        playlistFile.writeText(playlistText)

        // 在IO线程中启动更新任务
        updatePlaylistJob = CoroutineScope(Dispatchers.IO).launch {
            var times = 0  // 重试次数计数器

            // 定义检查是否需要更新的lambda函数
            // 如果距离上次更新超过24小时，则需要更新
            val needUpdate = {
                System.currentTimeMillis() - preference.getLong(
                    KEY_LAST_UPDATE,
                    0L
                ) > CACHE_EXPIRATION_MS
            }

            // 标记为正在更新
            isUpdating = true

            // 循环重试，直到更新成功或缓存未过期
            while (needUpdate()) {
                ++times
                Log.i(TAG, "Updating playlist... times=${times}")

                try {
                    // 1. 构建HTTP请求
                    val url = getPlaylistUrl()
                    val request = Request.Builder().url(url).get().build()

                    // 2. 执行网络请求
                    val response = client.newCall(request).execute()

                    // 3. 检查HTTP响应是否成功
                    if (!response.isSuccessful) {
                        throw Exception("Response code ${response.code}")
                    }

                    // 4. 读取响应内容
                    var remote = response.body!!.string()

                    // 5. 解析M3U格式为JSON
                    remote = parseM3UTextToJson(remote)

                    // 6. 读取本地缓存
                    val local = runCatching { playlistFile.readText() }.getOrNull()

                    // 7. 比较远程和本地内容，如果有变化则更新
                    if (remote != local) {
                        // 保存到本地文件
                        playlistFile.writeText(remote)

                        // 通知UI播放列表已更新
                        onPlaylistChange?.invoke(createPlaylistFromJson(remote))
                    }

                    // 8. 更新最后更新时间
                    setLastUpdate(System.currentTimeMillis())

                    Log.i(TAG, "Update playlist successfully.")
                    break  // 更新成功，退出循环
                } catch (e: Exception) {
                    // 更新失败，记录错误
                    Log.w(TAG, "Cannot update playlist, reason: ${e.message}")
                }

                // 如果仍然需要更新，等待一段时间后重试
                if (needUpdate()) {
                    delay(UPDATE_RETRY_DELAY)
                }
            }

            // 标记为更新完成
            isUpdating = false
        }
    }

    /**
     * 将自定义的M3U格式文本解析成JSON字符串
     *
     * M3U格式说明：
     * - 每行一个频道或分组
     * - 分组行格式：分组名,#genre#
     * - 频道行格式：频道名,播放URL
     *
     * 解析规则：
     * 1. 遇到 #genre# 标记，表示这是一个分组，后续频道都属于这个分组
     * 2. 普通行表示一个频道，包含频道名和播放URL
     * 3. 同名频道会合并URL到urls列表中（多源支持）
     * 4. 过滤掉包含"更新时间"的分组
     *
     * @param m3uText 原始的M3U格式文本，以逗号分隔
     * @return 符合Channel结构的JSON字符串
     */
    private fun parseM3UTextToJson(m3uText: String): String {
        // 使用HashMap存储频道，key为频道名，value为Channel对象
        // 这样可以方便地合并同名频道的多个URL
        val channels = HashMap<String, Channel>()

        // 当前分组名，默认为"default"
        var currentGroup = "default"

        // 逐行解析文本
        m3uText.lines().forEach { line ->
            // 跳过空行或不包含逗号的无效行
            if (line.isBlank() || !line.contains(',')) {
                return@forEach
            }

            // 按逗号分割，获取频道名和URL/标记
            val parts = line.split(',')
            val name = parts[0].trim()          // 频道名或分组名
            val url = parts.getOrNull(1)?.trim() ?: ""  // URL或#genre#标记

            if (url == "#genre#") {
                // 这是一个分组标记行
                currentGroup = name
            } else {
                // 这是一个频道行
                val existingChannel = channels[name]

                if (existingChannel == null) {
                    // 如果这个频道名还没有出现过，创建新的Channel对象
                    val channel = Channel(
                        name,
                        currentGroup,
                        listOf(url)  // 将URL包装在列表中
                    )
                    channels[name] = channel
                } else {
                    // 如果这个频道名已经存在，添加新的URL到urls列表
                    // 这样一个频道就可以有多个播放源
                    val updatedUrls = existingChannel.urls.toMutableList()
                    updatedUrls.add(url)
                    existingChannel.urls = updatedUrls
                    channels[name] = existingChannel
                }
            }
        }

        // 过滤掉包含"更新时间"的频道
        // 这些通常是频道列表的元信息，不是真正的频道
        val filteredChannels = channels.values.filter { channel ->
            !channel.groupName.contains("更新时间")
        }

        // 使用Gson将Channel列表转换成JSON字符串
        return gson.toJson(filteredChannels)
    }

    /**
     * 从JSON字符串创建Playlist对象
     *
     * @param json JSON格式的频道列表字符串
     * @return 解析后的Playlist对象
     */
    private fun createPlaylistFromJson(json: String): Playlist {
        // 使用Gson将JSON解析为Channel列表
        val channels = gson.fromJson(json, jsonTypeToken)

        // 从频道列表创建Playlist对象
        return Playlist.createFromAllChannels("default", channels)
    }

    /**
     * 加载内置播放列表
     *
     * 当无法从缓存或网络加载播放列表时，返回一个空的播放列表
     *
     * @return 空的Playlist对象
     */
    private fun loadBuiltInPlaylist() = createPlaylistFromJson("[]")

    /**
     * 加载播放列表
     *
     * 加载顺序：
     * 1. 尝试从本地缓存文件加载
     * 2. 如果失败，返回空的内置播放列表
     * 3. 无论成功与否，都会在后台请求更新播放列表
     *
     * @return Playlist对象，可能是缓存的或空的
     */
    fun loadPlaylist(): Playlist {
        return try {
            // 读取缓存文件
            val json = playlistFile.readText()

            // 注释掉的代码：如果需要解析M3U格式
//            json = parseM3UTextToJson(json)

            // 从JSON创建播放列表
            createPlaylistFromJson(json)
        } catch (e: Exception) {
            // 加载失败，记录错误
            Log.w(TAG, "Cannot load playlist, reason: ${e.message}")

            // 重置更新时间，强制重新下载
            setLastUpdate(0L)

            // 返回空的内置播放列表
            loadBuiltInPlaylist()
        } finally {
            // 无论加载成功与否，都请求更新播放列表
            // 这样可以在后台自动获取最新的频道列表
            requestUpdatePlaylist()
        }
    }
}
