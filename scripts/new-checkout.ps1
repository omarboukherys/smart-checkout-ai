# Opens a demo checkout from PowerShell (alternative to docs/requests.http).
# Usage: powershell -ExecutionPolicy Bypass -File scripts\new-checkout.ps1 [-Channel WEB] [-Token tok_visa_4242]
param(
    [string]$BaseUrl = "http://localhost:18101",
    [ValidateSet("POS", "WEB", "MOBILE")]
    [string]$Channel = "POS",
    [string]$CustomerId = "C00042",
    [string]$Token = "tok_visa_4242",
    [string]$IdempotencyKey = [guid]::NewGuid().ToString()
)

$body = @{
    storeId    = "CASA-MAARIF-01"
    channel    = $Channel
    customerId = $CustomerId
    currency   = "MAD"
    lines      = @(
        @{ sku = "SKU-CAFE-500"; label = "Cafe moulu 500 g"; quantity = 2; unitPriceMinor = 4590 },
        @{ sku = "SKU-THE-250"; label = "The vert 250 g"; quantity = 1; unitPriceMinor = 2250 }
    )
    payment    = @{ method = "CARD"; token = $Token; last4 = "4242"; country = "MA" }
    context    = @{ ipCountry = "MA"; deviceId = "POS-TERM-07" }
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/checkouts" `
    -Headers @{ "Idempotency-Key" = $IdempotencyKey } `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
