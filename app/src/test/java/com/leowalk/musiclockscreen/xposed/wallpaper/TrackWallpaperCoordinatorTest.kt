package com.leowalk.musiclockscreen.xposed.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TrackWallpaperCoordinatorTest {

    private lateinit var c: TrackWallpaperCoordinator

    @Before
    fun setUp() {
        c = TrackWallpaperCoordinator()
    }

    @Test
    fun differentTrackInvalidatesOldJob_oldCannotWrite() {
        val first = c.submitTrackIntent("netease:1")
        assertTrue(first.startBuild)
        val job1 = first.job!!
        assertTrue(c.markBuilding(job1.jobId))
        assertTrue(c.markPreviewed(job1.jobId))

        val second = c.submitTrackIntent("netease:2")
        assertTrue(second.startBuild)
        val job2 = second.job!!
        assertTrue(job2.jobId > job1.jobId)

        assertFalse(c.shouldWriteApply(job1.jobId))
        assertTrue(c.shouldWriteApply(job2.jobId))
        assertFalse(c.markApplyCommitted(job1.jobId, "netease:1"))
        assertNull(c.appliedTrackKey())
    }

    @Test
    fun sameTrackWhileBuilding_coalescesWithoutNewJob() {
        val first = c.submitTrackIntent("netease:9")
        val jobId = first.job!!.jobId
        c.markBuilding(jobId)

        val again = c.submitTrackIntent("netease:9")
        assertTrue(again.coalesced)
        assertFalse(again.startBuild)
        assertEquals(jobId, again.job!!.jobId)
        assertTrue(c.shouldWriteApply(jobId))
    }

    @Test
    fun previewDoesNotAdvanceApplied() {
        val job = c.submitTrackIntent("netease:3").job!!
        c.markBuilding(job.jobId)
        c.markPreviewed(job.jobId)
        assertNull(c.appliedTrackKey())
        assertEquals(JobPhase.Previewing, c.activeJob()!!.phase)
    }

    @Test
    fun enhanceOnlyAfterApplyCommitted_newTrackCancelsEnhance() {
        val job = c.submitTrackIntent("netease:4").job!!
        c.markBuilding(job.jobId)
        c.markPreviewed(job.jobId)
        c.markApplying(job.jobId)
        assertFalse(c.shouldScheduleEnhance(job.jobId, "netease:4"))

        assertTrue(c.markApplyCommitted(job.jobId, "netease:4"))
        assertEquals("netease:4", c.appliedTrackKey())
        assertTrue(c.shouldScheduleEnhance(job.jobId, "netease:4"))
        assertTrue(c.beginEnhance(job.jobId))

        val next = c.submitTrackIntent("netease:5")
        assertTrue(next.startBuild)
        assertFalse(c.shouldScheduleEnhance(job.jobId, "netease:4"))
        assertFalse(c.shouldWriteApply(job.jobId))
    }

    @Test
    fun lateOldApply_shouldWriteFalse() {
        val old = c.submitTrackIntent("netease:old").job!!
        c.markBuilding(old.jobId)
        c.markPreviewed(old.jobId)
        c.markApplying(old.jobId)

        val newer = c.submitTrackIntent("netease:new").job!!
        c.markBuilding(newer.jobId)
        c.markPreviewed(newer.jobId)
        c.markApplying(newer.jobId)

        // 旧 apply 晚到：禁止写入（对应 written-then-superseded 回退 bug）
        assertFalse(c.shouldWriteApply(old.jobId))
        assertTrue(c.shouldWriteApply(newer.jobId))
        assertTrue(c.markApplyCommitted(newer.jobId, "netease:new"))
        assertEquals("netease:new", c.appliedTrackKey())
        assertFalse(c.markApplyCommitted(old.jobId, "netease:old"))
        assertEquals("netease:new", c.appliedTrackKey())
    }

    @Test
    fun settledSameTrack_coalesces() {
        val job = c.submitTrackIntent("netease:7").job!!
        c.markApplyCommitted(job.jobId, "netease:7")
        val again = c.submitTrackIntent("netease:7")
        assertTrue(again.coalesced)
        assertFalse(again.startBuild)
        assertEquals("netease:7", c.appliedTrackKey())
    }

    @Test
    fun restoreInvalidatesMusicJobs() {
        val job = c.submitTrackIntent("netease:8").job!!
        c.markApplying(job.jobId)
        val epoch = c.beginRestore()
        assertFalse(c.shouldWriteApply(job.jobId))
        assertTrue(c.shouldWriteRestore(epoch))
        assertTrue(c.markRestoreCommitted(epoch))
        assertFalse(c.shouldWriteRestore(epoch))
    }

    @Test
    fun serialApplier_skipsWriteWhenSuperseded() {
        val writes = AtomicInteger()
        val commits = AtomicInteger()
        val cancels = AtomicInteger()
        val latch = CountDownLatch(2)
        val coord = TrackWallpaperCoordinator()
        val applier = SerialWallpaperApplier(
            shouldWrite = { id -> coord.shouldWriteApply(id) },
            executor = Executors.newSingleThreadExecutor()
        )

        val old = coord.submitTrackIntent("a").job!!
        coord.markApplying(old.jobId)
        val newer = coord.submitTrackIntent("b").job!!
        coord.markApplying(newer.jobId)

        applier.enqueue(
            jobId = old.jobId,
            write = {
                writes.incrementAndGet()
                true
            },
            onCommitted = {
                commits.incrementAndGet()
                latch.countDown()
            },
            onCancelled = {
                cancels.incrementAndGet()
                latch.countDown()
            }
        )
        applier.enqueue(
            jobId = newer.jobId,
            write = {
                writes.incrementAndGet()
                assertTrue(coord.markApplyCommitted(newer.jobId, "b"))
                true
            },
            onCommitted = {
                commits.incrementAndGet()
                latch.countDown()
            },
            onCancelled = {
                cancels.incrementAndGet()
                latch.countDown()
            }
        )

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertEquals(1, writes.get())
        assertEquals(1, commits.get())
        assertEquals(1, cancels.get())
        assertEquals("b", coord.appliedTrackKey())
        applier.shutdown()
    }

    @Test
    fun buildFailedAllowsRetry() {
        val job = c.submitTrackIntent("netease:retry").job!!
        c.markBuilding(job.jobId)
        c.markBuildFailed(job.jobId)
        assertEquals(JobPhase.Idle, c.activeJob()!!.phase)
        // Idle 后同曲可再 submit 启动构建
        val again = c.submitTrackIntent("netease:retry")
        assertTrue(again.startBuild)
        assertNotNull(again.job)
        assertTrue(again.job!!.jobId > job.jobId)
    }
}
