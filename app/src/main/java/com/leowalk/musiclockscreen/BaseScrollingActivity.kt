package com.leowalk.musiclockscreen

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

/**
 * 统一的列表页骨架：MaterialToolbar + ScrollView 卡片列表，
 * 顶部工具栏与内容列表的搭建逻辑在所有设置页间保持一致。
 */
abstract class BaseScrollingActivity : AppCompatActivity() {

    protected abstract fun titleText(): String

    protected open fun buildToolbarAction(ctx: Context): View? = null

    /** 是否显示左上角返回键（根页面返回 false） */
    protected open fun showHomeAsUp(): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ModuleConfig.init(this)

        val surface = M3.attrColor(this, com.google.android.material.R.attr.colorSurface, 0xFF1C1B1F.toInt())

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
        }

        val toolbar = MaterialToolbar(this).apply {
            setTitle(titleText())
            setTitleTextColor(M3.attrColor(this@BaseScrollingActivity, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5.toInt()))
            setBackgroundColor(surface)
        }
        root.addView(toolbar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, M3.dp(this, 56f)))

        val sv = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(M3.dp(this@BaseScrollingActivity, 16f), M3.dp(this@BaseScrollingActivity, 8f),
                M3.dp(this@BaseScrollingActivity, 16f), M3.dp(this@BaseScrollingActivity, 24f))
        }
        buildContent(list)
        sv.addView(list)
        root.addView(sv, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(showHomeAsUp())
        buildToolbarAction(this)?.let { action ->
            toolbar.addView(action, androidx.appcompat.widget.Toolbar.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL))
        }
    }

    protected abstract fun buildContent(list: LinearLayout)

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}