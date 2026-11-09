import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NewShipmentPage } from './new-shipment.page';
import { configureSpec, render, shipmentOf, signIn, submitForm, textOf, typeInto } from '../testing/helpers';

describe('NewShipmentPage', () => {
  let backend: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    sessionStorage.clear();
    signIn('CUSTOMER');
    backend = configureSpec();
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => backend.verify());

  async function fillIn(fixture: ComponentFixture<NewShipmentPage>, description = ''): Promise<void> {
    await typeInto(fixture, '#senderName', 'Sara');
    await typeInto(fixture, '#senderPhone', '+92 300 1234567');
    await typeInto(fixture, '#pickupAddress', 'House 12');
    await typeInto(fixture, '#receiverName', 'Ali');
    await typeInto(fixture, '#receiverPhone', '+92 321 7654321');
    await typeInto(fixture, '#deliveryAddress', 'Flat 3B');
    await typeInto(fixture, '#description', description);
  }

  it('books the parcel and opens it', async () => {
    const fixture = render(NewShipmentPage);
    await fillIn(fixture, '  Books  ');
    submitForm(fixture);
    const call = backend.expectOne('/api/v1/shipments');
    expect(call.request.body.description).toBe('Books');
    expect(call.request.body.weightKg).toBe(1);
    expect(call.request.headers.get('Idempotency-Key')).toBeTruthy();
    call.flush(shipmentOf({ id: 42 }));
    expect(navigate).toHaveBeenCalledWith(['/shipments', 42], { queryParams: { booked: 1 } });
  });

  it('sends no description when it was left blank', async () => {
    const fixture = render(NewShipmentPage);
    await fillIn(fixture);
    submitForm(fixture);
    const call = backend.expectOne('/api/v1/shipments');
    expect(call.request.body.description).toBeUndefined();
    call.flush(shipmentOf());
  });

  it('retries with the same key, so a dropped connection cannot book twice', async () => {
    const fixture = render(NewShipmentPage);
    await fillIn(fixture);
    submitForm(fixture);
    const first = backend.expectOne('/api/v1/shipments');
    const key = first.request.headers.get('Idempotency-Key');
    first.error(new ProgressEvent('error'), { status: 0 });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(textOf(fixture)).toContain('Check your connection');

    submitForm(fixture);
    const second = backend.expectOne('/api/v1/shipments');
    expect(second.request.headers.get('Idempotency-Key')).toBe(key);
    second.flush(shipmentOf());
  });

  it('uses a phone rule that fits the database column', async () => {
    const fixture = render(NewShipmentPage);
    await fillIn(fixture);
    await typeInto(fixture, '#senderPhone', '+' + '1'.repeat(20));
    const field = (fixture.nativeElement as HTMLElement).querySelector('#senderPhone')!;
    expect(field.classList.contains('ng-invalid')).toBe(true);

    await typeInto(fixture, '#senderPhone', '+' + '1'.repeat(19));
    expect(field.classList.contains('ng-valid')).toBe(true);
  });
});
