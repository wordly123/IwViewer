package com.rerere.iwara4a.data.api.service

import com.rerere.iwara4a.data.model.detail.video.VideoLink
import retrofit2.http.*

/**
 * 使用Retrofit直接获取 RESTFUL API 资源
 */
interface IwaraService {
    @POST("api/video/{videoId}")
    suspend fun getVideoInfo(@Path("videoId") videoId: String): VideoLink
}