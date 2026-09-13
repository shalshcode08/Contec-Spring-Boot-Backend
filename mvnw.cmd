@REM Maven wrapper: downloads the distribution in .mvn\wrapper\maven-wrapper.properties on first use.
@echo off
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "PROPS=%SCRIPT_DIR%.mvn\wrapper\maven-wrapper.properties"

if not exist "%PROPS%" (
  echo mvnw: cannot find %PROPS% 1>&2
  exit /b 1
)

set "DISTRIBUTION_URL="
for /f "usebackq tokens=1,* delims==" %%A in ("%PROPS%") do (
  if "%%A"=="distributionUrl" set "DISTRIBUTION_URL=%%B"
)

if "%DISTRIBUTION_URL%"=="" (
  echo mvnw: distributionUrl is not set in %PROPS% 1>&2
  exit /b 1
)

for %%F in ("%DISTRIBUTION_URL%") do set "ARCHIVE=%%~nxF"
set "DIST_NAME=%ARCHIVE:-bin.zip=%"

set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\%DIST_NAME%"

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  echo mvnw: downloading %DISTRIBUTION_URL% 1>&2
  set "TMP_DIR=%TEMP%\mvnw-%RANDOM%"
  mkdir "!TMP_DIR!"
  powershell -NoProfile -Command "Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '!TMP_DIR!\%ARCHIVE%'" || exit /b 1
  powershell -NoProfile -Command "Expand-Archive -Path '!TMP_DIR!\%ARCHIVE%' -DestinationPath '!TMP_DIR!\unpacked' -Force" || exit /b 1
  mkdir "%USERPROFILE%\.m2\wrapper\dists" 2>nul
  move "!TMP_DIR!\unpacked\%DIST_NAME%" "%MAVEN_HOME%" >nul
  rmdir /s /q "!TMP_DIR!"
)

set "MAVEN_PROJECTBASEDIR=%SCRIPT_DIR%"
call "%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
