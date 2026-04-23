@echo off
setlocal

echo [UniversalAttributes] Bootstrapping Gradle wrapper...
call gradlew.bat --version >nul 2>nul
if %errorlevel% neq 0 (
  echo ERROR: Failed to bootstrap Gradle wrapper.
  echo Ensure internet access is available, then run setup.bat again.
  exit /b 1
)

echo Done. You can now run:
echo   gradlew.bat build
exit /b 0
