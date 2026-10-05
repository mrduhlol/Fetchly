package com.fetchly.app.data.remote

import com.fetchly.app.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface FetchlyApi {
    @POST("api/analyze")
    suspend fun analyze(@Body body: AnalyzeRequest): AnalyzeResponse
}

object ApiClient {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun baseUrl(): String {
        val configured = BuildConfig.FETCHLY_API_BASE_URL.trim().trimEnd('/')
        return if (configured.isNotEmpty()) "$configured/" else "https://api.fetchly.app/"
    }

    fun create(baseUrl: String = baseUrl()): FetchlyApi {
        // Never log request lines in release builds.
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FetchlyApi::class.java)
    }
}
