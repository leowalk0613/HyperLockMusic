package com.leowalk.musiclockscreen.xposed.wallpaper

/**
 * 切歌壁纸管线纯逻辑：单一 jobId、同曲合并、异曲作废、Preview≠Commit、HD 仅 Settled。
 * 无 Android 依赖，便于单元测试。
 */
class TrackWallpaperCoordinator {

    private val lock = Any()
    private var nextJobId: Long = 0L
    private var active: TrackWallpaperJob? = null
    private var appliedTrackKey: String? = null
    private var appliedJobId: Long? = null
    /** 恢复原壁纸世代；音乐 job 或再次 restore 会递增 */
    private var restoreEpoch: Long = 0L
    private var pendingRestoreEpoch: Long? = null

    fun activeJob(): TrackWallpaperJob? = synchronized(lock) { active }

    fun appliedTrackKey(): String? = synchronized(lock) { appliedTrackKey }

    fun appliedJobId(): Long? = synchronized(lock) { appliedJobId }

    fun isJobCurrent(jobId: Long): Boolean = synchronized(lock) { active?.jobId == jobId }

    /**
     * 曲目是否已应用、或活跃 job 正覆盖该曲（含 building/applying/enhancing）。
     * Settled 且 applied 匹配时视为 in-flight（已追上，勿重复刷）。
     */
    fun isTrackInFlight(trackKey: String?): Boolean = synchronized(lock) {
        isTrackInFlightLocked(trackKey)
    }

    private fun isTrackInFlightLocked(trackKey: String?): Boolean {
        if (trackKey.isNullOrBlank()) return false
        if (trackKey == appliedTrackKey) return true
        val job = active ?: return false
        if (job.trackKey != trackKey) return false
        return when (job.phase) {
            JobPhase.Resolving,
            JobPhase.Building,
            JobPhase.Previewing,
            JobPhase.Applying,
            JobPhase.Settled,
            JobPhase.Enhancing -> true
            JobPhase.Idle -> false
        }
    }

    /**
     * 提交切歌/刷壁纸意图。
     * - 同曲且已在飞 → coalesce，不新建 job
     * - 否则作废旧 job，新建 Resolving job
     */
    fun submitTrackIntent(trackKey: String?): SubmitResult = synchronized(lock) {
        pendingRestoreEpoch = null
        val current = active
        if (current != null &&
            sameTrack(current.trackKey, trackKey) &&
            current.phase != JobPhase.Idle &&
            isTrackInFlightLocked(trackKey)
        ) {
            if (current.phase == JobPhase.Settled || current.phase == JobPhase.Enhancing) {
                if (trackKey == appliedTrackKey) {
                    return SubmitResult(startBuild = false, job = current, coalesced = true)
                }
            }
            if (current.phase == JobPhase.Resolving ||
                current.phase == JobPhase.Building ||
                current.phase == JobPhase.Previewing ||
                current.phase == JobPhase.Applying
            ) {
                return SubmitResult(startBuild = false, job = current, coalesced = true)
            }
        }
        val job = TrackWallpaperJob(
            jobId = ++nextJobId,
            trackKey = trackKey,
            phase = JobPhase.Resolving
        )
        active = job
        return SubmitResult(startBuild = true, job = job, coalesced = false)
    }

    fun markBuilding(jobId: Long): Boolean = synchronized(lock) {
        val job = active ?: return false
        if (job.jobId != jobId) return false
        active = job.copy(phase = JobPhase.Building)
        true
    }

    /** 构建失败：若仍是当前 job，清回 Idle，允许重试 */
    fun markBuildFailed(jobId: Long): Unit = synchronized(lock) {
        val job = active ?: return
        if (job.jobId != jobId) return
        active = job.copy(phase = JobPhase.Idle)
    }

    /** Preview 可更新 overlay，但不得推进 appliedTrackKey */
    fun markPreviewed(jobId: Long): Boolean = synchronized(lock) {
        val job = active ?: return false
        if (job.jobId != jobId) return false
        active = job.copy(phase = JobPhase.Previewing)
        true
    }

    fun markApplying(jobId: Long): Boolean = synchronized(lock) {
        val job = active ?: return false
        if (job.jobId != jobId) return false
        active = job.copy(phase = JobPhase.Applying)
        true
    }

    /** setBitmap 前调用：仅当前 job 允许写入 */
    fun shouldWriteApply(jobId: Long): Boolean = synchronized(lock) {
        active?.jobId == jobId
    }

    /**
     * setBitmap 成功后 commit。返回 false 表示 job 已过期（调用方本不应写入；
     * 若因竞态已写，也不得更新 applied）。
     */
    fun markApplyCommitted(jobId: Long, trackKey: String?): Boolean = synchronized(lock) {
        val job = active ?: return false
        if (job.jobId != jobId) return false
        appliedTrackKey = trackKey ?: job.trackKey
        appliedJobId = jobId
        active = job.copy(
            trackKey = appliedTrackKey,
            phase = JobPhase.Settled
        )
        true
    }

    fun shouldScheduleEnhance(jobId: Long, trackKey: String?): Boolean = synchronized(lock) {
        shouldScheduleEnhanceLocked(jobId, trackKey)
    }

    private fun shouldScheduleEnhanceLocked(jobId: Long, trackKey: String?): Boolean {
        val job = active ?: return false
        if (job.jobId != jobId) return false
        if (job.phase != JobPhase.Settled && job.phase != JobPhase.Enhancing) return false
        if (trackKey != null && trackKey != appliedTrackKey) return false
        return appliedJobId == jobId
    }

    fun beginEnhance(jobId: Long): Boolean = synchronized(lock) {
        if (!shouldScheduleEnhanceLocked(jobId, active?.trackKey)) return false
        val job = active ?: return false
        active = job.copy(phase = JobPhase.Enhancing)
        true
    }

    fun markEnhanceDone(jobId: Long): Unit = synchronized(lock) {
        val job = active ?: return
        if (job.jobId != jobId) return
        if (job.phase == JobPhase.Enhancing) {
            active = job.copy(phase = JobPhase.Settled)
        }
    }

    /** 布局切换等同曲再 apply：复用 applied 曲目新建 job */
    fun submitLayoutApply(trackKey: String?): SubmitResult = synchronized(lock) {
        pendingRestoreEpoch = null
        val key = trackKey ?: appliedTrackKey
        val job = TrackWallpaperJob(
            jobId = ++nextJobId,
            trackKey = key,
            phase = JobPhase.Applying
        )
        active = job
        SubmitResult(startBuild = true, job = job, coalesced = false)
    }

    /** 开始恢复原壁纸：作废一切音乐 job，返回 restore epoch */
    fun beginRestore(): Long = synchronized(lock) {
        active = null
        appliedTrackKey = null
        appliedJobId = null
        val epoch = ++restoreEpoch
        pendingRestoreEpoch = epoch
        epoch
    }

    fun shouldWriteRestore(epoch: Long): Boolean = synchronized(lock) {
        pendingRestoreEpoch == epoch && active == null
    }

    fun markRestoreCommitted(epoch: Long): Boolean = synchronized(lock) {
        if (pendingRestoreEpoch != epoch || active != null) return false
        pendingRestoreEpoch = null
        true
    }

    /** 标记 stale：清空 applied，保留/清空 active 以便强制刷新 */
    fun markStale(): Unit = synchronized(lock) {
        appliedTrackKey = null
        appliedJobId = null
        val job = active
        if (job != null && job.phase != JobPhase.Idle) {
            active = job.copy(phase = JobPhase.Idle)
        }
        pendingRestoreEpoch = null
        restoreEpoch++
    }

    fun invalidateAll(): Unit = synchronized(lock) {
        active = null
        appliedTrackKey = null
        appliedJobId = null
        pendingRestoreEpoch = null
        restoreEpoch++
        nextJobId++
    }

    private fun sameTrack(a: String?, b: String?): Boolean {
        if (a == null && b == null) return true
        if (a == null || b == null) return false
        return a == b
    }
}
