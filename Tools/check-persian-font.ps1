param(
    [string]$BaseRef = '0aae959806c57be8ad457980f2a5c6277ff18635'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputPath = Join-Path $projectRoot 'build/font-check/review'
$utf8 = New-Object System.Text.UTF8Encoding($false)

Push-Location -LiteralPath $projectRoot
try {
    foreach ($command in @('git', 'java', 'javac')) {
        Get-Command $command -ErrorAction Stop | Out-Null
    }
    $baseCommit = & git -c core.safecrlf=false merge-base $BaseRef HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve the review base.' }

    $changed = @(& git -c core.safecrlf=false diff --name-only --diff-filter=ACMR --ignore-submodules=all $baseCommit --)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot list changed files.' }
    $untracked = @(& git ls-files --others --exclude-standard --)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot list untracked files.' }
    $paths = @($changed + $untracked | Sort-Object -Unique)
    $sources = @($paths | Where-Object {
        $_ -match '^(TMessagesProj|TMessagesProj_AppTests)/.*\.java$'
    })
    New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
    $manifest = Join-Path $outputPath 'sources.txt'
    [System.IO.File]::WriteAllLines($manifest, [string[]]$sources, $utf8)

    & java --source 17 Tools/tests/PersianFontSourceCheck.java $manifest
    if ($LASTEXITCODE -ne 0) { throw 'Java syntax check failed.' }

    $classes = Join-Path $outputPath 'classes'
    New-Item -ItemType Directory -Path $classes -Force | Out-Null
    & javac -encoding UTF-8 -d $classes TMessagesProj/src/main/java/org/telegram/messenger/PersianTextUtils.java Tools/tests/PersianTextUtilsTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Standalone range-test compilation failed.' }
    $rangeResult = @(& java -cp $classes PersianTextUtilsTest)
    if ($LASTEXITCODE -ne 0) { throw 'Standalone range tests failed.' }
    $rangeResult | Write-Output

    $resources = @($paths | Where-Object { $_ -match '^TMessagesProj/src/main/res/.*\.xml$' })
    foreach ($path in $resources) {
        $document = New-Object System.Xml.XmlDocument
        $document.XmlResolver = $null
        $document.Load((Join-Path $projectRoot $path))
        $names = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        foreach ($node in $document.SelectNodes('/resources/*[@name]')) {
            $key = $node.LocalName + ':' + $node.GetAttribute('name')
            if (!$names.Add($key)) { throw "Duplicate resource in ${path}: $key" }
        }
    }
    Write-Output "Parsed resource XML files: $($resources.Count)"

    $fontDirectory = Join-Path $projectRoot 'TMessagesProj/src/main/assets/fonts'
    $fontBytes = 0L
    foreach ($weight in @('Regular', 'Medium', 'Bold')) {
        $fontPath = Join-Path $fontDirectory "Vazirmatn-$weight.ttf"
        $data = [System.IO.File]::ReadAllBytes($fontPath)
        if ($data.Length -lt 12 -or $data[0] -ne 0 -or $data[1] -ne 1 -or $data[2] -ne 0 -or $data[3] -ne 0) {
            throw "Invalid TrueType header: $fontPath"
        }
        $fontBytes += $data.Length
        Write-Output "$([System.IO.Path]::GetFileName($fontPath)) SHA256: $((Get-FileHash -LiteralPath $fontPath -Algorithm SHA256).Hash)"
    }
    $license = [System.IO.File]::ReadAllText((Join-Path $fontDirectory 'Vazirmatn-OFL.txt'))
    if ($license -notmatch 'SIL OPEN FONT LICENSE Version 1\.1') { throw 'Missing SIL OFL notice.' }
    Write-Output "Bundled font bytes: $fontBytes; SIL OFL notice present."

    & git -c core.safecrlf=false -c core.whitespace=cr-at-eol diff --check --ignore-submodules=all $baseCommit --
    if ($LASTEXITCODE -ne 0) { throw 'Diff whitespace check failed.' }
    foreach ($path in $untracked | Where-Object { $_ -match '\.(java|ps1|md)$' }) {
        $lineNumber = 0
        foreach ($line in [System.IO.File]::ReadAllLines((Join-Path $projectRoot $path))) {
            $lineNumber++
            if ($line -match '[ \t]+$') { throw "Trailing whitespace in ${path}:$lineNumber" }
        }
    }
    $inputPaths = @($paths | Where-Object {
        $_ -match '^(TMessagesProj|TMessagesProj_AppTests)/' -and (Test-Path -LiteralPath $_ -PathType Leaf)
    })
    $inputHashes = @($inputPaths | ForEach-Object {
        [ordered]@{ path = $_; sha256 = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash }
    })
    $head = & git rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Cannot identify HEAD.' }
    $deletedPaths = @(& git -c core.safecrlf=false diff --name-only --diff-filter=D --ignore-submodules=all $baseCommit --)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot list deleted inputs.' }
    $report = [ordered]@{
        capturedUtc = [DateTime]::UtcNow.ToString('o')
        head = "$head"
        base = "$baseCommit"
        javaSourceCount = $sources.Count
        rangeResult = $rangeResult
        resourceXmlCount = $resources.Count
        bundledFontBytes = $fontBytes
        inputs = $inputHashes
        deletedPaths = $deletedPaths
        androidBuildRunByThisScript = $false
        instrumentationRunByThisScript = $false
    }
    $reportPath = Join-Path $outputPath 'result.json'
    [System.IO.File]::WriteAllText($reportPath, ($report | ConvertTo-Json -Depth 6), $utf8)
    Write-Output "Review base: $baseCommit"
    Write-Output "Source evidence: $reportPath"
    Write-Output 'Source checks passed. No Android build, instrumentation, device or performance checks were run.'
} finally {
    Pop-Location
}
