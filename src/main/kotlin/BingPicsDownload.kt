import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class RequestJson(val MediaContents: List<MediaContent>) {

    @Serializable
    data class MediaContent(val ImageContent: ImageContent)

    @Serializable
    data class ImageContent(val Image: Image, val Title: String)

    @Serializable
    data class Image(val Url: String, val Wallpaper: String)
}

private val json = Json { ignoreUnknownKeys = true }
private const val URL_BASE = "https://cn.bing.com"
private const val API = "$URL_BASE/hp/api/model"


private suspend fun getImageInfo() = suspendCancellableCoroutine<String> { cont ->
    try {
        val client = HttpClient.newBuilder().build()
        val request = HttpRequest.newBuilder().uri(URI.create(API)).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        cont.resume(response.body())
    } catch (e: Exception) {
        cont.resumeWithException(e)
    }
}

private fun download(url: String, path: String) {
    URL(url).openStream().use { input ->
        FileOutputStream(File(path)).use { output ->
            input.copyTo(output)
        }
    }
}

fun main(args: Array<String>) {
    val jobs = GlobalScope.async(Dispatchers.IO) {
        println("Download bing pic start...")
        val imageInfos = getImageInfo()
        if (imageInfos.isNotEmpty()) {
            try {
                val requestJson = json.decodeFromString<RequestJson>(imageInfos)
                println("${requestJson.MediaContents.size} images will be download soon")
                for (mediaContent in requestJson.MediaContents) {
                    val imageUrl = URL_BASE + mediaContent.ImageContent.Image.Url
                    val picDir = "${Paths.get("").toAbsolutePath()}${File.separator}pics"
                    Files.createDirectories(Paths.get(picDir))
                    val imageFileName = "$picDir${File.separator}${mediaContent.ImageContent.Title}_1920x1080.jpg"

                    // check url if valid
                    try {
                        URL(imageUrl).toURI()
                    } catch (e: Exception) {
                        println("Invalid url, will continue to next")
                        continue
                    }

                    println("Downloading ${mediaContent.ImageContent.Title}")
                    download(imageUrl, imageFileName)
                }
                println("All Images downloaded")
            } catch (e: Exception) {
                println("Download image failed")
                e.printStackTrace()
            }
        } else {
            println("fetch image info failed!")
        }
    }

    runBlocking {
        // wait until the IO coroutines finished
        jobs.join()
    }

    println("App will down")
}