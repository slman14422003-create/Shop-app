package com.shopmanager.app.data.chat

import android.content.Context

object ChatLocalStore {

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("chat_local", Context.MODE_PRIVATE)

    fun hiddenIds(context: Context, chatId: String): Set<String> =
        prefs(context).getStringSet("hidden_$chatId", emptySet()).orEmpty().toSet()

    fun hide(context: Context, chatId: String, msgId: String): Set<String> {
        val next = hiddenIds(context, chatId) + msgId
        prefs(context).edit().putStringSet("hidden_$chatId", next).apply()
        return next
    }

    fun pinned(context: Context, me: String): Set<String> =
        prefs(context).getStringSet("pinned_$me", emptySet()).orEmpty().toSet()

    fun togglePin(context: Context, me: String, chatId: String): Set<String> {
        val cur = pinned(context, me)
        val next = if (chatId in cur) cur - chatId else cur + chatId
        prefs(context).edit().putStringSet("pinned_$me", next).apply()
        return next
    }
}
