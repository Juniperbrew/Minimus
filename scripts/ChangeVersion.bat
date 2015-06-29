@echo off
Setlocal EnableDelayedExpansion

cd minimus
git pull

set /p version="Enter version: "
git checkout %version%

copy /y build\install\Minimus\bin\serverlist.txt ..\serverlist.txt
call gradlew installDist
copy /y ..\serverlist.txt build\install\Minimus\bin\serverlist.txt
del ..\serverlist.txt

cd /d %~dp0