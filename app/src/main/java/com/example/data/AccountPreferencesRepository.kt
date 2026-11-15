package com.example.data

import android.content.Context
import com.example.model.CloudflareAccount
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AccountPreferencesRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        seedDefaultAccountIfEmpty()
    }

    private fun seedDefaultAccountIfEmpty() {
        val raw = prefs.getString(KEY_ACCOUNTS, null)
        if (raw.isNullOrBlank()) {
            val defaultAccount = createDefaultAccount()
            saveAccountsInternal(listOf(defaultAccount), defaultAccount.id)
        }
    }

    fun createDefaultAccount(): CloudflareAccount = CloudflareAccount(
        id = DEFAULT_ACCOUNT_UUID,
        profileName = DEFAULT_PROFILE_NAME,
        accountId = DEFAULT_ACCOUNT_ID,
        apiToken = DEFAULT_API_TOKEN,
        isVerified = false,
        tokenStatusText = "",
        subdomain = ""
    )

    fun loadAccounts(): List<CloudflareAccount> {
        val raw = prefs.getString(KEY_ACCOUNTS, null)
        if (raw.isNullOrBlank()) {
            val def = createDefaultAccount()
            saveAccountsInternal(listOf(def), def.id)
            return listOf(def)
        }
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<CloudflareAccount>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    CloudflareAccount(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        profileName = obj.optString("profileName", "اکانت بدون نام"),
                        accountId = obj.optString("accountId", ""),
                        apiToken = obj.optString("apiToken", ""),
                        isVerified = obj.optBoolean("isVerified", false),
                        tokenStatusText = obj.optString("tokenStatusText", ""),
                        subdomain = obj.optString("subdomain", ""),
                        lastCheckedAt = obj.optString("lastCheckedAt", "")
                    )
                )
            }
            if (list.isEmpty()) {
                val def = createDefaultAccount()
                saveAccountsInternal(listOf(def), def.id)
                listOf(def)
            } else {
                list
            }
        } catch (_: Exception) {
            val def = createDefaultAccount()
            saveAccountsInternal(listOf(def), def.id)
            listOf(def)
        }
    }

    fun getActiveAccountId(accounts: List<CloudflareAccount>): String {
        val saved = prefs.getString(KEY_ACTIVE_ACCOUNT_ID, null)
        return if (saved != null && accounts.any { it.id == saved }) {
            saved
        } else {
            val first = accounts.firstOrNull()?.id ?: DEFAULT_ACCOUNT_UUID
            prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, first).apply()
            first
        }
    }

    fun setActiveAccountId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, id).apply()
    }

    fun saveAccounts(accounts: List<CloudflareAccount>, activeId: String) {
        saveAccountsInternal(accounts, activeId)
    }

    fun resetToDefaultAccount(): List<CloudflareAccount> {
        val def = createDefaultAccount()
        saveAccountsInternal(listOf(def), def.id)
        return listOf(def)
    }

    private fun saveAccountsInternal(accounts: List<CloudflareAccount>, activeId: String) {
        val arr = JSONArray()
        accounts.forEach { acc ->
            arr.put(
                JSONObject().apply {
                    put("id", acc.id)
                    put("profileName", acc.profileName)
                    put("accountId", acc.accountId)
                    put("apiToken", acc.apiToken)
                    put("isVerified", acc.isVerified)
                    put("tokenStatusText", acc.tokenStatusText)
                    put("subdomain", acc.subdomain)
                    put("lastCheckedAt", acc.lastCheckedAt)
                }
            )
        }
        prefs.edit()
            .putString(KEY_ACCOUNTS, arr.toString())
            .putString(KEY_ACTIVE_ACCOUNT_ID, activeId)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "setup_cloud_accounts_prefs"
        private const val KEY_ACCOUNTS = "cf_accounts_json"
        private const val KEY_ACTIVE_ACCOUNT_ID = "cf_active_account_id"

        const val DEFAULT_ACCOUNT_UUID = "default-main-cf-account"
        const val DEFAULT_PROFILE_NAME = "اکانت اصلی"
        const val DEFAULT_ACCOUNT_ID = ""
        const val DEFAULT_API_TOKEN = ""

        fun maskAccountId(accountId: String): String {
            if (accountId.length <= 10) return accountId
            return "${accountId.take(6)}••••${accountId.takeLast(4)}"
        }

        fun maskToken(token: String): String {
            if (token.length <= 12) return "••••••••••••"
            return "${token.take(8)}••••••••••••${token.takeLast(4)}"
        }
    }
}
