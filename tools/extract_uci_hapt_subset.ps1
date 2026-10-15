param(
    [Parameter(Mandatory = $true)]
    [string]$DatasetRoot,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$rawDataDirectory = Join-Path $DatasetRoot 'RawData'
$accelerometerPath = Join-Path $rawDataDirectory 'acc_exp01_user01.txt'
$gyroscopePath = Join-Path $rawDataDirectory 'gyro_exp01_user01.txt'

if (-not (Test-Path -LiteralPath $accelerometerPath)) {
    throw "Missing UCI accelerometer source: $accelerometerPath"
}
if (-not (Test-Path -LiteralPath $gyroscopePath)) {
    throw "Missing UCI gyroscope source: $gyroscopePath"
}

$accelerometerRows = [System.IO.File]::ReadAllLines($accelerometerPath)
$gyroscopeRows = [System.IO.File]::ReadAllLines($gyroscopePath)
if ($accelerometerRows.Count -ne $gyroscopeRows.Count) {
    throw "Paired source files have different row counts: $($accelerometerRows.Count) and $($gyroscopeRows.Count)."
}

$segments = @(
    @{ Start = 500; End = 549; Label = 'STANDING' },
    @{ Start = 7650; End = 7699; Label = 'WALKING' },
    @{ Start = 13300; End = 13349; Label = 'WALKING_DOWNSTAIRS' }
)

function Get-AxisValues {
    param(
        [string]$Line,
        [string]$SourceName,
        [int]$RowNumber
    )

    $values = @($Line.Trim() -split '\s+')
    if ($values.Count -ne 3) {
        throw "$SourceName row $RowNumber must contain exactly three axes."
    }

    foreach ($value in $values) {
        $parsed = 0.0
        if (-not [double]::TryParse(
                $value,
                [System.Globalization.NumberStyles]::Float,
                [System.Globalization.CultureInfo]::InvariantCulture,
                [ref]$parsed
            ) -or [double]::IsNaN($parsed) -or [double]::IsInfinity($parsed)) {
            throw "$SourceName row $RowNumber contains an invalid number: $value"
        }
    }
    return $values
}

$outputRows = [System.Collections.Generic.List[string]]::new()
$outputRows.Add('elapsed_ms,ax_g,ay_g,az_g,gx_rad_s,gy_rad_s,gz_rad_s,activity_label')
$elapsedMilliseconds = 0

foreach ($segment in $segments) {
    if ($segment.Start -lt 1 -or $segment.End -gt $accelerometerRows.Count -or $segment.Start -gt $segment.End) {
        throw "Invalid source range $($segment.Start)-$($segment.End)."
    }

    for ($rowNumber = $segment.Start; $rowNumber -le $segment.End; $rowNumber++) {
        $sourceIndex = $rowNumber - 1
        $accelerometer = Get-AxisValues $accelerometerRows[$sourceIndex] 'accelerometer' $rowNumber
        $gyroscope = Get-AxisValues $gyroscopeRows[$sourceIndex] 'gyroscope' $rowNumber
        $outputRows.Add((@(
                    $elapsedMilliseconds
                    $accelerometer[0]
                    $accelerometer[1]
                    $accelerometer[2]
                    $gyroscope[0]
                    $gyroscope[1]
                    $gyroscope[2]
                    $segment.Label
                ) -join ','))
        $elapsedMilliseconds += 20
    }
}

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
$outputDirectory = [System.IO.Path]::GetDirectoryName($resolvedOutput)
[System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
[System.IO.File]::WriteAllLines(
    $resolvedOutput,
    $outputRows,
    [System.Text.UTF8Encoding]::new($false)
)

Write-Output "Created $resolvedOutput with $($outputRows.Count - 1) paired sensor rows."
