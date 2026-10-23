package com.example.earthonline.data.network

import com.amap.api.maps.model.LatLng
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 高德 Web 服务（REST）封装。
 *
 * 为何不用原生 Search SDK：本项目地图用的是 AMap 3dmap 10.0.600，与 search 9.7.1
 * 两包内含 2 个同名类（`AMapUtilCoreApi` / `NetProxy`），dex 合并会报「重复类」直接阻断构建。
 * 而 Web 服务走标准 HTTPS，Retrofit 已是现存依赖，零新增 SDK、零构建风险，且功能等价：
 *  - 输入提示（POI 搜索）v3/assistant/inputtips
 *  - 驾车/步行/骑行路径规划 v3/direction/{driving|walking|bicycling}
 *
 * 密钥：AMap Web 服务 Key（与 Android 地图 Key 是两套，需在设置里单独填）。
 */
@Singleton
class AmapWebService @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://restapi.amap.com/")
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .client(OkHttpClient.Builder().build())
        .build()

    val api: AmapWebApi = retrofit.create(AmapWebApi::class.java)
}

interface AmapWebApi {
    @GET("v3/assistant/inputtips")
    suspend fun inputTips(
        @Query("key") key: String,
        @Query("keywords") keywords: String,
        @Query("city") city: String = "",
        @Query("datatype") datatype: String = "all"
    ): InputTipsResp

    /** 关键字搜索（POI v3）：城市留空 = 全国范围，避免「故宫」这类跨城 POI 被城市过滤掉 */
    @GET("v3/place/text")
    suspend fun placeText(
        @Query("key") key: String,
        @Query("keywords") keywords: String,
        @Query("city") city: String = "",
        @Query("offset") offset: Int = 10,
        @Query("page") page: Int = 1,
        @Query("extensions") extensions: String = "base"
    ): PlaceTextResp

    @GET("v3/direction/driving")
    suspend fun driving(
        @Query("key") key: String,
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("extensions") extensions: String = "base"
    ): RouteResp

    @GET("v3/direction/walking")
    suspend fun walking(
        @Query("key") key: String,
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("extensions") extensions: String = "base"
    ): RouteResp

    @GET("v3/direction/bicycling")
    suspend fun bicycling(
        @Query("key") key: String,
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("extensions") extensions: String = "base"
    ): RouteResp
}

@Serializable
data class InputTipsResp(
    val status: String = "",
    val info: String = "",
    val count: String = "0",
    val tips: List<Tip> = emptyList()
)

@Serializable
data class Tip(
    val id: String = "",
    val name: String = "",
    val district: String = "",
    val address: String = "",
    // 高德返回 "经度,纬度"
    val location: String = ""
)

/**
 * 关键字搜索（POI v3/place/text）响应。
 * status="1" 表示成功；"0" 时 [info] 是高德给出的具体原因（如 INVALID_USER_SCODE /
 * DAILY_QUERY_OVER_LIMIT），必须原样透给用户，否则排查无门。
 */
@Serializable
data class PlaceTextResp(
    val status: String = "",
    val info: String = "",
    val count: String = "0",
    val pois: List<PoiText> = emptyList()
)

@Serializable
data class PoiText(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val address: String = "",
    // 高德返回 "经度,纬度"
    val location: String = "",
    val adname: String = "",
    val cityname: String = ""
)

@Serializable
data class RouteResp(
    val status: String = "",
    val info: String = "",
    val route: RouteData? = null
)

@Serializable
data class RouteData(
    val paths: List<Path> = emptyList()
)

@Serializable
data class Path(
    val distance: String = "0",
    val duration: String = "0",
    val steps: List<Step> = emptyList()
)

@Serializable
data class Step(
    val instruction: String = "",
    val distance: String = "0",
    val duration: String = "0",
    // 高德 Web 路径：多个点以 ';' 分隔，每点 "经度,纬度"
    val polyline: String = ""
)

/** 把高德 Web 路径串解码成 LatLng 列表（非增量编码，直接按 ';' / ',' 拆分） */
fun decodeAmapPolyline(poly: String): List<LatLng> {
    if (poly.isBlank()) return emptyList()
    return poly.split(";").mapNotNull { seg ->
        val p = seg.split(",")
        if (p.size == 2) runCatching {
            LatLng(p[1].toDouble(), p[0].toDouble())
        }.getOrNull() else null
    }
}
