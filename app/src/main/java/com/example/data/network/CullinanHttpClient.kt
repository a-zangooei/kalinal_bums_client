package com.example.data.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object CullinanHttpClient {

    const val BASE_URL = "https://jeton.bums.ac.ir"
    const val LOGIN_URL = "$BASE_URL/Login.aspx"
    const val CAPTCHA_URL = "$BASE_URL/UserControls/Captcha.ashx"
    const val RESERVATION_URL = "$BASE_URL/Reservation/Reservation.aspx"

    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    val cookieJar = CullinanCookieJar()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept-Language", "fa-IR,fa;q=0.9,en-US;q=0.8,en;q=0.7")
                    .build()
                chain.proceed(request)
            }
            .build()
    }
}

