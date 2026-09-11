package dev.aaa1115910.bv.player.impl.exo

/** 单调时钟的五秒滑动平均，校准系统日期不影响结果。 */
internal class RollingNetworkRate(private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val buckets = LongArray(5)
    private var lastSecond: Long? = null
    @Volatile var enabled = false
        set(value) = synchronized(this) { field = value; clear() }

    @Synchronized fun clear() { buckets.fill(0); lastSecond = null }

    fun add(bytes: Int) {
        if (!enabled || bytes <= 0) return
        synchronized(this) {
            if (!enabled) return
            val second = nowMs() / 1000
            rotate(second)
            buckets[bucketIndex(second)] += bytes
        }
    }

    @Synchronized fun bitsPerSecond(): Long {
        if (!enabled) return 0
        rotate(nowMs() / 1000)
        return buckets.sum() * 8 / 5
    }

    private fun rotate(second: Long) {
        val old = lastSecond
        if (old == null || second < old || second - old >= 5) buckets.fill(0)
        else for (delta in 1..(second - old)) buckets[bucketIndex(old + delta)] = 0
        lastSecond = second
    }

    private fun bucketIndex(second: Long): Int = ((second % 5 + 5) % 5).toInt()
}
