package org.jitsi.jibri.api.rest

import org.jitsi.jibri.*
import org.jitsi.jibri.util.debug
import java.util.logging.Logger
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

//TODO(brian): i had to put default values here or else jackson would fail
// to parse it as part of a request, though from what i've read this
// shouldn't be necessary?
// https://github.com/FasterXML/jackson-module-kotlin
// https://craftsmen.nl/kotlin-create-rest-services-using-jersey-and-jackson/
data class StartRecordingParams(
        val callUrlInfo: CallUrlInfo = CallUrlInfo(),
        val sinkType: RecordingSinkType = RecordingSinkType.FILE,
        val streamUrl: String = ""
)

@Path("/jibri/api/v1.0")
class RestApi(val jibriManager: JibriManager) {
    private val logger = Logger.getLogger(this::class.simpleName)

    @GET
    @Path("health")
    @Produces(MediaType.APPLICATION_JSON)
    fun health(): Response {
        logger.debug("Got health request")
        return Response.ok(jibriManager.healthCheck()).build()
    }

    /**
     * Start recording will start a new recording using the given params
     * immediately
     */
    @POST
    @Path("startRecording")
    @Consumes(MediaType.APPLICATION_JSON)
    fun startRecording(recordingParams: StartRecordingParams): Response {
        // Map the single call into the specific service type, in the future
        // look at different REST calls for this
        val result: StartServiceResult = when (recordingParams.sinkType) {
            RecordingSinkType.FILE -> {
                jibriManager.startFileRecording(FileRecordingParams(
                        callUrlInfo = recordingParams.callUrlInfo
                ))
            }
            RecordingSinkType.STREAM -> {
                jibriManager.startStreaming(StreamingParams(
                        callUrlInfo = recordingParams.callUrlInfo,
                        streamUrl = recordingParams.streamUrl
                ))
            }
            else -> TODO()
        }
        val response = when (result) {
            StartServiceResult.SUCCESS -> Response.ok().build()
            StartServiceResult.BUSY -> Response.status(Response.Status.PRECONDITION_FAILED).build()
            StartServiceResult.ERROR -> Response.status(Response.Status.INTERNAL_SERVER_ERROR).build()
        }
        return response
    }

    /**
     * Stop recording will stop the current recording immediately
     */
    @POST
    @Path("stopRecording")
    fun stopRecording(): Response {
        logger.debug("Got stop recording request")
        jibriManager.stopService()
        return Response.ok().build()
    }

    @POST
    @Path("reloadConfig")
    fun reloadConfig(): Response {
        logger.debug("Got reload config reuest")
        jibriManager.reloadConfig()
        return Response.ok().build()
    }


//    @GET
//    @Path("hello")
//    @Produces(MediaType.TEXT_PLAIN)
//    fun helloWorld(): String {
//        return "Hello, world!"
//    }
//
//    @GET
//    @Path("param")
//    @Produces(MediaType.TEXT_PLAIN)
//    fun paramMethod(@QueryParam("name") name: String): String {
//        return "Hello, " + name
//    }
//
//    @GET
//    @Path("path/{var}")
//    @Produces(MediaType.TEXT_PLAIN)
//    fun pathMethod(@PathParam("var") name: String): String {
//        return "Hello, " + name
//    }
//
//    @POST
//    @Path("post")
//    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
//    @Produces(MediaType.TEXT_HTML)
//    fun postMethod(@FormParam("name") name: String): String {
//        return "<h2>Hello, $name</h2>"
//    }
//
//    @POST
//    @Path("postjson")
//    @Consumes(MediaType.APPLICATION_JSON)
//    @Produces(MediaType.APPLICATION_JSON)
//    fun postJsonMethod(jsonData: Map<String, String>): String {
//        return jsonData.toString()
//    }
}