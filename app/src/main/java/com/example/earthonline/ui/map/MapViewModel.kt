package com.example.earthonline.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.local.entity.LocationEntity
import com.example.earthonline.data.repository.LocationRepository
import com.example.earthonline.util.uid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * 足迹地图 ViewModel（对应 HTML 的 state.locations）。
 * 经纬度来自地图长按或设备定位；tags 以 List<String> JSON 持久化。
 *
 * v1.0.7：地图逻辑简化 —— 移除「POI 搜索 / 路径规划 / 高德 Web 服务 Key」整条链路
 * （原 v1.0.6 经 AmapWebService REST 实现，需用户自填 Web Key，体验重且易失败）。
 * 现只保留「路径点记录」：长按 / 定位取点 → 记录足迹（名称 / 备注 / 标签）。
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val repo: LocationRepository
) : ViewModel() {

    val locations = repo.observeAll()

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
}
