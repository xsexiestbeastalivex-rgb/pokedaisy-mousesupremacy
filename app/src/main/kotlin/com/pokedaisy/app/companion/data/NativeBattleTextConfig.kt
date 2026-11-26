package com.pokedaisy.app.companion.data

/** Read-only text-printer globals, enabled only by an independently bound exact ROM profile. */
data class NativeBattleTextConfig(
    val printers: Long,
    val flags: Long,
    val disabled: Long,
)
