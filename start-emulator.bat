@echo off
rem This file is intentionally 100%% ASCII, both name and content.
rem Two reasons, both learned the hard way:
rem   1. a Chinese *file name* breaks when the path is passed through a shell
rem      that is not Unicode-clean (cmd reports "cannot find the path");
rem   2. multi-byte characters in the *body* break cmd's own read-position
rem      tracking right after `chcp`, silently skipping the rest of the script.
rem So: no Chinese anywhere in here. The Node script prints all the Chinese,
rem writes it to a cp65001 console, and also saves a startup log file.
chcp 65001 >nul
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo.
  echo   [!] Node.js not found. Install it first: https://nodejs.org
  echo.
  pause
  exit /b 1
)
node tools\emulator.mjs
if errorlevel 1 pause
ping -n 4 127.0.0.1 >nul
exit /b 0
