@echo off
setlocal

title FreeLLMAPI - Ordem

echo ========================================
echo       INICIANDO FREELLMAPI
echo ========================================

where node >nul 2>&1
if errorlevel 1 (
    echo ERRO: Node.js nao encontrado.
    pause
    exit /b 1
)

where npm >nul 2>&1
if errorlevel 1 (
    echo ERRO: NPM nao encontrado.
    pause
    exit /b 1
)

cd /d "%~dp0infra\freellmapi-main"

if errorlevel 1 (
    echo ERRO: Pasta do FreeLLMAPI nao encontrada.
    pause
    exit /b 1
)

if not exist package.json (
    echo ERRO: package.json nao encontrado.
    pause
    exit /b 1
)

echo Iniciando cliente e servidor...
call npm run dev

echo.
echo FreeLLMAPI encerrado.
pause