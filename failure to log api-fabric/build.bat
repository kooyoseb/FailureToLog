@echo off
setlocal
cd /d "%~dp0"

echo Building FailureToLog API Fabric mod...
call gradlew.bat build
if errorlevel 1 (
    echo.
    echo Build failed.
    pause
    exit /b 1
)

echo.
echo Build complete.
echo Output: %cd%\build\libs
pause
