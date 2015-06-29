@echo off
Setlocal EnableDelayedExpansion

IF NOT EXIST Minimus ( 
    git clone https://github.com/Juniperbrew/Minimus
    cd Minimus
    call gradlew installDist
) ELSE (
    cd Minimus
    set branch=
    for /f "delims=" %%a in ('git rev-parse --abbrev-ref HEAD') do @set branch=%%a
    IF NOT "!branch!" == "HEAD" (
        set upToDate=
        for /f "delims=" %%a in ('git pull') do @set upToDate=%%a
        ECHO !upToDate!
        IF NOT "!upToDate!" == "Already up-to-date." (
            copy /y build\install\Minimus\bin\serverlist.txt ..\serverlist.txt
            call gradlew installDist
            copy /y ..\serverlist.txt build\install\Minimus\bin\serverlist.txt
            del ..\serverlist.txt
        )
    ) ELSE (
        ECHO Program cannot be updated in this state, use CheckoutBranch.bat to enable auto updates.
    )
)