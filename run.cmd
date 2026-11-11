@echo off
REM ---------------------------------------------------------------------------
REM SAP Transport & Change Management Analyzer - convenience runner (Windows)
REM
REM   run.cmd            build, test and start the analyzer
REM   run.cmd test       run the test suite only
REM   run.cmd package    build without starting
REM   run.cmd clean      remove build output
REM ---------------------------------------------------------------------------
setlocal
cd /d "%~dp0"

if "%~1"=="" goto run
if /I "%~1"=="test" goto test
if /I "%~1"=="package" goto package
if /I "%~1"=="clean" goto clean
goto run

:test
call mvn -B test
goto end

:package
call mvn -B clean package
goto end

:clean
call mvn -B clean
goto end

:run
echo.
echo Building the SAP Transport ^& Change Management Analyzer...
call mvn -B clean package
if errorlevel 1 goto end

echo.
echo Starting on http://localhost:8085
echo Sign in with change.manager / demo1234 for the full view.
echo.
java -jar target\sap-transport-analyzer.jar
goto end

:end
endlocal
