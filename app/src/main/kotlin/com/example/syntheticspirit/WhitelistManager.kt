package com.example.syntheticspirit

import android.content.Context
import android.content.SharedPreferences

class WhitelistManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("user_whitelist", Context.MODE_PRIVATE)

    fun isWhitelisted(domain: String): Boolean {
        return prefs.getBoolean(domain, false)
    }

    fun setWhitelisted(domain: String, isWhitelisted: Boolean) {
        with(prefs.edit()) {
            if (isWhitelisted) {
                putBoolean(domain, true)
            } else {
                remove(domain)
            }
            apply()
        }
    }

    fun getAllWhitelistedDomains(): Set<String> {
        return prefs.all.keys
    }
}
