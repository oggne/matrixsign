@echo off
cd /d "%~dp0"
echo Starting RSL Model Training Setup...
echo Working directory: %CD%

if exist "python-3.10\python.exe" (
    echo Found embedded Python.
    set PYTHON_CMD=.\python-3.10\python.exe
) else (
    echo Embedded Python not found, trying system Python...
    set PYTHON_CMD=python
)

echo Using Python: %PYTHON_CMD%
%PYTHON_CMD% --version

echo Checking and installing dependencies (TensorFlow, scikit-learn)...
echo This requires an internet connection.
%PYTHON_CMD% -m pip install tensorflow scikit-learn

if %errorlevel% neq 0 (
    echo.
    echo Warning: Automatically installing dependencies failed.
    echo If you have no internet content, you need to install 'tensorflow' and 'scikit-learn' manually.
    echo Attempting to proceed anyway...
    echo.
)

echo Starting Training...
echo This may take a few minutes depending on your CPU/GPU.
%PYTHON_CMD% train_rsl_model.py > training_log.txt 2>&1

if %errorlevel% neq 0 (
    echo An error occurred. Please check training_log.txt.
    type training_log.txt
) else (
    echo.
    echo ===========================================
    echo Training complete!
    echo Model saved to rsl_classifier.tflite
    echo ===========================================
)
pause
