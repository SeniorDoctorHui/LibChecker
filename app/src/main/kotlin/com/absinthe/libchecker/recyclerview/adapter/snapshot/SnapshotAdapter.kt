package com.absinthe.libchecker.recyclerview.adapter.snapshot

import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ImageSpan
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.isGone
import androidx.core.view.isVisible
import coil.load
import com.absinthe.libchecker.R
import com.absinthe.libchecker.bean.SnapshotDiffItem
import com.absinthe.libchecker.constant.Constants
import com.absinthe.libchecker.utils.PackageUtils
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.zhangyue.we.x2c.X2C
import com.zhangyue.we.x2c.ano.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.appiconloader.AppIconLoader
import kotlin.math.abs

const val ARROW = "→"

@Xml(layouts = ["item_snapshot"])
class SnapshotAdapter : BaseQuickAdapter<SnapshotDiffItem, BaseViewHolder>(0) {

    private val iconSize by lazy { context.resources.getDimensionPixelSize(R.dimen.app_icon_size) }
    private val iconLoader by lazy { AppIconLoader(iconSize, false,context) }
    private val iconMap = mutableMapOf<String, Bitmap>()

    override fun onCreateDefViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return createBaseViewHolder(X2C.inflate(context, R.layout.item_snapshot, parent, false))
    }

    override fun convert(holder: BaseViewHolder, item: SnapshotDiffItem) {
        holder.getView<ImageView>(R.id.iv_icon).apply {
            tag = item.packageName
            iconMap[item.packageName]?.let {
                load(it) {
                    crossfade(true)
                }
            } ?: let {
                GlobalScope.launch(Dispatchers.IO) {
                    val bitmap = try {
                        iconLoader.loadIcon(PackageUtils.getPackageInfo(item.packageName, PackageManager.GET_META_DATA).applicationInfo)
                    } catch (e: PackageManager.NameNotFoundException) {
                        ContextCompat.getDrawable(context, R.drawable.ic_app_list)?.apply {
                            setTint(ContextCompat.getColor(context, R.color.textNormal))
                        }?.toBitmap(iconSize, iconSize)
                    }
                    withContext(Dispatchers.Main) {
                        findViewWithTag<ImageView>(item.packageName)?.let {
                            load(bitmap) {
                                crossfade(true)
                            }
                        }
                    }
                    if (bitmap != null) {
                        iconMap[item.packageName] = bitmap
                    }
                }
            }
        }

        holder.getView<View>(R.id.view_red_mask).isVisible = item.deleted

        var isNewOrDeleted = false

        when {
            item.deleted -> {
                holder.itemView.backgroundTintList =
                    ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.material_red_300)
                    )
                holder.setTextColor(
                    R.id.tv_version,
                    ContextCompat.getColor(context, R.color.textNormal)
                )
                holder.setTextColor(
                    R.id.tv_target_api,
                    ContextCompat.getColor(context, R.color.textNormal)
                )
                isNewOrDeleted = true
            }
            item.newInstalled -> {
                holder.itemView.backgroundTintList =
                    ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.material_green_300)
                    )
                holder.setTextColor(
                    R.id.tv_version,
                    ContextCompat.getColor(context, R.color.textNormal)
                )
                holder.setTextColor(
                    R.id.tv_target_api,
                    ContextCompat.getColor(context, R.color.textNormal)
                )
                isNewOrDeleted = true
            }
            else -> {
                holder.itemView.backgroundTintList = null
                holder.setTextColor(
                    R.id.tv_version,
                    ContextCompat.getColor(context, android.R.color.darker_gray)
                )
                holder.setTextColor(
                    R.id.tv_target_api,
                    ContextCompat.getColor(context, android.R.color.darker_gray)
                )
            }
        }

        if (isNewOrDeleted) {
            holder.getView<TextView>(R.id.indicator_added).isGone = true
            holder.getView<TextView>(R.id.indicator_removed).isGone = true
            holder.getView<TextView>(R.id.indicator_changed).isGone = true
            holder.getView<TextView>(R.id.indicator_moved).isGone = true
        } else {
            holder.getView<TextView>(R.id.indicator_added).isVisible =
                item.added and !isNewOrDeleted
            holder.getView<TextView>(R.id.indicator_removed).isVisible =
                item.removed and !isNewOrDeleted
            holder.getView<TextView>(R.id.indicator_changed).isVisible =
                item.changed and !isNewOrDeleted
            holder.getView<TextView>(R.id.indicator_moved).isVisible =
                item.moved and !isNewOrDeleted
        }

        val tvAppName = holder.getView<TextView>(R.id.tv_app_name)
        if (item.isTrackItem) {
            val imageSpan = ImageSpan(context, R.drawable.ic_track)
            val spannable = SpannableString(" ${getDiffString(item.labelDiff, isNewOrDeleted)}")
            spannable.setSpan(imageSpan, 0, 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            tvAppName.text = spannable
        } else {
            tvAppName.text = getDiffString(item.labelDiff, isNewOrDeleted)
        }

        holder.setText(R.id.tv_package_name, item.packageName)
        holder.setText(R.id.tv_version, getDiffString(item.versionNameDiff, item.versionCodeDiff, isNewOrDeleted, "%s (%s)"))
        holder.setText(R.id.tv_target_api, getDiffString(item.targetApiDiff, isNewOrDeleted, "API %s"))
        holder.setText(R.id.tv_abi, PackageUtils.getAbiString(item.abiDiff.old.toInt(), false))
        holder.getView<ImageView>(R.id.iv_abi_type).load(PackageUtils.getAbiBadgeResource(item.abiDiff.old.toInt()))

        if (item.abiDiff.new != null && item.abiDiff.old != item.abiDiff.new && abs(item.abiDiff.old - item.abiDiff.new) != Constants.MULTI_ARCH) {
            holder.getView<TextView>(R.id.tv_arrow).isVisible = true

            val abiBadgeNewLayout = holder.getView<LinearLayout>(R.id.layout_abi_badge_new)
            abiBadgeNewLayout.isVisible = true
            abiBadgeNewLayout.findViewById<TextView>(R.id.tv_abi).text = PackageUtils.getAbiString(item.abiDiff.new.toInt(), false)
            abiBadgeNewLayout.findViewById<ImageView>(R.id.iv_abi_type).load(PackageUtils.getAbiBadgeResource(item.abiDiff.new.toInt()))
        } else {
            holder.getView<TextView>(R.id.tv_arrow).isGone = true
            holder.getView<LinearLayout>(R.id.layout_abi_badge_new).isGone = true
        }
    }

    fun release() {
        iconMap.forEach { it.value.recycle() }
    }

    private fun <T> getDiffString(diff: SnapshotDiffItem.DiffNode<T>, isNewOrDeleted: Boolean = false, format: String = "%s"): String {
        return if (diff.old != diff.new && !isNewOrDeleted) {
            "${String.format(format, diff.old.toString())} $ARROW ${String.format(format, diff.new.toString())}"
        } else {
            String.format(format, diff.old.toString())
        }
    }

    private fun getDiffString(diff1: SnapshotDiffItem.DiffNode<*>, diff2: SnapshotDiffItem.DiffNode<*>, isNewOrDeleted: Boolean = false, format: String = "%s"): String {
        return if ((diff1.old != diff1.new || diff2.old != diff2.new) && !isNewOrDeleted) {
            "${String.format(format, diff1.old.toString(), diff2.old.toString())} $ARROW ${String.format(format, diff1.new.toString(), diff2.new.toString())}"
        } else {
            String.format(format, diff1.old.toString(), diff2.old.toString())
        }
    }
}