package org.jitsi.jibri

import org.jitsi.jibri.util.Tail
import org.jitsi.jibri.util.bitrate
import org.jitsi.jibri.util.dataSize
import org.jitsi.jibri.util.decimal
import org.jitsi.jibri.util.oneOrMoreDigits
import org.jitsi.jibri.util.speed
import org.jitsi.jibri.util.timestamp
import org.jitsi.jibri.util.zeroOrMoreSpaces
import org.testng.annotations.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.function.Consumer
import java.util.regex.Pattern

class TempTest {

    @Test
    fun test() {
//        val pb = ProcessBuilder("ffmpeg -y -v info -thread_queue_size 4096 -f avfoundation -framerate 30 -video_size 1280x720 -i 0:0 -vsync 2 -acodec aac -strict -2 -ar 44100 -c:v libx264 -preset veryfast -maxrate 2976k -bufsize 5952k -pix_fmt yuv420p -crf 25 -g 60 -tune zerolatency -f flv rtmp://a.rtmp.youtube.com/live2/gx3c-aw44-hkda-5wrt".split(" "))
//        pb.redirectErrorStream(true)
//        //pb.redirectOutput(File("/tmp/ffmpeg.out"))
//        val p = pb.start()
//        println("Process started")
//
//        //val or = OutputReader(p.inputStream)
//
//        //val reader = BufferedReader(InputStreamReader(p.inputStream))
//        val t = Tail(p.inputStream)
//
//        Thread.sleep(5000)
//        println("Sleep done")
//        println("Got most recent line: ${t.mostRecentLine}")
//        val digit = """\d"""
//        val oneOrMoreDigits = "$digit+"
//        val decimal =  """${oneOrMoreDigits}\.${oneOrMoreDigits}"""
//        val string = """\D*"""
//        val dataSize = "$oneOrMoreDigits$string"
//        val timestamp = """${oneOrMoreDigits}\:${oneOrMoreDigits}\:${oneOrMoreDigits}\.${oneOrMoreDigits}"""
//        val bitrate = "$decimal$string"
//        val speed = "${decimal}x"
//        val space = """\s"""
//        val zeroOrMoreSpaces = "$space*"

        val fields = listOf(
            Pair("frame", oneOrMoreDigits),
            Pair("fps", oneOrMoreDigits),
            Pair("q", decimal),
            Pair("size", dataSize),
            Pair("time", timestamp),
            Pair("bitrate", bitrate),
            Pair("speed", speed)
        )
        val pattern = fields
            // Format each field name and value to how they appear in the output line, with support for any number
            // of spaces in between (and name each regex group according to the field name)
            // <fieldName>= value
            .map { (fieldName, value) -> "$fieldName=$zeroOrMoreSpaces(?<$fieldName>$value)$zeroOrMoreSpaces" }
            // Concatenate all the individual fields into one regex pattern string
            .fold("") { pattern, currentField -> "$pattern$currentField" }
        println("got pattern: $pattern")
        val regex = Pattern.compile(pattern)

        val matcher = regex.matcher("frame=  109 fps= 31 q=26.0 size=     641kB time=00:00:03.96 bitrate=1323.2kbits/s speed=1.12x")
        matcher.find()
        println("frame: ${matcher.group("frame")}")
        println("done")
        //p.destroyForcibly()





//        println("Sleep done")
//        for (i in 0..10) {
//            println(or.mostRecentLine)
//            Thread.sleep(2000)
//        }
//        or.stop()



        //val last = reader.lines().reduce { first, second -> second }
        //println(last)
//        val count = reader.lines().count()
//        println(reader.lines().skip(count))
//        val mostRecentLine = reader.lines().limit(1)
//        for (line in mostRecentLine) {
//            println(line)
//        }
//        println("got line: ${reader.readLine()}")
//        for (line in reader.lines()) {
//            println(line)
//        }
    }
}