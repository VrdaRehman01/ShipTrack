import { ComponentFixture } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ShipmentPage } from './shipment.page';
import { Role, Shipment } from '../core/models';
import { NOT_FOUND, buttonNamed, configureSpec, hasButton, invoiceOf, pageOf, render, shipmentOf, signIn, textOf, userOf } from '../testing/helpers';

const SHIPMENT = '/api/v1/shipments/1';
const HISTORY = '/api/v1/shipments/1/history';
const LATEST = '/api/v1/tracking/1/latest';
const INVOICE = '/api/v1/invoices?shipmentId=1';
const NO_LOCATION = { error: 'NO_LOCATION_YET' };
const CHANGE = { fromStatus: null, toStatus: 'CREATED', changedBy: 7, changedByRole: 'CUSTOMER', note: 'at the door', changedAt: '2026-11-04T09:30:00Z' };

describe('ShipmentPage', () => {
  let backend: HttpTestingController;
  let fixture: ComponentFixture<ShipmentPage>;

  beforeEach(() => sessionStorage.clear());

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    fixture?.destroy();
    backend.verify();
  });

  function flushDrivers(role: Role): void {
    if (role === 'ADMIN') backend.expectOne((r) => r.url === '/api/v1/users').flush(pageOf([userOf('DRIVER', 9)]));
  }

  /** Opens the page and answers its first round of calls. Location and invoice calls are left to each test. */
  function open(role: Role, shipment: Shipment, userId = 7): void {
    signIn(role, userId);
    backend = configureSpec();
    fixture = render(ShipmentPage, { id: '1' });
    flushDrivers(role);
    backend.expectOne(SHIPMENT).flush(shipment);
    backend.expectOne(HISTORY).flush([CHANGE]);
    backend.expectOne(HISTORY).flush([CHANGE]);
  }

  function noLocation(): void {
    backend.expectOne(LATEST).flush(NO_LOCATION, NOT_FOUND);
  }

  async function text(): Promise<string> {
    await fixture.whenStable();
    fixture.detectChanges();
    return textOf(fixture);
  }

  it('shows the details and history', async () => {
    open('CUSTOMER', shipmentOf({ weightKg: '2.500', description: 'Books' }));
    const shown = await text();
    expect(shown).toContain('ST-2026-000001');
    expect(shown).toContain('House 12');
    expect(shown).toContain('2.5 kg, Books');
    expect(shown).toContain('by the customer');
    expect(shown).toContain('at the door');
    expect(shown).toContain('Not assigned yet');
  });

  it('thanks the customer right after booking', async () => {
    signIn('CUSTOMER');
    backend = configureSpec();
    fixture = render(ShipmentPage, { id: '1', booked: '1' });
    backend.expectOne(SHIPMENT).flush(shipmentOf());
    backend.expectOne(HISTORY).flush([]);
    backend.expectOne(HISTORY).flush([]);
    expect(await text()).toContain('Booked. Share the tracking code');
  });

  it.each([
    ['FORBIDDEN', 403, 'This shipment belongs to someone else.'],
    ['NOT_FOUND', 404, 'No shipment with this number.'],
  ])('explains a %s when loading', async (error, status, message) => {
    signIn('CUSTOMER');
    backend = configureSpec();
    fixture = render(ShipmentPage, { id: '1' });
    backend.expectOne(SHIPMENT).flush({ error, message: 'x' }, { status, statusText: 'x' });
    expect(backend.expectOne(HISTORY).cancelled).toBe(true); // no point finishing it once the shipment failed
    expect(await text()).toContain(message);
  });

  describe('what the customer can see about the delivery', () => {
    it('shows who is bringing it, how to call them, and when', async () => {
      open('CUSTOMER', shipmentOf({ driverId: 9, driverName: 'Bilal Ahmed', driverPhone: '+92 311 7654321', expectedDeliveryDate: '2026-11-12' }));
      const shown = await text();
      expect(shown).toContain('Bilal Ahmed');
      expect(shown).toContain('Thursday 12 November');
      const call = (fixture.nativeElement as HTMLElement).querySelector('a[href^="tel:"]') as HTMLAnchorElement;
      expect(call.getAttribute('href')).toBe('tel:+923117654321');
    });

    it('stops showing an expected date once delivered', async () => {
      open('CUSTOMER', shipmentOf({ status: 'DELIVERED', driverId: 9, expectedDeliveryDate: '2026-11-12' }));
      noLocation();
      backend.expectOne(INVOICE).flush(invoiceOf());
      expect(await text()).not.toContain('Expected');
    });

    it('still reads well for a driver with no number on file', async () => {
      open('CUSTOMER', shipmentOf({ driverId: 9, driverName: 'Bilal Ahmed' }));
      await text();
      expect((fixture.nativeElement as HTMLElement).querySelector('a[href^="tel:"]')).toBeNull();
    });
  });

  describe('live updates', () => {
    function answerLiveUpdate(shipment: Shipment): void {
      backend.expectOne(SHIPMENT).flush(shipment);
      backend.expectOne(HISTORY).flush([CHANGE]);
      backend.expectOne(LATEST).flush(NO_LOCATION, NOT_FOUND);
    }

    it('picks up a new status every 30 seconds without a reload', () => {
      vi.useFakeTimers();
      open('CUSTOMER', shipmentOf());
      vi.advanceTimersByTime(30_000);
      answerLiveUpdate(shipmentOf({ status: 'PICKED_UP', driverId: 9, driverName: 'Bilal Ahmed', version: 4 }));
      fixture.detectChanges();
      expect(textOf(fixture)).toContain('Picked up');
      expect(textOf(fixture)).toContain('Bilal Ahmed');
    });

    it('starts looking for the invoice as soon as it sees the delivery', () => {
      vi.useFakeTimers();
      open('CUSTOMER', shipmentOf({ status: 'IN_TRANSIT', driverId: 9 }));
      noLocation();
      vi.advanceTimersByTime(30_000);
      answerLiveUpdate(shipmentOf({ status: 'DELIVERED', driverId: 9 }));
      backend.expectOne(HISTORY).flush([CHANGE]);
      noLocation();
      backend.expectOne(INVOICE).flush(invoiceOf());
      fixture.detectChanges();
      expect(textOf(fixture)).toContain('PKR 550.00');
    });

    it('stops once the parcel is finished', () => {
      vi.useFakeTimers();
      open('CUSTOMER', shipmentOf({ status: 'CANCELLED' }));
      noLocation();
      vi.advanceTimersByTime(90_000);
      backend.expectNone(SHIPMENT);
    });

    it('does not poll the driver, who makes the changes', () => {
      vi.useFakeTimers();
      open('DRIVER', shipmentOf({ status: 'IN_TRANSIT', driverId: 9 }), 9);
      noLocation();
      vi.advanceTimersByTime(90_000);
      backend.expectNone(SHIPMENT);
    });

    it('waits while the tab is hidden', () => {
      vi.useFakeTimers();
      open('CUSTOMER', shipmentOf());
      vi.spyOn(document, 'hidden', 'get').mockReturnValue(true);
      vi.advanceTimersByTime(60_000);
      backend.expectNone(SHIPMENT);
    });

    it('survives a failed update and tries again next round', () => {
      vi.useFakeTimers();
      open('CUSTOMER', shipmentOf());
      vi.advanceTimersByTime(30_000);
      backend.expectOne(SHIPMENT).flush({}, { status: 500, statusText: 'Error' });
      backend.expectOne(HISTORY);
      backend.expectOne(LATEST);
      vi.advanceTimersByTime(30_000);
      answerLiveUpdate(shipmentOf({ status: 'PICKED_UP', driverId: 9 }));
      fixture.detectChanges();
      expect(textOf(fixture)).toContain('Picked up');
    });
  });

  describe('what each role can do', () => {
    it('a customer can cancel their own booking, after confirming', async () => {
      open('CUSTOMER', shipmentOf());
      await text();
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      buttonNamed(fixture, 'Cancel shipment').click();
      const call = backend.expectOne(`${SHIPMENT}/status`);
      expect(call.request.body).toEqual({ status: 'CANCELLED', note: null, expectedVersion: 3 });
      call.flush(shipmentOf({ status: 'CANCELLED', version: 4 }));
      backend.expectOne(HISTORY).flush([CHANGE]);
      noLocation();
      expect(await text()).toContain('Cancelled');
      expect(hasButton(fixture, 'Cancel shipment')).toBe(false);
    });

    it('nothing is sent if the customer changes their mind', async () => {
      open('CUSTOMER', shipmentOf());
      await text();
      vi.spyOn(window, 'confirm').mockReturnValue(false);
      buttonNamed(fixture, 'Cancel shipment').click();
      backend.expectNone(`${SHIPMENT}/status`);
    });

    it('the assigned driver moves it along', async () => {
      open('DRIVER', shipmentOf({ status: 'PICKED_UP', driverId: 9 }), 9);
      noLocation();
      await text();
      expect(hasButton(fixture, 'Cancel shipment')).toBe(false);
      buttonNamed(fixture, 'Start delivery').click();
      backend.expectOne(`${SHIPMENT}/status`).flush(shipmentOf({ status: 'IN_TRANSIT', driverId: 9, version: 4 }));
      backend.expectOne(HISTORY).flush([CHANGE]);
      noLocation();
      expect(await text()).toContain('On the way');
    });

    it('pickup is not offered until a driver is assigned', async () => {
      open('ADMIN', shipmentOf());
      await text();
      expect(hasButton(fixture, 'Mark picked up')).toBe(false);
      expect(hasButton(fixture, 'Assign driver')).toBe(true);
    });

    it('an admin assigns a driver', async () => {
      open('ADMIN', shipmentOf());
      await fixture.whenStable();
      fixture.detectChanges();
      const select = (fixture.nativeElement as HTMLElement).querySelector('select') as HTMLSelectElement;
      select.selectedIndex = 1;
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));
      const call = backend.expectOne(`${SHIPMENT}/driver`);
      expect(call.request.body).toEqual({ driverId: 9 });
      call.flush(shipmentOf({ driverId: 9, version: 4 }));
      expect(await text()).toContain('Driver Test User assigned.');
      expect(hasButton(fixture, 'Mark picked up')).toBe(true);
    });

    it('an unassigned driver cannot do anything', async () => {
      open('DRIVER', shipmentOf({ status: 'PICKED_UP', driverId: 9 }), 8);
      noLocation();
      await text();
      expect(hasButton(fixture, 'Start delivery')).toBe(false);
      expect(hasButton(fixture, 'Share my location')).toBe(false);
    });
  });

  describe('someone else changed it first', () => {
    it.each(['VERSION_MISMATCH', 'CONCURRENT_MODIFICATION'])('%s reloads the shipment', async (error) => {
      open('CUSTOMER', shipmentOf());
      await text();
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      buttonNamed(fixture, 'Cancel shipment').click();
      backend.expectOne(`${SHIPMENT}/status`).flush({ error, message: 'x' }, { status: 409, statusText: 'Conflict' });
      backend.expectOne(SHIPMENT).flush(shipmentOf({ version: 9 }));
      backend.expectOne(HISTORY).flush([CHANGE]);
      backend.expectOne(HISTORY).flush([CHANGE]);
      expect(await text()).toContain('Someone else updated this shipment');
    });

    it('other failures are shown as they are', async () => {
      open('CUSTOMER', shipmentOf());
      await text();
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      buttonNamed(fixture, 'Cancel shipment').click();
      backend.expectOne(`${SHIPMENT}/status`).flush({ error: 'X', message: 'Not allowed now' }, { status: 400, statusText: 'Bad' });
      expect(await text()).toContain('Not allowed now');
    });
  });

  describe('invoice', () => {
    const DELIVERED = shipmentOf({ status: 'DELIVERED', invoiced: true, driverId: 9 });

    it('is shown to the owner once delivered', async () => {
      open('CUSTOMER', DELIVERED);
      noLocation();
      backend.expectOne(INVOICE).flush(invoiceOf());
      expect(await text()).toContain('PKR 550.00');
    });

    it('is not requested for a driver', async () => {
      open('DRIVER', DELIVERED, 9);
      noLocation();
      backend.expectNone(INVOICE);
      expect(await text()).not.toContain('Invoice');
    });

    it('keeps checking until billing has created it', () => {
      vi.useFakeTimers();
      open('CUSTOMER', DELIVERED);
      noLocation();
      backend.expectOne(INVOICE).flush({ error: 'NOT_FOUND' }, NOT_FOUND);
      backend.expectNone(INVOICE);

      vi.advanceTimersByTime(3000);
      backend.expectOne(INVOICE).flush({ error: 'NOT_FOUND' }, NOT_FOUND);

      vi.advanceTimersByTime(3000);
      backend.expectOne(INVOICE).flush(invoiceOf());
      fixture.detectChanges();
      expect(textOf(fixture)).toContain('PKR 550.00');
    });

    it('stops checking on a real error', () => {
      vi.useFakeTimers();
      open('CUSTOMER', DELIVERED);
      noLocation();
      backend.expectOne(INVOICE).flush({ error: 'INTERNAL_ERROR' }, { status: 500, statusText: 'Error' });
      vi.advanceTimersByTime(9000);
      backend.expectNone(INVOICE);
    });

    it('stops checking when the page is closed', () => {
      vi.useFakeTimers();
      open('CUSTOMER', DELIVERED);
      noLocation();
      backend.expectOne(INVOICE).flush({ error: 'NOT_FOUND' }, NOT_FOUND);
      fixture.destroy();
      vi.advanceTimersByTime(9000);
      backend.expectNone(INVOICE);
    });

    it('an admin can record the payment', async () => {
      open('ADMIN', DELIVERED);
      noLocation();
      backend.expectOne(INVOICE).flush(invoiceOf());
      await text();
      buttonNamed(fixture, 'Record payment').click();
      backend.expectOne('/api/v1/invoices/5/payments').flush(invoiceOf({ status: 'PAID', paidAt: '2026-11-05T09:30:00Z' }));
      expect(await text()).toContain('Payment recorded for INV-2026-000005');
    });
  });

  describe('sharing the driver’s location', () => {
    const IN_TRANSIT = shipmentOf({ status: 'IN_TRANSIT', driverId: 9 });
    const DENIED = { code: 1, PERMISSION_DENIED: 1 };
    const NO_SIGNAL = { code: 2, PERMISSION_DENIED: 1 };
    let getCurrentPosition: ReturnType<typeof vi.fn>;

    function stubGps(secure = true): void {
      getCurrentPosition = vi.fn();
      Object.defineProperty(navigator, 'geolocation', { value: { getCurrentPosition }, configurable: true });
      Object.defineProperty(window, 'isSecureContext', { value: secure, configurable: true });
    }

    function toggle(on: boolean): void {
      const box = (fixture.nativeElement as HTMLElement).querySelector('input[type=checkbox]') as HTMLInputElement;
      box.checked = on;
      box.dispatchEvent(new Event('change'));
    }

    beforeEach(() => stubGps());

    it('posts the position', async () => {
      open('DRIVER', IN_TRANSIT, 9);
      noLocation();
      await text();
      buttonNamed(fixture, 'Share my location').click();
      getCurrentPosition.mock.calls[0][0]({ coords: { latitude: 24.86, longitude: 67.01 } });
      const call = backend.expectOne('/api/v1/tracking/1/locations');
      expect(call.request.body).toEqual({ latitude: 24.86, longitude: 67.01 });
      call.flush({ id: 1, shipmentId: 1, driverId: 9, latitude: 24.86, longitude: 67.01, recordedAt: '2026-11-04T12:10:00Z' });
      expect(await text()).toContain('Location shared.');
    });

    it('explains how to fix blocked location access', async () => {
      open('DRIVER', IN_TRANSIT, 9);
      noLocation();
      await text();
      buttonNamed(fixture, 'Share my location').click();
      getCurrentPosition.mock.calls[0][1](DENIED);
      expect(await text()).toContain('Location is blocked');
    });

    it('does not blame permissions for a weak GPS signal', async () => {
      open('DRIVER', IN_TRANSIT, 9);
      noLocation();
      await text();
      buttonNamed(fixture, 'Share my location').click();
      getCurrentPosition.mock.calls[0][1](NO_SIGNAL);
      const shown = await text();
      expect(shown).toContain('GPS fix');
      expect(shown).not.toContain('blocked');
    });

    it('asks for https when the site is not secure, and starts no timer', async () => {
      stubGps(false);
      open('DRIVER', IN_TRANSIT, 9);
      noLocation();
      await text();
      vi.useFakeTimers();
      toggle(true);
      vi.advanceTimersByTime(120_000);
      expect(getCurrentPosition).not.toHaveBeenCalled();
      vi.useRealTimers();
      expect(await text()).toContain('needs a secure connection');
    });

    it('keeps sharing every minute until turned off', async () => {
      open('DRIVER', IN_TRANSIT, 9);
      noLocation();
      await text();
      vi.useFakeTimers();
      toggle(true);
      expect(getCurrentPosition).toHaveBeenCalledTimes(1);
      vi.advanceTimersByTime(60_000);
      expect(getCurrentPosition).toHaveBeenCalledTimes(2);
      toggle(false);
      vi.advanceTimersByTime(180_000);
      expect(getCurrentPosition).toHaveBeenCalledTimes(2);
    });

    it('stops automatic sharing when permission is refused', async () => {
      open('DRIVER', IN_TRANSIT, 9);
      noLocation();
      await text();
      vi.useFakeTimers();
      toggle(true);
      getCurrentPosition.mock.calls[0][1](DENIED);
      vi.advanceTimersByTime(180_000);
      expect(getCurrentPosition).toHaveBeenCalledTimes(1);
    });

    it('stops automatic sharing when the page is closed', async () => {
      open('DRIVER', IN_TRANSIT, 9);
      noLocation();
      await text();
      vi.useFakeTimers();
      toggle(true);
      fixture.destroy();
      vi.advanceTimersByTime(180_000);
      expect(getCurrentPosition).toHaveBeenCalledTimes(1);
    });

    it('shows when the API refuses a location', async () => {
      open('DRIVER', IN_TRANSIT, 9);
      noLocation();
      await text();
      buttonNamed(fixture, 'Share my location').click();
      getCurrentPosition.mock.calls[0][0]({ coords: { latitude: 1, longitude: 2 } });
      backend.expectOne('/api/v1/tracking/1/locations').flush({ message: 'Shipment not in progress' }, { status: 409, statusText: 'Conflict' });
      expect(await text()).toContain('Shipment not in progress');
    });
  });
});
