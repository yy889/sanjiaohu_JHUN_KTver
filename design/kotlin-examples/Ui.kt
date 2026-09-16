package cn.jhun.sanjiaohu

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * Kotlin equivalent of Ui.java, the shared stateless view factory.
 *
 * `object` + @JvmStatic keeps every existing Java call site working unchanged:
 * Java still writes `Ui.dp(this, x)`, not `Ui.INSTANCE.dp(...)`.
 *
 * dp() keeps the original "multiply, add 0.5f, truncate" rounding. Do not use
 * Math.round(): it rounds negative values the other way and would shift layouts.
 */
object Ui {
    @JvmStatic
    fun dp(c: Context, value: Float): Int =
        (c.resources.displayMetrics.density * value + 0.5f).toInt()

    @JvmStatic
    fun column(c: Context): LinearLayout =
        LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

    @JvmStatic
    fun row(c: Context): LinearLayout =
        LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }

    @JvmStatic
    fun weighted(c: Context): LinearLayout.LayoutParams =
        // 1f, not 1: LayoutParams' third parameter is a float weight, and Kotlin
        // does not widen Int to Float implicitly the way Java does.
        LinearLayout.LayoutParams(0, dp(c, 44f), 1f)

    /** Vertical spacer: a 1px-wide View of the requested height. */
    @JvmStatic
    fun space(c: Context, parent: LinearLayout, size: Int) {
        parent.addView(View(c), LinearLayout.LayoutParams(1, dp(c, size.toFloat())))
    }

    @JvmStatic
    fun place(parent: FrameLayout, v: View, x: Int, y: Int, w: Int, h: Int) {
        val lp = FrameLayout.LayoutParams(w, h)
        lp.leftMargin = x
        lp.topMargin = y
        parent.addView(v, lp)
    }

    @JvmStatic
    fun shape(c: Context, color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(c, radius.toFloat()).toFloat()
        }
}
