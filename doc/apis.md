# Jibri APIs
Jibri has two types of APIs:
1. One is for 'external' use and is used to control stopping and starting of Jibri services, as well as querying Jibri's health.
1. The other is 'internal' and is used to notify Jibri that there has been a change to its config file.

There is an HTTP implementation of the 'internal' API which lives in `InternalHttpApi.kt`, documentation for it can be found [here](internal_http_api.md).

The external API has both XMPP and HTTP implementations, however the HTTP implementation is not fully developed (though it's close to complete functionality).  Detailed documentation for the XMPP API can be found [here](xmpp_api.md), and for the HTTP API [here](http_api.md).

In general, the 'external' APIs boil down to the following available actions:
1. Start a service
1. Stop a service
1. Query the current 'status' of Jibri (is it busy or is it available to start a new service)

At this time, Jibri only runs a single service at a time, so if it has one running it will consider itself "busy".
