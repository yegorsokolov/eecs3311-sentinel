param([string]$DatasetId = 'demo')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    $javaExe=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{'java'}
    $jarPath=Join-Path $projectRoot 'target/sentinel-0.1.0-SNAPSHOT.jar'
    function Invoke-Sentinel([string[]]$CommandArgs) {
        $raw = & $javaExe -jar $jarPath @CommandArgs
        if($LASTEXITCODE -ne 0){throw "Sentinel command failed: $($CommandArgs[0])"}
        return ($raw | ConvertFrom-Json).message | ConvertFrom-Json
    }
    $created=Invoke-Sentinel @('new','--dataset',$DatasetId,'--request','Investigate synthetic account takeover','--target','demo-analyst','--start','2026-10-22T12:00:00Z','--end','2026-10-22T13:00:00Z','--feed','demo-feed-v1','--rule')
    $caseId=$created.id
    Invoke-Sentinel @('import','--case',$caseId,'--file','data/scenarios/account-takeover.json') | Format-List
    Invoke-Sentinel @('rule','--case',$caseId) | Out-Null
    $assessment=Invoke-Sentinel @('run','--case',$caseId)
    Write-Output "Case $caseId / $($assessment.completeness) / score $($assessment.severity.score) $($assessment.severity.level)"
    Invoke-Sentinel @('ask','--case',$caseId,'--question','What evidence supports this assessment?') | Format-List
    $outPath=Join-Path $projectRoot "runtime/exports/$caseId.md"
    Invoke-Sentinel @('export','--case',$caseId,'--out',$outPath) | Out-Null
    Write-Output "Report: $outPath"
    Write-Output "Reopen GUI with: gui --dataset $DatasetId ; select Open case."
} finally { Pop-Location }
