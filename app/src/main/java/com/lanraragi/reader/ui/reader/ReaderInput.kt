package com.lanraragi.reader.ui.reader

import androidx.compose.ui.input.key.Key

/** A navigation intent shared by every physical reader input. */
enum class ReaderAction {
    Previous,
    Next,
}

/**
 * Maps physical reader controls to navigation. Volume buttons are deliberately
 * opt-in; keyboard navigation must remain available when volume navigation is off.
 */
fun readerActionForKey(key: Key, volumeKeysEnabled: Boolean): ReaderAction? = when (key) {
    Key.DirectionLeft, Key.DirectionUp, Key.PageUp -> ReaderAction.Previous
    Key.DirectionRight, Key.DirectionDown, Key.PageDown -> ReaderAction.Next
    Key.VolumeUp -> if (volumeKeysEnabled) ReaderAction.Previous else null
    Key.VolumeDown -> if (volumeKeysEnabled) ReaderAction.Next else null
    else -> null
}
