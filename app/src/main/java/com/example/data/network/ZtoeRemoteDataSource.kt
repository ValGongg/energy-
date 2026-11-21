package com.example.data.network

import com.example.data.model.ParsedSchedulePage
import com.example.data.parser.ZtoeHtmlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class ZtoeRemoteDataSource(
    private val client: OkHttpClient = defaultOkHttpClient(),
) {
    suspend fun fetchSchedule(url: String = SCHEDULE_URL): ParsedSchedulePage =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "uk-UA,uk;q=0.9,ru;q=0.8,en;q=0.7")
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Сервер ZTOE вернул ошибку HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Пустой ответ от сервера ZTOE")
                val bytes = body.bytes()
                val contentType = response.header("Content-Type")
                val html = ZtoeHtmlParser.decodeBytes(bytes, contentType)
                val parsed = ZtoeHtmlParser.parse(html, System.currentTimeMillis())
                if (parsed.days.isEmpty()) {
                    throw IOException("Не удалось найти таблицу графика на странице ZTOE")
                }
                parsed
            }
        }

    companion object {
        const val SCHEDULE_URL = "https://www.ztoe.com.ua/unhooking-search.php"

        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
