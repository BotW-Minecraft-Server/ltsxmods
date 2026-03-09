param(
    [string]$RootDir = "D:\Projects\Minecraft\BotWMCS\ltsx_neo_mods",
    [int]$TimeoutSec = 900,
    [int]$MaxCaptures = 8,
    [int]$WaitForProcessSec = 300
)

$ErrorActionPreference = "Stop"

function Get-McJavaPid {
    $procs = Get-CimInstance Win32_Process -Filter "name='java.exe'"
    $candidates = @()
    foreach ($p in $procs) {
        $cmd = [string]$p.CommandLine
        $isWorkspace = $cmd -like "*ltsx_neo_mods*"
        $isClient = ($cmd -like "*clientRunProgramArgs.txt*") -or ($cmd -like "*forgeclientdev*")
        $isServer = $cmd -like "*serverRunProgramArgs.txt*"
        if ($isWorkspace -and $isClient -and -not $isServer) {
            $candidates += $p
        }
    }
    if ($candidates.Count -gt 0) {
        $pick = $candidates | Sort-Object CreationDate -Descending | Select-Object -First 1
        return [int]$pick.ProcessId
    }
    return $null
}

$logPath = Join-Path $RootDir "run\client\logs\latest.log"
$outDir = Join-Path $RootDir "run\client\debug\stack-capture"
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

$jcmd = if (Test-Path "$env:JAVA_HOME\bin\jcmd.exe") { "$env:JAVA_HOME\bin\jcmd.exe" } else { "jcmd.exe" }

$waitStart = Get-Date
$mcPid = Get-McJavaPid
while (-not $mcPid -and ((Get-Date) - $waitStart).TotalSeconds -lt $WaitForProcessSec) {
    Start-Sleep -Milliseconds 500
    $mcPid = Get-McJavaPid
}
if (-not $mcPid) {
    Write-Host "[watch-mc-network-stack] No Minecraft java process found within ${WaitForProcessSec}s."
    exit 1
}

if (-not (Test-Path $logPath)) {
    Write-Host "[watch-mc-network-stack] Log file not found: $logPath"
    exit 1
}

Write-Host "[watch-mc-network-stack] Watching PID=$mcPid log=$logPath"
Write-Host "[watch-mc-network-stack] Output dir: $outDir"

$lineCount = (Get-Content -Path $logPath).Count
$captures = 0
$start = Get-Date
$pattern = "No registration for payload|Failed to encode packet|Exception caught in connection|core_neb_global_batch|Src size is incorrect|timed out"

while (((Get-Date) - $start).TotalSeconds -lt $TimeoutSec) {
    $alive = Get-Process -Id $mcPid -ErrorAction SilentlyContinue
    if (-not $alive) {
        Write-Host "[watch-mc-network-stack] Process exited."
        break
    }

    $newLines = Get-Content -Path $logPath | Select-Object -Skip $lineCount
    if ($newLines.Count -gt 0) {
        $lineCount += $newLines.Count
        $joined = $newLines -join "`n"
        if ($joined -match $pattern) {
            $ts = Get-Date -Format "yyyy-MM-dd_HH.mm.ss.fff"
            $stackFile = Join-Path $outDir "jstack-$ts-pid$mcPid.txt"
            $triggerFile = Join-Path $outDir "trigger-$ts-pid$mcPid.log"

            & $jcmd $mcPid Thread.print -l | Out-File -FilePath $stackFile -Encoding utf8
            $joined | Out-File -FilePath $triggerFile -Encoding utf8

            Write-Host "[watch-mc-network-stack] Captured: $stackFile"
            $captures++
            if ($captures -ge $MaxCaptures) {
                Write-Host "[watch-mc-network-stack] Reached MaxCaptures=$MaxCaptures."
                break
            }
        }
    }

    Start-Sleep -Milliseconds 250
}

Write-Host "[watch-mc-network-stack] Done. captures=$captures"
