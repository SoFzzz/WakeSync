package com.wakesync.core.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionRecord
import com.wakesync.core.model.SessionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wakesync_history")

/**
 * Repository for local persistence of session history using Preferences DataStore (local-storage-spec).
 * Adheres to data minimization by only persisting session aggregates without raw biomedical time-series.
 */
class SessionHistoryRepository(private val context: Context) {

    /**
     * Flow emitting the list of historical session records in descending chronological order.
     */
    val sessionHistory: Flow<List<SessionRecord>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "IOException reading session history: ${exception.message}", exception)
                emit(emptyPreferences())
            } else {
                Log.e(TAG, "Unexpected error reading session history: ${exception.message}", exception)
                throw exception
            }
        }
        .map { preferences ->
            val jsonString = preferences[KEY_HISTORY] ?: "[]"
            deserializeRecords(jsonString)
        }

    /**
     * Persists a new session record into local DataStore.
     */
    suspend fun recordSession(record: SessionRecord) {
        context.dataStore.edit { preferences ->
            val existingJson = preferences[KEY_HISTORY] ?: "[]"
            val currentList = deserializeRecords(existingJson).toMutableList()
            currentList.add(0, record) // Prepend newest first
            preferences[KEY_HISTORY] = serializeRecords(currentList)
            Log.d(TAG, "Persisted session record with ID: ${record.id}")
        }
    }

    /**
     * Clears all session history from DataStore.
     * Must only be triggered upon explicit user confirmation in UI.
     */
    suspend fun clearHistory() {
        context.dataStore.edit { preferences ->
            preferences[KEY_HISTORY] = "[]"
            Log.i(TAG, "All session history cleared")
        }
    }

    /**
     * Updates an existing session record with its generated AI insight text (RF-INS-03).
     *
     * @param startTimestamp The unique timestamp identifying the session.
     * @param insightText The generated insight text (validated to <=140 chars).
     */
    suspend fun updateInsight(startTimestamp: Long, insightText: String) {
        context.dataStore.edit { preferences ->
            val existingJson = preferences[KEY_HISTORY] ?: "[]"
            val records = deserializeRecords(existingJson).map { record ->
                if (record.startTimestamp == startTimestamp) {
                    record.copy(insightText = insightText)
                } else {
                    record
                }
            }
            preferences[KEY_HISTORY] = serializeRecords(records)
            Log.d(TAG, "Updated insight for session at $startTimestamp")
        }
    }

    companion object {
        private const val TAG = "SessionHistoryRepo"
        private val KEY_HISTORY = stringPreferencesKey("session_history_json")

        internal fun serializeRecords(records: List<SessionRecord>): String {
            val array = JSONArray()
            for (record in records) {
                val obj = JSONObject().apply {
                    put("id", record.id)
                    put("sessionType", record.sessionType.name)
                    put("startTimestamp", record.startTimestamp)
                    put("durationSeconds", record.durationSeconds)
                    if (record.restLatencySeconds != null) {
                        put("restLatencySeconds", record.restLatencySeconds)
                    }
                    put("outcome", record.outcome.name)
                    if (record.insightText != null) {
                        put("insightText", record.insightText)
                    }
                }
                array.put(obj)
            }
            return array.toString()
        }

        internal fun deserializeRecords(json: String): List<SessionRecord> {
            val list = mutableListOf<SessionRecord>()
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        SessionRecord(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            sessionType = SessionType.valueOf(obj.getString("sessionType")),
                            startTimestamp = obj.getLong("startTimestamp"),
                            durationSeconds = obj.getInt("durationSeconds"),
                            restLatencySeconds = if (obj.has("restLatencySeconds")) obj.getInt("restLatencySeconds") else null,
                            outcome = SessionOutcome.valueOf(obj.getString("outcome")),
                            insightText = if (obj.has("insightText") && !obj.isNull("insightText")) obj.getString("insightText") else null
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deserializing session records: ${e.message}", e)
            }
            return list
        }
    }
}
