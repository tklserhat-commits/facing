package app.facing.data.export

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.min

/** Hilfen, um eine Musikdatei unter das fertige Video zu legen (nur AAC kann direkt ins MP4). */
internal object AudioMixer {
    private const val AAC = MediaFormat.MIMETYPE_AUDIO_AAC

    private fun audioTrack(ex: MediaExtractor): Int {
        for (i in 0 until ex.trackCount) {
            if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) return i
        }
        error("No audio track found")
    }

    fun isAac(context: Context, uri: Uri): Boolean {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, uri, null)
            return ex.getTrackFormat(audioTrack(ex)).getString(MediaFormat.KEY_MIME) == AAC
        } finally {
            ex.release()
        }
    }

    /** Dekodiert beliebige Musik (z. B. MP3) und kodiert sie als AAC in eine M4A-Datei, höchstens [maxUs] lang. */
    fun transcodeToAac(context: Context, uri: Uri, out: File, maxUs: Long, checkActive: () -> Unit) {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, uri, null)
            val track = audioTrack(ex)
            ex.selectTrack(track)
            val inFmt = ex.getTrackFormat(track)
            val rate = inFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = inFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            require(channels in 1..2) { "Only mono or stereo is supported" }

            val dec = MediaCodec.createDecoderByType(inFmt.getString(MediaFormat.KEY_MIME)!!)
            val encFmt = MediaFormat.createAudioFormat(AAC, rate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
            }
            val enc = MediaCodec.createEncoderByType(AAC)
            var muxer: MediaMuxer? = null
            var muxerStarted = false
            var muxTrack = -1
            try {
                dec.configure(inFmt, null, null, 0)
                dec.start()
                enc.configure(encFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                enc.start()
                muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                val encInfo = MediaCodec.BufferInfo()
                var encDone = false
                fun drainEncoder() {
                    while (true) {
                        val i = enc.dequeueOutputBuffer(encInfo, 0)
                        when {
                            i == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                            i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                muxTrack = muxer!!.addTrack(enc.outputFormat)
                                muxer!!.start()
                                muxerStarted = true
                            }
                            i >= 0 -> {
                                val buf = enc.getOutputBuffer(i)!!
                                if (encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) encInfo.size = 0
                                if (encInfo.size > 0 && muxerStarted) {
                                    buf.position(encInfo.offset)
                                    buf.limit(encInfo.offset + encInfo.size)
                                    muxer!!.writeSampleData(muxTrack, buf, encInfo)
                                }
                                if (encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) encDone = true
                                enc.releaseOutputBuffer(i, false)
                                if (encDone) return
                            }
                        }
                    }
                }

                val decInfo = MediaCodec.BufferInfo()
                var inputDone = false
                var decDone = false
                var pcmFrames = 0L // an den Encoder gelieferte PCM-Frames -> Zeitstempel
                fun ptsUs() = pcmFrames * 1_000_000L / rate

                while (!encDone) {
                    checkActive()
                    if (!inputDone) {
                        val i = dec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val b = dec.getInputBuffer(i)!!
                            val n = ex.readSampleData(b, 0)
                            if (n < 0 || ex.sampleTime > maxUs) {
                                dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                dec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                                ex.advance()
                            }
                        }
                    }
                    if (!decDone) {
                        val o = dec.dequeueOutputBuffer(decInfo, 10_000)
                        if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            val f = dec.outputFormat
                            check(
                                f.getInteger(MediaFormat.KEY_SAMPLE_RATE) == rate &&
                                    f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == channels
                            ) { "Audio format not supported" }
                        } else if (o >= 0) {
                            val ob = dec.getOutputBuffer(o)!!
                            ob.position(decInfo.offset)
                            ob.limit(decInfo.offset + decInfo.size)
                            while (ob.hasRemaining()) {
                                val ei = enc.dequeueInputBuffer(10_000)
                                if (ei >= 0) {
                                    val ib = enc.getInputBuffer(ei)!!
                                    ib.clear()
                                    val n = min(ib.remaining(), ob.remaining())
                                    val oldLimit = ob.limit()
                                    ob.limit(ob.position() + n)
                                    ib.put(ob)
                                    ob.limit(oldLimit)
                                    enc.queueInputBuffer(ei, 0, n, ptsUs(), 0)
                                    pcmFrames += n / (2L * channels)
                                }
                                drainEncoder()
                            }
                            if (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                decDone = true
                                var sent = false
                                while (!sent) {
                                    val ei = enc.dequeueInputBuffer(10_000)
                                    if (ei >= 0) {
                                        enc.queueInputBuffer(ei, 0, 0, ptsUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                        sent = true
                                    }
                                    drainEncoder()
                                }
                            }
                            dec.releaseOutputBuffer(o, false)
                        }
                    }
                    drainEncoder()
                }
                check(muxerStarted) { "Audio could not be encoded" }
                muxer!!.stop()
            } finally {
                runCatching { dec.stop() }
                runCatching { dec.release() }
                runCatching { enc.stop() }
                runCatching { enc.release() }
                runCatching { muxer?.release() }
            }
        } finally {
            ex.release()
        }
    }

    /** Führt die Videodatei und die AAC-Quelle in [out] zusammen; Audio wird bei [maxUs] abgeschnitten und wiederholt, falls es kürzer ist. */
    fun mux(video: File, audioSource: (MediaExtractor) -> Unit, out: File, maxUs: Long) {
        val vEx = MediaExtractor()
        val aEx = MediaExtractor()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            vEx.setDataSource(video.path)
            audioSource(aEx)
            val vTrack = (0 until vEx.trackCount).first {
                vEx.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
            val aTrack = audioTrack(aEx)
            val vOut = muxer.addTrack(vEx.getTrackFormat(vTrack))
            val aOut = muxer.addTrack(aEx.getTrackFormat(aTrack))
            muxer.start()
            copySamples(vEx, vTrack, muxer, vOut, Long.MAX_VALUE)
            copySamples(aEx, aTrack, muxer, aOut, maxUs, loop = true)
            muxer.stop()
        } finally {
            runCatching { vEx.release() }
            runCatching { aEx.release() }
            runCatching { muxer.release() }
        }
    }

    /** Kopiert die Samples; mit [loop] beginnt die Spur von vorn, bis [maxUs] erreicht ist. */
    private fun copySamples(
        ex: MediaExtractor, track: Int, muxer: MediaMuxer, outTrack: Int, maxUs: Long, loop: Boolean = false,
    ) {
        ex.selectTrack(track)
        val size = runCatching { ex.getTrackFormat(track).getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }
            .getOrDefault(0).coerceAtLeast(1 shl 20)
        val buf = ByteBuffer.allocate(size)
        val info = MediaCodec.BufferInfo()
        var offset = 0L      // Zeitversatz der aktuellen Wiederholung
        var last = 0L        // Zeit des letzten Samples dieser Runde
        var prev = 0L
        var readInPass = 0
        while (true) {
            buf.clear()
            val n = ex.readSampleData(buf, 0)
            if (n < 0) {
                if (!loop || readInPass == 0) break
                // Nächste Runde direkt hinter dem letzten Sample beginnen (AAC-Frame ≈ 23 ms)
                val step = if (readInPass >= 2) last - prev else 23_220L
                offset += last + step
                ex.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                last = 0L; prev = 0L; readInPass = 0
                continue
            }
            val raw = ex.sampleTime
            val t = raw + offset
            if (t > maxUs) break
            info.set(0, n, t, ex.sampleFlags)
            muxer.writeSampleData(outTrack, buf, info)
            prev = last
            last = raw
            readInPass++
            ex.advance()
        }
        ex.unselectTrack(track)
    }
}
