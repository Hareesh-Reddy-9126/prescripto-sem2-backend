param(
    [string]$BaseUrl = "http://localhost:4000"
)

$ErrorActionPreference = "Stop"

function Read-EnvMap {
    param([string]$Path)

    if (!(Test-Path $Path)) {
        throw "Missing env file at $Path"
    }

    $map = @{}
    Get-Content $Path | ForEach-Object {
        $line = $_
        if ($line -match '^\s*#' -or $line -match '^\s*$') {
            return
        }

        $parts = $line -split '=', 2
        if ($parts.Count -ne 2) {
            return
        }

        $key = $parts[0].Trim()
        $value = $parts[1].Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        $map[$key] = $value
    }

    return $map
}

function Ensure-SuccessResponse {
    param(
        [string]$Step,
        [object]$Response
    )

    if ($null -eq $Response) {
        throw "$Step failed: empty response"
    }

    if ($Response.PSObject.Properties.Name -contains "success") {
        if (-not [bool]$Response.success) {
            $message = ""
            if ($Response.PSObject.Properties.Name -contains "message") {
                $message = [string]$Response.message
            }
            if ([string]::IsNullOrWhiteSpace($message)) {
                $message = "unknown error"
            }
            throw "$Step failed: $message"
        }
    }
}

function Invoke-JsonPost {
    param(
        [string]$Url,
        [hashtable]$Body,
        [hashtable]$Headers = @{}
    )

    $jsonBody = $Body | ConvertTo-Json -Depth 12
    return Invoke-RestMethod -Uri $Url -Method POST -Headers $Headers -ContentType "application/json" -Body $jsonBody
}

function Invoke-CurlMultipart {
    param(
        [string]$Url,
        [hashtable]$Headers,
        [hashtable]$Fields,
        [string[]]$FileParts = @()
    )

    $arguments = @("-sS", "-X", "POST", $Url)

    foreach ($header in $Headers.GetEnumerator()) {
        $arguments += "-H"
        $arguments += ("{0}: {1}" -f $header.Key, $header.Value)
    }

    foreach ($field in $Fields.GetEnumerator()) {
        $arguments += "-F"
        $arguments += ("{0}={1}" -f $field.Key, $field.Value)
    }

    foreach ($filePart in $FileParts) {
        $arguments += "-F"
        $arguments += $filePart
    }

    $raw = & curl.exe @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Multipart request failed for $Url"
    }

    if ([string]::IsNullOrWhiteSpace($raw)) {
        throw "Multipart request returned empty response for $Url"
    }

    try {
        return $raw | ConvertFrom-Json
    }
    catch {
        throw "Multipart request returned non-JSON response for ${Url}: $raw"
    }
}

$scriptDir = $PSScriptRoot
$backendDir = Split-Path -Parent $scriptDir
$envPath = Join-Path $backendDir ".env"
$envMap = Read-EnvMap -Path $envPath

$adminEmail = $envMap["ADMIN_EMAIL"]
$adminPassword = $envMap["ADMIN_PASSWORD"]
if ([string]::IsNullOrWhiteSpace($adminEmail) -or [string]::IsNullOrWhiteSpace($adminPassword)) {
    throw "ADMIN_EMAIL or ADMIN_PASSWORD is missing in backend/.env"
}

$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$doctorEmail = "doctor.smoke.$stamp@example.com"
$doctorPassword = "SmokePass123!"
$userEmail = "user.smoke.$stamp@example.com"
$userPassword = "SmokePass123!"
$pharmacyEmail = "pharmacy.smoke.$stamp@example.com"
$pharmacyPassword = "SmokePass123!"
$slotDate = (Get-Date).AddDays(1).ToString("d_M_yyyy")
$slotTime = "10:00 AM"

$steps = New-Object System.Collections.Generic.List[object]
function Add-Step {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Detail
    )

    $steps.Add([ordered]@{
        step = $Name
        status = $Status
        detail = $Detail
    }) | Out-Null
}

try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/" -Method GET
    Add-Step -Name "health" -Status "ok" -Detail ([string]$health)

    $adminLogin = Invoke-JsonPost -Url "$BaseUrl/api/admin/login" -Body @{
        email = $adminEmail
        password = $adminPassword
    }
    Ensure-SuccessResponse -Step "admin login" -Response $adminLogin
    if ([string]::IsNullOrWhiteSpace([string]$adminLogin.token)) {
        throw "admin login failed: missing token"
    }
    $adminToken = [string]$adminLogin.token
    Add-Step -Name "admin login" -Status "ok" -Detail "token received"

    $tempImage = Join-Path $env:TEMP ("smoke-doctor-{0}.txt" -f $stamp)
    Set-Content -Path $tempImage -Value "smoke image"

    $addDoctor = Invoke-CurlMultipart -Url "$BaseUrl/api/admin/add-doctor" -Headers @{ atoken = $adminToken } -Fields @{
        name = "Smoke Doctor $stamp"
        email = $doctorEmail
        password = $doctorPassword
        speciality = "General physician"
        degree = "MBBS"
        experience = "5 Year"
        about = "Smoke test doctor"
        fees = "500"
        address = '{"line1":"Smoke Street","line2":"Test City"}'
    } -FileParts @("image=@$tempImage;type=image/png")
    Ensure-SuccessResponse -Step "admin add doctor" -Response $addDoctor
    Add-Step -Name "admin add doctor" -Status "ok" -Detail "doctor created"

    $allDoctors = Invoke-RestMethod -Uri "$BaseUrl/api/admin/all-doctors" -Method GET -Headers @{ atoken = $adminToken }
    Ensure-SuccessResponse -Step "admin all doctors" -Response $allDoctors
    $doctor = $allDoctors.doctors | Where-Object { $_.email -eq $doctorEmail } | Select-Object -First 1
    if ($null -eq $doctor) {
        throw "doctor lookup failed after creation"
    }
    $doctorId = [string]$doctor._id
    Add-Step -Name "doctor lookup" -Status "ok" -Detail "doctorId=$doctorId"

    $doctorLogin = Invoke-JsonPost -Url "$BaseUrl/api/doctor/login" -Body @{
        email = $doctorEmail
        password = $doctorPassword
    }
    Ensure-SuccessResponse -Step "doctor login" -Response $doctorLogin
    $doctorToken = [string]$doctorLogin.token
    if ([string]::IsNullOrWhiteSpace($doctorToken)) {
        throw "doctor login failed: missing token"
    }
    Add-Step -Name "doctor login" -Status "ok" -Detail "token received"

    $userRegister = Invoke-JsonPost -Url "$BaseUrl/api/user/register" -Body @{
        name = "Smoke User $stamp"
        email = $userEmail
        password = $userPassword
    }
    Ensure-SuccessResponse -Step "user register" -Response $userRegister
    $userToken = [string]$userRegister.token
    if ([string]::IsNullOrWhiteSpace($userToken)) {
        throw "user register failed: missing token"
    }
    Add-Step -Name "user register" -Status "ok" -Detail "token received"

    $userLogin = Invoke-JsonPost -Url "$BaseUrl/api/user/login" -Body @{
        email = $userEmail
        password = $userPassword
    }
    Ensure-SuccessResponse -Step "user login" -Response $userLogin
    Add-Step -Name "user login" -Status "ok" -Detail "credentials verified"

    $userProfile = Invoke-RestMethod -Uri "$BaseUrl/api/user/get-profile" -Method GET -Headers @{ token = $userToken }
    Ensure-SuccessResponse -Step "user profile" -Response $userProfile
    Add-Step -Name "user profile" -Status "ok" -Detail "profile fetched"

    $bookAppointment = Invoke-JsonPost -Url "$BaseUrl/api/user/book-appointment" -Headers @{ token = $userToken } -Body @{
        docId = $doctorId
        slotDate = $slotDate
        slotTime = $slotTime
    }
    Ensure-SuccessResponse -Step "book appointment" -Response $bookAppointment
    Add-Step -Name "book appointment" -Status "ok" -Detail "appointment booked"

    $userAppointments = Invoke-RestMethod -Uri "$BaseUrl/api/user/appointments" -Method GET -Headers @{ token = $userToken }
    Ensure-SuccessResponse -Step "user appointments" -Response $userAppointments
    $appointment = $userAppointments.appointments | Where-Object { $_.docId -eq $doctorId -and $_.slotDate -eq $slotDate -and $_.slotTime -eq $slotTime } | Select-Object -First 1
    if ($null -eq $appointment) {
        throw "appointment lookup failed for booked slot"
    }
    $appointmentId = [string]$appointment._id
    Add-Step -Name "appointment lookup" -Status "ok" -Detail "appointmentId=$appointmentId"

    $doctorAppointments = Invoke-RestMethod -Uri "$BaseUrl/api/doctor/appointments" -Method GET -Headers @{ dtoken = $doctorToken }
    Ensure-SuccessResponse -Step "doctor appointments" -Response $doctorAppointments
    $doctorHasAppointment = $doctorAppointments.appointments | Where-Object { $_._id -eq $appointmentId } | Select-Object -First 1
    if ($null -eq $doctorHasAppointment) {
        throw "doctor appointments missing booked appointment"
    }
    Add-Step -Name "doctor appointments" -Status "ok" -Detail "appointment visible"

    $scheduleConsultation = Invoke-JsonPost -Url "$BaseUrl/api/consultations/doctor/schedule" -Headers @{ dtoken = $doctorToken } -Body @{ appointmentId = $appointmentId }
    Ensure-SuccessResponse -Step "consultation schedule" -Response $scheduleConsultation
    Add-Step -Name "consultation schedule" -Status "ok" -Detail "scheduled"

    $startConsultation = Invoke-JsonPost -Url "$BaseUrl/api/consultations/doctor/start" -Headers @{ dtoken = $doctorToken } -Body @{ appointmentId = $appointmentId }
    Ensure-SuccessResponse -Step "consultation start" -Response $startConsultation
    Add-Step -Name "consultation start" -Status "ok" -Detail "live"

    $doctorConsultationDetail = Invoke-JsonPost -Url "$BaseUrl/api/consultations/doctor/details" -Headers @{ dtoken = $doctorToken } -Body @{ appointmentId = $appointmentId }
    Ensure-SuccessResponse -Step "consultation doctor details" -Response $doctorConsultationDetail
    Add-Step -Name "consultation doctor details" -Status "ok" -Detail "detail fetched"

    $patientConsultationDetail = Invoke-JsonPost -Url "$BaseUrl/api/consultations/patient/details" -Headers @{ token = $userToken } -Body @{ appointmentId = $appointmentId }
    Ensure-SuccessResponse -Step "consultation patient details" -Response $patientConsultationDetail
    Add-Step -Name "consultation patient details" -Status "ok" -Detail "detail fetched"

    $completeConsultation = Invoke-JsonPost -Url "$BaseUrl/api/consultations/doctor/complete" -Headers @{ dtoken = $doctorToken } -Body @{
        appointmentId = $appointmentId
        summary = "Patient stable"
        notesForPatient = "Continue hydration"
        notesForInternal = "Smoke test complete"
    }
    Ensure-SuccessResponse -Step "consultation complete" -Response $completeConsultation
    Add-Step -Name "consultation complete" -Status "ok" -Detail "completed"

    $savePrescription = Invoke-JsonPost -Url "$BaseUrl/api/doctor/prescription/save" -Headers @{ dtoken = $doctorToken } -Body @{
        appointmentId = $appointmentId
        diagnosis = "Mild viral fever"
        clinicalNotes = "No red flags"
        medications = @(
            @{ name = "Paracetamol"; dosage = "500mg"; frequency = "TID"; duration = "3 days" }
        )
        investigations = @(
            @{ name = "CBC"; note = "if fever persists" }
        )
        lifestyleAdvice = "Rest and fluids"
        attachments = @()
        preferredPharmacies = @()
    }
    Ensure-SuccessResponse -Step "prescription save" -Response $savePrescription
    Add-Step -Name "prescription save" -Status "ok" -Detail "saved"

    $doctorPrescriptionDetail = Invoke-JsonPost -Url "$BaseUrl/api/doctor/prescription/detail" -Headers @{ dtoken = $doctorToken } -Body @{ appointmentId = $appointmentId }
    Ensure-SuccessResponse -Step "doctor prescription detail" -Response $doctorPrescriptionDetail
    Add-Step -Name "doctor prescription detail" -Status "ok" -Detail "fetched"

    $userPrescriptionDetail = Invoke-JsonPost -Url "$BaseUrl/api/user/prescription/detail" -Headers @{ token = $userToken } -Body @{ appointmentId = $appointmentId }
    Ensure-SuccessResponse -Step "user prescription detail" -Response $userPrescriptionDetail
    Add-Step -Name "user prescription detail" -Status "ok" -Detail "fetched"

    $registerPharmacy = Invoke-CurlMultipart -Url "$BaseUrl/api/pharmacist/register" -Headers @{} -Fields @{
        name = "Smoke Pharmacy $stamp"
        email = $pharmacyEmail
        password = $pharmacyPassword
        ownerName = "Smoke Owner"
        phone = "9999999999"
        alternatePhone = "8888888888"
        address = '{"line1":"Smoke Pharmacy Lane","line2":"Test City"}'
        licenseNumber = "LIC-$stamp"
        gstNumber = "GST-$stamp"
        deliveryOptions = '["pickup"]'
        serviceRadiusKm = "10"
    }
    Ensure-SuccessResponse -Step "pharmacy register" -Response $registerPharmacy
    Add-Step -Name "pharmacy register" -Status "ok" -Detail "submitted"

    $pendingPharmacies = Invoke-RestMethod -Uri "$BaseUrl/api/admin/pharmacies?status=pending" -Method GET -Headers @{ atoken = $adminToken }
    Ensure-SuccessResponse -Step "admin pharmacies pending" -Response $pendingPharmacies
    $pendingPharmacy = $pendingPharmacies.pharmacies | Where-Object { $_.email -eq $pharmacyEmail } | Select-Object -First 1
    if ($null -eq $pendingPharmacy) {
        throw "pending pharmacy lookup failed"
    }
    $pharmacyId = [string]$pendingPharmacy._id
    Add-Step -Name "pending pharmacy lookup" -Status "ok" -Detail "pharmacyId=$pharmacyId"

    $approvePharmacy = Invoke-JsonPost -Url "$BaseUrl/api/admin/pharmacies/review" -Headers @{ atoken = $adminToken } -Body @{
        pharmacyId = $pharmacyId
        approve = $true
        notes = "approved via parity smoke"
    }
    Ensure-SuccessResponse -Step "admin approve pharmacy" -Response $approvePharmacy
    Add-Step -Name "admin approve pharmacy" -Status "ok" -Detail "approved"

    $pharmacistLogin = Invoke-JsonPost -Url "$BaseUrl/api/pharmacist/login" -Body @{
        email = $pharmacyEmail
        password = $pharmacyPassword
    }
    Ensure-SuccessResponse -Step "pharmacist login" -Response $pharmacistLogin
    $pharmacistToken = [string]$pharmacistLogin.token
    if ([string]::IsNullOrWhiteSpace($pharmacistToken)) {
        throw "pharmacist login failed: missing token"
    }
    Add-Step -Name "pharmacist login" -Status "ok" -Detail "token received"

    $pharmacistProfile = Invoke-RestMethod -Uri "$BaseUrl/api/pharmacist/profile" -Method GET -Headers @{ token = $pharmacistToken }
    Ensure-SuccessResponse -Step "pharmacist profile" -Response $pharmacistProfile
    Add-Step -Name "pharmacist profile" -Status "ok" -Detail "profile fetched"

    $userPharmacies = Invoke-RestMethod -Uri "$BaseUrl/api/user/pharmacies" -Method GET -Headers @{ token = $userToken }
    Ensure-SuccessResponse -Step "user pharmacies" -Response $userPharmacies
    $selectedPharmacy = $userPharmacies.pharmacies | Where-Object { $_._id -eq $pharmacyId } | Select-Object -First 1
    if ($null -eq $selectedPharmacy) {
        throw "approved pharmacy not visible in user pharmacies list"
    }
    Add-Step -Name "user pharmacies" -Status "ok" -Detail "approved pharmacy visible"

    $orderMedicine = Invoke-JsonPost -Url "$BaseUrl/api/user/order-medicine" -Headers @{ token = $userToken } -Body @{
        appointmentId = $appointmentId
        pharmacyId = $pharmacyId
        logistics = @{ method = "pickup" }
        notesForPharmacist = "Smoke order note"
    }
    Ensure-SuccessResponse -Step "user order medicine" -Response $orderMedicine
    $orderId = [string]$orderMedicine.order._id
    if ([string]::IsNullOrWhiteSpace($orderId)) {
        throw "order creation failed: missing order id"
    }
    Add-Step -Name "user order medicine" -Status "ok" -Detail "orderId=$orderId"

    $pharmacistOrders = Invoke-JsonPost -Url "$BaseUrl/api/pharmacist/orders" -Headers @{ token = $pharmacistToken } -Body @{}
    Ensure-SuccessResponse -Step "pharmacist orders" -Response $pharmacistOrders
    $orderInQueue = $pharmacistOrders.orders | Where-Object { $_._id -eq $orderId } | Select-Object -First 1
    if ($null -eq $orderInQueue) {
        throw "pharmacist order list missing created order"
    }
    Add-Step -Name "pharmacist orders" -Status "ok" -Detail "order visible"

    $acceptOrder = Invoke-JsonPost -Url "$BaseUrl/api/pharmacist/orders/update-status" -Headers @{ token = $pharmacistToken } -Body @{
        orderId = $orderId
        status = "accepted"
        note = "Accepted in parity smoke"
    }
    Ensure-SuccessResponse -Step "pharmacist order status" -Response $acceptOrder
    Add-Step -Name "pharmacist order status" -Status "ok" -Detail "accepted"

    $userOrderTimeline = Invoke-JsonPost -Url "$BaseUrl/api/user/pharmacy-orders/timeline" -Headers @{ token = $userToken } -Body @{ orderId = $orderId }
    Ensure-SuccessResponse -Step "user order timeline" -Response $userOrderTimeline
    if ([string]$userOrderTimeline.status -ne "accepted") {
        throw "user order timeline status mismatch: expected accepted, got $($userOrderTimeline.status)"
    }
    Add-Step -Name "user order timeline" -Status "ok" -Detail "status accepted"

    $adminDashboard = Invoke-RestMethod -Uri "$BaseUrl/api/admin/dashboard" -Method GET -Headers @{ atoken = $adminToken }
    Ensure-SuccessResponse -Step "admin dashboard" -Response $adminDashboard
    Add-Step -Name "admin dashboard" -Status "ok" -Detail "dashboard fetched"

    $output = [ordered]@{
        success = $true
        created = [ordered]@{
            doctorEmail = $doctorEmail
            userEmail = $userEmail
            pharmacyEmail = $pharmacyEmail
            appointmentId = $appointmentId
            pharmacyOrderId = $orderId
        }
        steps = $steps
    }

    $output | ConvertTo-Json -Depth 20
}
catch {
    Add-Step -Name "failure" -Status "error" -Detail $_.Exception.Message
    $output = [ordered]@{
        success = $false
        error = $_.Exception.Message
        steps = $steps
    }
    $output | ConvertTo-Json -Depth 20
    exit 1
}
