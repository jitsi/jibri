#!/bin/bash
RECORDING_PATH="$1"
[ -z "$RECORDING_PATH" ] && RECORDING_PATH="./recordings"
LPATHS=""
for file in $(find $RECORDING_PATH -type f); do
    LPATH=${file#$RECORDING_PATH}
    LPATHS="$LPATHS $LPATH"
    echo "copying $LPATH"
    #this would be a great place to put your own custom archival commands
    echo "removing $LPATH"
    rm $file
done

#now build JSON for notification to a URL
if [ ! -z "$LPATHS" ]; then
    LPJSON="{\"paths\":["
    for LPATH in $LPATHS; do
        LPJSON="$LPJSON\"$LPATH\","
    done;
    LPJSON=${LPJSON%,}
    LPJSON="${LPJSON}]}"

    #here is a good place to start with a curl that posts to your custom endpoint notifying about the presence of new files
    #curl -v -X POST \
    #  -H "Content-Type: application/json" \
    #  --data "$LPJSON" "${NOTIFY_URL}"
    #clear out any empty directories
    for d in $(find $RECORDING_PATH/* -type d ); do
        [ -d "$d" ] && rmdir -p $d
    done
fi