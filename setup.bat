@echo off
setlocal

echo [UniversalAttributes] Preparing Gradle wrapper...
where gradle >nul 2>nul
if %errorlevel% neq 0 (
  echo ERROR: Gradle is not installed or not on PATH.
  echo Install Gradle 8.x and re-run setup.bat.
  exit /b 1
)

gradle wrapper --gradle-version 8.14.3
if %errorlevel% neq 0 (
  echo ERROR: Failed to generate Gradle wrapper.
  exit /b 1
)

echo Done. You can now run:
echo   gradlew.bat build
exit /b 0
