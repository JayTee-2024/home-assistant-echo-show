package net.badacid.homeassist.echoshow.screens.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import net.badacid.homeassist.echoshow.core.CardPlace
import net.badacid.homeassist.echoshow.core.Device
import net.badacid.homeassist.echoshow.core.HomeSnapshot
import net.badacid.homeassist.echoshow.core.Layout
import net.badacid.homeassist.echoshow.core.cardsOnPage
import net.badacid.homeassist.echoshow.core.findDevice
import net.badacid.homeassist.echoshow.core.pageCount
import net.badacid.homeassist.echoshow.screens.Icons
import net.badacid.homeassist.echoshow.screens.Theme
import net.badacid.homeassist.echoshow.screens.Widgets

/** What the home screen draws. The app makes it from its AppState (screens do not depend on the app). */
data class HomeScreenState(
    val layout: Layout,
    val home: HomeSnapshot,
    val devices: List<Device>,
    val homeAddress: String,
    val isReconnecting: Boolean,
    val page: Int,
    val toast: String?,
)

/** What the home screen asks of the app. */
interface HomeCallbacks {
    fun onCardTap(deviceId: String)
    fun onCardHold(deviceId: String)
    fun onSettings()
    fun onPage(page: Int)
}

/**
 * The home screen (the LG app's HomeScreen.tsx): the title bar with Settings, the reconnecting banner, the
 * pages of cards with dots below, or the empty home; and the toast over all of it.
 */
@SuppressLint("ViewConstructor")
class HomeView(
    context: Context,
    private val callbacks: HomeCallbacks,
    private val pictures: PictureLoader,
    private val scope: CoroutineScope,
) : FrameLayout(context) {
    private val dp = { value: Float -> Theme.dp(context, value) }
    private val banner = Theme.text(context, "Lost the connection to Home Assistant. Showing the last known states. Trying to reconnect…", Theme.BODY_SP, Theme.AMBER).apply {
        setBackgroundColor(android.graphics.Color.parseColor("#3A2C12"))
        setPadding(dp(24f), dp(8f), dp(24f), dp(8f))
        visibility = GONE
    }
    private val pager = PagerView(context) { callbacks.onPage(it) }
    private val dots = LinearLayout(context).apply { gravity = Gravity.CENTER }
    private val empty = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(Theme.text(context, "No devices yet", Theme.TITLE_SP, bold = true), Widgets.wrap())
        addView(Theme.text(context, "Choose which of your Home Assistant devices to show here.", Theme.BODY_SP, Theme.TEXT_DIM), Widgets.wrap().apply { topMargin = dp(12f) })
        addView(Widgets.button(context, "Add devices") { callbacks.onSettings() }, Widgets.wrap().apply { topMargin = dp(28f) })
    }
    private val homeArea = FrameLayout(context)
    private val toast = Theme.text(context, "", Theme.BODY_SP).apply {
        background = Theme.roundedPanel(context, Theme.SURFACE_RAISED, 14f)
        setPadding(dp(20f), dp(12f), dp(20f), dp(12f))
        maxWidth = dp(640f)
        gravity = Gravity.CENTER
        visibility = GONE
    }

    /** Cards kept between updates, so a camera picture is not fetched again on every change. */
    private val cards = mutableMapOf<CardPlace, CardView>()
    private var shownLayout: Layout? = null
    private var shownPage = -1

    init {
        setBackgroundColor(Theme.BACKGROUND)
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(Widgets.titleBar(context, "Home Assist", Widgets.iconButton(context, Icons.SETTINGS, "Settings") { callbacks.onSettings() }), Widgets.matchWidth())
        column.addView(banner, Widgets.matchWidth())
        homeArea.addView(pager, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        homeArea.addView(dots, LayoutParams(LayoutParams.MATCH_PARENT, dp(28f), Gravity.BOTTOM))
        homeArea.addView(empty, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        column.addView(homeArea, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(toast, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(48f) })
    }

    fun show(state: HomeScreenState) {
        val layout = state.layout
        val reconnecting = state.isReconnecting
        banner.visibility = if (reconnecting) VISIBLE else GONE

        val isEmpty = layout.cards.isEmpty()
        empty.visibility = if (isEmpty) VISIBLE else GONE
        pager.visibility = if (isEmpty) GONE else VISIBLE
        dots.visibility = if (isEmpty) GONE else VISIBLE

        if (layout != shownLayout) {
            homeAddress = state.homeAddress
            rebuildPages(layout)
            shownLayout = layout
        }
        for ((place, card) in cards) {
            card.show(cardContent(place.deviceId, findDevice(state.devices, place.deviceId), state.home, place.size))
            // While the connection is down, every card shows its last known state, dimmed.
            card.stale = reconnecting
        }

        val pages = pageCount(layout)
        for ((place, card) in cards) card.setPictureActive(place.page == state.page)
        pager.showPage(state.page, animate = shownPage >= 0)
        shownPage = state.page
        showDots(if (pages > 1) pages else 0, state.page)

        toast.text = state.toast.orEmpty()
        toast.visibility = if (state.toast == null) GONE else VISIBLE
    }

    private var homeAddress = ""

    private fun rebuildPages(layout: Layout) {
        val old = cards.toMap()
        cards.clear()
        val grids = (0 until pageCount(layout)).map { page ->
            GridView(context).apply {
                setPadding(dp(24f), dp(8f), dp(24f), dp(32f))
                setCards(cardsOnPage(layout, page).map { place ->
                    val card = old[place]?.also { (it.parent as? GridView)?.removeView(it) } ?: newCard(place)
                    cards[place] = card
                    place to card
                })
            }
        }
        pager.setPages(grids)
    }

    private fun newCard(place: CardPlace) = CardView(
        context,
        place.size,
        onTap = { callbacks.onCardTap(place.deviceId) },
        onHold = { callbacks.onCardHold(place.deviceId) },
        makePicture = { CameraPictureView(context, scope, pictures, homeAddress) },
    )

    private fun showDots(count: Int, current: Int) {
        if (dots.childCount != count) {
            dots.removeAllViews()
            repeat(count) {
                dots.addView(View(context), LinearLayout.LayoutParams(dp(10f), dp(10f)).apply { leftMargin = dp(6f); rightMargin = dp(6f) })
            }
        }
        for (i in 0 until dots.childCount) {
            dots.getChildAt(i).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (i == current) Theme.TEXT else Theme.SURFACE_RAISED)
            }
        }
    }

    /** For tests: the toast's words, or null. */
    internal val toastView: TextView get() = toast
}
