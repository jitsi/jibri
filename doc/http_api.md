# Jibri HTTP API

Jibri has a somewhat-implemented HTTP API to mirror the XMPP API with the following endpoints:

##### URL
`/jibri/api/v1.0/health`
##### Method
`GET`
##### URL Params
None
##### Data Params
None
##### Response
Code: `200`
Body:
```
{
    "busy": Boolean // true if Jibri is currently busy, false otherwise
}
```
This call should always respond with a `200` and the status encoded in the response body.  The lack of any response would represent an error of some sort


##### URL
`/jibri/api/v1.0/startService`
##### Method
`POST`
##### URL Params
None
##### Data Params
```
{
	"callParams": {
		"callUrlInfo": {
			"baseUrl": String, // the base url of the call (e.g. https://meet.jit.si)
			"callName": String // the call name to be appended to the base url
		},
		"callLoginParams": {
			"domain": String, // The xmpp domain the Jibri client should log into when joining the call
			"username": String, // The username to use for logging in to the above domain
			"password": String // The password to use for logging in to the above domain
		}
	},
	"sinkType": String, // "stream" for streaming, "file" for recording
	"youTubeStreamKey": String // If using "stream" above, this is the YouTube stream key to use
	"rtmpUrl": String // If using "stream" above, this is the full RTMP URL to broadcast to. Overrides youtubeStreamKey if both are present.
}
```
##### Success Response
Code: `200`
Body: None
##### Error Response
Code: `412 Precondition Failed` // When the Jibri is already busy
Body: None
Code: `500 Internal Server Error` // When an internal error occurs
Body: None

##### URL
`/jibri/api/v1.0/stopService`
##### Method
`POST`
##### URL Params
None
##### Data Params
None
##### Response
Code: `200`
Body: None

This call should always respond with a `200`.  The lack of any response would represent an error of some sort.

Known HTTP API limitations:
* No push status updates.
  * No way currently exists for interested parties to be notified of Jibri status changes.  Queries must be made to the `health` endpoint.
* No way of passing Jitsi Meet call credentials.
  * When Jibri joins a call in order to capture the media, it can use a special set of credentials so that it doesn't show up like a normal participant in the meeting.  The HTTP API does not currently have a way of passing-in or configuring these credentials.
