package dadb

import okio.Buffer
import okio.Sink
import okio.Source
import okio.Timeout
import kotlin.math.max

class AdbConnection internal constructor(
        private val messageQueue: AdbMessageQueue,
        private val adbWriter: AdbWriter,
        private val maxPayloadSize: Int,
        private val localId: Int,
        private val remoteId: Int
) : AutoCloseable {

    private var isClosed = false

    val source = object : Source {

        private var message: AdbMessage? = null
        private var bytesRead = 0

        override fun read(sink: Buffer, byteCount: Long): Long {
            val message = message ?: nextMessage() ?: return -1

            val bytesRemaining = message.payloadLength - bytesRead
            val bytesToRead = Math.min(byteCount.toInt(), bytesRemaining)

            sink.write(message.payload, bytesRead, bytesToRead)

            bytesRead += bytesToRead

            check(bytesRead <= message.payloadLength)

            if (bytesRead == message.payloadLength) {
                this.message = null
                adbWriter.writeOkay(localId, remoteId)
            }

            return bytesToRead.toLong()
        }

        private fun nextMessage(): AdbMessage? {
            bytesRead = 0
            return nextMessage(Constants.CMD_WRTE)
        }

        override fun close() {}

        override fun timeout() = Timeout.NONE
    }

    val sink = object : Sink {

        private val buf = ByteArray(maxPayloadSize)

        override fun write(source: Buffer, byteCount: Long) {
            var bytesRemaining = byteCount.toInt()
            while (bytesRemaining > 0) {
                val bytesToWrite = Math.min(maxPayloadSize, bytesRemaining)
                val bytesRead = source.read(buf, 0, bytesToWrite)
                adbWriter.writeWrite(localId, remoteId, buf, 0, bytesRead)
                bytesRemaining -= bytesRead
            }
            check(bytesRemaining == 0)
        }

        override fun flush() {}

        override fun close() {}

        override fun timeout() = Timeout.NONE
    }

    private fun nextMessage(command: Int): AdbMessage? {
        return try {
            messageQueue.take(localId, command)
        } catch (e: AdbConnectionClosed) {
            close(false)
            return null
        }
    }

    override fun close() {
        close(true)
    }

    private fun close(readClose: Boolean) {
        if (isClosed) return
        isClosed = true

        messageQueue.stopListening(localId)

        adbWriter.writeClose(localId, remoteId)
        adbWriter.close()
        messageQueue.close()

        if (readClose) messageQueue.take(localId, Constants.CMD_CLSE)
    }
}
