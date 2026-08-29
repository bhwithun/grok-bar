package com.brian.grokbar.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class UsageRepository(
    private val storage: AppStorage,
    private val api: GrokUsageApi = GrokUsageApi()
) {
    private val mutex = Mutex()

    val snapshot: Flow<UsageSnapshot> = storage.snapshotJson.map { raw ->
        UsageSnapshot.fromJson(raw) ?: UsageSnapshot.demo()
    }

    val hasSession: Flow<Boolean> = storage.sessionCookie.map { cookie ->
        !cookie.isNullOrBlank()
    }

    suspend fun load(): UsageSnapshot = storage.snapshotOnce() ?: UsageSnapshot.demo()

    suspend fun signedIn(): Boolean {
        return !storage.sessionCookieOnce().isNullOrBlank() ||
            !storage.bearerTokenOnce().isNullOrBlank()
    }

    suspend fun saveCookie(cookie: String) {
        storage.setSessionCookie(cookie)
    }

    suspend fun saveBearer(token: String) {
        storage.setBearerToken(token.trim())
    }

    suspend fun saveSnapshot(snapshot: UsageSnapshot) {
        storage.setSnapshotJson(snapshot.toJson())
    }

    suspend fun signOut() {
        storage.clearSession()
        storage.setSnapshotJson(UsageSnapshot.demo().toJson())
    }

    suspend fun refresh(force: Boolean = true): UsageSnapshot = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = storage.snapshotOnce()
            if (!force && current != null && !current.isDemo &&
                System.currentTimeMillis() - current.fetchedAtEpochMs < STALE_MS
            ) {
                return@withContext current
            }
            val auth = GrokUsageApi.Auth(
                cookie = storage.sessionCookieOnce(),
                bearer = storage.bearerTokenOnce()
            )
            if (!auth.hasAny) {
                val demo = current?.takeIf { it.isDemo } ?: UsageSnapshot.demo()
                storage.setSnapshotJson(demo.toJson())
                return@withContext demo
            }
            try {
                val fresh = api.fetch(auth)
                storage.setSnapshotJson(fresh.toJson())
                fresh
            } catch (e: Exception) {
                val fallback = current?.copy(
                    errorMessage = e.message,
                    isDemo = current.isDemo
                ) ?: UsageSnapshot.demo().copy(errorMessage = e.message)
                storage.setSnapshotJson(fallback.toJson())
                fallback
            }
        }
    }

    companion object {
        private const val STALE_MS = 5 * 60 * 1000L

        @Volatile
        private var instance: UsageRepository? = null

        fun get(context: Context): UsageRepository {
            return instance ?: synchronized(this) {
                instance ?: UsageRepository(AppStorage(context.applicationContext)).also {
                    instance = it
                }
            }
        }
    }
}
