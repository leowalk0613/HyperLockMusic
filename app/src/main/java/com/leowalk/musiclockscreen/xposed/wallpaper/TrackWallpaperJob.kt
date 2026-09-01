package com.leowalk.musiclockscreen.xposed.wallpaper

/**
 * 单次切歌/刷壁纸任务。jobId 单调递增；过期 job 不得 setBitmap。
 */
data class TrackWallpaperJob(
    val jobId: Long,
    val trackKey: String?,
    val phase: JobPhase = JobPhase.Resolving
)

enum class JobPhase {
    Idle,
    Resolving,
    Building,
    Previewing,
    Applying,
    Settled,
    Enhancing
}

data class SubmitResult(
    /** 是否应启动新的构建（false 表示同曲已在飞，合并掉） */
    val startBuild: Boolean,
    val job: TrackWallpaperJob?,
    /** true：同曲意图被合并，未新建 job */
    val coalesced: Boolean
)
