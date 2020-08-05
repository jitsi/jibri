Jibri is moving to a new config file.  The old config file/command-line-arguments will be supported for a while and eventually removed.  This doc is a guide on how to migrate from an "old" configuration to a new one.

The new configuration leverages the [Typesafe config](https://github.com/lightbend/config) library.  Default configuration values are now stored in [reference.conf](src/main/resources/reference.conf).  Overrides are written to a file and passed via a JVM parameter:
```
-Dconfig.file=/path/to/config.conf
```

### Property migrations
---
#### Notes
* No default values have been changed, so if you weren't specifying a property before you can continue to leave it unspecified and the value will not change.
* The new configuration library supports different formats and many different features in configuration (substitutions, includes, overrides via environment variables, etc.).  The text here describes the 'plainest' way to define a new config file, but other methods are possible.  Read about the Typesafe config to learn more.
* The example below assume that you will define a new configuration file called `jibri.conf`

---
##### Configuration file
Old method: path to a JSON file via command line argument `--config=/path/to/config.json`
New method: path to a HOCON .conf file passed via JVM arg: `-Dconfig.file=/path/to/config.conf`

---
##### Internal and external HTTP API ports
Old method: command line arguments `--http-api-port` and `--internal-http-port`
New method: in `jibri.conf`:
```hocon
jibri {
    http {
        external-api-port = <number>
        internal-api-port = <number>
    }
}
```
---

##### Single use mode
Old method: in `config.json`:
```json
{
    "single_use_mode": <Boolean>
}
```
New method: in `jibri.conf`:
```hocon
jibri {
    single-use-mode = <boolean>
}
```

---
##### Recordings directory
Old method: in `config.json`:
```json
{
    "recording_directory": <path>
}
```
New method: in `jibri.conf`:
```hocon
jibri {
    recording {
        recordings-directory = <path>
    }
}
```
---
##### Finalize script path
Old method: in `config.json`:
```json
{
    "finalize_recording_script_path": <path>
}
```
New method: in `jibri.conf`:
```hocon
jibri {
    recording {
        finalize-script = <path>
    }
}
```
---
##### Enabling statsD
Old method: in `config.json`:
```json
{
    "enable_stats_d": <boolean>
}
```
New method: in `jibri.conf`:
```hocon
jibri {
    stats {
        enable-stats-d = <boolean>
    }
}
```
---
##### XMPP environment config
Old method: in `config.json`:
```json
{
    "xmpp_environments": [
        {
            "name": <String>,
            "xmpp_server_hosts": List<String>,
            "xmpp_domain": <String>,
            "control_login": {
                "domain": <String>,
                "username": <String>,
                "password": <String>
            },
            "control_muc": {
                "domain": <String>,
                "room_name": <String>,
                "nickname": <String>
            },
            "call_login": {
                "domain": <String>,
                "username": <String>,
                "password": <String>
            },
            "room_jid_domain_string_to_strip_from_start": <String>,
            "usage_timeout": <number>
        },
        { <more> },
    ]
}
```
New method: in `jibri.conf`:
```hocon
jibri {
    environments = [
        {
            name = <String>
            xmpp-server-hosts = <List<String>>
            xmpp-domain = <String>

            control-muc {
                domain = <String>
                room-name = <String>
                nickname = <String>
            }

            control-login {
                domain = <String>
                username = <String>
                password = <String>
            }

            sip-control-muc {
                domain = <String>
                room-name = <String>
                nickname = <String>
            }

            call-login {
                domain = <String>
                username = <String>
                password = <String>
            }

            strip-from-room-domain = <String>
            usage-timeout = <Duration String i.e. '5 minutes' or '1 hour'>

            trust-all-xmpp-certs = <boolean>
        },
        { <more> }
    ]
}
```
An example of a new environments config can be found [here](example_xmpp_envs.conf).
