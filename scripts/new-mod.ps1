param(
    [Parameter(Mandatory = $true)]
    [string]$ModId
)

$ErrorActionPreference = 'Stop'

if ($ModId -notmatch '^[a-z][a-z0-9_]{1,63}$') {
    throw "Invalid mod id '$ModId'. Use regex: [a-z][a-z0-9_]{1,63}"
}

$workspaceRoot = Split-Path -Parent $PSScriptRoot
$templateDir = Join-Path $workspaceRoot 'mod-template-1.21.1'
$targetDir = Join-Path $workspaceRoot ("$ModId-1.21.1")
$settingsPath = Join-Path $workspaceRoot 'settings.gradle'

if (-not (Test-Path $templateDir)) {
    throw "Template not found: $templateDir"
}

if (Test-Path $targetDir) {
    throw "Target already exists: $targetDir"
}

$modName = ($ModId -split '_' | ForEach-Object {
    if ($_.Length -eq 0) { return $_ }
    $_.Substring(0, 1).ToUpper() + $_.Substring(1)
}) -join ' '

Copy-Item -Path $templateDir -Destination $targetDir -Recurse

$javaPkgBase = Join-Path $targetDir 'src\main\java\link\botwmcs'
if (Test-Path (Join-Path $javaPkgBase 'modid')) {
    Rename-Item -Path (Join-Path $javaPkgBase 'modid') -NewName $ModId
}

$assetsBase = Join-Path $targetDir 'src\main\resources\assets'
if (Test-Path (Join-Path $assetsBase 'modid')) {
    Rename-Item -Path (Join-Path $assetsBase 'modid') -NewName $ModId
}

$mixinJson = Join-Path $targetDir 'src\main\resources\modid.mixins.json'
if (Test-Path $mixinJson) {
    Rename-Item -Path $mixinJson -NewName ("$ModId.mixins.json")
}

Get-ChildItem -Path $targetDir -Recurse -File | ForEach-Object {
    $file = $_.FullName
    $raw = Get-Content -Raw -Path $file
    $new = $raw.Replace('__MODID__', $ModId).Replace('__MOD_NAME__', $modName)
    if ($new -ne $raw) {
        Set-Content -Path $file -Value $new
    }
}

$includeLine = "include(':$ModId')"
$projectLine = "project(':$ModId').projectDir = file('$ModId-1.21.1')"
$settings = Get-Content -Raw $settingsPath
if ($settings -notmatch [regex]::Escape($includeLine)) {
    $settings = $settings.TrimEnd() + "`r`n`r`n$includeLine`r`n$projectLine`r`n"
    Set-Content -Path $settingsPath -Value $settings
}

Write-Host "Created module: :$ModId -> $ModId-1.21.1"
Write-Host "Included in settings.gradle and wired to depend on :ltsxcore via template build.gradle"
Write-Host "Next: .\\gradlew.bat :$ModId:build"
