#!/bin/bash
#
# 1. The script issues shutdown command via the graceful shutdown command
#    If unsuccessful then it exits with 1.
# 2. If the code is ok then it checks if jibri has exited.
# 3. If not then it polls jibri statistics until recording status is not true
# 4. Gives some time for jibri to shutdown. If it does not quit after that
#    time then it kills the process. If the process was successfully killed 0 is
#    returned and 1 otherwise.
#
#   NOTE: script depends on the tool jq, used to parse json, and curl to access URLs
#

# Initialize arguments
CURL_BIN="/usr/bin/curl"

# URL for jibri status
STATUS_URL="http://localhost:2222/jibri/api/v1.0/health"
# URL to POST to signal jibri stop/cleanup
STOP_URL="http://localhost:2222/jibri/api/v1.0/stopService"

# wait this long before failing stop curl command
STOP_TIMEOUT=3600
# wait this long before failing status curl command
STATUS_TIMEOUT=30

# sleep this long between checking jibri status
SLEEP_TIME=10
# delay shutdown AT MOST 6 hours = 21600 seconds
TERMINATION_DELAY_TIMEOUT=21600

function getJibriStatus() {
    $CURL_BIN --max-time $STATUS_TIMEOUT $STATUS_URL 2>/dev/null
}

function stopJibri() {
    $CURL_BIN -H 'Content-Type: application/json' -d '{}' --max-time $STOP_TIMEOUT $STOP_URL 2>/dev/null
}

function isRecording() {
    STATUS=`getJibriStatus`
    echo $STATUS | jq -r ".status.busyStatus"
}

verbose=1

# Parse arguments
OPTIND=1
while getopts "h:t:s" opt; do
    case "$opt" in
    h)
        STATUS_URL=$OPTARG
        ;;
    t)
        STATUS_TIMEOUT=$OPTARG
        ;;
    s)
        verbose=0
        ;;
    esac
done
shift "$((OPTIND-1))"


# Prints info messages
function printInfo {
    if [ "$verbose" == "1" ]
    then
        echo "$@"
    fi
}

# Prints errors
function printError {
    echo "$@" 1>&2
}

function gracefulShutdownJibri() {
    /opt/jitsi/jibri/graceful_shutdown.sh
}

pid=$(/bin/systemctl show -p MainPID jibri 2>/dev/null | cut -d= -f2)

echo -n "Graceful shutdown signal..."
gracefulShutdownJibri
shutdownStatus=$?
echo "sent"
if [ $shutdownStatus == 0 ];
then
    printInfo "Graceful shutdown started"
    recordingStatus=`isRecording`
    SLEEP_COUNT=0
    while [[ $recordingStatus == "BUSY" ]]; do
        if [[ $SLEEP_COUNT -ge $TERMINATION_DELAY_TIMEOUT ]]; then
            printInfo "WAITED $TERMINATION_DELAY_TIMEOUT seconds, stopping jibri."
            # send signal to stop streaming/recording and upload any recordings pending
            stopJibri
            # mark jibri service as intentionally stopped to avoid restarts
            /usr/sbin/service jibri stop
            # stop looping
            break;
        fi
        printInfo "A recording is in progress"
        sleep $SLEEP_TIME
        SLEEP_COUNT=$(( $SLEEP_COUNT + $SLEEP_TIME ))
        recordingStatus=`isRecording`
    done

    sleep 5

    # mark jibri service as intentionally stopped to avoid restarts
    /usr/sbin/service jibri stop

    if ps -p $pid > /dev/null 2>&1
    then
        printInfo "It is still running, lets give it $STATUS_TIMEOUT seconds"
        sleep $STATUS_TIMEOUT
        if ps -p $pid > /dev/null 2>&1
        then
            printError "Jibri did not exit after $STATUS_TIMEOUT sec - killing $pid"
            kill $pid
        else
            printInfo "Jibri shutdown OK"
            exit 0
        fi
    else
        printInfo "Jibri shutdown OK"
        exit 0
    fi
    # check for 3 seconds if we managed to kill
    for I in 1 2 3
    do
        if ps -p $pid > /dev/null 2>&1
        then
            sleep 1
        fi
    done
    if ps -p $pid > /dev/null 2>&1
    then
        printError "Failed to kill $pid"
        printError "Sending force kill to $pid"
        kill -9 $pid
        if ps -p $pid > /dev/null 2>&1
        then
            printError "Failed to force kill $pid"
            exit 1
        fi
    fi
    printInfo "Jibri shutdown OK"
    exit 0
else
    printError "Failed to signal shutdown of Jibri: $shutdownStatus"
    exit 1
fi