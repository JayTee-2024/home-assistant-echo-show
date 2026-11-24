package net.badacid.homeassist.echoshow.core

/** How much of the grid a card takes. `savedName` is the word used in saved layouts. */
enum class CardSize(val savedName: String) {
    SMALL("small"),
    WIDE("wide"),
    LARGE("large"),
    ;

    companion object {
        fun fromSavedName(name: String): CardSize? = entries.firstOrNull { it.savedName == name }
    }
}

/** What the device rules need to know about one kind of entity. Each device type is one of these. */
interface PartType {
    /** A short name for the type, used in saved data and as the folder name: 'light'. */
    val id: String

    /** The Home Assistant entity types this covers: ['switch', 'input_boolean']. */
    val domains: List<String>

    /**
     * Decides which part leads a device's card: the part whose type has the lowest number.
     * A doorbell's camera (10) leads over its battery sensor (100).
     */
    val mainOrder: Int

    /** The size of the card when a part of this type leads it. */
    val cardSize: CardSize

    /**
     * False for an entity of the right type that must not be shown, such as a cover that is a garage door.
     * Entities this refuses are treated as not supported.
     */
    fun supports(entity: EntityState): Boolean
}
