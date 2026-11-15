function Search-Medrag($question) {
    $q = [uri]::EscapeDataString($question)
    (Invoke-RestMethod "http://localhost:8080/api/search?q=$q") | ForEach-Object {
        [pscustomobject]@{
            sim      = [math]::Round($_.similarity, 3)
            page     = $_.page
            relevant = $_.relevant
            debut    = $_.content.Substring(0, [Math]::Min(80, $_.content.Length))
        }
    } | Format-Table -AutoSize
}

function Ask-Medrag($question) {
    $json = @{ question = $question } | ConvertTo-Json
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $r = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/ask" `
        -ContentType "application/json; charset=utf-8" -Body $bytes
    Write-Host ""
    Write-Host "answered : $($r.answered)"
    Write-Host ""
    Write-Host $r.answer
    Write-Host ""
    foreach ($s in $r.sources) {
        Write-Host ("[{0}] {1} ({2}), page {3}, sim {4}" -f $s.ref, $s.title, $s.organisme, $s.page, [math]::Round($s.similarity, 3))
    }
    Write-Host ""
    Write-Host $r.disclaimer
}

function Add-Medrag {
    param(
        [string]$Path,
        [string]$Title,
        [string]$Organisme = "OMS",
        [string]$Url = "",
        [string]$Lang = "fr"
    )
    $utf8 = New-Object System.Text.UTF8Encoding $false
    $tmpTitle = [System.IO.Path]::GetTempFileName()
    $tmpOrg = [System.IO.Path]::GetTempFileName()
    [System.IO.File]::WriteAllText($tmpTitle, $Title, $utf8)
    [System.IO.File]::WriteAllText($tmpOrg, $Organisme, $utf8)

    $curlArgs = @("-s", "--max-time", "1800", "-X", "POST", "http://localhost:8080/api/documents",
        "-F", "file=@$Path",
        "-F", "title=<$tmpTitle",
        "-F", "organisme=<$tmpOrg",
        "-F", "language=$Lang")
    if ($Url) { $curlArgs += @("-F", "sourceUrl=$Url") }

    $json = & curl.exe @curlArgs
    Remove-Item $tmpTitle, $tmpOrg -ErrorAction SilentlyContinue

    try { $doc = $json | ConvertFrom-Json } catch { $doc = $null }
    if (-not $doc -or -not $doc.id) {
        Write-Host "Echec de l'envoi : $json"
        return
    }
    Write-Host "Envoye : id=$($doc.id), pages=$($doc.pageCount), chunks=$($doc.chunkCount)"
    Index-Medrag $doc.id
}

function Index-Medrag($id) {
    for ($i = 1; $i -le 10; $i++) {
        try {
            $r = Invoke-RestMethod -Method Post -TimeoutSec 1800 "http://localhost:8080/api/documents/$id/embeddings"
            Write-Host "Essai $i : $($r.embeddedChunks) chunks indexes, restant : $($r.remainingChunks)"
            if ($r.remainingChunks -eq 0) { return }
        } catch {
            Write-Host "Essai $i : echec ($($_.Exception.Message)). Pause de 60 s..."
        }
        Start-Sleep -Seconds 60
    }
}