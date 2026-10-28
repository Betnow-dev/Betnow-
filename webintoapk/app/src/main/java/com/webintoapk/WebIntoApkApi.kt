package com.webintoapk

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

data class WebAppProject(
    val id: String,
    val name: String,
    val packageName: String,
    val siteUrl: String,
)

data class WebAppBuild(
    val id: String,
    val status: String,
    val apkPath: String?,
    val error: String?,
)

class WebIntoApkApi {
    private val executor = Executors.newSingleThreadExecutor()

    fun signIn(email: String, password: String, callback: (String?, String?) -> Unit) {
        executor.execute {
            runCatching {
                val body = JSONObject()
                    .put("email", email)
                    .put("password", password)
                    .toString()
                JSONObject(request("/auth/v1/token?grant_type=password", "POST", body = body))
                    .getString("access_token")
            }.fold(
                onSuccess = { callback(it, null) },
                onFailure = { callback(null, it.message ?: "Sign in failed.") },
            )
        }
    }

    fun listProjects(token: String, callback: (List<WebAppProject>?, String?) -> Unit) {
        executor.execute {
            runCatching {
                val rows = JSONArray(
                    request(
                        "/rest/v1/app_projects?select=id,name,package_name,site_url&order=created_at.desc",
                        "GET",
                        token,
                    ),
                )
                (0 until rows.length()).map { index ->
                    val row = rows.getJSONObject(index)
                    WebAppProject(
                        id = row.getString("id"),
                        name = row.getString("name"),
                        packageName = row.getString("package_name"),
                        siteUrl = row.getString("site_url"),
                    )
                }
            }.fold(
                onSuccess = { callback(it, null) },
                onFailure = { callback(null, it.message ?: "Could not load apps.") },
            )
        }
    }

    fun createProject(
        token: String,
        name: String,
        packageName: String,
        siteUrl: String,
        admobAppId: String,
        admobBannerId: String,
        callback: (WebAppProject?, String?) -> Unit,
    ) {
        executor.execute {
            runCatching {
                val body = JSONObject()
                    .put("name", name)
                    .put("package_name", packageName)
                    .put("site_url", siteUrl)
                    .put("admob_app_id", admobAppId)
                    .put("admob_banner_id", admobBannerId)
                    .put("status_bar_color", "#E91D31")
                    .toString()
                val rows = JSONArray(
                    request(
                        "/rest/v1/app_projects?select=id,name,package_name,site_url",
                        "POST",
                        token,
                        body,
                        "return=representation",
                    ),
                )
                val row = rows.getJSONObject(0)
                WebAppProject(
                    id = row.getString("id"),
                    name = row.getString("name"),
                    packageName = row.getString("package_name"),
                    siteUrl = row.getString("site_url"),
                )
            }.fold(
                onSuccess = { callback(it, null) },
                onFailure = { callback(null, it.message ?: "Could not create app.") },
            )
        }
    }

    fun requestBuild(token: String, projectId: String, callback: (String?, String?) -> Unit) {
        executor.execute {
            runCatching {
                val body = JSONObject().put("app_id", projectId).toString()
                JSONObject(request("/functions/v1/dispatch-build", "POST", token, body))
                    .getString("build_id")
            }.fold(
                onSuccess = { callback(it, null) },
                onFailure = { callback(null, it.message ?: "Could not start build.") },
            )
        }
    }

    fun listBuilds(
        token: String,
        projectId: String,
        callback: (List<WebAppBuild>?, String?) -> Unit,
    ) {
        executor.execute {
            runCatching {
                val encodedId = URLEncoder.encode(projectId, Charsets.UTF_8.name())
                val rows = JSONArray(
                    request(
                        "/rest/v1/app_builds?app_id=eq.$encodedId&select=id,status,apk_object_path,error_message&order=created_at.desc&limit=10",
                        "GET",
                        token,
                    ),
                )
                (0 until rows.length()).map { index ->
                    val row = rows.getJSONObject(index)
                    WebAppBuild(
                        id = row.getString("id"),
                        status = row.getString("status"),
                        apkPath = row.optString("apk_object_path").takeIf(String::isNotBlank),
                        error = row.optString("error_message").takeIf(String::isNotBlank),
                    )
                }
            }.fold(
                onSuccess = { callback(it, null) },
                onFailure = { callback(null, it.message ?: "Could not load builds.") },
            )
        }
    }

    fun getArtifactUrl(token: String, objectPath: String, callback: (String?, String?) -> Unit) {
        executor.execute {
            runCatching {
                val encodedPath = objectPath.split('/').joinToString("/") {
                    URLEncoder.encode(it, Charsets.UTF_8.name())
                }
                val body = JSONObject().put("expiresIn", 600).toString()
                val result = JSONObject(
                    request(
                        "/storage/v1/object/sign/app-builds/$encodedPath",
                        "POST",
                        token,
                        body,
                    ),
                )
                val signedPath = result.getString("signedURL")
                if (signedPath.startsWith("https://")) {
                    signedPath
                } else {
                    BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1" + signedPath
                }
            }.fold(
                onSuccess = { callback(it, null) },
                onFailure = { callback(null, it.message ?: "Could not prepare download.") },
            )
        }
    }

    private fun request(
        path: String,
        method: String,
        token: String? = null,
        body: String? = null,
        prefer: String? = null,
    ): String {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        if (baseUrl.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
            throw IllegalStateException(
                "Configure SUPABASE_URL and SUPABASE_ANON_KEY before building this app.",
            )
        }
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (prefer != null) connection.setRequestProperty("Prefer", prefer)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter().use { it.write(body) }
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) {
                throw IOException("Request failed ($responseCode): $response")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}
