package com.viewcompose.ui.overlay

/**
 * Keyed-tag slot shared by renderer and overlay-host modules for anchor metadata.
 *
 * The hexadecimal value is part of the cross-module Android interoperability contract and must
 * remain stable for independently versioned renderer and overlay artifacts.
 */
const val OVERLAY_ANCHOR_TAG_KEY: Int = 0x5643A001
