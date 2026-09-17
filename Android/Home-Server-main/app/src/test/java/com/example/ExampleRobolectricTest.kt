package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.MediaCategory
import com.example.data.repository.MockServerRepository
import com.example.network.AuthInterceptor
import com.example.network.NetworkResult
import com.example.network.ServerConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("DHILIP HOME", appName)
  }

  @Test
  fun `verify mock server status default values`() = runBlocking {
    val serverStatus = MockServerRepository.getServerStatus().first()
    assertEquals("dhilip-server", serverStatus.serverHostname)
    assertEquals(18, serverStatus.cpuPercent)
    assertEquals("Not connected", serverStatus.networkStatus)
    assertEquals(14, serverStatus.uptimeDays)
  }

  @Test
  fun `verify files repository returns root folders`() = runBlocking {
    val files = MockServerRepository.getFiles("/").first()
    assertTrue(files.isNotEmpty())
    assertTrue(files.any { it.name == "Movies" && it.isFolder })
    assertTrue(files.any { it.name == "Documents" && it.isFolder })
  }

  @Test
  fun `verify media repository returns mock movies`() = runBlocking {
    val movies = MockServerRepository.getMedia(MediaCategory.MOVIES).first()
    assertTrue(movies.isNotEmpty())
    val interstellar = movies.find { it.title == "Interstellar" }
    assertNotNull(interstellar)
    assertEquals(2014, interstellar?.year)
    assertEquals("2h 49m", interstellar?.duration)
  }

  @Test
  fun `verify server config dynamic address parsing`() {
    // Port 8080 default
    ServerConfig.parseAndSetServerAddress("192.168.1.150")
    assertEquals("192.168.1.150", ServerConfig.serverHost.value)
    assertEquals(8080, ServerConfig.serverPort.value)
    assertEquals("http://192.168.1.150:8080", ServerConfig.baseUrl.value)

    // With explicit port
    ServerConfig.parseAndSetServerAddress("10.0.0.50:9000")
    assertEquals("10.0.0.50", ServerConfig.serverHost.value)
    assertEquals(9000, ServerConfig.serverPort.value)
    assertEquals("http://10.0.0.50:9000", ServerConfig.baseUrl.value)

    // With http:// scheme
    ServerConfig.parseAndSetServerAddress("http://dhilip-server.local:8080")
    assertEquals("dhilip-server.local", ServerConfig.serverHost.value)
    assertEquals(8080, ServerConfig.serverPort.value)
    assertEquals("http://dhilip-server.local:8080", ServerConfig.baseUrl.value)

    // With https:// scheme
    ServerConfig.parseAndSetServerAddress("https://dhilip.home:8443")
    assertEquals("dhilip.home", ServerConfig.serverHost.value)
    assertEquals(8443, ServerConfig.serverPort.value)
    assertTrue(ServerConfig.useHttps.value)
    assertEquals("https://dhilip.home:8443", ServerConfig.baseUrl.value)
  }

  @Test
  fun `verify auth interceptor token handling`() {
    val interceptor = AuthInterceptor()
    interceptor.setToken("test-jwt-token-123")
    assertEquals("test-jwt-token-123", interceptor.getToken())
    interceptor.clear()
    assertEquals(null, interceptor.getToken())
  }

  @Test
  fun `verify structured server error parsing`() {
    val errorJson = """
      {
        "success": false,
        "error": {
          "code": "FILE_NOT_FOUND",
          "message": "File was not found on server"
        }
      }
    """.trimIndent()
    val parsed = NetworkResult.parseServerError(errorJson, 404)
    assertEquals("FILE_NOT_FOUND", parsed.code)
    assertEquals("File was not found on server", parsed.message)
    assertEquals(404, parsed.statusCode)
  }

  @Test
  fun `verify html file list parsing`() {
    val sampleHtml = """
      <html>
        <body>
          <a href="../">Parent Directory</a>
          <a href="movies/">movies/</a>
          <a href="song.mp3">song.mp3</a>
          <a href="document.pdf">document.pdf</a>
        </body>
      </html>
    """.trimIndent()
    val files = com.example.data.repository.HttpServerRepository.parseHtmlFileList(sampleHtml, "/")
    assertTrue(files.any { it.name == "movies" && it.isFolder })
    assertTrue(files.any { it.name == "song.mp3" && !it.isFolder })
    assertTrue(files.any { it.name == "document.pdf" && !it.isFolder })
  }

  @Test
  fun `verify backend files json parsing`() {
    val sampleBackendJson = """
      {
        "status": "success",
        "current_path": "",
        "directories": [
          {"name": "Movies", "path": "Movies", "size": 0, "is_dir": true},
          {"name": "Music", "path": "Music", "size": 0, "is_dir": true}
        ],
        "files": [
          {"name": "notes.txt", "path": "notes.txt", "size": 2048, "is_dir": false}
        ]
      }
    """.trimIndent()
    val files = com.example.data.repository.HttpServerRepository.parseJsonFileList(sampleBackendJson, "")
    assertEquals(3, files.size)
    assertTrue(files.any { it.name == "Movies" && it.isFolder })
    assertTrue(files.any { it.name == "Music" && it.isFolder })
    assertTrue(files.any { it.name == "notes.txt" && !it.isFolder && it.sizeBytes == 2048L })
  }

  @Test
  fun `verify backend storage json parsing`() {
    val sampleStorageJson = """
      {
        "drives": [
          {
            "mount": "/",
            "device": "/dev/sda1",
            "total_bytes": 107374182400,
            "used_bytes": 53687091200,
            "free_bytes": 53687091200,
            "usage_percent": 50.0
          }
        ]
      }
    """.trimIndent()
    val storageList = com.example.data.repository.HttpServerRepository.parseStorageJson(sampleStorageJson)
    assertTrue(storageList.isNotEmpty())
    val root = storageList.first()
    assertEquals("/", root.mountPoint)
    assertEquals(50.0, root.usedPercent, 0.1)
  }

  @Test
  fun `verify diagnostic transaction tracking`() {
    com.example.network.ApiDiagnosticManager.clearHistory()
    com.example.network.ApiDiagnosticManager.recordTransaction(
        method = "GET",
        endpoint = "/api/health",
        fullUrl = "http://192.168.1.100:8080/api/health",
        statusCode = 200,
        latencyMs = 15L,
        responseBody = "{\"status\":\"ok\"}"
    )
    val history = com.example.network.ApiDiagnosticManager.history.value
    assertEquals(1, history.size)
    assertEquals("/api/health", history[0].endpoint)
    assertEquals(200, history[0].statusCode)
    assertTrue(history[0].isSuccess)
  }
}
