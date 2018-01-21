package org.jitsi.jibri.api.rest

import org.jitsi.jibri.Jibri
import org.jitsi.jibri.JibriOptions
import org.jitsi.jibri.RecordingSinkType
import org.jitsi.jibri.StartRecordingResult
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

//TODO(brian): i had to put default values here or else jackson would fail
// to parse it as part of a request, though from what i've read this
// shouldn't be necessary?
// https://github.com/FasterXML/jackson-module-kotlin
// https://craftsmen.nl/kotlin-create-rest-services-using-jersey-and-jackson/
data class StartRecordingParams(
        val baseUrl: String = "",
        val callName: String = "",
        val sinkType: RecordingSinkType = RecordingSinkType.FILE
)

@Path("/jibri/api/v1.0")
class RestApi(val jibri: Jibri) {
    @GET
    @Path("health")
    @Produces(MediaType.APPLICATION_JSON)
    fun health(): Response {
        println("Got health request")
        return Response.ok(jibri.healthCheck()).build()
    }

    /**
     * Start recording will start a new recording using the given params
     * immediately
     */
    @POST
    @Path("startRecording")
    @Consumes(MediaType.APPLICATION_JSON)
    fun startRecording(recordingParams: StartRecordingParams): Response {
        println("Got startRecording request with params $recordingParams")
        val result = jibri.startRecording(JibriOptions(
                recordingSinkType = recordingParams.sinkType,
                baseUrl = recordingParams.baseUrl,
                callName =  recordingParams.callName
        ))
        val response = when (result) {
            StartRecordingResult.OK -> Response.ok().build()
            StartRecordingResult.ALREADY_RECORDING -> Response.status(Response.Status.PRECONDITION_FAILED).build()
            StartRecordingResult.ERROR -> Response.status(Response.Status.INTERNAL_SERVER_ERROR).build()
        }
        return response
    }

    /**
     * Stop recording will stop the current recording immediately
     */
    @POST
    @Path("stopRecording")
    fun stopRecording(): Response {
        println("Got stop recording request")
        jibri.stopRecording()
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