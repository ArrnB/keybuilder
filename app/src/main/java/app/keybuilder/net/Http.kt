package app.keybuilder.net

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/*
 * The only code in the app that goes online. It is used for exactly two things:
 * asking GIPHY for GIFs, and downloading the one GIF you tap so it can be sent.
 * These functions block until done, so call them from a background thread.
 */

class HttpResult(val code: Int, val body: String) {
    val ok: Boolean get() = code in 200..299
}

/** Fetches a web address as text. Throws IOException when there is no connection. */
@Throws(IOException::class)
fun httpGetText(url: String): HttpResult {
    val connection = open(url)
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        return HttpResult(code, body)
    } finally {
        connection.disconnect()
    }
}

/**
 * Downloads a file. Gives up with an IOException if the server refuses or the file is
 * larger than [maxBytes]; a half-written file is removed.
 */
@Throws(IOException::class)
fun httpDownload(url: String, target: File, maxBytes: Long) {
    val connection = open(url)
    try {
        val code = connection.responseCode
        if (code !in 200..299) throw IOException("Server answered $code")
        target.parentFile?.mkdirs()
        var total = 0L
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw IOException("File is larger than $maxBytes bytes")
                    output.write(buffer, 0, read)
                }
            }
        }
    } catch (e: IOException) {
        target.delete()
        throw e
    } finally {
        connection.disconnect()
    }
}

private fun open(url: String): HttpURLConnection {
    val address = try {
        URI(url).toURL()
    } catch (e: Exception) {
        throw IOException("Not a web address: $url", e)
    }
    val connection = address.openConnection() as HttpURLConnection
    connection.connectTimeout = 10_000
    connection.readTimeout = 15_000
    connection.instanceFollowRedirects = true
    return connection
}
