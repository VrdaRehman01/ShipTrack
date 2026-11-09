import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Type } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { authInterceptor } from '../core/auth.interceptor';
import { Invoice, Role, Shipment, User } from '../core/models';

/** Shared set-up for specs. Only specs import this file, so it never reaches the production bundle. */

export function userOf(role: Role, id = 7): User {
  return { id, email: 'test@example.com', fullName: 'Test User', role, createdAt: '2026-11-04T09:30:00Z' };
}

/** Call before configureSpec(): the auth service reads the session when it is created. */
export function signIn(role: Role, id = 7): void {
  const now = Date.now();
  sessionStorage.setItem('shiptrack.session', JSON.stringify({
    token: 'jwt', expiresAt: now + 900_000, renewAt: now + 300_000, user: userOf(role, id),
  }));
}

export function configureSpec(): HttpTestingController {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
  });
  return TestBed.inject(HttpTestingController);
}

export function render<T>(component: Type<T>, inputs: Record<string, unknown> = {}): ComponentFixture<T> {
  const fixture = TestBed.createComponent(component);
  for (const [name, value] of Object.entries(inputs)) fixture.componentRef.setInput(name, value);
  fixture.detectChanges();
  return fixture;
}

export function textOf(fixture: ComponentFixture<unknown>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}

export function buttonNamed(fixture: ComponentFixture<unknown>, name: string): HTMLButtonElement {
  const found = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
    .find((b) => b.textContent?.trim() === name);
  if (!found) throw new Error(`No button named "${name}"`);
  return found;
}

export function hasButton(fixture: ComponentFixture<unknown>, name: string): boolean {
  return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
    .some((b) => b.textContent?.trim() === name);
}

/** Types into a template-driven field. ngModel registers its control asynchronously, so await this. */
export async function typeInto(fixture: ComponentFixture<unknown>, selector: string, value: string): Promise<void> {
  await fixture.whenStable();
  const field = (fixture.nativeElement as HTMLElement).querySelector(selector) as HTMLInputElement;
  field.value = value;
  field.dispatchEvent(new Event('input'));
  fixture.detectChanges();
}

export function submitForm(fixture: ComponentFixture<unknown>): void {
  (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));
  fixture.detectChanges();
}

export const NOT_FOUND = { status: 404, statusText: 'Not Found' };

export function shipmentOf(overrides: Partial<Shipment> = {}): Shipment {
  return {
    id: 1, trackingCode: 'ST-2026-000001', status: 'CREATED', customerId: 7, driverId: null,
    senderName: 'Sara', senderPhone: '+92 300 1234567', pickupAddress: 'House 12',
    receiverName: 'Ali', receiverPhone: '+92 321 7654321', deliveryAddress: 'Flat 3B',
    weightKg: '2.500', description: null, invoiced: false, version: 3,
    createdAt: '2026-11-04T09:30:00Z', updatedAt: '2026-11-04T09:30:00Z', deliveredAt: null,
    driverName: null, driverPhone: null, expectedDeliveryDate: null,
    ...overrides,
  };
}

export function invoiceOf(overrides: Partial<Invoice> = {}): Invoice {
  return {
    id: 5, invoiceNumber: 'INV-2026-000005', shipmentId: 1, trackingCode: 'ST-2026-000001', customerId: 7,
    weightKg: '2.500', baseFee: '250.00', weightCharge: '300.00', amount: '550.00', currency: 'PKR',
    status: 'ISSUED', issuedAt: '2026-11-04T09:30:00Z', paidAt: null,
    ...overrides,
  };
}

export function pageOf<T>(content: T[], page = 0, totalPages = 1) {
  return { content, page, size: 20, totalElements: content.length, totalPages };
}
