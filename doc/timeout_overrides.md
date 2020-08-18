# Timeout overrides

Jibri is using several timeouts, which allow it to control and monitor the recording. It is possible to adjust the timeout values and tailor its operations to more specific requirements.

The configuration options are set in the configuration file. Both versions of the configuration file (HOCON and JSON) are supported.

### No Media Timeout
If all clients have their audio and video muted and if Jibri does not detect any data stream (audio or video) comming in, it will stop recording after `NO_MEDIA_TIMEOUT` expires.

**Default timeout:** 30 s

**Configuration option unit:** seconds

Old config (JSON):
```json
{
    "no_media_timeout": <number>
}
```

New config (HOCON):
```hocon
jibri {
    no-media-timeout = <number>
}
```

### All Muted Timeout
If all clients have their audio and video muted, Jibri consideres this as an empty call and stops the recording after `ALL_MUTED_TIMEOUT` expires.

**Default timeout:** 10 min

**Configuration option unit:** minutes

Old config (JSON):
```json
{
    "all_muted_timeout": <number>
}
```

New config (HOCON):
```hocon
jibri {
    all-muted-timeout = <number>
}
```


### Default call empty timeout
When detecting if a call is empty, Jibri takes into consideration for how long the call has been empty already. If it has been empty for more than `DEFAULT_CALL_EMPTY_TIMEOUT`, it will consider it empty and stop the recording.

**Default timeout:** 30 s

**Configuration option unit:** seconds

Old config (JSON):
```json
{
    "default_call_empty_timeout": <number>
}
```

New config (HOCON):
```hocon
jibri {
    default-call-empty-timeout = <number>
}
```
