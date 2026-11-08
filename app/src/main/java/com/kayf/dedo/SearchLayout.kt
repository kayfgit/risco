package com.kayf.dedo

import kotlin.math.max

/**
 * How far from the bottom of the screen search's results end. Until the keyboard arrives they
 * already sit where its last known height puts them, so they don't jump when it lands.
 */
fun searchBottom(keyboardUp: Boolean, keyboardBottom: Int, barsBottom: Int, lastKeyboard: Int): Int =
    if (keyboardUp) keyboardBottom else max(barsBottom, lastKeyboard)
