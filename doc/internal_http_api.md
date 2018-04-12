# Jibri Internal HTTP API
At startup, Jibri reads from a configuration file to determine which (if any) xmpp enviroments to connect to and to read some other configuration data.  Jibri takes a simple approach of only reading this file at startup, so if changes are made to the file and you want Jibri to read them, Jibri needs to be restarted.  Obviously one would prefer not to restart Jibri while it's busy, so Jibri has an internal API to notify it of config file changes.  When the API is called, Jibri will schedule a shutdown for the next time it's idle.  It is up for whatever is managing Jibri to restart it.

##### URL
`/jibri/api/internal/v1.0/notifyConfigChanged`
##### Method
`POST`
##### URL Params
None
##### Data Params
None
##### Response
If Jibri is currently idle, no respose will be sent as Jibri will shutdown immediately.  If Jibri is currently busy, it will respond with a `200`
