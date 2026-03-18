#!/bin/sh

EXEC_FILE=$(ls -t mypay.mypaycore-springboot* | head -n 1)

nohup java -jar "$EXEC_FILE" &
