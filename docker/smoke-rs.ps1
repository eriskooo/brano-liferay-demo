# The Rust app smoke test is a bash script (curl + grep); this wrapper runs it with Git Bash.
$bash = Join-Path $env:ProgramFiles 'Git\bin\bash.exe'
if (-not (Test-Path $bash)) { throw 'Smoke test needs Git Bash (C:\Program Files\Git\bin\bash.exe)' }
& $bash "$PSScriptRoot/smoke-rs.sh"
exit $LASTEXITCODE
