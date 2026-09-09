package dev.aaa1115910.bv.player

interface VideoPlayerListener {
    /** 异常 */
    fun onError(error: Exception)

    /**
     * 准备
     */
    fun onReady()

    /** 播放 */
    fun onPlay()

    /** 暂停 */
    fun onPause()

    /** 缓冲中 */
    fun onBuffering()

    /**
     * 空闲：还没准备好，或者播放出错、被 stop 之后
     *
     * 播放器出错后会停在这个状态，不处理的话界面会一直卡在上一个状态（通常是缓冲中）
     */
    fun onIdle() {}

    /**
     * 跳转完成，参数是播放器**实际**落到的位置
     *
     * 播放器可能按关键帧对齐（[androidx.media3.exoplayer.SeekParameters]），落点和请求的时间能差好几秒，
     * 弹幕要按这个位置重新对一次，否则每跳一次就偏一点
     */
    fun onSeekProcessed(positionMs: Long) {}

    /** 播放结束 */
    fun onEnd()

    /** 后退 */
    fun onSeekBack(seekBackIncrementMs: Long)

    /** 前进 */
    fun onSeekForward(seekForwardIncrementMs: Long)

}