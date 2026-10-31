# Publicerar step.by.step i nrnworld.one-hubben sa att appen hamnar pa
#   https://nrnworld.one/step.by.step
#
# Kor:  powershell -ExecutionPolicy Bypass -File "$env:TEMP\fix-nrnworld-step.ps1"
#
# Skriptet klonar hub-repot till %USERPROFILE%\nrnworld-work, applicerar patchen,
# bygger och pushar till main. GitHub Pages publicerar sedan automatiskt.
# Det ror inte dina andra repon och ingen befintlig klon skrivs over.

$ErrorActionPreference = 'Stop'

$patchUrl = 'https://raw.githubusercontent.com/nRn-World/step.by.step/hub-patch/nrnworld-hub-step-by-step.patch'
$hubRepo  = 'https://github.com/nRn-World/nrnworld.git'
$work     = Join-Path $env:USERPROFILE 'nrnworld-work'
$stamp    = Get-Date -Format 'yyyyMMdd-HHmmss'
$hub      = Join-Path $work "hub-$stamp"
$patch    = Join-Path $work "step-by-step-$stamp.patch"

function Step($text) {
    Write-Host ''
    Write-Host $text -ForegroundColor Cyan
}

Step "1/6 Skapar $work"
New-Item -ItemType Directory -Force -Path $work | Out-Null

Step '2/6 Laddar ner patchen'
Invoke-WebRequest -Uri $patchUrl -OutFile $patch
Write-Host ("    " + (Get-Item $patch).Length + ' byte')

Step '3/6 Klonar hub-repot'
# core.autocrlf=false: patchen har LF-radbrytningar, CRLF pa disk skulle fa git apply att fallera
git -c core.autocrlf=false clone $hubRepo $hub
if ($LASTEXITCODE -ne 0) { throw 'git clone misslyckades' }

Set-Location $hub
# Klonen ska sta pa main aven om repo-HEAD pekar nagon annanstans
git checkout main
if ($LASTEXITCODE -ne 0) { throw 'kunde inte checka ut main i klonen' }

Step '4/6 Applicerar patchen'
git -c core.autocrlf=false apply --whitespace=nowarn -- $patch
if ($LASTEXITCODE -ne 0) { throw "git apply misslyckades. Kor 'git status' i $hub och kontrollera." }
Write-Host '    Patchen applicerad.' -ForegroundColor Green

Step '5/6 Installerar och bygger'
npm ci
if ($LASTEXITCODE -ne 0) { throw 'npm ci misslyckades' }
npx vite build
if ($LASTEXITCODE -ne 0) { throw 'vite build misslyckades' }
if (-not (Test-Path (Join-Path $hub 'dist\step.by.step\index.html'))) { throw 'dist\step.by.step\index.html saknas' }
Write-Host '    dist\step.by.step\index.html finns.' -ForegroundColor Green

Step '6/6 Committar och pushar till main'
if (-not (git config user.email)) {
    git config user.email 'bynrnworld@gmail.com'
    git config user.name 'nrnworld.one'
    Write-Host '    Satte lokal git-identitet for klonen.' -ForegroundColor Yellow
}
git add -A
git commit -m 'feat: publicera step.by.step pa /step.by.step'
if ($LASTEXITCODE -ne 0) { Write-Host '    Inget nytt att committa.' -ForegroundColor Yellow }
git push origin main
if ($LASTEXITCODE -ne 0) { throw "git push misslyckades. Kor 'git push' sjalv i $hub" }

Write-Host ''
Write-Host 'Klart. GitHub Actions bygger och publicerar sajten.' -ForegroundColor Green
Write-Host 'Kontrollera om 2-3 minuter: https://nrnworld.one/step.by.step/' -ForegroundColor Green
Write-Host 'Byggstatus: https://github.com/nRn-World/nrnworld/actions' -ForegroundColor Green
Write-Host "Klonen ligger kvar i $hub"
