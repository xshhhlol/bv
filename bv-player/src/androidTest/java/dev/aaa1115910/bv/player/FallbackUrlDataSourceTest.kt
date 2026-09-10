package dev.aaa1115910.bv.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.aaa1115910.bv.player.impl.exo.FallbackUrlDataSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.IOException

@UnstableApi
@RunWith(AndroidJUnit4::class)
class FallbackUrlDataSourceTest {
    @Test
    fun slowConnectionSwitchesWithoutSkippingOrRepeatingBytes() {
        val fixture = Fixture(slow = true)
        assertArrayEquals(fixture.content.copyOfRange(5, 25), fixture.download())
        val resumed = fixture.opens.first { it.uri.host == "two.example" }
        assertEquals(9L, resumed.position)
        assertEquals(16L, resumed.length)
        assertEquals(1, fixture.preferred.index)
        assertTrue(fixture.sources.all { it.closed })
    }

    @Test
    fun failedSlowProbeKeepsReadableOriginalConnectionAndDoesNotOscillate() {
        val fixture = Fixture(slow = true, rejectBackup = true)
        assertArrayEquals(fixture.content.copyOfRange(5, 25), fixture.download())
        assertEquals(1, fixture.opens.count { it.uri.host == "two.example" })
        assertEquals(1, fixture.opens.count { it.uri.host == "one.example" })
        assertEquals(0, fixture.preferred.index)
        assertTrue(fixture.sources.all { it.closed })
    }

    @Test
    fun brokenConnectionResumesAtExactRangeOffset() {
        val fixture = Fixture(breakPrimary = true)
        assertArrayEquals(fixture.content.copyOfRange(5, 25), fixture.download())
        assertEquals(9L, fixture.opens.last().position)
        assertEquals(1, fixture.preferred.index)
        assertTrue(fixture.sources.all { it.closed })
    }

    @Test
    fun prematureEofUsesBackupButNormalEofDoesNot() {
        val fixture = Fixture(earlyEof = true)
        assertArrayEquals(fixture.content.copyOfRange(5, 25), fixture.download())
        assertEquals(2, fixture.opens.size)
        val normal = Fixture()
        assertArrayEquals(normal.content.copyOfRange(5, 25), normal.download())
        assertEquals(1, normal.opens.size)
    }

    @Test
    fun reopensStartFromSuccessfulBackupAndRespectNewSeekPosition() {
        val fixture = Fixture(breakPrimary = true)
        fixture.download()
        val source = fixture.newSource()
        source.open(DataSpec.Builder().setUri(fixture.primary).setPosition(15).setLength(4).build())
        val data = ByteArray(4)
        assertEquals(4, source.read(data, 0, data.size))
        assertArrayEquals(fixture.content.copyOfRange(15, 19), data)
        assertEquals("two.example", fixture.opens.last().uri.host)
        source.close()
    }

    private class Fixture(
        val slow: Boolean = false,
        val rejectBackup: Boolean = false,
        val breakPrimary: Boolean = false,
        val earlyEof: Boolean = false
    ) {
        val primary = Uri.parse("https://one.example/video")
        private val backup = Uri.parse("https://two.example/video")
        val content = ByteArray(40) { it.toByte() }
        val opens = mutableListOf<DataSpec>()
        val sources = mutableListOf<FakeSource>()
        val preferred = FallbackUrlDataSource.PreferredIndexHolder()
        var now = 0L

        fun newSource() = FallbackUrlDataSource(
            DataSource.Factory { FakeSource().also { sources.add(it) } },
            listOf(primary, backup), preferred, { 25_000_000L }, { now }
        )

        fun download(): ByteArray {
            val source = newSource()
            try {
                source.open(DataSpec.Builder().setUri(primary).setPosition(5).setLength(20).build())
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4)
                while (true) {
                    val count = source.read(buffer, 0, buffer.size)
                    if (count == -1) return output.toByteArray()
                    output.write(buffer, 0, count)
                }
            } finally {
                source.close()
            }
        }

        inner class FakeSource : DataSource {
            private lateinit var spec: DataSpec
            private var position = 0
            private var end = 0
            var closed = false

            override fun open(dataSpec: DataSpec): Long {
                spec = dataSpec
                opens.add(spec)
                if (rejectBackup && spec.uri == backup) throw IOException("backup unavailable")
                position = spec.position.toInt()
                end = (spec.position + spec.length).toInt()
                return spec.length
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                check(!closed)
                if (spec.uri == primary) {
                    if (breakPrimary && position >= 9) throw IOException("connection interrupted")
                    if (earlyEof && position >= 9) return -1
                    if (slow) now += 8_000_000_000L
                }
                if (position == end) return -1
                val count = minOf(length, end - position)
                content.copyInto(buffer, offset, position, position + count)
                position += count
                return count
            }

            override fun getUri(): Uri = spec.uri
            override fun addTransferListener(transferListener: TransferListener) = Unit
            override fun close() { closed = true }
        }
    }
}
