@echo off
Setlocal EnableDelayedExpansion

IF NOT EXIST Minimus\NUL ( 
git clone https://github.com/Juniperbrew/Minimus
cd Minimus
call gradlew installDist
) ELSE (
cd Minimus
set upToDate=
for /f "delims=" %%a in ('git pull') do @set upToDate=%%a
ECHO !upToDate!
IF NOT "!upToDate!" == "Already up-to-date." call gradlew installDist
)

cd build/install/Minimus/bin
cls
MinimusServer