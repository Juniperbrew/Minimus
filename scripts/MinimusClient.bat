@echo off
Setlocal EnableDelayedExpansion

IF NOT EXIST Minimus ( 
git clone https://github.com/Juniperbrew/Minimus
cd Minimus
call gradlew installDist
) ELSE (
cd Minimus
set upToDate=
for /f "delims=" %%a in ('git pull') do @set upToDate=%%a
ECHO !upToDate!
IF NOT "!upToDate!" == "Already up-to-date." (
move ..\serverlist.txt build\install\Minimus\bin\serverlist.txt
call gradlew installDist
copy /y Minimus\build\install\Minimus\bin\serverlist.txt serverlist.txt
)
)

cd build/install/Minimus/bin
Minimus