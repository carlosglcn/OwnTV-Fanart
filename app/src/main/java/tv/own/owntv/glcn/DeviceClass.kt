package tv.own.owntv.glcn

import android.app.ActivityManager
import android.content.Context

/**
 * glcn: is this a modest box (little memory, or few slow cores — a cheap TV stick)? Then the purely decorative
 * continuous effects stay off: the slow zoom on the focused poster, the drifting backdrops, the freeze-frame copies
 * while zapping, the trailer player made ahead of time. Nothing the person uses goes away, and light mode (which
 * the person turns on) still does far more. Read once at start.
 */
object DeviceClass {
    @Volatile var modest = false
        private set

    fun detect(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val total = runCatching { ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }.totalMem }.getOrDefault(0L)
        val lowRam = am?.isLowRamDevice == true
        val cores = Runtime.getRuntime().availableProcessors()
        val maxKhz = runCatching {
            (0 until cores).maxOf { i ->
                java.io.File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").takeIf { it.canRead() }?.readText()?.trim()?.toLongOrNull() ?: 0L
            }
        }.getOrDefault(0L)
        val slowCpu = maxKhz in 1 until SLOW_CPU_KHZ && cores <= SLOW_CORES
        modest = lowRam || (total in 1 until MODEST_RAM) || slowCpu
    }

    /** Decorative continuous motion may run: not on a modest box, not in light mode. */
    val richMotion: Boolean get() = !modest && !LowRam.on

    private const val MODEST_RAM = 2_600_000_000L
    private const val SLOW_CPU_KHZ = 1_700_000L
    private const val SLOW_CORES = 4
}
