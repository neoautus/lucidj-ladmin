#!/bin/sh
if [ ! -d ../build ]; then
  mkdir ../build
fi
gcc libtelnet.c telnet-proxy.c -o ../build/telnet-proxy
echo "Executable file available on:"
ls -l ../build/telnet-proxy

