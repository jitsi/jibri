#!/bin/bash

# script that creates an archive in current folder
# containing the heap and thread dump and the current log file

JAVA_HEAPDUMP_PATH="/tmp/java_*.hprof"
STAMP=`date +%Y-%m-%d-%H%M`
jibri_USER="jibri"
jibri_UID=`id -u $jibri_USER`
RUNNING=""
unset PID

#Find any crashes in /var/crash from our user in the past 20 minutes, if they exist
CRASH_FILES=$(find /var/crash -name '*.crash' -uid $jibri_UID -mmin -20 -type f)
PID=$(systemctl show -p MainPID jibri 2>/dev/null | cut -d= -f2)

if [ ! -z $PID ]; then
   ps -p $PID | grep -q java
   [ $? -eq 0 ] && RUNNING="true"
fi
if [ ! -z $RUNNING ]; then
    echo "jibri pid $PID"
    THREADS_FILE="/tmp/stack-${STAMP}-${PID}.threads"
    HEAP_FILE="/tmp/heap-${STAMP}-${PID}.bin"
    sudo -u $jibri_USER jstack ${PID} > ${THREADS_FILE}
    sudo -u $jibri_USER jmap -dump:live,format=b,file=${HEAP_FILE} ${PID}
    tar zcvf jibri-dumps-${STAMP}-${PID}.tgz ${THREADS_FILE} ${HEAP_FILE} ${CRASH_FILES} /var/log/jitsi/jibri/* /tmp/hs_err_*
    rm ${HEAP_FILE} ${THREADS_FILE}
else
    ls $JAVA_HEAPDUMP_PATH >/dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo "jibri not running, but previous heap dump found."
        tar zcvf jibri-dumps-${STAMP}-crash.tgz $JAVA_HEAPDUMP_PATH ${CRASH_FILES} /var/log/jitsi/jibri/* /tmp/hs_err_*
        rm ${JAVA_HEAPDUMP_PATH}
    else
        echo "jibri not running, no previous dump found. Including logs only."
        tar zcvf jibri-dumps-${STAMP}-crash.tgz ${CRASH_FILES} /var/log/jitsi/jibri/* /tmp/hs_err_*
    fi
fi
