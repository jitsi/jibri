# Jibri 'Webhooks'

Jibri supports being configured with a list of base URLs on which it will hit certain endpoints with data.  These are
not "true" webhooks as the endpoints are hard-coded, instead Jibri defines a "contract" which it expect a subscriber
to implement at the given base URL.  Information about this contract is below.

### Status updates
Jibri pushes status updates consisting of its "busy status" (whether it is busy or idle) and its health.  These updates
are sent periodically every minute and every time the status changes.

URL: `/v1/status`

method: `POST`

Data constraints:
```$json
{
    "jibriId":"[String]",
    "status":{
        "busyStatus":"[a String value of ComponentBusyStatus: (BUSY|IDLE|EXPIRED)]",
        "health": {
            "healthStatus":"[a String value of ComponentHealthStatus: (HEALTHY|UNHEALTHY)",
            "details": [A map of String to ComponentHealthDetails giving optional details of sub-component's health]
        }
    }
}
```

Data example:
```$json
{
    "jibriId":"jibri_id",
    "status":{
        "busyStatus":"IDLE",
        "health":{
            "healthStatus":"HEALTHY",
            "details":{}
        }
    }
}
```

Success response:

Code: `200 OK`

Data: body will be ignored
