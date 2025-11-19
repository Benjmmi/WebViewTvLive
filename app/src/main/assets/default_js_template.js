/**
 * WebViewTvLive - 默认JavaScript模板
 *
 * 这个脚本会被注入到电视台网页中，用于：
 * 1. 自动检测和控制网页中的视频元素
 * 2. 实现自动全屏播放
 * 3. 监控视频播放状态
 * 4. 与Android应用进行通信
 *
 * 注意：这是一个模板文件，其中的变量会在运行时被替换：
 * - %selector%: 视频元素的CSS选择器
 * - %enter_fullscreen_button%: 全屏按钮的选择器
 * - %playing_check_enabled%: 是否启用播放检查
 */

// ========== 配置变量（运行时替换）==========

/**
 * 视频元素选择器
 * 支持：类名（需要加.）、id（需要加 #）、标签名
 * 例如：'.video-player', '#player', 'video'
 */
var selector = '%selector%';

/**
 * 全屏按钮选择器
 * 某些网站需要点击全屏按钮才能进入全屏，而不是直接调用 requestFullscreen()
 */
var enterFullscreenButton = '%enter_fullscreen_button%'

/**
 * 是否启用播放状态检查
 * true: 定期向Android通知视频正在播放
 * false: 不进行播放检查
 */
var playingCheckEnabled = %playing_check_enabled%

// ========== 视频控制函数 ==========

/**
 * 视频时间更新事件处理
 *
 * 当视频播放时，这个函数会被定期调用（由timeupdate事件触发）
 * 主要功能：
 * - 确保视频声音已开启
 * - 通知Android应用视频正在播放
 *
 * @param {HTMLVideoElement} video - 视频元素
 */
function wvt_onTimeUpdate(video) {
	var now = Date.now();

	// 每秒最多通知一次
    if (now - window.wvt_lastNotifyVideoPlaying >= 1000) {
    	// 确保视频未静音且音量为最大
        video.muted = false;
    	video.volume = 1;

    	// 如果启用了播放检查，通知Android视频正在播放
    	if (playingCheckEnabled) {
            window.main.notifyVideoPlaying();
            window.wvt_lastNotifyVideoPlaying = now;
        }
    }
}

/**
 * 向Android报告视频尺寸
 *
 * 将视频的实际宽度和高度发送给Android应用
 * Android可以根据这个信息调整显示比例
 *
 * @param {HTMLVideoElement} video - 视频元素
 */
function wvt_reportVideoSize(video) {
	window.main.setVideoSize(video.videoWidth, video.videoHeight);
}

/**
 * 设置视频元素
 *
 * 当检测到视频元素时，对其进行初始化设置：
 * - 退出任何现有的全屏模式
 * - 设置自动播放
 * - 添加各种事件监听器
 * - 如果视频已在播放，立即进入全屏
 *
 * @param {HTMLVideoElement} video - 视频元素
 */
function wvt_setupVideo(video) {
	// 如果已经设置过，不重复设置
	if (video.wvt_setup) { return; }

	// 退出任何现有的全屏模式
	if (document.fullscreenElement) { document.exitFullscreen(); }

	// 启用播放检查
	if (playingCheckEnabled) { window.main.enablePlayCheck(); }

	// 初始化上次通知时间
	window.wvt_lastNotifyVideoPlaying = 0;

	// 配置视频属性
    video.autoplay = true;              // 自动播放
    video.defaultMuted = false;         // 不静音
    video.style['object-fit'] = 'fill'; // 填充模式

    // 如果视频已经在播放，立即进入全屏
    if (!video.paused) {
    	console.log("Video is playing, enter fullscreen now.");
    	window.main.schemeEnterFullscreen();
    	wvt_reportVideoSize(video);
    }

    // ===== 添加事件监听器 =====

    /**
     * 播放事件：视频开始播放时触发
     * 进入全屏并通知Android
     */
    video.addEventListener('play', function() {
    	console.log("Video state: PLAY.");
		window.main.schemeEnterFullscreen();
		if (playingCheckEnabled) {
            window.main.notifyVideoPlaying();
            window.wvt_lastNotifyVideoPlaying = Date.now();
    	}
    });

    /**
     * 暂停事件：视频暂停时触发
     */
    video.addEventListener('pause', function() {
    	console.log("Video state: PAUSE.");
    });

    /**
     * 时间更新事件：视频播放时定期触发
     * 用于持续监控播放状态
     */
    video.addEventListener('timeupdate', function() {
    	wvt_onTimeUpdate(video);
    });

    /**
     * 错误事件：视频加载或播放出错时触发
     */
    video.addEventListener('error', function(e) {
    	console.log("Video state: ERROR.");
    });

    /**
     * 可以播放事件：视频缓冲足够可以开始播放时触发
     * 此时报告视频尺寸
     */
    video.addEventListener('canplay', function(e) {
		console.log("Video state: CANPLAY.");
		wvt_reportVideoSize(video);
	});

	// 以下事件已注释，可根据需要启用：
    // canplaythrough: 视频可以流畅播放时触发
    // durationchange: 视频时长改变时触发

	// 标记为已设置，避免重复设置
	video.wvt_setup = true;
}

/**
 * 设置非视频元素
 *
 * 对于某些网站，可能不是标准的video标签，而是使用其他方式播放
 * 这种情况下直接尝试进入全屏
 *
 * @param {HTMLElement} element - 要全屏的元素
 */
function wvt_setupNonVideo(element) {
    window.main.schemeEnterFullscreen();
}

/**
 * 主循环函数
 *
 * 定期检查页面状态：
 * - 如果已找到视频元素，检查它是否还在页面上
 * - 如果还没找到视频元素，尝试查找
 *
 * 循环间隔：
 * - 全屏状态下：5秒
 * - 非全屏状态：1秒
 *
 * @param {number} counter - 循环计数器，用于调试
 */
function wvt_loop(counter) {
	if (window.wvt_video) {
		// 已找到视频元素，检查它是否还在DOM中
		if (!document.documentElement.contains(window.wvt_video)) {
			// 视频元素已从页面中移除
			console.warn("Loop " + counter + ", wvt_video is not on the document, set it to null.");
			window.main.disablePlayCheck();

			// 退出全屏
			if (document.fullscreenElement) { document.exitFullscreen(); }

			// 重置视频变量
			window.wvt_video = null;
		} else {
			// 视频元素仍在页面上，无需操作
			//console.log("Loop " + counter + ", nothing to do.");
		}
	} else {
		// 还没找到视频元素，尝试查找
		var element = document.querySelector(selector);
		if (element) {
			console.log("Loop " + counter + ", element [" + selector + "] found, tag name is " + element.tagName + ".");
			window.wvt_video = element;

			// 根据元素类型进行不同的处理
			if (element.tagName == 'VIDEO') {
			    wvt_setupVideo(element);  // 标准video元素
			} else {
			    wvt_setupNonVideo(element);  // 其他类型元素
			}
		} else {
			console.error("Loop " + counter + ", element [" + selector + "] not found.");
		}
	}

	// 安排下一次循环
	// 全屏时5秒检查一次，非全屏时1秒检查一次
	setTimeout(() => { wvt_loop(counter + 1) }, document.fullscreenElement ? 5000 : 1000);
}

/**
 * 视频全屏函数
 *
 * 将视频元素切换到全屏模式
 * 支持两种方式：
 * 1. 点击网站提供的全屏按钮
 * 2. 直接调用 video.requestFullscreen()
 */
function wvt_fullscreenVideo() {
	// 检查是否已经在全屏状态
	if (document.fullscreenElement == null) {
		// 检查视频元素是否存在且在DOM中
		if (window.wvt_video && document.documentElement.contains(window.wvt_video)) {
		    var element = null;

		    // 尝试找到全屏按钮
		    try {
		        element = document.querySelector(enterFullscreenButton);
		    } catch (e) {
		        console.error("use video.requestFullscreen(), element [" + enterFullscreenButton + "] not found.");
		    }

		    if (element != null) {
		        // 如果找到全屏按钮，点击它
		        element.click();
		    } else {
		        // 否则直接调用 requestFullscreen API
		        window.wvt_video.requestFullscreen();
		    }
		} else {
			console.error("wvt_video is null.");
		}
	} else {
		console.error("already in fullscreen.");
	}
}

/**
 * 主入口函数
 *
 * 初始化脚本，开始监控视频
 */
function wvt_main() {
	// 检查选择器是否有效
	if (selector === "null" || selector === "%selector%") {
		console.error("selector [" + selector + "] is null.");
		return;
	}

	// 监听键盘事件
	// 按 'f' 键可以手动触发全屏
	document.onkeydown = function(e) {
		console.log('Key down: ' + e.key);
		if (e.key == 'f') { wvt_fullscreenVideo(); }
	}

	// 延迟500ms后开始主循环
	// 给页面一些加载时间
	setTimeout(() => { wvt_loop(0) }, 500);
}

// ========== 脚本初始化 ==========

/**
 * 检查脚本是否已注入
 * 避免重复注入导致的问题
 */
if (!window.wvt_javascriptInjected) {
	console.log("AITV javascript injected successfully.");
	console.log("selector=[" + selector + "], enterFullscreenButton=[" + enterFullscreenButton
	 + "], playingCheckEnabled=[" + playingCheckEnabled + "].");

	// 标记脚本已注入
	window.wvt_javascriptInjected = true

	// 启动主函数
	wvt_main();
} else {
	console.error("AITV javascript already injected.");
}
