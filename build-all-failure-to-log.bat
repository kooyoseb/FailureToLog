@echo off
setlocal

set "ROOT=%~dp0"
set "PLUGIN_DIR=%ROOT%failure to log"
set "FABRIC_DIR=%ROOT%failure to log api-fabric"
set "DIST_DIR=%ROOT%dist"

if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"

echo ============================================================
echo Building FailureToLog Paper plugin
echo ============================================================
pushd "%PLUGIN_DIR%"
call gradlew.bat build
if errorlevel 1 (
    popd
    echo.
    echo Paper plugin build failed.
    pause
    exit /b 1
)
popd

echo.
echo ============================================================
echo Building FailureToLog API Fabric mod
echo ============================================================
pushd "%FABRIC_DIR%"
call gradlew.bat build
if errorlevel 1 (
    popd
    echo.
    echo Fabric mod build failed.
    pause
    exit /b 1
)
popd

echo.
echo Copying release jars to dist...
copy /Y "%PLUGIN_DIR%\build\libs\failure to log-1.0-SNAPSHOT.jar" "%DIST_DIR%\FailureToLog-Paper-1.0-SNAPSHOT.jar" >nul
if errorlevel 1 (
    echo Failed to copy Paper plugin jar.
    pause
    exit /b 1
)

copy /Y "%FABRIC_DIR%\build\libs\failure-to-log-api-fabric-1.0.0.jar" "%DIST_DIR%\FailureToLog-API-Fabric-1.0.0.jar" >nul
if errorlevel 1 (
    echo Failed to copy Fabric mod jar.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo Build complete.
echo Dist folder: %DIST_DIR%
echo Files:
echo   FailureToLog-Paper-1.0-SNAPSHOT.jar
echo   FailureToLog-API-Fabric-1.0.0.jar
echo ============================================================
pause
