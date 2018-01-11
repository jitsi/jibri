package org.jitsi.selenium

import org.jitsi.capture.CapturerParams
import org.jitsi.capture.ffmpeg.FfmpegCapturer
import org.testng.annotations.Test

class JibriSeleniumTest
{

    @Test
    fun test()
    {
        val options = JibriSeleniumOptions(
                baseUrl = "https://meet.jit.si")
        val selenium = JibriSelenium(options)

        selenium.joinCall("test")
        Thread.sleep(10000);
        selenium.quitBrowser()
    }

    @Test
    fun ffmpegTest()
    {
        val ffmpeg = FfmpegCapturer()
        ffmpeg.start(CapturerParams("dummySinkUri"))
        Thread.sleep(5000)
        ffmpeg.stop()
    }
}