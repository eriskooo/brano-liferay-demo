# Shared session "physically": read-source flag and every crm:session:{SSO_ID} hash with TTL and content.
Push-Location $PSScriptRoot
try {
    $flag = docker compose exec -T redis redis-cli GET crm:flags:session-read-source
    if (-not $flag) { $flag = '(not set = PORTLET)' }
    Write-Output "flag crm:flags:session-read-source = $flag"
    $keys = docker compose exec -T redis redis-cli --scan --pattern 'crm:session:*'
    foreach ($key in $keys) {
        if (-not $key) { continue }
        $ttl = docker compose exec -T redis redis-cli TTL $key
        Write-Output ''
        Write-Output "== $key  (TTL $ttl s)"
        docker compose exec -T redis redis-cli HGETALL $key
    }
} finally {
    Pop-Location
}
