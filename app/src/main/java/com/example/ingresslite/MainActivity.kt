package com.example.ingresslite

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import com.google.gson.Gson
import kotlin.random.Random
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import javax.net.ssl.TrustManager
import java.security.cert.X509Certificate

class MainActivity : AppCompatActivity(), AMapLocationListener {

    private lateinit var mMapView: MapView
    private var aMap: AMap? = null
    private lateinit var mapManager: MapManager

    // 默认位置 - 浙商大
    private val defaultLocation = LatLng(30.3152, 120.3715)
    private var hasValidLocation = false
    private var currentUserLocation: LatLng = defaultLocation
    private var isFirstLocation = true

    // 切换到真实服务器模式
    private var isSimulationMode = false

    // 服务器配置 - 使用ngrok公网地址
    private val SERVER_BASE_URL = "https://squally-nonhuman-lawanna.ngrok-free.dev"

    // 定位权限请求码
    private val LOCATION_PERMISSION_REQUEST_CODE = 100

    // 高德定位客户端
    private lateinit var locationClient: AMapLocationClient
    private lateinit var locationOption: AMapLocationClientOption

    // 修复HTTPS证书问题的OkHttpClient
    private val client = createUnsafeOkHttpClient()

    private fun createUnsafeOkHttpClient(): OkHttpClient {
        try {
            // 创建信任所有证书的TrustManager
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            // 创建SSLContext
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            // 创建OkHttpClient.Builder
            return OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            Log.e("OkHttp", "创建OkHttpClient失败", e)
            // 如果失败，回退到普通配置
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .hostnameVerifier { _, _ -> true }
                .build()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initPrivacy()
        setContentView(R.layout.activity_main)

        // 先检查权限再初始化
        checkAndRequestLocationPermissions()
    }

    private fun checkAndRequestLocationPermissions() {
        val fineLocationPermission = Manifest.permission.ACCESS_FINE_LOCATION
        val coarseLocationPermission = Manifest.permission.ACCESS_COARSE_LOCATION

        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, fineLocationPermission) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(fineLocationPermission)
        }
        if (ContextCompat.checkSelfPermission(this, coarseLocationPermission) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(coarseLocationPermission)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            initMapAndLocation()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showToastOnMainThread("定位权限已获取")
                initMapAndLocation()
            } else {
                showToastOnMainThread("您拒绝了定位权限，将无法使用定位功能。")
                initMapWithoutLocation()
            }
        }
    }

    private fun initMapAndLocation() {
        initMap(null)
        initLocation()

        mMapView.postDelayed({
            testServerConnection()
        }, 1000)
    }

    private fun initMapWithoutLocation() {
        initMap(null)

        mMapView.postDelayed({
            testServerConnection()
        }, 1000)
    }

    private fun initPrivacy() {
        try {
            MapsInitializer.updatePrivacyShow(this, true, true)
            MapsInitializer.updatePrivacyAgree(this, true)
            AMapLocationClient.updatePrivacyShow(this, true, true)
            AMapLocationClient.updatePrivacyAgree(this, true)
            Log.d("Privacy", "高德地图隐私合规配置完成")
        } catch (e: Exception) {
            Log.e("Privacy", "隐私合规配置失败", e)
        }
    }

    private fun initMap(savedInstanceState: Bundle?) {
        mMapView = findViewById(R.id.mapView)

        try {
            mMapView.onCreate(savedInstanceState)
            aMap = mMapView.map

            aMap?.let {
                mapManager = MapManager(this, it)
            }

            // 启用定位按钮和定位功能
            aMap?.uiSettings?.isMyLocationButtonEnabled = true
            aMap?.isMyLocationEnabled = true

            // 设置默认位置为浙商大
            aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15f))

            // 添加默认位置标记
            aMap?.addMarker(
                MarkerOptions()
                    .position(defaultLocation)
                    .title("默认位置")
                    .snippet("浙商大")
                    .icon(com.amap.api.maps.model.BitmapDescriptorFactory.defaultMarker(
                        com.amap.api.maps.model.BitmapDescriptorFactory.HUE_BLUE))
            )

            aMap?.setOnMarkerClickListener { marker ->
                marker.showInfoWindow()
                showCaptureDialog(marker)
                true
            }

            aMap?.setOnMapLoadedListener {
                Log.d("Map", "地图加载完成")
            }

            // 设置定位变化监听器 - 只更新位置数据，不自动移动地图
            aMap?.setOnMyLocationChangeListener { location ->
                if (location != null && location.latitude != 0.0 && location.longitude != 0.0) {
                    Log.d("Location", "位置更新: (${location.latitude}, ${location.longitude})")

                    if (!hasValidLocation && isFirstLocation) {
                        // 第一次获取到有效位置时，只显示一次提示
                        showToastOnMainThread("定位成功！点击定位按钮可移动到您的位置")
                        isFirstLocation = false
                    }

                    hasValidLocation = true
                    currentUserLocation = LatLng(location.latitude, location.longitude)
                    Log.d("Location", "当前位置更新: (${currentUserLocation.latitude}, ${currentUserLocation.longitude})")
                }
            }

            showToastOnMainThread("地图初始化成功！默认位置：浙商大")
        } catch (e: Exception) {
            showToastOnMainThread("地图初始化失败: ${e.message}")
            e.printStackTrace()
        }
    }

    // 移动到当前位置
    private fun moveToCurrentLocation() {
        if (hasValidLocation) {
            aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentUserLocation, 16f))
            showToastOnMainThread("已移动到您的位置")
        } else {
            aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15f))
            showToastOnMainThread("定位失败，已移动到默认位置：浙商大")

            // 尝试重新启动定位
            if (::locationClient.isInitialized) {
                locationClient.stopLocation()
                locationClient.startLocation()
                Log.d("LocationButton", "重新尝试启动定位...")
            }
        }
    }

    private fun initLocation() {
        try {
            // 初始化定位
            locationClient = AMapLocationClient(applicationContext)
            locationOption = AMapLocationClientOption()

            // 设置定位模式为高精度模式
            locationOption.locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy

            // 设置定位间隔，单位毫秒
            locationOption.interval = 15000 // 15秒定位一次，减少频繁定位

            // 设置是否返回地址信息
            locationOption.isNeedAddress = true

            // 设置是否强制刷新WIFI
            locationOption.isWifiActiveScan = true

            // 设置是否允许模拟位置
            locationOption.isMockEnable = false

            // 设置定位参数
            locationClient.setLocationOption(locationOption)

            // 设置定位监听
            locationClient.setLocationListener(this)

            // 启动定位
            locationClient.startLocation()

            Log.d("Location", "定位服务初始化成功")
        } catch (e: Exception) {
            Log.e("Location", "定位服务初始化失败", e)
        }
    }

    override fun onLocationChanged(location: AMapLocation?) {
        if (location != null) {
            if (location.errorCode == 0) {
                // 定位成功
                val latLng = LatLng(location.latitude, location.longitude)

                if (!hasValidLocation) {
                    // 第一次获取到有效位置
                    hasValidLocation = true
                    currentUserLocation = latLng
                    Log.d("Location", "首次定位成功: (${latLng.latitude}, ${latLng.longitude})")
                } else {
                    // 更新当前位置
                    currentUserLocation = latLng
                    Log.d("Location", "位置更新: (${latLng.latitude}, ${latLng.longitude})")
                }
            } else {
                // 定位失败
                Log.e("Location", "定位失败，错误码: ${location.errorCode}, 错误信息: ${location.errorInfo}")
            }
        }
    }

    // 处理定位按钮点击事件
    private fun handleLocationButtonClick() {
        moveToCurrentLocation()
    }

    private fun testServerConnection() {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                Log.d("Network", "开始测试服务器连接...")

                val result = withContext(Dispatchers.IO) {
                    val url = "$SERVER_BASE_URL/hello"
                    fetchDataFromServerWithDetails(url)
                }

                if (result != null) {
                    Log.d("Network", "服务器连接成功: $result")
                    fetchRealPOIs()
                } else {
                    showToastOnMainThread("服务器连接失败，切换到模拟模式")
                    enableSimulationMode()
                }
            } catch (e: Exception) {
                Log.e("Network", "连接测试异常", e)
                showToastOnMainThread("网络异常，切换到模拟模式")
                enableSimulationMode()
            }
        }
    }

    private fun enableSimulationMode() {
        isSimulationMode = true
        showToastOnMainThread("切换到模拟模式")
        showSimulatedData()
    }

    private fun showSimulatedData() {
        try {
            // 使用浙商大模拟数据
            val simulatedPOIs = listOf(
                // 校内核心区
                POI(1, "浙商大-图书馆", 30.3152, 120.3715, null),
                POI(2, "浙商大-体育中心", 30.3168, 120.3730, "player_a"),
                POI(3, "浙商大-综合楼", 30.3145, 120.3700, null),
                POI(4, "浙商大-学生活动中心", 30.3135, 120.3725, "player_b"),
                POI(5, "浙商大-田径场", 30.3175, 120.3745, null),
                // 学校周边近距离区
                POI(6, "文泽路地铁站-C口", 30.3120, 120.3708, "player_a"),
                POI(7, "福雷德广场", 30.3180, 120.3680, null),
                POI(8, "高沙商业街", 30.3195, 120.3710, "player_b"),
                POI(9, "学源街公交站", 30.3150, 120.3780, null),
                POI(10, "工商大学云滨公寓", 30.3110, 120.3735, null),
                // 中距离区
                POI(11, "金沙湖公园-北门", 30.3090, 120.3800, null),
                POI(12, "中国计量大学-东门", 30.3220, 120.3650, "player_a"),
                POI(13, "杭州电子科技大学-体育馆", 30.3200, 120.3785, null),
                POI(14, "邵逸夫医院(下沙院区)", 30.3060, 120.3750, "player_b"),
                POI(15, "和达城", 30.3140, 120.3850, null),
                // 远距离区
                POI(16, "下沙银泰", 30.2980, 120.3500, "player_a"),
                POI(17, "金沙印象城", 30.3055, 120.3880, null),
                POI(18, "奥特莱斯广场", 30.2850, 120.3750, "player_b"),
                POI(19, "沿江湿地公园", 30.2950, 120.4000, null),
                POI(20, "杭州绕城高速-下沙出口", 30.3350, 120.3850, "player_b")
            )
            val jsonData = Gson().toJson(simulatedPOIs)
            mapManager.showPOIsFromJson(jsonData)
            Log.d("Map", "显示模拟数据（20个浙商大据点）")
        } catch (e: Exception) {
            Log.e("Map", "模拟数据加载失败")
        }
    }

    private fun showCaptureDialog(marker: Marker) {
        val currentOwner = getCurrentOwnerFromMarker(marker)
        val modeIndicator = if (isSimulationMode) "\n🔧 模拟模式" else "\n🌐 在线模式"

        val message = if (currentOwner == null) {
            "是否要占领 ${marker.title}？\n(当前：中立状态)$modeIndicator"
        } else {
            "是否要重新占领 ${marker.title}？\n(当前被 $currentOwner 占领)$modeIndicator"
        }

        val alertDialog = android.app.AlertDialog.Builder(this)
            .setTitle("占领据点")
            .setMessage(message)
            .setPositiveButton("占领") { dialog, which ->
                if (isSimulationMode) {
                    simulateCapture(marker)
                } else {
                    attemptCapturePOI(marker)
                }
            }
            .setNegativeButton("取消", null)
            .create()

        alertDialog.show()
    }

    private fun getCurrentOwnerFromMarker(marker: Marker): String? {
        val snippet = marker.snippet ?: return null
        return if (snippet.contains("占领者: 无") || snippet.contains("占领者: null")) {
            null
        } else {
            val pattern = "占领者: (\\w+)".toRegex()
            pattern.find(snippet)?.groupValues?.get(1)
        }
    }

    private fun attemptCapturePOI(marker: Marker) {
        // 使用 currentUserLocation
        performCaptureWithLocation(marker, currentUserLocation.latitude, currentUserLocation.longitude)
    }

    private fun performCaptureWithLocation(marker: Marker, latitude: Double, longitude: Double) {
        val poiId = extractPOIIdFromMarker(marker)
        if (poiId == -1) {
            showToastOnMainThread("无法获取据点ID")
            return
        }

        Log.d("LocationDebug", "发送给服务器的位置: ($latitude, $longitude)")

        GlobalScope.launch(Dispatchers.Main) {
            try {
                val loadingToast = Toast.makeText(this@MainActivity, "占领中...", Toast.LENGTH_SHORT)
                loadingToast.show()

                val jsonString = withContext(Dispatchers.IO) {
                    capturePOI(poiId, "player_b", latitude, longitude)
                }

                loadingToast.cancel()

                if (jsonString != null) {
                    try {
                        val gson = Gson()
                        val responseType = object : com.google.gson.reflect.TypeToken<ApiResponse<POI>>() {}.type

                        val apiResponse: ApiResponse<POI> = gson.fromJson(jsonString, responseType)

                        if (apiResponse.code == 0) {
                            showToastOnMainThread("${apiResponse.message}")
                            Log.d("Capture", "占领成功响应: $jsonString")
                            fetchRealPOIs()
                        } else {
                            showToastOnMainThread("${apiResponse.message}")
                            Log.e("Capture", "占领失败: ${apiResponse.message}")
                        }
                    } catch (e: Exception) {
                        Log.e("JSON_PARSE", "解析占领响应失败", e)
                        showToastOnMainThread("响应格式错误")
                    }
                } else {
                    showToastOnMainThread("占领请求失败")
                }
            } catch (e: Exception) {
                showToastOnMainThread("占领请求异常: ${e.message}")
                Log.e("Capture", "占领请求异常", e)
            }
        }
    }

    private fun simulateCapture(marker: Marker) {
        val poiId = extractPOIIdFromMarker(marker)
        if (poiId == -1) {
            showToastOnMainThread("无法获取据点ID")
            return
        }

        GlobalScope.launch(Dispatchers.Main) {
            val loadingToast = Toast.makeText(this@MainActivity, "占领中...", Toast.LENGTH_SHORT)
            loadingToast.show()

            delay(Random.nextLong(1000, 2000))
            loadingToast.cancel()

            val isSuccess = Random.nextFloat() < 0.7
            if (isSuccess) {
                updateMarkerForCapture(marker, "player_b")
                showToastOnMainThread("占领成功！")
            } else {
                val reason = listOf("距离太远", "据点已被占领", "网络超时").random()
                showToastOnMainThread(reason)
            }
        }
    }

    private fun updateMarkerForCapture(marker: Marker, owner: String) {
        val newMarker = mapManager.updateMarkerCaptureState(marker, owner)
        if (newMarker != null) {
            Log.d("Marker", "成功更新标记: ${marker.title} -> $owner")
        } else {
            showToastOnMainThread("更新标记失败")
        }
    }

    private fun fetchRealPOIs() {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                Log.d("Network", "开始获取真实据点数据...")
                val jsonString = withContext(Dispatchers.IO) {
                    val url = "$SERVER_BASE_URL/pois"
                    fetchDataFromServerWithDetails(url)
                }

                if (jsonString != null) {
                    try {
                        val gson = Gson()
                        val responseType = object : com.google.gson.reflect.TypeToken<ApiResponse<List<POI>>>() {}.type

                        val apiResponse: ApiResponse<List<POI>> = gson.fromJson(jsonString, responseType)

                        if (apiResponse.code == 0) {
                            val poiList = apiResponse.data
                            if (poiList != null) {
                                val poiListJson = gson.toJson(poiList)
                                mapManager.showPOIsFromJson(poiListJson)
                                showToastOnMainThread("成功加载 ${poiList.size} 个据点")
                                Log.d("Network", "从服务器加载了 ${poiList.size} 个据点")
                            } else {
                                Log.d("Network", "成功加载 0 个据点 (data为null)")
                            }
                        } else {
                            showToastOnMainThread("获取数据失败: ${apiResponse.message}")
                        }
                    } catch (e: Exception) {
                        Log.e("JSON_PARSE", "解析 /pois 响应失败", e)
                        showToastOnMainThread("数据格式错误，切换到模拟模式")
                        enableSimulationMode()
                    }
                } else {
                    showToastOnMainThread("获取据点数据失败，切换到模拟模式")
                    enableSimulationMode()
                }
            } catch (e: Exception) {
                showToastOnMainThread("获取数据异常，切换到模拟模式")
                enableSimulationMode()
            }
        }
    }

    private fun capturePOI(poiId: Int, userId: String, latitude: Double, longitude: Double): String? {
        return try {
            val url = "$SERVER_BASE_URL/pois/$poiId/capture"

            val json = JSONObject().apply {
                put("user_id", userId)
                put("latitude", latitude)
                put("longitude", longitude)
            }

            Log.d("CaptureDebug", "发送占领请求: POI ID=$poiId, 位置=($latitude, $longitude)")

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = RequestBody.create(mediaType, json.toString())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                Log.d("Network", "占领请求响应码: ${response.code}")
                Log.d("Network", "占领请求响应消息: ${response.message}")

                if (response.isSuccessful) {
                    response.body?.string().also { result ->
                        Log.d("Network", "占领成功响应体: $result")
                    }
                } else {
                    val errorBody = response.body?.string()
                    Log.e("Network", "占领失败: ${response.code} - $errorBody")
                    extractAndShowErrorMessage(errorBody, response.code)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("Network", "占领请求异常", e)
            showToastOnMainThread("网络请求异常: ${e.message}")
            null
        }
    }

    private fun extractAndShowErrorMessage(errorBody: String?, responseCode: Int) {
        try {
            if (errorBody != null) {
                val errorJson = JSONObject(errorBody)
                val detail = errorJson.optString("detail", "")

                if (detail.isNotEmpty()) {
                    showToastOnMainThread(detail)
                } else {
                    showToastOnMainThread("占领失败，错误码: $responseCode")
                }
            } else {
                showToastOnMainThread("占领失败，错误码: $responseCode")
            }
        } catch (e: Exception) {
            Log.e("ErrorExtraction", "解析错误信息失败", e)
            showToastOnMainThread("占领失败: ${errorBody ?: "未知错误"}")
        }
    }

    private fun fetchDataFromServerWithDetails(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .build()

            client.newCall(request).execute().use { response ->
                Log.d("Network", "请求URL: $url")
                Log.d("Network", "响应码: ${response.code}")
                Log.d("Network", "响应消息: ${response.message}")

                if (response.isSuccessful) {
                    response.body?.string().also { result ->
                        Log.d("Network", "响应体: $result")
                    }
                } else {
                    Log.e("Network", "请求失败: ${response.code} - ${response.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("Network", "网络请求异常: ${e.javaClass.simpleName} - ${e.message}")
            null
        }
    }

    private fun extractPOIIdFromMarker(marker: Marker): Int {
        return mapManager.extractPOIIdFromMarker(marker)
    }

    private fun countPOIs(jsonData: String): Int {
        return try {
            val jsonArray = JSONArray(jsonData)
            jsonArray.length()
        } catch (e: Exception) {
            0
        }
    }

    private fun showToastOnMainThread(message: String) {
        runOnUiThread {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showToastOnMainThread(message: String, duration: Int) {
        runOnUiThread {
            Toast.makeText(this@MainActivity, message, duration).show()
        }
    }

    override fun onResume() {
        super.onResume()
        mMapView.onResume()
        if (::locationClient.isInitialized) {
            locationClient.startLocation()
        }
    }

    override fun onPause() {
        super.onPause()
        mMapView.onPause()
        if (::locationClient.isInitialized) {
            locationClient.stopLocation()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mMapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        mMapView.onDestroy()
        if (::locationClient.isInitialized) {
            locationClient.onDestroy()
        }
    }
}