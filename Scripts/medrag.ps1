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

function Wait-Medrag($id) {
    while ($true) {
        $d = (Invoke-RestMethod "http://localhost:8080/api/documents") | Where-Object { $_.id -eq $id }
        if (-not $d) { Write-Host "Document $id introuvable"; return }
        Write-Host "  $($d.status) : $($d.indexedChunkCount)/$($d.chunkCount) chunks"
        if ($d.status -eq "READY") { return }
        if ($d.status -eq "FAILED") { Write-Host "Echec : $($d.errorMessage)"; return }
        Start-Sleep -Seconds 10
    }
}

function Reindex-Medrag($id) {
    Invoke-RestMethod -Method Post "http://localhost:8080/api/documents/$id/reindex" | Out-Null
    Wait-Medrag $id
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
    if ([string]::IsNullOrWhiteSpace($Organisme) -or [string]::IsNullOrWhiteSpace($Title)) { Write-Host "Titre et organisme obligatoires"; return }
    if (-not (Test-Path $Path)) { Write-Host "Fichier introuvable : $Path"; return }
    $tmpTitle = [System.IO.Path]::GetTempFileName()
    $tmpOrg = [System.IO.Path]::GetTempFileName()
    [System.IO.File]::WriteAllText($tmpTitle, $Title, $utf8)
    [System.IO.File]::WriteAllText($tmpOrg, $Organisme, $utf8)

    $curlArgs = @("-sS", "--max-time", "600", "-X", "POST", "http://localhost:8080/api/documents",
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
    Wait-Medrag $doc.id
}

