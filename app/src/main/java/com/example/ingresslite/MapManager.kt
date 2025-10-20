package com.example.ingresslite

import android.content.Context
import android.util.Log
import com.amap.api.maps.AMap
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject

class MapManager(private val context: Context, private val aMap: AMap) {

    private val markers = mutableListOf<Marker>()
    private val gson = Gson()

    // 解析JSON并显示Marker
    fun showPOIsFromJson(jsonData: String) {
        try {
            Log.d("MapManager", "开始解析POI数据: ${jsonData.length} 字符")

            // 使用JSONArray解析，更可靠
            val jsonArray = JSONArray(jsonData)
            val pois = mutableListOf<POI>()

            for (i in 0 until jsonArray.length()) {
                val poiJson = jsonArray.getJSONObject(i)
                val poi = parsePOIFromJson(poiJson)
                pois.add(poi)
            }

            Log.d("MapManager", "解析到 ${pois.size} 个POI")
            clearAllMarkers()
            showPOIsOnMap(pois)

        } catch (e: Exception) {
            Log.e("MapManager", "解析POI数据失败", e)
            e.printStackTrace()
        }
    }

    // 显示测试数据
    fun showTestPOIs() {
        val testJson = """
        [
            {"id": 1, "name": "西湖文化广场", "latitude": 30.266667, "longitude": 120.333333, "capturedBy": null},
            {"id": 2, "name": "杭州图书馆", "latitude": 30.267, "longitude": 120.334, "capturedBy": "player_a"},
            {"id": 3, "name": "武林广场", "latitude": 30.268, "longitude": 120.332, "capturedBy": null},
            {"id": 4, "name": "商业大厦", "latitude": 30.265, "longitude": 120.335, "capturedBy": "player_b"},
            {"id": 5, "name": "中心公园", "latitude": 30.269, "longitude": 120.333, "capturedBy": null}
        ]
        """.trimIndent()

        showPOIsFromJson(testJson)
    }

    private fun showPOIsOnMap(pois: List<POI>) {
        pois.forEach { poi ->
            val marker = createMarker(poi)
            marker?.let {
                markers.add(it)
                Log.d("MapManager", "添加标记: ${poi.name} (ID: ${poi.id})")
            }
        }
        Log.d("MapManager", "总共显示 ${markers.size} 个标记")
    }

    private fun createMarker(poi: POI): Marker? {
        return try {
            val latLng = LatLng(poi.latitude, poi.longitude)

            // 根据占领状态选择不同颜色的图标
            val (icon, status) = when {
                poi.owner == null -> {
                    Pair(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE), "中立")
                }
                poi.owner == "player_a" -> {
                    Pair(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE), "玩家A")
                }
                poi.owner == "player_b" -> {
                    Pair(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN), "玩家B")
                }
                else -> {
                    Pair(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED), "其他")
                }
            }

            val markerOptions = MarkerOptions()
                .position(latLng)
                .title(poi.name)
                .snippet("ID: ${poi.id} | 占领者: ${poi.owner ?: "无"}")
                .icon(icon)

            val marker = aMap.addMarker(markerOptions)
            // 使用 setObject 方法存储 POI ID
            marker.setObject(poi.id)

            Log.d("MapManager", "创建标记: ${poi.name}, 状态: $status, 位置: (${poi.latitude}, ${poi.longitude})")
            marker
        } catch (e: Exception) {
            Log.e("MapManager", "创建标记失败: ${poi.name}", e)
            null
        }
    }

    fun clearAllMarkers() {
        Log.d("MapManager", "清除所有标记，当前数量: ${markers.size}")
        markers.forEach {
            try {
                it.remove()
            } catch (e: Exception) {
                Log.e("MapManager", "移除标记失败", e)
            }
        }
        markers.clear()
        Log.d("MapManager", "清除完成")
    }

    // 从Marker中提取POI ID - 改进版本
    fun extractPOIIdFromMarker(marker: Marker): Int {
        // 首先尝试从object中获取
        try {
            val objectId = marker.getObject() as? Int
            if (objectId != null && objectId != -1) {
                return objectId
            }
        } catch (e: Exception) {
            Log.e("MapManager", "从object获取ID失败", e)
        }

        // 如果object中没有，从snippet中解析
        val snippet = marker.snippet ?: return -1
        return try {
            val idPattern = "ID: (\\d+)".toRegex()
            val matchResult = idPattern.find(snippet)
            matchResult?.groupValues?.get(1)?.toInt() ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    // 更新标记的占领状态 - 改进版本
    fun updateMarkerCaptureState(marker: Marker, capturedBy: String): Marker? {
        return try {
            val poiId = extractPOIIdFromMarker(marker)
            if (poiId == -1) {
                Log.e("MapManager", "无法从标记中提取POI ID")
                return null
            }

            Log.d("MapManager", "更新标记状态: POI ID=$poiId, 新占领者=$capturedBy")

            // 移除旧标记
            val position = marker.position
            val title = marker.title ?: "未知据点"

            marker.remove()
            markers.remove(marker)

            // 创建新标记
            val newMarker = createUpdatedMarker(poiId, title, position, capturedBy)
            newMarker?.let { markers.add(it) }

            Log.d("MapManager", "标记更新完成")
            newMarker
        } catch (e: Exception) {
            Log.e("MapManager", "更新标记状态失败", e)
            null
        }
    }

    private fun createUpdatedMarker(poiId: Int, title: String, position: LatLng, capturedBy: String): Marker? {
        return try {
            // 根据占领者选择颜色
            val iconColor = when {
                capturedBy == "player_a" -> BitmapDescriptorFactory.HUE_BLUE
                capturedBy == "player_b" -> BitmapDescriptorFactory.HUE_GREEN
                else -> BitmapDescriptorFactory.HUE_ORANGE
            }

            val markerOptions = MarkerOptions()
                .position(position)
                .title(title)
                .snippet("ID: $poiId | 占领者: $capturedBy")
                .icon(BitmapDescriptorFactory.defaultMarker(iconColor))

            val newMarker = aMap.addMarker(markerOptions)
            // 使用 setObject 方法存储 POI ID
            newMarker.setObject(poiId)

            Log.d("MapManager", "创建更新后的标记: $title, 占领者: $capturedBy")
            newMarker
        } catch (e: Exception) {
            Log.e("MapManager", "创建更新标记失败", e)
            null
        }
    }

    // 解析POI数据 - 改进版本，支持多种字段名
    private fun parsePOIFromJson(poiJson: JSONObject): POI {
        return try {
            val id = poiJson.getInt("id")
            val name = poiJson.getString("name")
            val latitude = poiJson.getDouble("latitude")
            val longitude = poiJson.getDouble("longitude")

            // 支持多种字段名：capturedBy 或 captured_by 或 owner
            val capturedBy = when {
                poiJson.has("capturedBy") -> poiJson.optString("capturedBy", null)
                poiJson.has("captured_by") -> poiJson.optString("captured_by", null)
                poiJson.has("owner") -> poiJson.optString("owner", null)
                else -> null
            }

            POI(id, name, latitude, longitude, capturedBy)
        } catch (e: Exception) {
            Log.e("MapManager", "解析单个POI失败", e)
            throw e
        }
    }

    // 获取标记数量（用于调试）
    fun getMarkerCount(): Int {
        return markers.size
    }

    // 根据ID查找标记
    fun findMarkerById(poiId: Int): Marker? {
        return markers.find { marker ->
            extractPOIIdFromMarker(marker) == poiId
        }
    }

    // 获取所有标记的ID列表
    fun getAllMarkerIds(): List<Int> {
        return markers.map { extractPOIIdFromMarker(it) }.filter { it != -1 }
    }

    // 调试方法：打印所有标记信息
    fun debugMarkers() {
        Log.d("MapManager", "=== 标记调试信息 ===")
        Log.d("MapManager", "总标记数: ${markers.size}")
        markers.forEachIndexed { index, marker ->
            val poiId = extractPOIIdFromMarker(marker)
            Log.d("MapManager", "标记 $index: ID=$poiId, 标题=${marker.title}, 描述=${marker.snippet}")
        }
        Log.d("MapManager", "==================")
    }
}