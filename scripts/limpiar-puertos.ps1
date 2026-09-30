# Script para liberar puertos comunes de desarrollo (8080, 8081, 8025, 1025)
param (
    [int[]]$Puertos = @(8080, 8081, 8025, 1025)
)

# Con docker compose, quien escucha estos puertos es Docker Desktop; matarlo a la fuerza lo deja sin cerrar bien
# y no vuelve a arrancar (sockets huérfanos). Esos puertos se liberan con `docker compose stop`.
$ProcesosDeDocker = '^(com\.docker\..*|Docker Desktop|wslrelay|vpnkit.*)$'

Write-Host "Verificando puertos ocupados: $($Puertos -join ', ')..." -ForegroundColor Cyan

foreach ($puerto in $Puertos) {
    try {
        $conexiones = Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue
        if ($conexiones) {
            foreach ($conn in $conexiones) {
                $pidProc = $conn.OwningProcess
                $proc = Get-Process -Id $pidProc -ErrorAction SilentlyContinue
                if ($proc -and $proc.ProcessName -match $ProcesosDeDocker) {
                    Write-Host "Puerto $puerto lo usa Docker ($($proc.ProcessName)); no se toca. Usa: docker compose stop" -ForegroundColor Yellow
                } elseif ($proc) {
                    Write-Host "Liberando puerto $puerto ocupado por proceso $($proc.ProcessName) (PID: $pidProc)..." -ForegroundColor Yellow
                    Stop-Process -Id $pidProc -Force -ErrorAction SilentlyContinue
                }
            }
        } else {
            Write-Host "Puerto $puerto libre." -ForegroundColor Green
        }
    } catch {
        Write-Warning "No se pudo verificar el puerto ${puerto}: $_"
    }
}
Write-Host "Verificación finalizada." -ForegroundColor Cyan
