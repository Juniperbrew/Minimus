@echo off
Setlocal EnableDelayedExpansion

cd minimus
git fetch
git branch -a

ECHO NOTE: Using this will disable auto updates until CheckoutBranch.bat is used

set /p branch="Enter branch: "

set /p date="Enter date: "

set first=1
set commit=""
for /f "delims=" %%a in ('git rev-list --since=%date% --reverse %branch%') do (
	IF !first!==1 (
	set commit=%%a
	set first=0
	)
)

ECHO Checking out commit %commit%
set command="git show -s --format=%%ci !commit!"
set actualDate=
for /f "delims=" %%a in ('!command!') do set actualDate=%%a
echo %actualDate%

git rev-list -n1 --before=%date% %branch% | xargs git checkout

call gradlew installDist

cd /d %~dp0
PAUSE