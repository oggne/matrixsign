@echo off
cd /d "%~dp0"
echo Starting Slovo dataset processing...
echo Working directory: %CD%
echo Checking Python...
python --version
if %errorlevel% neq 0 (
    echo Error: Python is not found or not in PATH.
    echo Please install Python or add it to your PATH.
    pause
    exit /b
)

echo Running process_slovo_zip.py...
python process_slovo_zip.py > processing_log.txt 2>&1

if %errorlevel% neq 0 (
    echo An error occurred. Please check processing_log.txt for details.
    type processing_log.txt
) else (
    echo Processing completed successfully.
)

pause
