@echo off
rem ASCII-only file name and content on purpose - see start-emulator.bat for why.
chcp 65001 >nul
title Build BudgetBook APK
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo.
  echo   [!] Node.js not found. Install it first: https://nodejs.org
  echo.
  pause
  exit /b 1
)
node build-apk.mjs
pause
