@echo off
setlocal
set "MAVEN_VERSION=3.9.9"
set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%"
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  echo Bootstrapping Apache Maven %MAVEN_VERSION% into the user Maven cache...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; $mavenHome=$env:USERPROFILE + '\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%'; $parent=Split-Path -Parent $mavenHome; New-Item -ItemType Directory -Force -Path $parent | Out-Null; $zip=Join-Path $env:TEMP 'apache-maven-%MAVEN_VERSION%-bin.zip'; Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile $zip; Expand-Archive -LiteralPath $zip -DestinationPath $parent -Force; Remove-Item -LiteralPath $zip -Force"
  if errorlevel 1 exit /b 1
)
call "%MAVEN_HOME%\bin\mvn.cmd" -f "%~dp0pom.xml" %*
exit /b %errorlevel%
