param([string]$DatasetId = 'demo')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    $javaExe=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{'java'}
    $jarPath=Join-Path $projectRoot 'target/sentinel-0.1.0-SNAPSHOT.jar'
    function Invoke-Sentinel([string[]]$CommandArgs) {
        $savedPreference=$ErrorActionPreference
        $ErrorActionPreference='Continue'
        try { $raw = & $javaExe -jar $jarPath @CommandArgs } finally { $ErrorActionPreference=$savedPreference }
        if($LASTEXITCODE -ne 0){throw "Sentinel command failed: $($CommandArgs[0])"}
        return ($raw | ConvertFrom-Json).message | ConvertFrom-Json
    }
    $created=Invoke-Sentinel -CommandArgs @('new','--dataset',$DatasetId,'--request','Investigate synthetic account takeover','--target','demo-analyst','--start','2026-10-22T12:00:00Z','--end','2026-10-22T13:00:00Z','--feed','demo-feed-v1','--rule')
    $caseId=$created.id
    Invoke-Sentinel -CommandArgs @('import','--case',$caseId,'--file','data/scenarios/account-takeover.json') | Format-List
    Invoke-Sentinel -CommandArgs @('rule','--case',$caseId) | Out-Null
    $assessment=Invoke-Sentinel -CommandArgs @('run','--case',$caseId)
    if($assessment.completeness -ne 'COMPLETE' -or $assessment.severity.score -ne 7 -or $assessment.severity.level -ne 'HIGH' -or @($assessment.hypotheses).Count -lt 1){throw 'Synthetic assessment incomplete or unexpected; inspect model availability and the saved case.'}
    Write-Output "Case $caseId / $($assessment.completeness) / score $($assessment.severity.score) $($assessment.severity.level)"
    $answer=Invoke-Sentinel -CommandArgs @('ask','--case',$caseId,'--question','What evidence supports this assessment?')
    if(@($answer.evidenceIds).Count -lt 1){throw 'Follow-up has no accepted citations.'}
    $answer | Format-List
    $outPath=Join-Path $projectRoot "runtime/exports/$caseId.md"
    Invoke-Sentinel -CommandArgs @('export','--case',$caseId,'--out',$outPath) | Out-Null
    $reopened=Invoke-Sentinel -CommandArgs @('open','--case',$caseId)
    if($reopened.id -ne $caseId -or -not (Test-Path -LiteralPath $outPath)){throw 'Persistence or export verification failed.'}
    Write-Output "Report: $outPath"
    Write-Output "Reopen GUI with: gui --dataset $DatasetId ; select Open case."
} finally { Pop-Location }
