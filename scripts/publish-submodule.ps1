param(
    [string]$SubmodulePath = 'eclipticseasons-1.21.1',
    [string]$SubmoduleBranch = '',
    [string]$SubmoduleCommitMessage = '',
    [string]$MainCommitMessage = '',
    [string[]]$SubmoduleInclude = @(),
    [string[]]$MainInclude = @(),
    [switch]$SubmoduleStageAll,
    [switch]$MainStageAll,
    [switch]$SkipSubmoduleCommit,
    [switch]$SkipSubmodulePush,
    [switch]$PushMain,
    [string]$MainRemote = 'origin',
    [string]$MainBranch = '',
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Args,
        [switch]$Mutating,
        [switch]$Capture,
        [switch]$AllowFailure
    )

    $display = "git " + ($Args -join ' ')
    if ($DryRun -and $Mutating) {
        Write-Host "[DRY-RUN] $display"
        return ''
    }

    if ($Capture) {
        $out = & git @Args 2>&1
        $code = $LASTEXITCODE
        if ($code -ne 0 -and -not $AllowFailure) {
            throw "Command failed ($code): $display`n$($out -join [Environment]::NewLine)"
        }
        return ($out | Out-String).Trim()
    }

    & git @Args
    $code = $LASTEXITCODE
    if ($code -ne 0 -and -not $AllowFailure) {
        throw "Command failed ($code): $display"
    }
    return ''
}

function Resolve-BranchName {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    return Invoke-Git -Args @('-C', $Path, 'rev-parse', '--abbrev-ref', 'HEAD') -Capture
}

$workspaceRoot = Split-Path -Parent $PSScriptRoot
Set-Location $workspaceRoot

if (-not (Test-Path (Join-Path $workspaceRoot '.git'))) {
    throw "Current directory is not a git workspace root: $workspaceRoot"
}
if (-not (Test-Path (Join-Path $workspaceRoot $SubmodulePath))) {
    throw "Submodule path not found: $SubmodulePath"
}
if (-not (Test-Path (Join-Path $workspaceRoot '.gitmodules'))) {
    throw ".gitmodules not found. Please add submodule mapping first."
}

Write-Host "Workspace: $workspaceRoot"
Write-Host "Submodule: $SubmodulePath"

if ([string]::IsNullOrWhiteSpace($SubmoduleBranch)) {
    $SubmoduleBranch = Invoke-Git -Args @('config', '-f', '.gitmodules', '--get', "submodule.$SubmodulePath.branch") -Capture -AllowFailure
}
if ([string]::IsNullOrWhiteSpace($SubmoduleBranch)) {
    $SubmoduleBranch = Resolve-BranchName -Path $SubmodulePath
}

if ([string]::IsNullOrWhiteSpace($MainBranch)) {
    $MainBranch = Invoke-Git -Args @('rev-parse', '--abbrev-ref', 'HEAD') -Capture
}

Write-Host "Target submodule branch: $SubmoduleBranch"
Write-Host "Target main branch: $MainBranch"

Invoke-Git -Args @('submodule', 'sync', '--', $SubmodulePath) -Mutating
Invoke-Git -Args @('submodule', 'init', $SubmodulePath) -Mutating

if (-not $SkipSubmoduleCommit) {
    if ([string]::IsNullOrWhiteSpace($SubmoduleCommitMessage)) {
        throw "SubmoduleCommitMessage is required unless -SkipSubmoduleCommit is set."
    }

    Write-Host "Staging submodule changes..."
    if ($SubmoduleStageAll) {
        Invoke-Git -Args @('-C', $SubmodulePath, 'add', '-A') -Mutating
    } elseif ($SubmoduleInclude.Count -gt 0) {
        foreach ($item in $SubmoduleInclude) {
            Invoke-Git -Args @('-C', $SubmodulePath, 'add', '--', $item) -Mutating
        }
    } else {
        Invoke-Git -Args @('-C', $SubmodulePath, 'add', '-u') -Mutating
    }

    $submoduleStaged = Invoke-Git -Args @('-C', $SubmodulePath, 'diff', '--cached', '--name-only') -Capture
    if (-not $DryRun -and [string]::IsNullOrWhiteSpace($submoduleStaged)) {
        throw "No staged changes in submodule '$SubmodulePath'. Use -SubmoduleStageAll or -SubmoduleInclude as needed."
    }

    Invoke-Git -Args @('-C', $SubmodulePath, 'commit', '-m', $SubmoduleCommitMessage) -Mutating
} else {
    Write-Host "Skip submodule commit."
}

$submoduleCommit = Invoke-Git -Args @('-C', $SubmodulePath, 'rev-parse', 'HEAD') -Capture
if ([string]::IsNullOrWhiteSpace($submoduleCommit)) {
    throw "Failed to resolve submodule HEAD commit."
}

if (-not $SkipSubmodulePush) {
    Invoke-Git -Args @('-C', $SubmodulePath, 'push', '-u', 'origin', "HEAD:refs/heads/$SubmoduleBranch") -Mutating
} else {
    Write-Host "Skip submodule push."
}

Write-Host "Staging main repository changes..."
if ($MainStageAll) {
    Invoke-Git -Args @('add', '-A') -Mutating
} else {
    Invoke-Git -Args @('add', '--', $SubmodulePath) -Mutating
    if (Test-Path (Join-Path $workspaceRoot '.gitmodules')) {
        Invoke-Git -Args @('add', '--', '.gitmodules') -Mutating
    }
    foreach ($item in $MainInclude) {
        Invoke-Git -Args @('add', '--', $item) -Mutating
    }
}

$mainStaged = Invoke-Git -Args @('diff', '--cached', '--name-only') -Capture
if (-not $DryRun -and [string]::IsNullOrWhiteSpace($mainStaged)) {
    throw "No staged changes in main repository."
}

if ([string]::IsNullOrWhiteSpace($MainCommitMessage)) {
    $MainCommitMessage = "chore(submodule): bump $SubmodulePath to $submoduleCommit"
}

Invoke-Git -Args @('commit', '-m', $MainCommitMessage) -Mutating

if ($PushMain) {
    Invoke-Git -Args @('push', $MainRemote, $MainBranch) -Mutating
}

Write-Host ''
Write-Host 'Done.'
Write-Host "Submodule path: $SubmodulePath"
Write-Host "Submodule commit: $submoduleCommit"
Write-Host "Submodule branch: $SubmoduleBranch"
Write-Host "Main branch: $MainBranch"
Write-Host "Main pushed: $PushMain"
Write-Host "DryRun: $DryRun"
