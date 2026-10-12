# Switch the session read source without restart or deploy (feature flag): PORTLET | REDIS.
param([Parameter(Mandatory = $true)][ValidateSet('PORTLET', 'REDIS')][string]$Source)
Push-Location $PSScriptRoot
try {
    docker compose exec -T redis redis-cli SET crm:flags:session-read-source $Source
} finally {
    Pop-Location
}
