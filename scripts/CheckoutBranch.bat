@echo off
Setlocal EnableDelayedExpansion

cd minimus
git fetch
git branch -a

set /p branch="Enter branch: "
git checkout %branch%

copy /y build\install\Minimus\bin\serverlist.txt ..\serverlist.txt
call gradlew installDist
copy /y ..\serverlist.txt build\install\Minimus\bin\serverlist.txt
del ..\serverlist.txt

cd /d %~dp0