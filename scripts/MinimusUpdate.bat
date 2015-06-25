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
copy /y build\install\Minimus\bin\serverlist.txt ..\serverlist.txt
call gradlew installDist
move ..\serverlist.txt build\install\Minimus\bin\serverlist.txt
)
)