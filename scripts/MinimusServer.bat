@echo off
call MinimusUpdate

cd minimus/build/install/Minimus/bin
call MinimusServer
PAUSE
cd /d %~dp0