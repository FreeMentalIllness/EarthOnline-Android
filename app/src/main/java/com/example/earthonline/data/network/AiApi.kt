package com.example.earthonline.data.network

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming

/** OpenAI 兼容的 chat/completions 接口 */
interface AiApi {
    @POST("chat/completions")
    suspend fun chat(
        @Header("Authorization") authorization: String,
        @Body body: ChatRequest
    ): ChatResponse

    /** 流式（SSE）版本：返回原始响应体，由仓库侧逐行解析 data: 增量。 */
    @POST("chat/completions")
    @Streaming
    suspend fun streamRaw(
        @Header("Authorization") authorization: String,
        @Body body: ChatRequest
    ): ResponseBody
}
