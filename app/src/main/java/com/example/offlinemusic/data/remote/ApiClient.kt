package com.example.offlinemusic.data.remote

import com.google.gson.GsonBuilder
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class ResilientDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isNotEmpty()) return addresses
        } catch (e: Exception) {
            android.util.Log.w("ResilientDns", "System DNS lookup failed for $hostname: ${e.message}")
        }

        if (hostname.equals("meta-enricher.onrender.com", ignoreCase = true)) {
            try {
                return listOf(
                    InetAddress.getByAddress(hostname, byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 16.toByte())),
                    InetAddress.getByAddress(hostname, byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 18.toByte()))
                )
            } catch (e: Exception) {
                android.util.Log.w("ResilientDns", "Fallback IP failed: ${e.message}")
            }
        }

        return Dns.SYSTEM.lookup(hostname)
    }
}

object ApiClient {
    const val BASE_URL = "https://meta-enricher.onrender.com/"
    const val API_SECRET_KEY = "metaenricher_secret_key"

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(ResilientDns())
            // 1. Attach Auth Header to every request
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("X-API-Key", API_SECRET_KEY)
                    .build()
                chain.proceed(request)
            }
            // 2. Network timeouts (Render cold start can take ~20-30s if idle, external searches ~15s)
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            // 3. Logging for debugging
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    val metaEnricherApi: MetaEnricherApi by lazy {
        val gson = GsonBuilder()
            .serializeNulls()
            .create()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(MetaEnricherApi::class.java)
    }
}
