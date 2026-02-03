@echo off
setlocal

rem Use UTF-8 code page (safe even without non-ASCII output)
chcp 65001 >nul

set "ROOT_DIR=%~dp0"
pushd "%ROOT_DIR%1memos" >nul

call ".\\goujian.bat"
set "EXIT_CODE=%ERRORLEVEL%"

popd >nul
exit /b %EXIT_CODE%

