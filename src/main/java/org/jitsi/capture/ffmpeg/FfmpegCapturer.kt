package org.jitsi.capture.ffmpeg

import org.jitsi.capture.Capturer
import org.jitsi.capture.CapturerParams
import org.jitsi.capture.ffmpeg.executors.FfmpegExecutor
import org.jitsi.capture.ffmpeg.executors.LinuxFfmpegExecutor
import org.jitsi.capture.ffmpeg.executors.MacFfmpegExecutor
import java.io.File
import java.io.IOException

fun String.isStreaming(): Boolean {
    return this.startsWith("rtmp://")
}

// Taken from https://stackoverflow.com/questions/35421699/how-to-invoke-external-command-from-within-kotlin-code
fun String.runCommand(workingDir: File): Process? {
    try {
        println("raw command string: $this")
        val parts = this.split("\\s".toRegex())
        println("parts: $parts")
        val proc = ProcessBuilder(*parts.toTypedArray())
                .directory(workingDir)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(File("/tmp/jibri-ffmpeg.out")))
                .redirectErrorStream(true)
                //.redirectError(ProcessBuilder.Redirect.PIPE)
                .start()

        return proc
        //proc.waitFor(60, TimeUnit.MINUTES)
        //return proc.inputStream.bufferedReader().readText()
    } catch(e: IOException) {
        e.printStackTrace()
        return null
    }
}

class FfmpegCapturer : Capturer {
    // FfmpegCapturer config values
    val resolution: String = "1280x720"
    val framerate: Int = 30
    val videoEncodePreset: String = "veryfast"
    val queueSize: Int = 4096
    val audioInputDevice: String = "hw:0,1,0"
    val streamingMaxBitrate: Int = 2976
    val streamingBufSize: Int = streamingMaxBitrate * 2
    // The range of the CRF scale is 0–51, where 0 is lossless,
    // 23 is the default, and 51 is worst quality possible. A lower value
    // generally leads to higher quality, and a subjectively sane range is
    // 17–28. Consider 17 or 18 to be visually lossless or nearly so;
    // it should look the same or nearly the same as the input but it
    // isn't technically lossless.
    // https://trac.ffmpeg.org/wiki/Encode/H.264#crf
    val h264ConstantRateFactor: Int = 25
    val gopSize: Int = framerate * 2
    val display: String = ":0"
    val workingDirectory: File = File("/Users/bbaldino")

    var currentFfmpegProc: Process? = null

    val ffmpegExecutor: FfmpegExecutor?

    init
    {
        //TODO: use this down the line
        ffmpegExecutor = when (System.getProperty("os.name"))
        {
            "Mac" -> MacFfmpegExecutor()
            "Linux" -> LinuxFfmpegExecutor()
            else -> null
        }
    }

    fun launchFfmpeg(format: String, sinkUri: String, sinkOptions: String): Process?
    {
        //"ffmpeg -f avfoundation -list_devices true -i \"\"".runCommand(workingDirectory)
        //Thread.sleep(2000)

        // avfoundation docs: https://ffmpeg.org/ffmpeg-devices.html#avfoundation
        val ffmpegCommandMac = "" +
                "ffmpeg -y -v info -thread_queue_size 4096 -f avfoundation -i 2: -r " +
                "$framerate -s $resolution " +
                "-acodec aac -strict -2 -ar 44100 " +
                "-c:v libx264 -preset $videoEncodePreset $sinkOptions -pix_fmt yuv420p -r $framerate -crf $h264ConstantRateFactor -g $gopSize -tune zerolatency " +
                "-f $format $sinkUri"
        currentFfmpegProc = ffmpegCommandMac.runCommand(workingDirectory)
        println("ffmpeg is alive? ${currentFfmpegProc?.isAlive()}")

        return currentFfmpegProc
    }

    override fun start(capturerParams: CapturerParams)
    {
        val (format, sinkOptions) = if (capturerParams.sinkUri.isStreaming())
        {
            Pair("flv", "-maxrate ${streamingMaxBitrate}k -bufsize ${streamingBufSize}k")
        }
        else
        {
            Pair("mp4", "-profile:v main -level 3.1")
        }

        launchFfmpeg(format, capturerParams.sinkUri, sinkOptions)
    }

    override fun isAlive(): Boolean
    {
        return currentFfmpegProc?.isAlive() ?: false
    }

    override fun getExitCode(): Int {
        // TODO is this the right default?
        return currentFfmpegProc?.exitValue() ?: 1
    }

    override fun stop()
    {
        //var inputStream = currentFfmpegProc?.inputStream
        currentFfmpegProc?.destroyForcibly()?.waitFor()
        println("FfmpegCapturer exited with ${currentFfmpegProc?.exitValue()}")
        //for (line in inputStream?.bufferedReader()?.lines())
    }
}