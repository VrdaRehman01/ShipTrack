import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { Api, describeError, errorCode } from './api';
import { configureSpec } from '../testing/helpers';

describe('describeError', () => {
  it('prefers the message the API sent', () => {
    expect(describeError(new HttpErrorResponse({ status: 400, error: { message: 'weightKg: too big' } }))).toBe('weightKg: too big');
  });

  it('explains a dropped connection', () => {
    expect(describeError(new HttpErrorResponse({ status: 0 }))).toContain('Check your connection');
  });

  it('never shows the raw body of a server failure', () => {
    expect(describeError(new HttpErrorResponse({ status: 502, error: '<html>Bad Gateway</html>' }))).toBe('ShipTrack hit a problem. Try again in a moment.');
  });

  it('has a fallback for anything else', () => {
    expect(describeError(new Error('boom'))).toBe('Something went wrong. Try again.');
    expect(describeError(new HttpErrorResponse({ status: 418 }))).toBe('Something went wrong. Try again.');
  });
});

describe('errorCode', () => {
  it('reads the code from an API error', () => {
    expect(errorCode(new HttpErrorResponse({ status: 404, error: { error: 'NOT_FOUND' } }))).toBe('NOT_FOUND');
  });

  it('is undefined for anything else', () => {
    expect(errorCode(new Error('boom'))).toBeUndefined();
    expect(errorCode(new HttpErrorResponse({ status: 500, error: null }))).toBeUndefined();
  });
});

describe('Api', () => {
  let api: Api;
  let backend: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    backend = configureSpec();
    api = TestBed.inject(Api);
  });

  afterEach(() => backend.verify());

  it('leaves empty filters out of the query string', () => {
    api.shipments('', 2, 10).subscribe();
    expect(backend.expectOne('/api/v1/shipments?page=2&size=10').request.method).toBe('GET');
  });

  it('includes a status filter when there is one', () => {
    api.shipments('IN_TRANSIT').subscribe();
    backend.expectOne('/api/v1/shipments?status=IN_TRANSIT&page=0&size=20');
  });

  it('reads one shipment and its history', () => {
    api.shipment(4).subscribe();
    api.history(4).subscribe();
    backend.expectOne('/api/v1/shipments/4');
    backend.expectOne('/api/v1/shipments/4/history');
  });

  it('sends the Idempotency-Key when creating a shipment', () => {
    api.createShipment({
      senderName: 'S', senderPhone: '1234567', pickupAddress: 'A',
      receiverName: 'R', receiverPhone: '1234567', deliveryAddress: 'B', weightKg: 1,
    }, 'key-1').subscribe();
    const call = backend.expectOne('/api/v1/shipments');
    expect(call.request.method).toBe('POST');
    expect(call.request.headers.get('Idempotency-Key')).toBe('key-1');
  });

  it('moves a shipment with the version it last saw, sending no empty note', () => {
    api.moveShipment(4, 'PICKED_UP', 2, '').subscribe();
    const call = backend.expectOne('/api/v1/shipments/4/status');
    expect(call.request.method).toBe('PATCH');
    expect(call.request.body).toEqual({ status: 'PICKED_UP', note: null, expectedVersion: 2 });
  });

  it('assigns a driver', () => {
    api.assignDriver(4, 9).subscribe();
    const call = backend.expectOne('/api/v1/shipments/4/driver');
    expect(call.request.method).toBe('PATCH');
    expect(call.request.body).toEqual({ driverId: 9 });
  });

  it('escapes a tracking code and trims spaces', () => {
    api.track(' ST/1 ').subscribe();
    backend.expectOne('/api/v1/public/shipments/ST%2F1');
  });

  it('reads the latest location', () => {
    api.latestLocation(4).subscribe();
    backend.expectOne('/api/v1/tracking/4/latest');
  });

  it('rounds GPS readings to the 6 decimals the API accepts', () => {
    api.postLocation(4, 24.123456789, 67.987654321).subscribe();
    expect(backend.expectOne('/api/v1/tracking/4/locations').request.body).toEqual({ latitude: 24.123457, longitude: 67.987654 });
  });

  it('reads invoices by shipment and by page', () => {
    api.invoiceForShipment(4).subscribe();
    api.invoices('PAID', 1).subscribe();
    backend.expectOne('/api/v1/invoices?shipmentId=4');
    backend.expectOne('/api/v1/invoices?status=PAID&page=1&size=20');
  });

  it('records a payment', () => {
    api.recordPayment(5).subscribe();
    expect(backend.expectOne('/api/v1/invoices/5/payments').request.method).toBe('POST');
  });

  it('lists and creates users', () => {
    api.users('DRIVER').subscribe();
    api.createUser('a@b.com', 'password1', 'A', 'DRIVER').subscribe();
    backend.expectOne('/api/v1/users?role=DRIVER&page=0&size=50');
    expect(backend.expectOne('/api/v1/users').request.body).toEqual({ email: 'a@b.com', password: 'password1', fullName: 'A', role: 'DRIVER' });
  });
});
