package net.badacid.homeassist.echoshow.app

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.TextView
import net.badacid.homeassist.echoshow.R

/** The app's only activity. For now it shows the app's name; the screens come in a later plan. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = getString(R.string.app_name)
                textSize = 32f
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.text))
                setBackgroundColor(getColor(R.color.background))
            },
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Full screen: the status and navigation bars come back for a moment with a swipe from the edge. */
    private fun hideSystemBars() {
        window.insetsController?.let {
            it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
