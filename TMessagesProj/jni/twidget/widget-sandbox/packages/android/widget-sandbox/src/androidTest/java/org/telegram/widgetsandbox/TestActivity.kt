package org.telegram.widgetsandbox

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout

/** Hosts a widget view at a fixed 400×600 dp so layout queries have real geometry. */
class TestActivity : Activity() {
    lateinit var container: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = FrameLayout(this)
        setContentView(container)
    }

    fun show(view: View, widthDp: Int = 400, heightDp: Int = 600) {
        val d = resources.displayMetrics.density
        container.removeAllViews()
        container.addView(view, FrameLayout.LayoutParams((widthDp * d).toInt(), (heightDp * d).toInt()))
    }
}
