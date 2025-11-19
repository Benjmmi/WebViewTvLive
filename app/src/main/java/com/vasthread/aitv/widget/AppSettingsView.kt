/*
 * Copyright (c) 2025 WebViewTvLive
 *
 * 文件: AppSettingsView.kt
 * 描述: 应用设置视图，提供应用级别的配置选项
 */

package com.vasthread.aitv.widget

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vasthread.aitv.R
import com.vasthread.aitv.misc.adjustValue
import com.vasthread.aitv.playlist.PlaylistManager
import com.vasthread.aitv.settings.SettingItem
import com.vasthread.aitv.settings.SettingsManager

/**
 * AppSettingsView - 应用设置视图
 *
 * 这个视图显示在屏幕中央，提供应用级别的配置选项：
 *
 * 可用设置项：
 * 1. 频道列表选择：从多个播放列表中选择一个
 * 2. 最大加载时间：设置视频加载超时时间（秒）
 * 3. 刷新频道列表：清除缓存，重新下载播放列表
 * 4. WebView可触摸：启用或禁用WebView的触摸交互
 *
 * 设计特点：
 * - 使用RecyclerView显示设置项列表
 * - 支持遥控器左右键调整设置值
 * - 支持点击左右箭头按钮调整值
 * - 设置项分为两种类型：
 *   * 可选择型：有多个选项可供选择（如频道列表、加载时间）
 *   * 点击型：点击执行操作（如刷新频道列表）
 *
 * 交互方式：
 * - 遥控器上下键：在设置项之间切换焦点
 * - 遥控器左右键或点击左右按钮：调整当前设置项的值
 * - 遥控器确认键：执行点击型设置项的操作
 *
 * @param context Android上下文
 * @param attrs XML属性集
 * @param defStyleAttr 默认样式属性
 */
class AppSettingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // ========== UI组件 ==========

    /** 设置项列表的RecyclerView */
    private val rvSettings: RecyclerView

    // ========== 设置项定义 ==========

    /**
     * 设置项数组
     *
     * 包含所有可配置的应用设置项，按显示顺序排列：
     *
     * 1. 频道列表（可选择型）
     *    - 标题：R.string.channel_list
     *    - 选项：从SettingsManager获取的播放列表名称数组
     *    - 当前值：当前选中的播放列表索引
     *    - 选择回调：保存选中的播放列表索引
     *
     * 2. 最大加载时间（可选择型）
     *    - 标题：R.string.max_loading_time
     *    - 选项：从资源文件loading_time_text获取的文本数组（如"10秒", "15秒"）
     *    - 当前值：从loading_time_value数组中查找当前设置值的索引
     *    - 选择回调：将选中的索引转换为实际的秒数并保存
     *
     * 3. 刷新频道列表（点击型）
     *    - 标题：R.string.refresh_channel_list
     *    - 点击回调：清除播放列表缓存，下次加载时会重新下载
     *
     * 4. WebView可触摸（可选择型）
     *    - 标题：R.string.web_view_touchable
     *    - 选项：["关闭", "开启"]
     *    - 当前值：根据当前设置值转换为索引（0或1）
     *    - 选择回调：将索引转换为布尔值并保存
     */
    private val settings = arrayOf(
        // 设置项1：频道列表选择
        SettingItem(
            R.string.channel_list,
            SettingsManager.getPlaylistNames(),
            SettingsManager.getSelectedPlaylistPosition(),
            onItemSelect = SettingsManager::setSelectedPlaylistPosition
        ),

        // 设置项2：最大加载时间
        SettingItem(
            R.string.max_loading_time,
            context.resources.getStringArray(R.array.loading_time_text),
            context.resources.getIntArray(R.array.loading_time_value).indexOf(SettingsManager.getMaxLoadingTime()),
            onItemSelect = {
                SettingsManager.setMaxLoadingTime(context.resources.getIntArray(R.array.loading_time_value)[it])
            }
        ),

        // 设置项3：刷新频道列表（点击型）
        SettingItem(
            R.string.refresh_channel_list,
            onClick = { PlaylistManager.setLastUpdate(0, true) }
        ),

        // 设置项4：WebView可触摸开关
        SettingItem(
            R.string.web_view_touchable,
            arrayOf(context.getString(R.string.off), context.getString(R.string.on)),
            if (SettingsManager.isWebViewTouchable()) 1 else 0,
            onItemSelect = { SettingsManager.setWebViewTouchable(it != 0) }
        )
    )

    /**
     * 初始化视图
     *
     * 设置：
     * - 可点击，阻止事件穿透
     * - 不可获得焦点
     * - 背景样式
     * - 加载布局文件
     * - 初始化RecyclerView和适配器
     */
    init {
        isClickable = true   // 可点击，阻止事件穿透
        isFocusable = false  // 不可获得焦点

        // 设置背景
        setBackgroundResource(R.drawable.bg)

        // 加载布局文件
        LayoutInflater.from(context).inflate(R.layout.widget_settings, this)

        // 初始化RecyclerView
        rvSettings = findViewById(R.id.rvSettings)
        rvSettings.adapter = SettingsAdapter()
    }

    /**
     * 设置视图可见性
     *
     * 重写此方法以实现：
     * - 当设置视图显示时，自动让第一个设置项获得焦点
     * - 这样用户可以直接用遥控器操作
     *
     * @param visibility 可见性：VISIBLE、INVISIBLE 或 GONE
     */
    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)

        // 如果视图变为可见，让第一个设置项获得焦点
        if (visibility == VISIBLE) {
            post { rvSettings.getChildAt(0)?.requestFocus() }
        }
    }

    // ========== 内部类：RecyclerView适配器 ==========

    /**
     * SettingsAdapter - 设置项列表的适配器
     *
     * 负责：
     * - 创建设置项的ViewHolder
     * - 绑定设置项数据到ViewHolder
     * - 管理设置项的数量
     */
    private inner class SettingsAdapter : RecyclerView.Adapter<ViewHolder>() {

        /**
         * 创建ViewHolder
         *
         * 加载item_settings布局并创建ViewHolder实例
         *
         * @param parent 父视图组
         * @param viewType 视图类型（本应用中未使用）
         * @return 创建的ViewHolder实例
         */
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_settings, parent, false)
            return ViewHolder(view)
        }

        /**
         * 获取设置项的数量
         *
         * @return 设置项总数
         */
        override fun getItemCount() = settings.size

        /**
         * 绑定数据到ViewHolder
         *
         * 将指定位置的设置项数据绑定到ViewHolder进行显示
         *
         * @param holder ViewHolder实例
         * @param position 设置项在数组中的位置
         */
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(settings[position])
        }
    }

    // ========== 内部类：ViewHolder ==========

    /**
     * ViewHolder - 设置项的视图持有者
     *
     * 负责：
     * - 持有设置项的所有子视图引用
     * - 处理用户交互（点击、按键）
     * - 更新设置项的显示
     * - 调整设置项的值
     *
     * 布局结构（item_settings.xml）：
     * - tvTitle: 设置项标题
     * - llItem: 可选择型设置项的容器（包含左右按钮和当前值）
     *   - btnLeft: 向左调整按钮（"<"）
     *   - tvItem: 当前选中的选项文本
     *   - btnRight: 向右调整按钮（">"）
     *
     * @param view 设置项的根视图
     */
    private inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        // ========== UI组件 ==========

        /** 设置项标题 */
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)

        /** 向左调整按钮 */
        private val btnLeft: Button = itemView.findViewById(R.id.btnLeft)

        /** 向右调整按钮 */
        private val btnRight: Button = itemView.findViewById(R.id.btnRight)

        /** 当前选中的选项文本 */
        private val tvItem: TextView = itemView.findViewById(R.id.tvItem)

        /** 可选择型设置项的容器 */
        private val llItem: LinearLayout = itemView.findViewById(R.id.llItem)

        /** 当前绑定的设置项 */
        private lateinit var setting: SettingItem

        /**
         * 初始化ViewHolder
         *
         * 设置各种事件监听器：
         * - 整体点击事件：用于点击型设置项
         * - 按键事件：处理遥控器左右键
         * - 左右按钮点击：调整设置值
         */
        init {
            // 设置整体点击事件（用于点击型设置项）
            itemView.setOnClickListener { setting.onClick?.invoke() }

            // 处理遥控器按键事件
            itemView.setOnKeyListener { _, keyCode, event ->
                // 只处理左右方向键
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    // 只在按键按下时处理，避免重复触发
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        // 左键：向左调整（上一个选项）
                        // 右键：向右调整（下一个选项）
                        adjustItem(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                    }
                    true  // 事件已处理
                } else {
                    false  // 其他按键不处理
                }
            }

            // 左按钮点击：向左调整（上一个选项）
            btnLeft.setOnClickListener { adjustItem(false) }

            // 右按钮点击：向右调整（下一个选项）
            btnRight.setOnClickListener { adjustItem(true) }
        }

        /**
         * 绑定设置项数据
         *
         * 根据设置项类型显示不同的UI：
         * - 可选择型：显示标题 + 左右按钮 + 当前选项
         * - 点击型：只显示标题
         *
         * @param setting 要绑定的设置项
         */
        fun bind(setting: SettingItem) {
            this.setting = setting

            // 显示设置项标题
            tvTitle.text = rootView.context.getString(setting.titleRes)

            // 根据是否有选项数组决定显示方式
            if (setting.items.isNullOrEmpty()) {
                // 点击型设置项：隐藏选项容器
                llItem.visibility = GONE
            } else {
                // 可选择型设置项：显示选项容器和当前选中的选项
                llItem.visibility = VISIBLE
                tvItem.text = setting.items[setting.selectedItemPosition]
            }
        }

        /**
         * 调整设置项的值
         *
         * 只对可选择型设置项有效，循环切换选项：
         * - next = true：切换到下一个选项（循环到第一个）
         * - next = false：切换到上一个选项（循环到最后一个）
         *
         * 调整流程：
         * 1. 检查是否为可选择型设置项
         * 2. 使用adjustValue函数计算新的索引（支持循环）
         * 3. 更新设置项的选中索引
         * 4. 更新UI显示
         * 5. 触发onItemSelect回调，通知设置已改变
         *
         * @param next true表示下一个选项，false表示上一个选项
         */
        private fun adjustItem(next: Boolean) {
            // 如果没有选项数组，说明是点击型设置项，不处理
            if (setting.items.isNullOrEmpty()) return

            // 获取当前选中的索引
            var selectedItemPosition = setting.selectedItemPosition

            // 计算新的索引（循环）
            // adjustValue函数确保索引在0到size-1之间循环
            selectedItemPosition = adjustValue(selectedItemPosition, setting.items!!.size, next)

            // 更新设置项的选中索引
            setting.selectedItemPosition = selectedItemPosition

            // 更新UI显示新的选项文本
            tvItem.text = setting.items!![selectedItemPosition]

            // 触发选择回调，通知外部设置已改变
            // 外部回调会将新的值保存到SharedPreferences
            setting.onItemSelect?.invoke(selectedItemPosition)
        }
    }
}
