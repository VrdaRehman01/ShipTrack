"""Generates ShipTrack.postman_collection.json: the end-to-end API flow, run in CI via Newman.

Edit the steps here, then run:  python postman/build_collection.py
Keeping the source in Python keeps the (long) JSON collection reviewable in pull requests.
"""
import json
from pathlib import Path

BASE = "{{baseUrl}}"


def req(name, method, path, token=None, body=None, tests=(), pre=(), headers=None):
    hdrs = [{"key": "Content-Type", "value": "application/json"}] if body is not None else []
    for k, v in (headers or {}).items():
        hdrs.append({"key": k, "value": v})
    request = {
        "method": method,
        "header": hdrs,
        "url": BASE + path,
        "auth": {"type": "bearer", "bearer": [{"key": "token", "value": "{{" + token + "}}", "type": "string"}]}
        if token else {"type": "noauth"},
    }
    if body is not None:
        request["body"] = {"mode": "raw", "raw": json.dumps(body, indent=2)}
    events = [{"listen": "test", "script": {"exec": list(tests)}}]
    if pre:
        events.append({"listen": "prerequest", "script": {"exec": list(pre)}})
    return {"name": name, "event": events, "request": request}


def status(code):
    return f"pm.test('status {code}', () => pm.response.to.have.status({code}));"


def error_code(code):
    return f"pm.test('error {code}', () => pm.expect(pm.response.json().error).to.eql('{code}'));"


def setvar(name, expr):
    return f"pm.collectionVariables.set('{name}', {expr});"


SHIPMENT = {
    "senderName": "Sara Ahmed", "senderPhone": "+92 300 1234567", "pickupAddress": "House 12, F-7/2, Islamabad",
    "receiverName": "Ali Khan", "receiverPhone": "+92 321 7654321", "deliveryAddress": "Flat 3B, Clifton, Karachi",
    "weightKg": 2.5, "description": "Books",
}

auth = [
    req("Register customer", "POST", "/api/v1/auth/register",
        body={"email": "{{customerEmail}}", "password": "S3cure-pass!", "fullName": "Ali Khan"},
        pre=[setvar("customerEmail", "`customer-${Date.now()}@example.com`"),
             setvar("otherEmail", "`other-${Date.now()}@example.com`"),
             setvar("driverEmail", "`driver-${Date.now()}@example.com`")],
        tests=[status(201),
               "pm.test('role CUSTOMER', () => pm.expect(pm.response.json().role).to.eql('CUSTOMER'));",
               "pm.test('no password leaked', () => pm.expect(pm.response.json()).to.not.have.property('passwordHash'));",
               "pm.test('correlation id', () => pm.expect(pm.response.headers.get('X-Correlation-Id')).to.be.a('string'));"]),
    req("Register duplicate (409)", "POST", "/api/v1/auth/register",
        body={"email": "{{customerEmail}}", "password": "S3cure-pass!", "fullName": "Ali Khan"},
        tests=[status(409), error_code("EMAIL_ALREADY_REGISTERED")]),
    req("Register with bad input (400)", "POST", "/api/v1/auth/register",
        body={"email": "not-an-email", "password": "short", "fullName": ""},
        tests=[status(400), error_code("VALIDATION_FAILED")]),
    req("Login customer", "POST", "/api/v1/auth/login",
        body={"email": "{{customerEmail}}", "password": "S3cure-pass!"},
        tests=[status(200), "const b = pm.response.json();",
               "pm.test('15-minute token', () => pm.expect(b.expiresIn).to.eql(900));",
               setvar("customerToken", "b.accessToken"), setvar("customerId", "b.user.id")]),
    req("Login wrong password (401)", "POST", "/api/v1/auth/login",
        body={"email": "{{customerEmail}}", "password": "wrong-password"},
        tests=[status(401), error_code("INVALID_CREDENTIALS")]),
    req("Register + login second customer", "POST", "/api/v1/auth/register",
        body={"email": "{{otherEmail}}", "password": "S3cure-pass!", "fullName": "Someone Else"},
        tests=[status(201)]),
    req("Login second customer", "POST", "/api/v1/auth/login",
        body={"email": "{{otherEmail}}", "password": "S3cure-pass!"},
        tests=[status(200), setvar("otherToken", "pm.response.json().accessToken")]),
    req("Login admin", "POST", "/api/v1/auth/login",
        body={"email": "{{adminEmail}}", "password": "{{adminPassword}}"},
        tests=[status(200), "pm.test('is admin', () => pm.expect(pm.response.json().user.role).to.eql('ADMIN'));",
               setvar("adminToken", "pm.response.json().accessToken")]),
    req("Customer cannot create users (403)", "POST", "/api/v1/users", token="customerToken",
        body={"email": "x@example.com", "password": "Dr1ver-pass!", "fullName": "X", "role": "ADMIN"},
        tests=[status(403), error_code("FORBIDDEN")]),
    req("Admin creates driver", "POST", "/api/v1/users", token="adminToken",
        body={"email": "{{driverEmail}}", "password": "Dr1ver-pass!", "fullName": "Bilal Ahmed", "role": "DRIVER"},
        tests=[status(201), setvar("driverId", "pm.response.json().id")]),
    req("Login driver", "POST", "/api/v1/auth/login",
        body={"email": "{{driverEmail}}", "password": "Dr1ver-pass!"},
        tests=[status(200), setvar("driverToken", "pm.response.json().accessToken")]),
    req("Admin lists drivers", "GET", "/api/v1/users?role=DRIVER&size=5", token="adminToken",
        tests=[status(200), "pm.test('page shape', () => pm.expect(pm.response.json()).to.include.keys("
                            "'content','page','size','totalElements','totalPages'));"]),
    req("Me without token (401 at gateway)", "GET", "/api/v1/users/me",
        tests=[status(401), error_code("UNAUTHORIZED")]),
]

shipments = [
    req("Create shipment", "POST", "/api/v1/shipments", token="customerToken", body=SHIPMENT,
        headers={"Idempotency-Key": "{{idempotencyKey}}"},
        pre=[setvar("idempotencyKey", "pm.variables.replaceIn('{{$guid}}')")],
        tests=[status(201), "const s = pm.response.json();",
               "pm.test('tracking code format', () => pm.expect(s.trackingCode).to.match(/^ST-\\d{4}-\\d{6,}$/));",
               "pm.test('starts CREATED', () => pm.expect(s.status).to.eql('CREATED'));",
               "pm.test('weight as decimal string', () => pm.expect(s.weightKg).to.eql('2.500'));",
               setvar("shipmentId", "s.id"), setvar("trackingCode", "s.trackingCode")]),
    req("Retry with same Idempotency-Key (no duplicate)", "POST", "/api/v1/shipments", token="customerToken",
        body=SHIPMENT, headers={"Idempotency-Key": "{{idempotencyKey}}"},
        tests=[status(200),
               "pm.test('same shipment', () => pm.expect(pm.response.json().id).to.eql(pm.collectionVariables.get('shipmentId')));",
               "pm.test('replay header', () => pm.expect(pm.response.headers.get('Idempotent-Replayed')).to.eql('true'));"]),
    req("Other customer cannot read it (403, FR-6)", "GET", "/api/v1/shipments/{{shipmentId}}", token="otherToken",
        tests=[status(403), error_code("FORBIDDEN")]),
    req("Customer lists own shipments", "GET", "/api/v1/shipments?size=5", token="customerToken",
        tests=[status(200),
               "pm.test('contains new shipment', () => pm.expect(pm.response.json().content.map(s => s.id))"
               ".to.include(pm.collectionVariables.get('shipmentId')));"]),
    req("Drivers cannot create shipments (403)", "POST", "/api/v1/shipments", token="driverToken", body=SHIPMENT,
        tests=[status(403)]),
    req("Skipping a step is rejected (400, FR-5)", "PATCH", "/api/v1/shipments/{{shipmentId}}/status", token="adminToken",
        body={"status": "DELIVERED"},
        tests=[status(400), error_code("INVALID_STATUS_TRANSITION"),
               "pm.test('explains', () => pm.expect(pm.response.json().message).to.include('CREATED to DELIVERED'));"]),
    req("Pickup without driver is rejected (400)", "PATCH", "/api/v1/shipments/{{shipmentId}}/status", token="adminToken",
        body={"status": "PICKED_UP"}, tests=[status(400), error_code("DRIVER_NOT_ASSIGNED")]),
    req("Admin assigns driver", "PATCH", "/api/v1/shipments/{{shipmentId}}/driver", token="adminToken",
        body={"driverId": "{{driverId}}"},
        tests=[status(200), "pm.test('assigned', () => pm.expect(pm.response.json().driverId)"
                            ".to.eql(pm.collectionVariables.get('driverId')));"]),
    req("Assigning a non-driver is rejected (400)", "PATCH", "/api/v1/shipments/{{shipmentId}}/driver", token="adminToken",
        body={"driverId": "{{customerId}}"}, tests=[status(400), error_code("INVALID_DRIVER")]),
    req("Driver: PICKED_UP", "PATCH", "/api/v1/shipments/{{shipmentId}}/status", token="driverToken",
        body={"status": "PICKED_UP", "note": "Collected from reception"}, tests=[status(200)]),
    req("Driver posts location (FR-8)", "POST", "/api/v1/tracking/{{shipmentId}}/locations", token="driverToken",
        body={"latitude": 33.7215, "longitude": 73.0433}, tests=[status(201)]),
    req("Driver: IN_TRANSIT", "PATCH", "/api/v1/shipments/{{shipmentId}}/status", token="driverToken",
        body={"status": "IN_TRANSIT"}, tests=[status(200)]),
    req("Driver posts second location", "POST", "/api/v1/tracking/{{shipmentId}}/locations", token="driverToken",
        body={"latitude": 24.860966, "longitude": 67.01004}, tests=[status(201)]),
    req("Bad coordinates are rejected (400)", "POST", "/api/v1/tracking/{{shipmentId}}/locations", token="driverToken",
        body={"latitude": 123, "longitude": 0}, tests=[status(400), error_code("VALIDATION_FAILED")]),
    req("Customer reads latest location", "GET", "/api/v1/tracking/{{shipmentId}}/latest", token="customerToken",
        tests=[status(200), "pm.test('latest is Karachi', () => pm.expect(pm.response.json().latitude).to.eql(24.860966));"]),
    req("Other customer cannot track it (403)", "GET", "/api/v1/tracking/{{shipmentId}}/latest", token="otherToken",
        tests=[status(403)]),
    req("Driver: DELIVERED", "PATCH", "/api/v1/shipments/{{shipmentId}}/status", token="driverToken",
        body={"status": "DELIVERED"},
        tests=[status(200), "pm.test('delivered', () => pm.expect(pm.response.json().deliveredAt).to.be.a('string'));"]),
    req("Status history", "GET", "/api/v1/shipments/{{shipmentId}}/history", token="customerToken",
        tests=[status(200), "pm.test('4 steps', () => pm.expect(pm.response.json().map(c => c.toStatus))"
                            ".to.eql(['CREATED','PICKED_UP','IN_TRANSIT','DELIVERED']));"]),
    req("Public tracking by code (no login, no personal data)", "GET", "/api/v1/public/shipments/{{trackingCode}}",
        tests=[status(200), "const p = pm.response.json();",
               "pm.test('delivered', () => pm.expect(p.status).to.eql('DELIVERED'));",
               "pm.test('no personal data', () => pm.expect(p).to.not.have.any.keys('receiverName','deliveryAddress','customerId'));"]),
]

billing = [
    req("Invoice created automatically (FR-7)", "GET", "/api/v1/invoices?shipmentId={{shipmentId}}", token="customerToken",
        tests=["// Invoicing runs in the background right after delivery: poll for up to ~15 s.",
               "const tries = Number(pm.collectionVariables.get('invoiceTries') || 0);",
               "if (pm.response.code === 404 && tries < 15) {",
               "  pm.collectionVariables.set('invoiceTries', tries + 1);",
               "  setTimeout(() => {}, 1000);",
               "  postman.setNextRequest(pm.info.requestName);",
               "} else {",
               "  " + status(200),
               "  const i = pm.response.json();",
               "  pm.test('priced: 250 + 120 x 2.5', () => pm.expect(i.amount).to.eql('550.00'));",
               "  pm.test('PKR', () => pm.expect(i.currency).to.eql('PKR'));",
               "  pm.test('ISSUED', () => pm.expect(i.status).to.eql('ISSUED'));",
               "  " + setvar("invoiceId", "i.id"),
               "}"]),
    req("Other customer cannot see the invoice (403)", "GET", "/api/v1/invoices?shipmentId={{shipmentId}}", token="otherToken",
        tests=[status(403)]),
    req("Customer cannot mark paid (403)", "POST", "/api/v1/invoices/{{invoiceId}}/payments", token="customerToken",
        tests=[status(403)]),
    req("Admin records payment", "POST", "/api/v1/invoices/{{invoiceId}}/payments", token="adminToken",
        tests=[status(200), "pm.test('PAID', () => pm.expect(pm.response.json().status).to.eql('PAID'));"]),
]

gateway = [
    req("Internal APIs are not reachable through the gateway", "POST", "/internal/invoices", body={},
        headers={"X-Internal-Api-Key": "guess"},
        tests=["pm.test('blocked', () => pm.expect(pm.response.code).to.be.oneOf([401, 403, 404]));"]),
    req("Gateway health", "GET", "/actuator/health", tests=[status(200)]),
]

collection = {
    "info": {
        "name": "ShipTrack",
        "description": "End-to-end ShipTrack v1 flow through the API gateway. Run top to bottom (Runner or Newman). "
                       "Generated by postman/build_collection.py.",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "variable": [
        {"key": "baseUrl", "value": "http://localhost:8080"},
        {"key": "adminEmail", "value": "admin@shiptrack.local"},
        {"key": "adminPassword", "value": "change-me-admin"},
    ],
    "item": [
        {"name": "1. Auth & users", "item": auth},
        {"name": "2. Shipments & tracking", "item": shipments},
        {"name": "3. Billing", "item": billing},
        {"name": "4. Gateway", "item": gateway},
    ],
}

out = Path(__file__).with_name("ShipTrack.postman_collection.json")
out.write_text(json.dumps(collection, indent=2) + "\n", encoding="utf-8", newline="\n")
print(f"Wrote {out} ({sum(len(f['item']) for f in collection['item'])} requests)")
