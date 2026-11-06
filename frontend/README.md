# ShipTrack web

The website for ShipTrack: customers send and follow parcels, drivers update deliveries from their phone,
admins assign drivers and record payments, and anyone can track a parcel by its code.

Angular 21 (standalone components, signals), served by nginx in production. The browser only talks to its own
origin: nginx forwards `/api`, Swagger and health checks to the API gateway, so no CORS is needed.

## Run it

**With everything else (recommended):** from the repo root
```bash
docker compose up -d --build --wait
```
Then open http://localhost:4200.

**While working on the UI** (hot reload; the backend must be running on :8080):
```bash
cd frontend
npm ci
npx ng serve          # http://localhost:4200, /api is proxied to http://localhost:8080 (proxy.conf.json)
```

## Test and build
```bash
npx ng test --watch=false   # unit tests (Vitest)
npx ng build                # production build in dist/shiptrack-web/browser
```

## Layout
```
src/app/
├── core/       api client, auth (session, interceptor, guards), models, lifecycle rules
├── shared/     route line (the journey), tracking-code label, location map
└── pages/      track (public home), login, register, shipments, shipment, new shipment, invoices, people
```

## Design
- **The route line** is the one bold element: a shipment's four stops drawn like a transit line.
- **Type:** Barlow, a typeface based on road and highway signs. Barlow Condensed for headings and codes, Barlow for text.
- **Colour:** navy ink, route green for progress and actions, high-visibility amber only for tracking codes.
- **Drivers first on mobile:** 44–56 px touch targets, one big next-step button, "Share my location" uses the phone's GPS.
- Keyboard focus is always visible, motion respects "reduce motion", and tables stack on small screens.
- Strict Content Security Policy (`script-src 'self'`): no inline scripts, map tiles only from OpenStreetMap.
