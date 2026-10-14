# The benchmark is a bash script (oha + curl in Docker); this wrapper runs it with Git Bash.
$bash = Join-Path $env:ProgramFiles 'Git\bin\bash.exe'
if (-not (Test-Path $bash)) { throw 'Benchmark needs Git Bash (C:\Program Files\Git\bin\bash.exe)' }
& $bash "$PSScriptRoot/bench.sh" @args
exit $LASTEXITCODE
