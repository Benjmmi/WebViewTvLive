/*
 * Copyright (c) 2025 AITVLive
 *
 * 文件: PlaylistView.kt
 * 描述: 频道列表视图，显示和管理频道选择
 */

package com.vasthread.aitv.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vasthread.aitv.R
import com.vasthread.aitv.misc.adjustValue
import com.vasthread.aitv.playlist.Channel
import com.vasthread.aitv.playlist.ChannelGroup
import com.vasthread.aitv.playlist.Playlist
import com.vasthread.aitv.playlist.Playlist.Companion.firstChannel

/**
 * PlaylistView - 频道列表视图
 *
 * 这个视图显示在屏幕左侧，提供完整的频道浏览和选择功能：
 *
 * 主要功能：
 * 1. 频道列表显示：使用RecyclerView显示当前分组的所有频道
 * 2. 分组切换：通过上下翻页按钮切换频道分组
 * 3. 频道选择：点击或使用遥控器选择频道
 * 4. 焦点管理：自动定位到当前播放的频道
 * 5. 更新提示：显示播放列表更新进度
 *
 * 数据结构（三层）：
 * - Playlist（播放列表）
 *   └─ ChannelGroup（频道分组，如"央视频道"、"卫视频道"）
 *      └─ Channel（单个频道，如"CCTV-1"）
 *
 * 布局结构：
 * - 顶部：分组名称 + 频道数量
 * - 中间：频道列表（RecyclerView）
 * - 底部：上翻页/下翻页按钮
 * - 右上角：更新进度条
 *
 * 交互方式：
 * - 遥控器上下键：在频道之间移动焦点
 * - 遥控器左右键：切换频道分组
 * - 遥控器确认键：选择当前焦点的频道
 * - 点击频道：直接选择该频道
 * - 点击翻页按钮：切换频道分组
 *
 * @param context Android上下文
 * @param attrs XML属性集
 * @param defStyleAttr 默认样式属性
 */
class PlaylistView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        /** 按钮按下状态数组，用于手动控制按钮的视觉状态 */
        private val STATE_PRESSED = intArrayOf(android.R.attr.state_pressed)

        /** 按钮正常状态数组 */
        private val STATE_EMPTY = intArrayOf()
    }

    // ========== UI组件 ==========

    /** 上翻页按钮 */
    private val btnPageUp: Button

    /** 下翻页按钮 */
    private val btnPageDown: Button

    /** 分组名称TextView，显示格式："分组名(频道数)" */
    private val tvGroupName: TextView

    /** 频道列表RecyclerView */
    private val rvChannels: RecyclerView

    /** 更新进度条，播放列表更新时显示 */
    private val pbUpdating: ProgressBar

    // ========== 公共属性 ==========

    /**
     * 当前播放列表
     *
     * 设置此属性会触发：
     * - 如果为null：清空频道列表，隐藏翻页按钮
     * - 如果不为null：重置到第一页，根据分组数量显示或隐藏翻页按钮
     *
     * 自定义setter确保：
     * - 避免重复设置相同的播放列表
     * - 自动处理UI状态更新
     * - 单个分组时隐藏翻页按钮
     */
    var playlist: Playlist? = null
        set(value) {
            // 如果播放列表没有变化，不重复处理
            if (field == value) return

            field = value

            if (value == null) {
                // 播放列表为空，清空UI
                rvChannels.adapter = null
                tvGroupName.text = null
                btnPageDown.visibility = GONE
                btnPageUp.visibility = GONE
            } else {
                // 设置新的播放列表，重置到第一页
                currentPage = 0

                // 如果只有一个分组，隐藏翻页按钮
                val singleGroup = value.groups.size <= 1
                btnPageDown.visibility = if (singleGroup) GONE else VISIBLE
                btnPageUp.visibility = if (singleGroup) GONE else VISIBLE
            }
        }

    /**
     * 当前选中的频道
     *
     * 设置此属性会触发频道选择回调，通知外部（MainActivity）切换播放
     *
     * 注意：使用 !! 断言，假设value不为null
     * 调用方应确保传入的value不为null
     */
    var currentChannel: Channel? = null
        set(value) {
            field = value
            // 触发频道选择回调，通知外部切换频道
            onChannelSelectCallback?.invoke(value!!)
        }

    /**
     * 播放列表更新状态
     *
     * true: 正在更新，显示进度条
     * false: 更新完成，隐藏进度条
     */
    var updating = false
        set(value) {
            // 输出调试信息
            println("updating $value")
            field = value
            // 控制进度条的显示和隐藏
            pbUpdating.visibility = if (value) VISIBLE else GONE
        }

    /**
     * 当前显示的分组页码（从0开始）
     *
     * 设置此属性会自动加载对应分组的频道列表
     *
     * 自定义setter：
     * - 从播放列表中获取指定索引的分组
     * - 调用setCurrentGroup()更新UI
     */
    private var currentPage: Int = 0
        set(value) {
            field = value
            // 获取当前页对应的频道分组
            val group = playlist!!.groups[value]
            // 显示该分组的频道列表
            setCurrentGroup(group)
        }

    // ========== 回调 ==========

    /**
     * 频道选择回调
     * 当用户选择频道时，触发此回调通知外部
     */
    var onChannelSelectCallback: ((Channel) -> Unit)? = null

    /**
     * 初始化视图
     *
     * 设置：
     * - 可点击（阻止事件穿透）
     * - 不可获得焦点
     * - 垂直布局方向
     * - 背景样式
     * - 加载布局文件
     * - 初始化UI组件
     * - 设置翻页按钮点击事件
     */
    init {
        isClickable = true   // 可点击，阻止事件穿透
        isFocusable = false  // 不可获得焦点
        orientation = VERTICAL  // 垂直布局

        // 设置背景
        setBackgroundResource(R.drawable.bg)

        // 加载布局文件
        LayoutInflater.from(context).inflate(R.layout.widget_playlist, this)

        // 获取UI组件引用
        btnPageUp = findViewById(R.id.btnPageUp)
        btnPageDown = findViewById(R.id.btnPageDown)
        tvGroupName = findViewById(R.id.tvGroupName)
        rvChannels = findViewById(R.id.rvChannels)
        pbUpdating = findViewById(R.id.pbUpdating)

        // 设置翻页按钮点击事件
        // false: 向上翻页（上一个分组）
        // true: 向下翻页（下一个分组）
        btnPageUp.setOnClickListener { turnPage(false) }
        btnPageDown.setOnClickListener { turnPage(true) }
    }

    // ========== 公共方法 ==========

    /**
     * 选择上一个频道
     *
     * 在当前分组内向上切换频道（循环）
     */
    fun previousChannel() = selectChannel(false)

    /**
     * 选择下一个频道
     *
     * 在当前分组内向下切换频道（循环）
     */
    fun nextChannel() = selectChannel(true)

    /**
     * 分发按键事件
     *
     * 特殊处理遥控器左右键：
     * - 左键：向上翻页（上一个分组）
     * - 右键：向下翻页（下一个分组）
     * - 同时手动控制翻页按钮的视觉反馈（按下/释放状态）
     *
     * 这样做的原因：
     * - 用户按遥控器左右键时，对应的翻页按钮会有视觉反馈
     * - 提供更好的用户体验，让用户知道按键被识别了
     *
     * @param event 按键事件
     * @return true表示事件已处理
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode

        // 只处理左右方向键
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            // 判断是左键还是右键
            val isLeft = keyCode == KeyEvent.KEYCODE_DPAD_LEFT

            // 获取对应按钮的背景Drawable（StateListDrawable支持状态切换）
            val background = (if (isLeft) btnPageUp else btnPageDown).background as StateListDrawable

            if (event.action == KeyEvent.ACTION_DOWN) {
                // 按键按下：设置按钮为按下状态，执行翻页
                background.setState(STATE_PRESSED)
                turnPage(!isLeft)  // 左键对应向上翻页(false)，右键对应向下翻页(true)
            } else {
                // 按键释放：恢复按钮为正常状态
                background.setState(STATE_EMPTY)
            }

            return true  // 事件已处理
        }

        // 其他按键传递给父类处理
        return super.dispatchKeyEvent(event)
    }

    // ========== 私有方法 ==========

    /**
     * 翻页（切换频道分组）
     *
     * 在多个分组之间循环切换：
     * - down = true：下一个分组（向下翻页）
     * - down = false：上一个分组（向上翻页）
     *
     * 翻页后：
     * - 自动让新分组的第一个频道获得焦点
     *
     * @param down true表示向下翻页，false表示向上翻页
     */
    private fun turnPage(down: Boolean) {
        // 如果只有一个或没有分组，不处理
        if (playlist!!.groups.size <= 1) return

        // 计算新的页码（循环）
        currentPage = adjustValue(currentPage, playlist!!.groups.size, down)

        // 让新分组的第一个频道获得焦点
        rvChannels.post { rvChannels.getChildAt(0)?.requestFocus() }
    }

    /**
     * 选择频道
     *
     * 在当前分组内选择上一个或下一个频道（循环）
     *
     * 处理逻辑：
     * 1. 如果当前没有选中频道，选择播放列表的第一个频道
     * 2. 在播放列表中查找当前频道的位置
     * 3. 如果找到，在当前分组内循环切换到下一个/上一个频道
     * 4. 如果找不到（可能播放列表已更新），选择第一个频道
     *
     * @param next true表示下一个频道，false表示上一个频道
     */
    private fun selectChannel(next: Boolean) {
        // 如果当前没有选中频道，选择第一个
        if (currentChannel == null) {
            currentChannel = playlist.firstChannel()
        }

        if (playlist != null) {
            // 在播放列表中查找当前频道的位置
            // 返回 Pair(分组索引, 频道索引) 或 null
            val index = playlist!!.indexOf(currentChannel!!)

            currentChannel = if (index == null) {
                // 找不到当前频道，可能播放列表已更新，选择第一个
                playlist.firstChannel()
            } else {
                // 找到了，在当前分组内循环切换
                val channels = playlist!!.groups[index.first].channels
                val j = adjustValue(index.second, channels.size, next)
                channels[j]
            }
        }
    }

    /**
     * 设置当前显示的频道分组
     *
     * 更新UI以显示指定分组的频道列表：
     * 1. 创建新的ChannelAdapter绑定到RecyclerView
     * 2. 更新分组名称TextView，显示格式："分组名(频道数)"
     *
     * @param group 要显示的频道分组
     */
    @SuppressLint("SetTextI18n")
    private fun setCurrentGroup(group: ChannelGroup) {
        // 创建并设置适配器
        rvChannels.adapter = ChannelAdapter(group)

        // 显示分组名称和频道数量
        tvGroupName.text = "${group.name}(${group.channels.size})"
    }

    /**
     * 让当前选中的频道获得焦点
     *
     * 实现步骤：
     * 1. 在RecyclerView中找到当前频道的位置
     * 2. 滚动到该位置
     * 3. 遍历所有可见的子视图，找到匹配的ViewHolder
     * 4. 让该ViewHolder的视图获得焦点
     *
     * 使用场景：
     * - 切换回频道列表时，自动定位到当前播放的频道
     * - 提供更好的用户体验，让用户知道当前在播放哪个频道
     */
    private fun focusCurrentChannel() {
        val adapter = rvChannels.adapter as ChannelAdapter

        // 找到当前频道在列表中的位置
        val position = adapter.group.channels.indexOf(currentChannel)

        // 滚动到该位置
        rvChannels.scrollToPosition(position)

        // 在下一帧执行，确保滚动完成
        rvChannels.post {
            // 遍历所有可见的子视图
            for (i in 0..<rvChannels.childCount) {
                val child = rvChannels.getChildAt(i)
                val holder = rvChannels.getChildViewHolder(child) as ViewHolder

                // 找到匹配的频道，让其获得焦点
                if (holder.channel == currentChannel) {
                    child.requestFocus()
                }
            }
        }
    }

    /**
     * 视图可见性变化回调
     *
     * 当频道列表视图显示时：
     * 1. 检查当前频道是否在当前显示的分组中
     * 2. 如果不在，自动切换到包含该频道的分组
     * 3. 如果在，刷新列表（更新选中状态）
     * 4. 让当前频道获得焦点
     *
     * 这样确保：
     * - 用户打开频道列表时，总能看到当前播放的频道
     * - 当前频道会高亮显示
     * - 焦点自动定位到当前频道
     *
     * @param changedView 可见性发生变化的视图
     * @param visibility 新的可见性状态
     */
    @SuppressLint("NotifyDataSetChanged")
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)

        // 只处理视图变为可见的情况
        if (visibility == VISIBLE && playlist != null && currentChannel != null) {
            val adapter = rvChannels.adapter as ChannelAdapter

            // 检查当前频道是否在当前显示的分组中
            if (currentChannel!!.groupName != adapter.group.name) {
                // 不在，需要切换到包含该频道的分组
                val index = playlist!!.indexOf(currentChannel!!)
                if (index != null) {
                    // 切换到对应的分组页
                    currentPage = index.first
                }
            } else {
                // 在当前分组中，刷新列表以更新选中状态
                adapter.notifyDataSetChanged()
            }

            // 让当前频道获得焦点
            focusCurrentChannel()
        }
    }

    // ========== 内部类：RecyclerView适配器 ==========

    /**
     * ChannelAdapter - 频道列表的适配器
     *
     * 负责：
     * - 创建频道项的ViewHolder
     * - 绑定频道数据到ViewHolder
     * - 管理频道数量
     *
     * @param group 要显示的频道分组
     */
    private inner class ChannelAdapter(val group: ChannelGroup) : RecyclerView.Adapter<ViewHolder>() {

        /**
         * 创建ViewHolder
         *
         * 加载item_channel布局并创建ViewHolder实例
         *
         * @param parent 父视图组
         * @param viewType 视图类型（本应用中未使用）
         * @return 创建的ViewHolder实例
         */
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
            return ViewHolder(view)
        }

        /**
         * 获取频道数量
         *
         * @return 当前分组中的频道总数
         */
        override fun getItemCount() = group.channels.size

        /**
         * 绑定数据到ViewHolder
         *
         * 将指定位置的频道数据绑定到ViewHolder进行显示
         * 序号从1开始（position + 1）
         *
         * @param holder ViewHolder实例
         * @param position 频道在分组中的位置（从0开始）
         */
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(position + 1, group.channels[position])
        }
    }

    // ========== 内部类：ViewHolder ==========

    /**
     * ViewHolder - 频道项的视图持有者
     *
     * 负责：
     * - 持有频道项的所有子视图引用
     * - 显示频道序号和名称
     * - 处理频道点击事件
     * - 更新选中状态（高亮显示）
     *
     * 布局结构（item_channel.xml）：
     * - tvNumber: 频道序号（两位数格式，如"01", "02"）
     * - tvTitle: 频道名称
     *
     * 选中状态：
     * - 使用TextView的isSelected属性控制
     * - 选中时，TextView会应用选中状态的样式（通常是高亮显示）
     *
     * @param view 频道项的根视图
     */
    private inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view), OnClickListener {

        // ========== UI组件 ==========

        /** 频道序号TextView */
        private val tvNumber: TextView = itemView.findViewById(R.id.tvNumber)

        /** 频道名称TextView */
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)

        /** 当前绑定的频道 */
        var channel: Channel? = null

        /**
         * 初始化ViewHolder
         *
         * 设置点击事件监听器
         */
        init {
            itemView.setOnClickListener(this)
        }

        /**
         * 绑定频道数据
         *
         * 更新UI显示频道信息：
         * - 序号：格式化为两位数（如"01", "02", "10"）
         * - 名称：显示频道名称
         * - 选中状态：如果是当前播放的频道，高亮显示
         *
         * @param number 频道序号（从1开始）
         * @param channel 频道对象
         */
        fun bind(number: Int, channel: Channel) {
            this.channel = channel

            // 格式化序号为两位数
            tvNumber.text = String.format("%02d", number)

            // 显示频道名称
            tvTitle.text = channel.name

            // 更新选中状态
            val isSelected = channel == currentChannel
            tvNumber.isSelected = isSelected
            tvTitle.isSelected = isSelected
        }

        /**
         * 处理点击事件
         *
         * 用户点击频道项时，将该频道设置为当前频道
         * 这会触发currentChannel的setter，进而触发频道选择回调
         *
         * @param v 被点击的视图
         */
        override fun onClick(v: View) {
            currentChannel = channel
        }
    }
}
