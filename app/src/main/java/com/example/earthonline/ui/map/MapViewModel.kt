package com.example.earthonline.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.AppConfig
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.data.local.entity.LocationEntity
import com.example.earthonline.data.network.AmapWebService
import com.example.earthonline.data.repository.LocationRepository
import com.example.earthonline.util.uid
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * 足迹地图 ViewModel（对应 HTML 的 state.locations）。
 * 经纬度来自地图长按或设备定位；tags 以 List<String> JSON 持久化。
 *
 * v1.0.7：移除「POI 搜索 / 路径规划」链路，只保留「路径点记录」。
 * v1.0.6.4：POI 搜索走高德 Web 服务 inputtips（需自填 Web Key）。
 * v1.0.6.7（本次）：POI 搜索定为高德 **Web 服务 REST v3/place/text**，
 * Key 优先取内置的 [AppConfig.AMAP_WEB_KEY]（开箱即用），落回「设置 → 通用」自填值。
 * 相比原生 Search SDK：不受 Android Key 的 SHA1 绑定 / 搜索服务配额门槛限制，
 * 且失败时 status+info 直接给出可读原因，便于定位。
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val repo: LocationRepository,
    private val json: Json,
    private val amapWeb: AmapWebService,
    private val settings: SettingsDataStore
) : ViewModel() {

    val locations = repo.observeAll()

    /** 最近一次 POI 搜索错误（null = 正常）。非 1000 错误码必须透出给用户，不能再静默吞掉 */
    private val _poiError = MutableStateFlow<String?>(null)
    val poiError: StateFlow<String?> = _poiError.asStateFlow()

    /** 记录一处足迹（路径点）。 */
    fun addLocation(
        name: String,
        lat: Double,
        lng: Double,
        date: String,
        note: String?,
        tags: List<String>
    ) {
        viewModelScope.launch {
            repo.insert(
                LocationEntity(
                    id = uid("loc"),
                    name = name.take(60),
                    lat = lat,
                    lng = lng,
                    date = date,
                    note = note?.take(500),
                    tagsJson = if (tags.isEmpty()) null
                    else Json.encodeToString(ListSerializer(String.serializer()), tags)
                )
            )
        }
    }

    fun deleteLocation(loc: LocationEntity) {
        viewModelScope.launch { repo.delete(loc.id) }
    }

    /**
     * POI 关键字搜索 —— 走高德 **Web 服务 REST**（v3/place/text），而不是原生 Search SDK。
     *
     * 为何换回 Web 服务：原生 SDK 的 Key 是「Android 平台 Key」，还需与签名 SHA1 绑定、
     * 单独开通搜索服务，任一环节缺失都会回调 10009 直接静默失败；Web 服务 Key 只校验
     * Key 本身，绑定成本低、报错原因可读（status/info 字段）。
     *
     * Key 取值优先级：内置常量 [AppConfig.AMAP_WEB_KEY] → 「设置 → 通用」用户自填的 Key。
     * 两者都空时给出明确配置提示；city 传空串表示**全国范围**（传本城市会把「故宫」这类
     * 跨城 POI 过滤掉）。任何异常都降级为空列表 + 错误文案，绝不崩。
     */
    suspend fun searchPoi(keyword: String): List<LocationSuggestion> {
        val kw = keyword.trim()
        if (kw.isBlank()) {
            _poiError.value = null
            return emptyList()
        }
        return withContext(Dispatchers.IO) {
            val key = AppConfig.AMAP_WEB_KEY.trim().ifBlank {
                runCatching { settings.amapWebKey.first() }.getOrDefault("").trim()
            }
            if (key.isBlank()) {
                _poiError.value = "尚未配置高德 Web 服务 Key：请让开发者补充，或到「设置 → 通用」自行填写。"
                return@withContext emptyList()
            }
            try {
                val resp = amapWeb.api.placeText(key = key, keywords = kw, city = "")
                if (resp.status != "1") {
                    // status="0"：info 是高德的原始原因（INVALID_USER_SCODE / DAILY_QUERY_OVER_LIMIT 等）
                    Log.w("MapViewModel", "placeText failed: status=${resp.status}, info=${resp.info}")
                    _poiError.value = friendlyWebError(resp.info, resp.status)
                    return@withContext emptyList()
                }
                _poiError.value = null
                resp.pois.mapNotNull { poi ->
                    val parts = poi.location.split(",")
                    if (parts.size != 2) return@mapNotNull null
                    val lng = parts[0].toDoubleOrNull() ?: return@mapNotNull null
                    val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                    LocationSuggestion(
                        name = poi.name,
                        district = listOf(poi.cityname, poi.adname).filter { it.isNotBlank() }.joinToString(" · "),
                        address = poi.address,
                        lat = lat,
                        lng = lng
                    )
                }
            } catch (t: Throwable) {
                Log.w("MapViewModel", "placeText threw", t)
                _poiError.value = when (t) {
                    is java.net.UnknownHostException, is java.net.ConnectException -> "无网络连接，请检查网络后重试"
                    is java.net.SocketTimeoutException -> "请求超时，请稍后重试"
                    else -> "搜索异常：${t.message ?: t.javaClass.simpleName}"
                }
                emptyList()
            }
        }
    }

    /** 高德 Web 服务错误 → 可操作的中文提示（重点覆盖 Key 权限类问题） */
    private fun friendlyWebError(info: String, status: String): String {
        val upper = info.uppercase()
        return when {
            upper.contains("INVALID_USER_SCODE") || upper.contains("INVALID_USER_KEY") ->
                "Key 校验失败（$info）：请到高德开放平台确认该 Key 的「服务平台」为 Web 服务，且未被禁用。"
            upper.contains("DAILY_QUERY_OVER_LIMIT") || upper.contains("OVER_LIMIT") ->
                "Key 配额超限（$info）：今日搜索次数已用完，请明日再试或到控制台提额。"
            upper.contains("USERKEY_PLAT_NOMATCH") ->
                "Key 类型不匹配（$info）：POI 搜索需使用「Web 服务」类型的 Key，不能用 Android 地图 Key。"
            upper.contains("INVALID_PARAMS") ->
                "请求参数有误（$info）：请换个关键字重试。"
            info.isNotBlank() -> "搜索失败（$status / $info）"
            else -> "搜索失败（状态码 $status）：请检查网络或 Key 配置。"
        }
    }
}

/** POI 搜索候选（高德 Web 服务 v3/place/text 结果的轻量映射） */
data class LocationSuggestion(
    val name: String,
    val district: String,
    val address: String,
    val lat: Double,
    val lng: Double
)
