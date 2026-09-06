package com.lanraragi.reader.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lanraragi.reader.data.api.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

private val Context.favoritesDataStore by preferencesDataStore(name = "favorites")

class FavoritesRepository(private val context: Context) {

    private val dataStore = context.favoritesDataStore

    companion object {
        /** 旧版单集合键（F5 迁移前使用，迁移后保留兼容读取）。 */
        val KEY_LEGACY = stringSetPreferencesKey("favorite_arcids")
        val KEY_SCHEMA_VERSION = intPreferencesKey("schema_version")
        /** F5 多收藏列表 JSON：`{ "listName": ["arcid", ...] }`。 */
        val KEY_FAVORITES_JSON = stringPreferencesKey("favorites_json")
        const val DEFAULT_LIST = "默认收藏"
    }

    /** 多收藏列表 JSON 解析后的映射。 */
    private val allLists: Flow<Map<String, Set<String>>> = dataStore.data.map { p ->
        val json = p[KEY_FAVORITES_JSON]
        if (!json.isNullOrBlank()) {
            runCatching {
                val obj = ApiClient.json.decodeFromString<JsonObject>(json)
                obj.mapValues { (_, v) ->
                    v.jsonArray.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.toSet()
                }
            }.getOrDefault(readLegacy(p))
        } else {
            readLegacy(p)
        }
    }

    /** 默认收藏列表（向后兼容现有心跳 UI）。 */
    val favorites: Flow<Set<String>> = allLists.map { it[DEFAULT_LIST] ?: emptySet() }

    /** 全部收藏列表名称。 */
    val listIds: Flow<List<String>> = allLists.map { it.keys.toList().sorted() }

    val schemaVersion: Flow<Int> = dataStore.data.map { it[KEY_SCHEMA_VERSION] ?: 1 }

    init {
        DataMigration.register(4) { migrateFavoritesV4() }
    }

    suspend fun migrateIfNeeded() {
        try {
            val cur = dataStore.data.first()[KEY_SCHEMA_VERSION] ?: 1
            val next = DataMigration.runPending(context, cur)
            if (next > cur) dataStore.edit { it[KEY_SCHEMA_VERSION] = next }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    /** F5 v4 迁移：旧单一集合 KEY_LEGACY → favorites_json 的"默认收藏"列表。 */
    private suspend fun migrateFavoritesV4() {
        try {
            val prefs = dataStore.data.first()
            val legacy = prefs[KEY_LEGACY] ?: emptySet()
            if (legacy.isEmpty()) {
                // 无旧数据，确保新键存在空映射
                val existing = prefs[KEY_FAVORITES_JSON]
                if (existing.isNullOrBlank()) {
                    dataStore.edit { it[KEY_FAVORITES_JSON] = "{}" }
                }
                return
            }
            val map = mutableMapOf(DEFAULT_LIST to legacy)
            dataStore.edit {
                it[KEY_FAVORITES_JSON] = mapToJson(map)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    /** 读取指定列表。 */
    fun favoritesFor(listId: String): Flow<Set<String>> = allLists.map { it[listId] ?: emptySet() }

    suspend fun isFavorite(arcid: String, listId: String = DEFAULT_LIST): Boolean =
        favoritesFor(listId).first().contains(arcid)

    /** 在指定列表内切换收藏（listId 默认值保持旧调用点编译兼容） */
    suspend fun toggle(arcid: String, listId: String = DEFAULT_LIST) {
        val current = readCurrent()
        val set = (current[listId] ?: emptySet()).toMutableSet()
        if (!set.add(arcid)) set.remove(arcid)
        val updated = current.toMutableMap().apply { this[listId] = set }
        writeCurrent(updated)
    }

    /** 创建列表（幂等） */
    suspend fun createList(listId: String) {
        val current = readCurrent()
        if (listId in current) return
        writeCurrent(current + (listId to emptySet()))
    }

    /** 删除列表并移除其中所有收藏 */
    suspend fun deleteList(listId: String) {
        val current = readCurrent()
        if (listId !in current) return
        writeCurrent(current - listId)
    }

    /** 重命名列表 */
    suspend fun renameList(oldId: String, newId: String) {
        val current = readCurrent()
        val set = current[oldId] ?: return
        val updated = current.toMutableMap()
        updated.remove(oldId)
        updated[newId] = set
        writeCurrent(updated)
    }

    private suspend fun readCurrent(): Map<String, Set<String>> = allLists.first()
    private suspend fun writeCurrent(map: Map<String, Set<String>>) {
        dataStore.edit { it[KEY_FAVORITES_JSON] = mapToJson(map) }
    }

    private fun mapToJson(map: Map<String, Set<String>>): String =
        ApiClient.json.encodeToString(map.mapValues { it.value.toList() })

    private fun readLegacy(p: androidx.datastore.preferences.core.Preferences): Map<String, Set<String>> {
        val legacy = p[KEY_LEGACY] ?: emptySet()
        return if (legacy.isNotEmpty()) mapOf(DEFAULT_LIST to legacy) else emptyMap()
    }
}