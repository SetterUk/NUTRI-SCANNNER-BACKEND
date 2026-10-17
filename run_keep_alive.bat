@echo off
title Render Keep-Alive Service
cd /d "%~dp0"
echo Starting Render Keep-Alive Service...
python keep_alive.py
pause
