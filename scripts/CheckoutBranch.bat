@echo off
Setlocal EnableDelayedExpansion

cd minimus
git fetch
git branch -a

set /p branch="Enter branch: "
git checkout %branch%

call gradlew installDist

cd /d %~dp0