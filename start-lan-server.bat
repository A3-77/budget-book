@echo off
rem ASCII-only file name and content on purpose - see start-emulator.bat for why.
rem Serves this folder over the LAN so a phone on the same Wi-Fi can open the
rem web version (index.html) and add it to the home screen.
chcp 65001 >nul
title Budget Book - Phone Access
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo.
  echo   [!] Node.js not found. Install it first: https://nodejs.org
  echo.
  pause
  exit /b 1
)
node server.mjs
pause
