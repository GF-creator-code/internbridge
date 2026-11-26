@echo off
rem Start delivery for all platforms via local backend (default port 9527)
setlocal
set LOG=%~dp0start_delivery.log
echo [%date% %time%] start >> "%LOG%"
curl -s -m 10 http://localhost:9527/api/shixiseng/status >nul 2>&1
if errorlevel 1 (
  echo [%date% %time%] backend not ready, abort >> "%LOG%"
  exit /b 1
)
for %%P in (shixiseng liepin 51job jobsdb zhilian boss) do (
  curl -s -m 20 -X POST http://localhost:9527/api/%%P/start >> "%LOG%" 2>&1
  echo. >> "%LOG%"
)
echo [%date% %time%] done >> "%LOG%"
exit /b 0
