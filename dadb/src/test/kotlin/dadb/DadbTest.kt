package dadb

import okio.buffer
import org.junit.Before
import java.io.IOException
import java.net.Socket
import kotlin.test.Test

internal class DadbTest {

    @Before
    fun setUp() {
        killServer()
    }

    @Test
    fun name() {
        val socket = Socket("localhost", 5555)
        val keyPair = AdbKeyPair.readDefault()
        AdbChannel.open(socket, keyPair).use { channel ->
            channel.connect("shell,raw:echo hello").use { connection ->
                val response = connection.source.buffer().readString(Charsets.UTF_8)
                println(response)
            }
        }
    }

    private fun killServer() {
        try {
            // Connection fails if there are simultaneous auth requests
            Runtime.getRuntime().exec("adb kill-server").waitFor()
        } catch (ignore: IOException) {}
    }
}
