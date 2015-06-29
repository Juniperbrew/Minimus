@echo off
call MinimusUpdate

cd minimus/build/install/Minimus/bin
call Minimus
PAUSE
cd /d %~dp0