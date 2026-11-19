package app.facing.data.export

import java.time.format.FormatStyle
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import app.facing.data.db.PhotoEntity
import app.facing.data.files.PhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.min

/**
 * Rendert die Fotos als H.264-MP4 (960×1280, 3:4). Jedes Foto ist genau ein Bild,
 * die Zeitstempel werden aus [fps] berechnet, damit der Film so läuft wie in der Vorschau.
 */
class MovieExporter(private val context: Context) {

    private val dateFmt: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    suspend fun export(
        frames: List<PhotoEntity>,
        storage: PhotoStorage,
        fps: Int,
        dateStamp: Boolean,
        audio: Uri? = null,
        onProgress: (done: Int, total: Int) -> Unit,
    ): File = withContext(Dispatchers.Default) {
        require(frames.size >= 2) { "At least 2 photos required" }

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "facing-film.mp4")
        // Mit Musik wird erst ein Video ohne Ton gerendert und danach mit der Tonspur zusammengeführt.
        val videoOut = if (audio == null) out else File(dir, "video-only.mp4")
        val m4a = File(dir, "audio.m4a")

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, W, H).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var surface: Surface? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var track = -1
        var written = 0
        val info = MediaCodec.BufferInfo()

        // Holt fertige Daten aus dem Encoder und schreibt sie mit eigenen Zeitstempeln ins MP4.
        fun drain(untilEnd: Boolean) {
            while (true) {
                val idx = codec.dequeueOutputBuffer(info, if (untilEnd) 10_000L else 0L)
                when {
                    idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!untilEnd) return
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer!!.addTrack(codec.outputFormat)
                        muxer!!.start()
                        muxerStarted = true
                    }
                    idx >= 0 -> {
                        val buf = codec.getOutputBuffer(idx)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxerStarted) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            info.presentationTimeUs = written * 1_000_000L / fps
                            muxer!!.writeSampleData(track, buf, info)
                            written++
                        }
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(idx, false)
                        if (eos) return
                    }
                }
            }
        }

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val input = codec.createInputSurface()
            surface = input
            codec.start()
            muxer = MediaMuxer(videoOut.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = H * 0.032f
            }
            val boxPaint = Paint().apply { color = Color.argb(140, 0, 0, 0) }

            var drawn = 0
            frames.forEachIndexed { i, p ->
                ensureActive()
                val bmp = BitmapFactory.decodeFile(storage.photoFile(p.fileName).path)
                if (bmp != null) {
                    val canvas: Canvas = input.lockHardwareCanvas()
                    try {
                        canvas.drawColor(Color.BLACK)
                        val scale = min(W.toFloat() / bmp.width, H.toFloat() / bmp.height)
                        val w = bmp.width * scale
                        val h = bmp.height * scale
                        val left = (W - w) / 2f
                        val top = (H - h) / 2f
                        canvas.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), paint)
                        if (dateStamp) {
                            val text = runCatching { LocalDate.parse(p.day).format(dateFmt) }.getOrDefault(p.day)
                            val pad = H * 0.012f
                            val tw = textPaint.measureText(text)
                            val right = W - H * 0.015f
                            val bottom = H - H * 0.015f
                            canvas.drawRect(
                                right - tw - 2 * pad, bottom - textPaint.textSize - pad,
                                right, bottom + pad, boxPaint,
                            )
                            canvas.drawText(text, right - tw - pad, bottom - pad * 0.3f, textPaint)
                        }
                    } finally {
                        input.unlockCanvasAndPost(canvas)
                        bmp.recycle()
                    }
                    drawn++
                    drain(untilEnd = false)
                }
                onProgress(i + 1, frames.size)
            }
            check(drawn >= 2) { "Photos could not be read" }

            codec.signalEndOfInputStream()
            drain(untilEnd = true)
            check(muxerStarted && written > 0) { "Encoder produced no output" }
            muxer!!.stop()

            if (audio != null) {
                try {
                    val videoUs = written * 1_000_000L / fps
                    if (AudioMixer.isAac(context, audio)) {
                        AudioMixer.mux(videoOut, { it.setDataSource(context, audio, null) }, out, videoUs)
                    } else {
                        AudioMixer.transcodeToAac(context, audio, m4a, videoUs) { ensureActive() }
                        AudioMixer.mux(videoOut, { it.setDataSource(m4a.path) }, out, videoUs)
                    }
                } finally {
                    videoOut.delete()
                    m4a.delete()
                }
            }
            out
        } catch (t: Throwable) {
            out.delete()
            videoOut.delete()
            m4a.delete()
            throw t
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { surface?.release() }
            runCatching { muxer?.release() }
        }
    }

    private companion object {
        const val W = 960
        const val H = 1280
    }
}
