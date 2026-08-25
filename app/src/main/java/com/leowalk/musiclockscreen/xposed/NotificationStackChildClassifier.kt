package com.leowalk.musiclockscreen.xposed

import android.app.Notification
import android.view.View

/**
 * 通知栈子 View 分类（对齐 HyperOS 反编译）：
 *
 * - [NotificationStackScrollLayoutInjector.isVisibleNotificationOrMedia]
 *   可见项 = ExpandableNotificationRow | MiuiMediaHeaderView
 * - 媒体控件：[MiuiMediaHeaderView] extends ExpandableView（锁屏播放器，绝不能 GONE）
 * - 普通/媒体通知行：[ExpandableNotificationRow]
 */
object NotificationStackChildClassifier {

    private const val CLASS_MIUI_MEDIA_HEADER =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaHeaderView"
    private const val CLASS_EXPANDABLE_ROW =
        "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow"

    private const val LYRIC_FOCUS_CHANNEL = "channel_id_focusNotifLyrics"
    private const val LYRIC_FOCUS_PACKAGE = "com.leowalk.LyricFocus"

    /** 锁屏媒体控件容器，必须始终可见 */
    fun isMiuiMediaHeaderView(view: View): Boolean {
        return view.javaClass.name == CLASS_MIUI_MEDIA_HEADER ||
            view.javaClass.simpleName == "MiuiMediaHeaderView"
    }

    fun isExpandableNotificationRow(view: View): Boolean {
        var cls: Class<*>? = view.javaClass
        while (cls != null) {
            if (cls.name == CLASS_EXPANDABLE_ROW || cls.simpleName == "ExpandableNotificationRow") {
                return true
            }
            cls = cls.superclass
        }
        return false
    }

    /** 音乐锁屏下应保留：MiuiMediaHeaderView + 媒体通知行 + LyricFocus */
    fun shouldKeepVisible(view: View): Boolean {
        if (isMiuiMediaHeaderView(view)) return true
        if (!isExpandableNotificationRow(view)) return true // SectionHeader / Footer 等不碰
        return isMediaNotificationRow(view) || isLyricFocusRow(view)
    }

    /** 仅对普通通知行返回 true */
    fun shouldHideNotificationRow(view: View): Boolean {
        return isExpandableNotificationRow(view) && !shouldKeepVisible(view)
    }

    fun isMediaNotificationRow(view: View): Boolean {
        val sbn = getStatusBarNotification(view) ?: return false
        if (isLyricFocusSbn(sbn)) return true
        return isMediaNotification(sbn.notification)
    }

    fun isLyricFocusRow(view: View): Boolean {
        val sbn = getStatusBarNotification(view) ?: return false
        return isLyricFocusSbn(sbn)
    }

    private fun isLyricFocusSbn(sbn: android.service.notification.StatusBarNotification): Boolean {
        return sbn.packageName == LYRIC_FOCUS_PACKAGE ||
            sbn.notification?.channelId == LYRIC_FOCUS_CHANNEL
    }

    private fun isMediaNotification(notification: Notification?): Boolean {
        if (notification == null) return false
        try {
            val method = notification.javaClass.getMethod("isMediaNotification")
            val result = method.invoke(notification) as? Boolean
            if (result == true) return true
        } catch (_: Throwable) {
        }
        val extras = notification.extras ?: return false
        val template = extras.getString(Notification.EXTRA_TEMPLATE)
        if (template == "android.app.Notification\$MediaStyle" ||
            template == "androidx.media.app.NotificationCompat\$MediaStyle"
        ) {
            return true
        }
        return extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
    }

    fun getStatusBarNotification(row: View): android.service.notification.StatusBarNotification? {
        return try {
            val entry = row.javaClass.getMethod("getEntry").invoke(row) ?: return null
            val sbnField = HookUtils.findField(entry.javaClass, "mSbn") ?: return null
            sbnField.get(entry) as? android.service.notification.StatusBarNotification
        } catch (_: Throwable) {
            null
        }
    }
}
