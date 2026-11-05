// ShipTrack load test (success metric: p95 < 300 ms with 100 concurrent users, locally).
//
// Run against the local stack (docker compose up -d --wait):
//   docker run --rm -i -v "$PWD/k6:/scripts" grafana/k6 run /scripts/load-test.js -e BASE_URL=http://host.docker.internal:8080
//
// Each virtual user behaves like a customer: create a shipment, list shipments, open one, and check it
// through the public tracking page. Logins happen once in setup() (BCrypt is deliberately slow and is
// not part of the per-request latency target).
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CUSTOMERS = 20;

export const options = {
  scenarios: {
    customers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 100 }, // ramp up
        { duration: '60s', target: 100 }, // 100 concurrent users
        { duration: '10s', target: 0 },   // ramp down
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<300'],        // the project's success metric
    http_req_failed: ['rate<0.01'],          // < 1% errors
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const createLatency = new Trend('shipment_create_ms', true);
const listLatency = new Trend('shipment_list_ms', true);
const getLatency = new Trend('shipment_get_ms', true);
const publicLatency = new Trend('public_track_ms', true);

const json = { headers: { 'Content-Type': 'application/json' } };

export function setup() {
  const run = Date.now();
  const tokens = [];
  for (let i = 0; i < CUSTOMERS; i++) {
    const email = `k6-${run}-${i}@example.com`;
    const creds = JSON.stringify({ email, password: 'Load-test-pass1' });
    const reg = http.post(`${BASE_URL}/api/v1/auth/register`,
      JSON.stringify({ email, password: 'Load-test-pass1', fullName: `Load User ${i}` }), json);
    check(reg, { 'setup: registered': (r) => r.status === 201 });
    const login = http.post(`${BASE_URL}/api/v1/auth/login`, creds, json);
    check(login, { 'setup: logged in': (r) => r.status === 200 });
    tokens.push(login.json('accessToken'));
  }
  return { tokens };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const auth = { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } };

  const created = http.post(`${BASE_URL}/api/v1/shipments`, JSON.stringify({
    senderName: 'Sara Ahmed', senderPhone: '+92 300 1234567', pickupAddress: 'F-7/2, Islamabad',
    receiverName: 'Ali Khan', receiverPhone: '+92 321 7654321', deliveryAddress: 'Clifton, Karachi',
    weightKg: 1 + (__ITER % 10) / 2, description: 'k6',
  }), { ...auth, tags: { name: 'POST /shipments' } });
  createLatency.add(created.timings.duration);
  check(created, { 'create 201': (r) => r.status === 201 });
  if (created.status !== 201) {
    sleep(1);
    return;
  }
  const id = created.json('id');
  const code = created.json('trackingCode');

  const list = http.get(`${BASE_URL}/api/v1/shipments?size=20`, { ...auth, tags: { name: 'GET /shipments' } });
  listLatency.add(list.timings.duration);
  check(list, { 'list 200': (r) => r.status === 200 });

  const one = http.get(`${BASE_URL}/api/v1/shipments/${id}`, { ...auth, tags: { name: 'GET /shipments/{id}' } });
  getLatency.add(one.timings.duration);
  check(one, { 'get 200': (r) => r.status === 200 });

  const pub = http.get(`${BASE_URL}/api/v1/public/shipments/${code}`, { tags: { name: 'GET /public/shipments/{code}' } });
  publicLatency.add(pub.timings.duration);
  check(pub, { 'public 200': (r) => r.status === 200 });

  sleep(1); // think time: each user does ~4 requests per second at most
}
