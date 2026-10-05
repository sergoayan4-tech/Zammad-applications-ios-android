@echo off
cd /d "%~dp0"
echo Pushing project to GitHub...
echo.
"C:\Program Files\Git\bin\git.exe" push -u origin main
echo.
if errorlevel 1 (
    echo ============================================================
    echo  Push FAILED. Look at the message above.
    echo  Most likely you need to log in to GitHub in the browser
    echo  window that opens, or run this file again.
    echo ============================================================
) else (
    echo ============================================================
    echo  Done! The build has started on GitHub automatically.
    echo  Open https://github.com/sergoayan4-tech/hondaCBR/actions
    echo  to watch it. When it finishes - download Artifacts.
    echo ============================================================
)
pause
