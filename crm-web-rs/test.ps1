# The Rust tests run in Docker via a bash script; this wrapper runs it with Git Bash.
$bash = Join-Path $env:ProgramFiles 'Git\bin\bash.exe'
if (-not (Test-Path $bash)) { throw 'Tests need Git Bash (C:\Program Files\Git\bin\bash.exe)' }
& $bash "$PSScriptRoot/test.sh" @args
exit $LASTEXITCODE
