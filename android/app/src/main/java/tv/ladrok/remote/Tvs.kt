// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

/** Which Bluetooth devices the Remote offers as TVs, and in what order. */
object Tvs {
    // Bluetooth class of device, major Audio/Video: set-top box (Ladrok TV), video monitor, display and loudspeaker.
    private val TV_CLASSES = setOf(0x0424, 0x0438, 0x043C)

    /** Whether a paired device's class of device (as BluetoothClass reports it) is a TV or a TV box. */
    fun looksLikeTv(classOfDevice: Int): Boolean = (classOfDevice and 0x1FFC) in TV_CLASSES

    /** The TV to connect to without asking: the last one, if the phone is still paired with it. */
    fun toReconnect(paired: List<Tv>, lastAddress: String?): Tv? = paired.firstOrNull { it.address == lastAddress }

    /** [found] added to the TVs found so far, or, when found again, given its newest name. */
    fun found(tvs: List<Tv>, found: Tv): List<Tv> =
        if (tvs.any { it.address == found.address }) tvs.map { if (it.address == found.address) found else it } else tvs + found

    /** TVs to choose from: the last one first, then by name. */
    fun ordered(tvs: List<Tv>, lastAddress: String?): List<Tv> =
        tvs.sortedWith(compareBy<Tv> { it.address != lastAddress }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}
