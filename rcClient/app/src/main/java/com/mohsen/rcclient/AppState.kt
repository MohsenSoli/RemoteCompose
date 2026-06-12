package com.mohsen.rcclient

import android.content.Context

/*
 * Host-side mirrors of document state: each in-document mutation also fires a
 * host action; we record it here and send it back as a query parameter so the
 * server bakes the right initial value into the next document.
 */

/** Process-lifetime state: survives navigation, resets when the app dies. */
object SessionState {
    var waves: Int = 0
}

/** Durable state: favorite user ids, kept in SharedPreferences. */
class FavoritesStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun ids(): Set<Int> =
        prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull(String::toIntOrNull).toSet()

    fun isFavorite(id: Int): Boolean = id in ids()

    fun toggle(id: Int) {
        val ids = ids().toMutableSet()
        if (!ids.add(id)) ids.remove(id)
        prefs.edit().putStringSet(KEY, ids.map(Int::toString).toSet()).apply()
    }

    private companion object {
        const val KEY = "ids"
    }
}
