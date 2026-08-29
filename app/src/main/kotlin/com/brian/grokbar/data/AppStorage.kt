package com.brian.grokbar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class AppStorage(private val context: Context) {

    object Keys {
        val SESSION_COOKIE = stringPreferencesKey("session_cookie")
        val BEARER_TOKEN = stringPreferencesKey("bearer_token")
        val SNAPSHOT_JSON = stringPreferencesKey("snapshot_json")
    }

    fun getStringFlow(key: Preferences.Key<String>, default: String? = null): Flow<String?> =
        context.dataStore.data.map { it[key] ?: default }

    suspend fun setString(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun remove(key: Preferences.Key<*>) {
        context.dataStore.edit { it.remove(key) }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    val sessionCookie: Flow<String?> = getStringFlow(Keys.SESSION_COOKIE)
    suspend fun setSessionCookie(value: String) = setString(Keys.SESSION_COOKIE, value)
    suspend fun sessionCookieOnce(): String? = getStringFlow(Keys.SESSION_COOKIE).first()

    val bearerToken: Flow<String?> = getStringFlow(Keys.BEARER_TOKEN)
    suspend fun setBearerToken(value: String) = setString(Keys.BEARER_TOKEN, value)
    suspend fun bearerTokenOnce(): String? = getStringFlow(Keys.BEARER_TOKEN).first()

    val snapshotJson: Flow<String?> = getStringFlow(Keys.SNAPSHOT_JSON)
    suspend fun setSnapshotJson(value: String) = setString(Keys.SNAPSHOT_JSON, value)
    suspend fun snapshotJsonOnce(): String? = getStringFlow(Keys.SNAPSHOT_JSON).first()

    suspend fun snapshotOnce(): UsageSnapshot? = UsageSnapshot.fromJson(snapshotJsonOnce())

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(Keys.SESSION_COOKIE)
            it.remove(Keys.BEARER_TOKEN)
        }
    }
}
