package com.leowalk.musiclockscreen

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * 通知使用权：用本组件 [MediaSessionManager.getActiveSessions] 准确检测播放会话。
 * 状态写入 [ConfigProvider]，供 SystemUI 侧退出音乐锁屏。
 */
class MusicNotificationListenerService : NotificationListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            ModuleConfig.init(applicationContext)
        } catch (_: Throwable) {
        }
        Log.i(TAG, "notification listener connected")
        publishListenerReady(true)
        bindSessionListener()
        refreshAndPublish()
    }

    override fun onListenerDisconnected() {
        unbindSessionListener()
        publishListenerReady(false)
        Log.i(TAG, "notification listener disconnected")
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(
        sbn: android.service.notification.StatusBarNotification?,
        rankingMap: RankingMap?
    ) {
        mainHandler.post { refreshAndPublish() }
    }

    override fun onNotificationRemoved(
        sbn: android.service.notification.StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int
    ) {
        mainHandler.post { refreshAndPublish() }
    }

    private fun bindSessionListener() {
        unbindSessionListener()
        val msm = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        val cn = componentName(this)
        val listener = MediaSessionManager.OnActiveSessionsChangedListener {
            refreshAndPublish()
        }
        sessionsListener = listener
        try {
            msm.addOnActiveSessionsChangedListener(listener, cn, mainHandler)
            Log.i(TAG, "OnActiveSessionsChangedListener registered")
        } catch (e: Throwable) {
            Log.e(TAG, "addOnActiveSessionsChangedListener failed", e)
            sessionsListener = null
        }
    }

    private fun unbindSessionListener() {
        val listener = sessionsListener ?: return
        sessionsListener = null
        try {
            val msm = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager
            msm?.removeOnActiveSessionsChangedListener(listener)
        } catch (_: Throwable) {
        }
    }

    private fun refreshAndPublish() {
        val active = findActiveMusicController()
        publishPlayback(
            active = active != null,
            packageName = active?.packageName
        )
    }

    private fun findActiveMusicController(): MediaController? {
        val controllers = MediaSessionAccess.getActiveControllers(this)
        return controllers.firstOrNull { controller ->
            if (!ModuleConfig.isPackageAllowed(controller.packageName)) return@firstOrNull false
            val state = controller.playbackState?.state ?: return@firstOrNull true
            state == PlaybackState.STATE_PLAYING ||
                state == PlaybackState.STATE_PAUSED ||
                state == PlaybackState.STATE_BUFFERING ||
                state == PlaybackState.STATE_CONNECTING
        }
    }

    private fun publishListenerReady(ready: Boolean) {
        try {
            val values = ContentValues().apply {
                put(ConfigProvider.KEY_MEDIA_LISTENER_READY, if (ready) 1 else 0)
                if (!ready) {
                    put(ConfigProvider.KEY_MEDIA_PLAYBACK_ACTIVE, 0)
                    put(ConfigProvider.KEY_MEDIA_PLAYBACK_PACKAGE, "")
                }
            }
            contentResolver.update(CONFIG_URI, values, null, null)
        } catch (e: Throwable) {
            Log.e(TAG, "publishListenerReady failed", e)
        }
    }

    private fun publishPlayback(active: Boolean, packageName: String?) {
        try {
            val values = ContentValues().apply {
                put(ConfigProvider.KEY_MEDIA_LISTENER_READY, 1)
                put(ConfigProvider.KEY_MEDIA_PLAYBACK_ACTIVE, if (active) 1 else 0)
                put(ConfigProvider.KEY_MEDIA_PLAYBACK_PACKAGE, packageName.orEmpty())
            }
            contentResolver.update(CONFIG_URI, values, null, null)
            Log.i(TAG, "playback active=$active pkg=$packageName")
        } catch (e: Throwable) {
            Log.e(TAG, "publishPlayback failed", e)
        }
    }

    companion object {
        private const val TAG = "HyperLockMusic_NLS"
        private val CONFIG_URI =
            android.net.Uri.parse("content://${ConfigProvider.AUTHORITY}/config")

        const val SERVICE_CLASS =
            "com.leowalk.musiclockscreen.MusicNotificationListenerService"

        fun componentName(context: Context): ComponentName =
            ComponentName(context.packageName, SERVICE_CLASS)

        /** SystemUI 进程内构造（包名固定） */
        fun moduleComponentName(): ComponentName =
            ComponentName("com.leowalk.musiclockscreen", SERVICE_CLASS)
    }
}

/**
 * 通知使用权与媒体会话读取（模块进程 / SystemUI 共用）。
 */
object MediaSessionAccess {

    fun isNotificationAccessEnabled(context: Context): Boolean {
        return try {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val want = MusicNotificationListenerService.moduleComponentName()
            flat.split(':').any { entry ->
                val cn = ComponentName.unflattenFromString(entry) ?: return@any false
                cn.packageName == want.packageName && cn.className == want.className
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun openNotificationAccessSettings(context: Context) {
        val intent = android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun getActiveControllers(context: Context): List<MediaController> {
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
                as? MediaSessionManager ?: return emptyList()
            val cn = MusicNotificationListenerService.moduleComponentName()
            val withListener = try {
                msm.getActiveSessions(cn)
            } catch (_: Throwable) {
                emptyList()
            }
            if (withListener.isNotEmpty()) return withListener
            try {
                msm.getActiveSessions(null)
            } catch (_: Throwable) {
                emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
