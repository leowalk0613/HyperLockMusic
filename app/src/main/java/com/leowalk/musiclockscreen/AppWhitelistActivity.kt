package com.leowalk.musiclockscreen

import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.TreeSet

/**
 * 音乐应用白名单管理页（参考 AodChange AppWhitelistActivity）。
 */
class AppWhitelistActivity : AppCompatActivity() {

    companion object {
        private val PKG_PATTERN =
            Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    }

    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ModuleConfig.init(this)

        val surface = M3.attrColor(this, com.google.android.material.R.attr.colorSurface, 0xFF1C1B1F.toInt())
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
        }

        val toolbar = MaterialToolbar(this).apply {
            title = "音乐应用白名单"
            setTitleTextColor(
                M3.attrColor(this@AppWhitelistActivity,
                    com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5.toInt())
            )
            setBackgroundColor(surface)
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
        }
        root.addView(toolbar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, M3.dp(this, 56f)))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(M3.dp(this@AppWhitelistActivity, 16f), M3.dp(this@AppWhitelistActivity, 8f),
                M3.dp(this@AppWhitelistActivity, 16f), M3.dp(this@AppWhitelistActivity, 24f))
        }

        content.addView(TextView(this).apply {
            text = "仅白名单内的应用播放时，可开启/保持音乐锁屏与歌词；其他应用（如视频、播客）不会生效。"
            textSize = 14f
            setTextColor(
                M3.attrColor(this@AppWhitelistActivity,
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0.toInt())
            )
            setLineSpacing(M3.dp(this@AppWhitelistActivity, 2f).toFloat(), 1f)
            setPadding(0, M3.dp(this@AppWhitelistActivity, 4f), 0, M3.dp(this@AppWhitelistActivity, 12f))
        })

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val addApp = makeTonalButton("添加应用").apply {
            setOnClickListener { showAppPicker() }
        }
        btnRow.addView(addApp, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = M3.dp(this@AppWhitelistActivity, 8f)
        })
        val addPkg = makeTonalButton("添加包名").apply {
            setOnClickListener { showAddPackageDialog() }
        }
        btnRow.addView(addPkg, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(btnRow)

        val reset = makeTonalButton("恢复默认").apply {
            setOnClickListener {
                ModuleConfig.saveWhitelist(ModuleConfig.DEFAULT_WHITELIST)
                ModuleConfig.push(this@AppWhitelistActivity)
                refreshList()
            }
        }
        content.addView(reset, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = M3.dp(this@AppWhitelistActivity, 8f)
            bottomMargin = M3.dp(this@AppWhitelistActivity, 12f)
        })

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listContainer)

        val sv = ScrollView(this)
        sv.addView(content)
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        refreshList()
    }

    private fun makeTonalButton(label: String): MaterialButton {
        return MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle).apply {
            text = label
            textSize = 14f
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                M3.attrColor(this@AppWhitelistActivity,
                    com.google.android.material.R.attr.colorSurfaceContainer, 0xFF2B2930.toInt())
            )
            setTextColor(
                M3.attrColor(this@AppWhitelistActivity,
                    com.google.android.material.R.attr.colorPrimary, 0xFFFFFFFF.toInt())
            )
        }
    }

    private fun refreshList() {
        listContainer.removeAllViews()
        val list = ModuleConfig.getWhitelist()
        if (list.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "白名单为空，可通过上方按钮添加音乐应用"
                textSize = 14f
                setTextColor(
                    M3.attrColor(this@AppWhitelistActivity,
                        com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0.toInt())
                )
                setPadding(0, M3.dp(this@AppWhitelistActivity, 12f), 0, 0)
            })
            return
        }
        for (pkg in list) {
            listContainer.addView(buildAppRow(pkg))
        }
    }

    private fun buildAppRow(pkg: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(M3.dp(this@AppWhitelistActivity, 4f), M3.dp(this@AppWhitelistActivity, 10f),
                M3.dp(this@AppWhitelistActivity, 4f), M3.dp(this@AppWhitelistActivity, 10f))
        }

        val icon = ImageView(this)
        try {
            icon.setImageDrawable(packageManager.getApplicationIcon(pkg))
        } catch (_: Exception) {
            icon.setImageDrawable(null)
        }
        row.addView(icon, LinearLayout.LayoutParams(M3.dp(this, 40f), M3.dp(this, 40f)))

        val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(TextView(this).apply {
            text = appLabel(pkg)
            textSize = 16f
            setTextColor(
                M3.attrColor(this@AppWhitelistActivity,
                    com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5.toInt())
            )
        })
        textCol.addView(TextView(this).apply {
            text = pkg
            textSize = 12f
            setTextColor(
                M3.attrColor(this@AppWhitelistActivity,
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0.toInt())
            )
        })
        row.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTextColor(
                M3.attrColor(this@AppWhitelistActivity,
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0.toInt())
            )
            setPadding(M3.dp(this@AppWhitelistActivity, 12f), 0, M3.dp(this@AppWhitelistActivity, 4f), 0)
            setOnClickListener {
                val list = ModuleConfig.getWhitelist().toMutableList()
                list.remove(pkg)
                ModuleConfig.saveWhitelist(list)
                ModuleConfig.push(this@AppWhitelistActivity)
                refreshList()
            }
        })
        return row
    }

    private fun appLabel(pkg: String): String {
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg
        }
    }

    private fun showAppPicker() {
        val all = loadInstalledApps()
        if (all.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setMessage("没有可添加的应用")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val filtered = ArrayList(all)

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(M3.dp(this@AppWhitelistActivity, 20f), M3.dp(this@AppWhitelistActivity, 8f),
                M3.dp(this@AppWhitelistActivity, 20f), M3.dp(this@AppWhitelistActivity, 4f))
        }

        val search = EditText(this).apply {
            hint = "搜索应用或包名"
            setSingleLine()
            setTextColor(
                M3.attrColor(this@AppWhitelistActivity,
                    com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5.toInt())
            )
            setHintTextColor(
                M3.attrColor(this@AppWhitelistActivity,
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0.toInt())
            )
        }
        dialogView.addView(search)

        val listView = ListView(this)
        listView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, M3.dp(this, 360f))
        dialogView.addView(listView)

        val adapter = object : BaseAdapter() {
            override fun getCount() = filtered.size
            override fun getItem(position: Int) = filtered[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val e = filtered[position]
                val row = LinearLayout(this@AppWhitelistActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, M3.dp(this@AppWhitelistActivity, 8f), 0, M3.dp(this@AppWhitelistActivity, 8f))
                }
                row.addView(ImageView(this@AppWhitelistActivity).apply {
                    setImageDrawable(e.icon)
                }, LinearLayout.LayoutParams(M3.dp(this@AppWhitelistActivity, 40f), M3.dp(this@AppWhitelistActivity, 40f)))
                val col = LinearLayout(this@AppWhitelistActivity).apply { orientation = LinearLayout.VERTICAL }
                col.addView(TextView(this@AppWhitelistActivity).apply {
                    text = e.label
                    textSize = 15f
                    setTextColor(
                        M3.attrColor(this@AppWhitelistActivity,
                            com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5.toInt())
                    )
                })
                col.addView(TextView(this@AppWhitelistActivity).apply {
                    text = e.packageName
                    textSize = 12f
                    setTextColor(
                        M3.attrColor(this@AppWhitelistActivity,
                            com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0.toInt())
                    )
                })
                row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                return row
            }
        }
        listView.adapter = adapter

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("选择应用")
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        listView.tag = dialog
        listView.setOnItemClickListener { _, _, pos, _ ->
            val e = filtered[pos]
            val whitelist = ModuleConfig.getWhitelist().toMutableList()
            if (!whitelist.contains(e.packageName)) {
                whitelist.add(e.packageName)
                ModuleConfig.saveWhitelist(whitelist)
                ModuleConfig.push(this)
            }
            refreshList()
            dialog.dismiss()
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim()?.lowercase().orEmpty()
                filtered.clear()
                if (q.isEmpty()) filtered.addAll(all)
                else for (e in all) {
                    if (e.label.lowercase().contains(q) || e.packageName.lowercase().contains(q)) {
                        filtered.add(e)
                    }
                }
                adapter.notifyDataSetChanged()
            }
        })
        dialog.show()
    }

    private fun showAddPackageDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(M3.dp(this@AppWhitelistActivity, 20f), M3.dp(this@AppWhitelistActivity, 8f),
                M3.dp(this@AppWhitelistActivity, 20f), M3.dp(this@AppWhitelistActivity, 4f))
        }
        val input = EditText(this).apply {
            hint = "例如 com.netease.cloudmusic"
            setSingleLine()
            setTextColor(
                M3.attrColor(this@AppWhitelistActivity,
                    com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5.toInt())
            )
            setHintTextColor(
                M3.attrColor(this@AppWhitelistActivity,
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0.toInt())
            )
        }
        dialogView.addView(input)
        val err = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFFFB4AB.toInt())
            setPadding(0, M3.dp(this@AppWhitelistActivity, 4f), 0, 0)
        }
        dialogView.addView(err)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("添加包名")
            .setMessage("可手动输入音乐应用的包名，未安装的应用也会保留在白名单中。")
            .setView(dialogView)
            .setPositiveButton("添加", null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pkg = input.text.toString().trim()
                when {
                    pkg.isEmpty() -> err.text = "请输入包名"
                    !PKG_PATTERN.matches(pkg) -> err.text = "包名格式不正确"
                    ModuleConfig.getWhitelist().contains(pkg) -> err.text = "该包名已在白名单中"
                    else -> {
                        val list = ModuleConfig.getWhitelist().toMutableList()
                        list.add(pkg)
                        ModuleConfig.saveWhitelist(list)
                        ModuleConfig.push(this)
                        refreshList()
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private data class AppEntry(val packageName: String, val label: String, val icon: Drawable?)

    private fun loadInstalledApps(): List<AppEntry> {
        val pm = packageManager
        val infos = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledApplications(
                android.content.pm.PackageManager.ApplicationInfoFlags.of(
                    android.content.pm.PackageManager.GET_META_DATA.toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
        }
        val whitelist = TreeSet(ModuleConfig.getWhitelist())
        val out = ArrayList<AppEntry>()
        for (ai in infos) {
            if (ai.packageName == packageName) continue
            if (whitelist.contains(ai.packageName)) continue
            val system = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            val updated = (ai.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (system && !updated && pm.getLaunchIntentForPackage(ai.packageName) == null) continue
            try {
                out.add(AppEntry(ai.packageName, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai)))
            } catch (_: Throwable) {
            }
        }
        out.sortBy { it.label.lowercase() }
        return out
    }
}
