param (
    [string]$ModelPath = "gemma4-e2b-it.task"
)

Write-Host "===========================================" -ForegroundColor Cyan
Write-Host "  Gemma 4 E2B ADB Sideload Tool" -ForegroundColor Cyan
Write-Host "===========================================" -ForegroundColor Cyan

# 1. Check if ADB is available
$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) {
    Write-Host "[ERROR] 'adb' command was not found in PATH." -ForegroundColor Red
    Write-Host "Please ensure Android SDK platform-tools is installed and in your PATH." -ForegroundColor Yellow
    exit 1
}

# 2. Check connected devices
Write-Host "`n[1/3] Checking connected Android devices..." -ForegroundColor Yellow
$devices = adb devices | Select-String -Pattern "device$"
if (-not $devices) {
    Write-Host "[ERROR] No authorized Android device/emulator found via ADB." -ForegroundColor Red
    Write-Host "Please connect your phone via USB and enable USB Debugging in Developer Options." -ForegroundColor Yellow
    exit 1
}
Write-Host "  Found device: $devices" -ForegroundColor Green

# 3. Check model file existence
Write-Host "`n[2/3] Checking model file: $ModelPath..." -ForegroundColor Yellow
if (-not (Test-Path $ModelPath)) {
    Write-Host "[WARNING] File '$ModelPath' not found in current directory." -ForegroundColor Yellow
    Write-Host "Please download the Gemma 4 E2B .task / .bin file from Kaggle or HuggingFace and place it here," -ForegroundColor Yellow
    Write-Host "or provide the path as an argument: .\push_gemma_model.ps1 -ModelPath C:\path\to\model.task" -ForegroundColor Yellow
    exit 1
}

$fileSize = (Get-Item $ModelPath).Length / 1MB
Write-Host "  Model file size: $([math]::Round($fileSize, 2)) MB" -ForegroundColor Green

# 4. Push model to device
$targetPath = "/data/local/tmp/gemma4-e2b-it.task"
Write-Host "`n[3/3] Pushing model to $targetPath on device..." -ForegroundColor Yellow
adb push $ModelPath $targetPath

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n[SUCCESS] Gemma 4 E2B model sideloaded successfully!" -ForegroundColor Green
    Write-Host "You can now open the NutriScanner app and tap the '? Gemma 4 E2B' badge in Nutribot chat to verify." -ForegroundColor Cyan
} else {
    Write-Host "`n[ERROR] Failed to push model to device." -ForegroundColor Red
}
