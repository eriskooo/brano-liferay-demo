# E2e testy sú bash skript (Maven + Playwright); tento wrapper ho spustí cez Git Bash.
$bash = Join-Path $env:ProgramFiles 'Git\bin\bash.exe'
if (-not (Test-Path $bash)) { throw 'e2e potrebuje Git Bash (C:\Program Files\Git\bin\bash.exe)' }
& $bash "$PSScriptRoot/e2e.sh" @args
exit $LASTEXITCODE
