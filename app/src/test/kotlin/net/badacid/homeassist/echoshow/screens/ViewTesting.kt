package net.badacid.homeassist.echoshow.screens

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** The words on screen: every TextView whose parents are all visible. (isShown needs a window.) */
fun visibleTexts(view: View): List<String> = if (view.visibility != View.VISIBLE) emptyList() else when (view) {
    is TextView -> listOf(view.text.toString())
    is ViewGroup -> (0 until view.childCount).flatMap { visibleTexts(view.getChildAt(it)) }
    else -> emptyList()
}

private fun View.isVisibleUpTo(root: View): Boolean =
    generateSequence(this) { if (it === root) null else it.parent as? View }.all { it.visibility == View.VISIBLE }

/** The visible view showing exactly these words. */
fun View.withText(words: String): View {
    val found = ArrayList<View>()
    findViewsWithText(found, words, View.FIND_VIEWS_WITH_TEXT)
    return found.first { (it as? TextView)?.text?.toString() == words && it.isVisibleUpTo(this) }
}

/** The visible view with this content description. */
fun View.described(words: String): View {
    val found = ArrayList<View>()
    findViewsWithText(found, words, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
    return found.first { it.isVisibleUpTo(this) }
}

/** The row (clickable parent) holding these words. */
fun View.rowWith(words: String): View {
    var view: View = withText(words)
    while (!view.isClickable) view = view.parent as View
    return view
}
