package com.example.earthonline.ui.steam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.data.steam.SteamGame
import com.example.earthonline.data.steam.SteamRepository
import com.example.earthonline.data.steam.SteamResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Steam 游戏数据 UI 状态。
 * **诚实原则是这里的第一原则** —— 未配置就是 [Unconfigured]，
 * 失败就是 [Error]（带真实原因），绝不存在「看起来有数据其实没有」的中间态。
 */
sealed interface SteamUiState {
    /** 未配置 Steam ID / API Key */
    object Unconfigured : SteamUiState
    /** 正在拉取 */
    object Loading : SteamUiState
    /** 拉取成功（games 为空也要如实显示「库里没有游戏」） */
    data class Ready(val games: List<SteamGame>, val totalCount: Int) : SteamUiState
    /** 失败，带用户可读的原因 */
    data class Error(val reason: String) : SteamUiState
}

@HiltViewModel
class SteamViewModel @Inject constructor(
    private val settings: SettingsDataStore
) : ViewModel() {

    val steamId: StateFlow<String> = settings.steamId
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val steamApiKey: StateFlow<String> = settings.steamApiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val configured: StateFlow<Boolean> = settings.steamConfigured
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _state = MutableStateFlow<SteamUiState>(SteamUiState.Unconfigured)
    val state: StateFlow<SteamUiState> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        // 已配置时进来就拉一次；未配置保持 Unconfigured，绝不发无意义的请求
        viewModelScope.launch {
            configured.collect { ok ->
                if (ok && _state.value == SteamUiState.Unconfigured) refresh()
            }
        }
    }

    /** 保存 Steam ID 与 API Key，并立即拉取 */
    fun saveConfig(id: String, key: String) {
        viewModelScope.launch {
            settings.setSteamId(id)
            settings.setSteamApiKey(key)
            if (id.isBlank() || key.isBlank()) {
                settings.setSteamGamesCache("")
                _state.value = SteamUiState.Unconfigured
            } else {
                refresh()
            }
        }
    }

    /** 拉取游戏库（阻塞 IO 已收口到 Dispatchers.IO，绝不卡主线程） */
    fun refresh() {
        viewModelScope.launch {
            _busy.value = true
            _state.value = SteamUiState.Loading
            // 直接读已 stateIn 好的当前值（DataStore 首值同步可用），避免额外挂起
            val id = steamId.value
            val key = steamApiKey.value
            val result = withContext(Dispatchers.IO) {
                SteamRepository.fetchOwnedGames(id, key)
            }
            _state.value = when (result) {
                is SteamResult.Ok -> SteamUiState.Ready(result.games, result.totalCount)
                is SteamResult.Fail -> SteamUiState.Error(result.reason)
            }
            _busy.value = false
        }
    }

    /** 断开 Steam（清配置与缓存），回到未配置的诚实状态 */
    fun disconnect() {
        viewModelScope.launch {
            settings.clearSteam()
            _state.value = SteamUiState.Unconfigured
        }
    }
}
