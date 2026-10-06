package com.example.zammad.api

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder

/** Kind of a failed request — mapped to localized messages. */
enum class ApiErrorKind {
    NETWORK, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, SERVER, REQUEST, DECODE, INVALID_URL, UNKNOWN
}

class ApiException(val kind: ApiErrorKind, message: String) : Exception(message)

/** Thin REST client around HttpURLConnection (mirrors the iOS APIClient). */
class ApiClient {
    var baseUrl: String = ""
    var authHeader: String? = null

    companion object {
        fun basicAuth(login: String, password: String): String {
            val raw = "$login:$password".toByteArray(Charsets.UTF_8)
            return "Basic " + Base64.encodeToString(raw, Base64.NO_WRAP)
        }

        fun tokenAuth(token: String): String = "Token token=$token"
    }

    fun request(
        method: String,
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        jsonBody: String? = null
    ): String {
        val url = try {
            URL(buildUrl(path, query))
        } catch (e: ApiException) {
            throw e
        } catch (e: MalformedURLException) {
            throw ApiException(ApiErrorKind.INVALID_URL, e.message ?: "bad url")
        }

        val conn = try {
            url.openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw ApiException(ApiErrorKind.NETWORK, e.message ?: "open failed")
        }

        try {
            conn.requestMethod = method
            conn.connectTimeout = 20_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "application/json")
            authHeader?.let { conn.setRequestProperty("Authorization", it) }

            if (jsonBody != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val errText = try {
                    (conn.errorStream ?: conn.inputStream)
                        ?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (_: Exception) {
                    ""
                }
                throw ApiException(kindFor(code), "HTTP $code $errText")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(ApiErrorKind.NETWORK, e.message ?: "request failed")
        } finally {
            conn.disconnect()
        }
    }

    private fun buildUrl(path: String, query: List<Pair<String, String>>): String {
        val base = baseUrl.trimEnd('/')
        if (base.isEmpty()) throw ApiException(ApiErrorKind.INVALID_URL, "empty server address")
        val sb = StringBuilder(base)
        sb.append(if (path.startsWith("/")) path else "/$path")
        if (query.isNotEmpty()) {
            sb.append('?')
            query.forEachIndexed { i, (k, v) ->
                if (i > 0) sb.append('&')
                sb.append(URLEncoder.encode(k, "UTF-8"))
                sb.append('=')
                sb.append(URLEncoder.encode(v, "UTF-8"))
            }
        }
        return sb.toString()
    }

    private fun kindFor(code: Int): ApiErrorKind = when {
        code == 401 -> ApiErrorKind.UNAUTHORIZED
        code == 403 -> ApiErrorKind.FORBIDDEN
        code == 404 -> ApiErrorKind.NOT_FOUND
        code >= 500 -> ApiErrorKind.SERVER
        else -> ApiErrorKind.REQUEST
    }

    // ---- JSON helpers ----

    fun getArray(path: String, query: List<Pair<String, String>> = emptyList()): List<JSONObject> =
        parseArray(request("GET", path, query))

    fun getObject(path: String, query: List<Pair<String, String>> = emptyList()): JSONObject =
        try {
            JSONObject(request("GET", path, query))
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(ApiErrorKind.DECODE, e.message ?: "decode failed")
        }

    fun post(path: String, body: String): String = request("POST", path, jsonBody = body)

    fun put(path: String, body: String): String = request("PUT", path, jsonBody = body)

    fun parseArray(text: String): List<JSONObject> = try {
        JSONArray(text).jsonObjects()
    } catch (e: Exception) {
        throw ApiException(ApiErrorKind.DECODE, e.message ?: "decode failed")
    }

    fun parseObject(text: String): JSONObject = try {
        JSONObject(text)
    } catch (e: Exception) {
        throw ApiException(ApiErrorKind.DECODE, e.message ?: "decode failed")
    }
}

// ---- generic org.json helpers (the "flex" layer from iOS) ----

fun JSONObject.flexLong(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return when (val v = get(key)) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull()
        else -> null
    }
}

fun JSONObject.flexInt(key: String): Int? = flexLong(key)?.toInt()

fun JSONObject.flexString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return when (val v = get(key)) {
        is String -> v
        is Number, is Boolean -> v.toString()
        else -> null
    }
}

fun JSONObject.flexBool(key: String): Boolean? {
    if (!has(key) || isNull(key)) return null
    return when (val v = get(key)) {
        is Boolean -> v
        is String -> v.equals("true", ignoreCase = true)
        else -> null
    }
}

fun JSONArray.jsonObjects(): List<JSONObject> {
    val out = ArrayList<JSONObject>(length())
    for (i in 0 until length()) {
        optJSONObject(i)?.let { out.add(it) }
    }
    return out
}

fun jsonBody(build: JSONObject.() -> Unit): String = JSONObject().apply(build).toString()
