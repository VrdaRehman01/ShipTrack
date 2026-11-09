import { HttpClient, HttpContext, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { BACKGROUND_CALL } from './auth.service';
import {
  ApiError, AvailableJob, Invoice, Location, NewShipment, Page, PublicTracking, Role, Shipment, ShipmentStatus, StatusChange, User,
} from './models';

/** Options for an automatic refresh, which must not keep an idle session alive. */
function refreshOptions(background: boolean): { context?: HttpContext } {
  return background ? { context: new HttpContext().set(BACKGROUND_CALL, true) } : {};
}

/** Turns any failed call into one sentence a person can act on. */
export function describeError(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as Partial<ApiError> | null;
    if (body && typeof body === 'object' && body.message) return body.message;
    if (err.status === 0) return 'Can’t reach ShipTrack. Check your connection and try again.';
    if (err.status >= 500) return 'ShipTrack hit a problem. Try again in a moment.';
  }
  return 'Something went wrong. Try again.';
}

export function errorCode(err: unknown): string | undefined {
  return err instanceof HttpErrorResponse ? (err.error as Partial<ApiError> | null)?.error : undefined;
}

function params(values: Record<string, string | number | undefined | null>): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(values)) {
    if (v !== undefined && v !== null && v !== '') p = p.set(k, String(v));
  }
  return p;
}

/** Every ShipTrack endpoint the web app uses, all through the gateway (same origin, /api/...). */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  // shipments
  shipments(status: ShipmentStatus | '' = '', page = 0, size = 20): Observable<Page<Shipment>> {
    return this.http.get<Page<Shipment>>('/api/v1/shipments', { params: params({ status, page, size }) });
  }
  shipment(id: number, background = false): Observable<Shipment> {
    return this.http.get<Shipment>(`/api/v1/shipments/${id}`, refreshOptions(background));
  }
  history(id: number, background = false): Observable<StatusChange[]> {
    return this.http.get<StatusChange[]>(`/api/v1/shipments/${id}/history`, refreshOptions(background));
  }
  availableJobs(page = 0, size = 50): Observable<Page<AvailableJob>> {
    return this.http.get<Page<AvailableJob>>('/api/v1/shipments/available', { params: params({ page, size }) });
  }
  acceptJob(id: number, expectedDeliveryDate: string): Observable<Shipment> {
    return this.http.post<Shipment>(`/api/v1/shipments/${id}/accept`, { expectedDeliveryDate });
  }
  releaseJob(id: number): Observable<Shipment> {
    return this.http.post<Shipment>(`/api/v1/shipments/${id}/release`, {});
  }
  createShipment(body: NewShipment, idempotencyKey: string): Observable<Shipment> {
    return this.http.post<Shipment>('/api/v1/shipments', body, { headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }) });
  }
  moveShipment(id: number, status: ShipmentStatus, expectedVersion: number, note?: string): Observable<Shipment> {
    return this.http.patch<Shipment>(`/api/v1/shipments/${id}/status`, { status, note: note || null, expectedVersion });
  }
  assignDriver(id: number, driverId: number, expectedDeliveryDate?: string): Observable<Shipment> {
    return this.http.patch<Shipment>(`/api/v1/shipments/${id}/driver`, { driverId, expectedDeliveryDate });
  }
  track(code: string): Observable<PublicTracking> {
    return this.http.get<PublicTracking>(`/api/v1/public/shipments/${encodeURIComponent(code.trim())}`);
  }

  // tracking
  latestLocation(shipmentId: number, background = false): Observable<Location> {
    return this.http.get<Location>(`/api/v1/tracking/${shipmentId}/latest`, refreshOptions(background));
  }
  postLocation(shipmentId: number, latitude: number, longitude: number): Observable<Location> {
    return this.http.post<Location>(`/api/v1/tracking/${shipmentId}/locations`, {
      latitude: Number(latitude.toFixed(6)),
      longitude: Number(longitude.toFixed(6)),
    });
  }

  // invoices
  invoiceForShipment(shipmentId: number): Observable<Invoice> {
    return this.http.get<Invoice>('/api/v1/invoices', { params: params({ shipmentId }) });
  }
  invoices(status: '' | 'ISSUED' | 'PAID' = '', page = 0, size = 20): Observable<Page<Invoice>> {
    return this.http.get<Page<Invoice>>('/api/v1/invoices', { params: params({ status, page, size }) });
  }
  recordPayment(invoiceId: number): Observable<Invoice> {
    return this.http.post<Invoice>(`/api/v1/invoices/${invoiceId}/payments`, {});
  }

  // users (admin)
  users(role: Role | '' = '', page = 0, size = 50): Observable<Page<User>> {
    return this.http.get<Page<User>>('/api/v1/users', { params: params({ role, page, size }) });
  }
  createUser(email: string, password: string, fullName: string, role: Role, phone?: string): Observable<User> {
    return this.http.post<User>('/api/v1/users', { email, password, fullName, role, phone });
  }
  updateUser(id: number, fullName: string, phone?: string): Observable<User> {
    return this.http.patch<User>(`/api/v1/users/${id}`, { fullName, phone });
  }
}
