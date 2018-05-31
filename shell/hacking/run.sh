#!/bin/sh
if [ ! -e ../build/telnet-proxy ]; then
  ./build.sh
fi
echo "Connect via proxy using: telnet localhost 7023"
../build/telnet-proxy localhost 6523 7023

