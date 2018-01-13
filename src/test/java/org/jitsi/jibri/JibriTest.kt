package org.jitsi.jibri

import org.jitsi.jibri.selenium.JibriSelenium
import org.jitsi.jibri.selenium.JibriSeleniumOptions
import org.testng.annotations.Test

class JibriTest
{

    @Test
    fun jibriTest()
    {
        val jibri = Jibri()
        jibri.startRecording(JibriOptions(
                recordingMode = RecordingMode.FILE,
                callName = "jibritest"
        ))
        Thread.sleep(10000)
        jibri.stopRecording()
    }

    @Test
    fun jibriSeleniumTest()
    {
        val options = JibriSeleniumOptions(
                baseUrl = "https://meet.jit.si")
        val selenium = JibriSelenium(options)

        selenium.joinCall("test")
        Thread.sleep(30000)
        selenium.leaveCallAndQuitBrowser()
    }

    @Test
    fun ffmpegTest()
    {
//        val ffmpeg = FfmpegCapturer()
//        ffmpeg.start(CapturerParams("dummySinkUri"))
//        Thread.sleep(5000)
//        ffmpeg.stop()
    }
}