package ru.besuglovs.fitness.data

import org.json.JSONArray
import org.json.JSONObject

internal fun encodeStringMap(map: Map<Long, String>): JSONObject {
    val obj = JSONObject()
    map.forEach { (k, v) -> obj.put(k.toString(), v) }
    return obj
}

internal fun decodeStringMap(obj: JSONObject?): Map<Long, String> {
    if (obj == null) return emptyMap()
    val out = mutableMapOf<Long, String>()
    for (key in obj.keys()) {
        out[key.toLong()] = obj.optString(key, "")
    }
    return out
}

internal fun encodeDoubleMap(map: Map<Long, Double>): JSONObject {
    val obj = JSONObject()
    map.forEach { (k, v) -> obj.put(k.toString(), v) }
    return obj
}

internal fun decodeDoubleMap(obj: JSONObject?): Map<Long, Double> {
    if (obj == null) return emptyMap()
    val out = mutableMapOf<Long, Double>()
    for (key in obj.keys()) {
        out[key.toLong()] = obj.optDouble(key, 0.0)
    }
    return out
}

internal fun encodeIntListMap(map: Map<Long, List<Int>>): JSONObject {
    val obj = JSONObject()
    map.forEach { (k, v) ->
        val arr = JSONArray()
        v.forEach { arr.put(it) }
        obj.put(k.toString(), arr)
    }
    return obj
}

internal fun decodeIntListMap(obj: JSONObject?): Map<Long, MutableList<Int>> {
    if (obj == null) return emptyMap()
    val out = mutableMapOf<Long, MutableList<Int>>()
    for (key in obj.keys()) {
        val arr = obj.optJSONArray(key) ?: continue
        out[key.toLong()] = mutableListOf<Int>().apply {
            for (i in 0 until arr.length()) add(arr.getInt(i))
        }
    }
    return out
}

internal fun encodeIntMap(map: Map<Long, Int>): JSONObject {
    val obj = JSONObject()
    map.forEach { (k, v) -> obj.put(k.toString(), v) }
    return obj
}

internal fun decodeIntMap(obj: JSONObject?): Map<Long, Int> {
    if (obj == null) return emptyMap()
    val out = mutableMapOf<Long, Int>()
    for (key in obj.keys()) {
        out[key.toLong()] = obj.optInt(key, 0)
    }
    return out
}

internal fun decodeLongArray(arr: JSONArray?): List<Long> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).map { arr.getLong(it) }
}