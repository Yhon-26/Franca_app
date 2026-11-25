package com.grafito.finanzas.receipts

import android.accounts.Account
import android.content.Context
import android.util.Base64
import androidx.core.text.HtmlCompat
import com.grafito.finanzas.data.BcpMailSuggestion
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal const val GMAIL_READ_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"

internal fun gmailAuthorizationRequest(email: String? = null): AuthorizationRequest = AuthorizationRequest.builder()
    .setRequestedScopes(listOf(Scope(GMAIL_READ_SCOPE)))
    .apply { if (email != null) setAccount(Account(email, "com.google")) }
    .build()

internal class GmailHttpError(val status: Int) : IOException()

internal class BcpGmailClient(private val token: String) {
    suspend fun email(): String = withContext(Dispatchers.IO) { get("profile").getString("emailAddress") }

    suspend fun collectPayments(accept: suspend (BcpMailSuggestion) -> Unit) = withContext(Dispatchers.IO) {
        val query = "${BcpMailParser.senderQuery} newer_than:7d"
        var page: String? = null
        do {
            val suffix = page?.let { "&pageToken=${encode(it)}" }.orEmpty()
            val response = get("messages?maxResults=50&q=${encode(query)}$suffix")
            val messages = response.optJSONArray("messages")
            for (index in 0 until (messages?.length() ?: 0)) {
                val id = messages!!.getJSONObject(index).getString("id")
                val message = try { get("messages/${encode(id)}?format=full") } catch (error: GmailHttpError) {
                    if (error.status == 404) continue
                    throw error
                }
                val payload = message.optJSONObject("payload") ?: continue
                val headers = payload.optJSONArray("headers") ?: continue
                fun header(name: String): String = (0 until headers.length()).asSequence().map { headers.getJSONObject(it) }
                    .firstOrNull { it.optString("name").equals(name, ignoreCase = true) }?.optString("value").orEmpty()
                if (!BcpMailParser.isBankMail(header("From"), header("Authentication-Results"))) continue
                messageText(payload).firstNotNullOfOrNull(BcpMailParser::parse)?.let { accept(it) }
            }
            page = response.optString("nextPageToken").ifBlank { null }
        } while (page != null)
    }

    private fun messageText(part: JSONObject): Sequence<String> = sequence {
        val mime = part.optString("mimeType")
        val data = part.optJSONObject("body")?.optString("data").orEmpty()
        if (data.isNotBlank() && mime in setOf("text/plain", "text/html")) {
            val decoded = runCatching { Base64.decode(data, Base64.URL_SAFE).decodeToString() }.getOrNull()
            if (decoded != null) yield(if (mime == "text/html") HtmlCompat.fromHtml(
                decoded.replace(Regex("</(?:td|th|tr)\\s*>", RegexOption.IGNORE_CASE), "<br>"),
                HtmlCompat.FROM_HTML_MODE_COMPACT).toString() else decoded)
        }
        val children = part.optJSONArray("parts")
        for (index in 0 until (children?.length() ?: 0)) yieldAll(messageText(children!!.getJSONObject(index)))
    }

    private fun get(path: String): JSONObject {
        val connection = URL("https://gmail.googleapis.com/gmail/v1/users/me/$path").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) throw GmailHttpError(status)
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val data = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    if (data.length + read > 2_000_000) throw IOException()
                    data.append(buffer, 0, read)
                }
                JSONObject(data.toString())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}

internal suspend fun getGmailAccessToken(context: Context, email: String): String? {
    val result = Identity.getAuthorizationClient(context).authorize(gmailAuthorizationRequest(email)).await()
    return result.accessToken.takeUnless { result.hasResolution() }
}
