import { ComponentFixture } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DriverPage } from './driver.page';
import { callLink, dayFromNow, directionsLink } from '../core/links';
import { AvailableJob, Shipment } from '../core/models';
import { buttonNamed, configureSpec, hasButton, pageOf, render, shipmentOf, signIn, textOf } from '../testing/helpers';

const JOB: AvailableJob = {
  id: 42, pickupAddress: 'F-7/2, Islamabad', deliveryAddress: 'Clifton, Karachi', weightKg: '2.500', description: 'Books', createdAt: '2026-11-04T09:30:00Z',
};
const MINE = { driverId: 9, driverName: 'Test User' };
const statusUrl = (status: string) => `/api/v1/shipments?status=${status}&page=0&size=50`;

describe('DriverPage', () => {
  let backend: HttpTestingController;
  let fixture: ComponentFixture<DriverPage>;

  beforeEach(() => {
    sessionStorage.clear();
    signIn('DRIVER', 9);
    backend = configureSpec();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    fixture?.destroy();
    backend.verify();
  });

  function answer(onRoute: Shipment[], open: AvailableJob[]): void {
    for (const status of ['IN_TRANSIT', 'PICKED_UP', 'CREATED']) {
      backend.expectOne(statusUrl(status)).flush(pageOf(onRoute.filter((s) => s.status === status)));
    }
    backend.expectOne('/api/v1/shipments/available?page=0&size=50').flush(pageOf(open));
  }

  function open(onRoute: Shipment[], jobs: AvailableJob[]): void {
    fixture = render(DriverPage);
    answer(onRoute, jobs);
  }

  async function text(): Promise<string> {
    await fixture.whenStable();
    fixture.detectChanges();
    return textOf(fixture);
  }

  function links(): string[] {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('a[href]')).map((a) => a.getAttribute('href') ?? '');
  }

  it('says what to do when there is nothing yet', async () => {
    open([], []);
    const shown = await text();
    expect(shown).toContain('Nothing on your route yet');
    expect(shown).toContain('No open parcels right now');
  });

  describe('open parcels', () => {
    it('shows where it goes, without anyone’s name or number', async () => {
      open([], [JOB]);
      const shown = await text();
      expect(shown).toContain('F-7/2, Islamabad');
      expect(shown).toContain('Clifton, Karachi');
      expect(shown).toContain('2.5 kg, Books');
    });

    it.each([['Today', 0], ['Tomorrow', 1], ['In 3 days', 3]] as const)('accepts with "%s" as the delivery day', async (label, days) => {
      open([], [JOB]);
      await text();
      buttonNamed(fixture, label).click();
      const call = backend.expectOne('/api/v1/shipments/42/accept');
      expect(call.request.body).toEqual({ expectedDeliveryDate: dayFromNow(days) });
      call.flush(shipmentOf({ id: 42, ...MINE }));
      answer([shipmentOf({ id: 42, ...MINE })], []);
      const shown = await text();
      expect(shown).toContain('Parcel accepted');
      expect(shown).toContain('Pick up from Sara');
    });

    it.each(['JOB_TAKEN', 'CONCURRENT_MODIFICATION'])('explains losing the race (%s) and refreshes', async (error) => {
      open([], [JOB]);
      await text();
      buttonNamed(fixture, 'Today').click();
      backend.expectOne('/api/v1/shipments/42/accept').flush({ error, message: 'x' }, { status: 409, statusText: 'Conflict' });
      answer([], []);
      const shown = await text();
      expect(shown).toContain('Another driver took that parcel first');
      expect(shown).not.toContain('F-7/2, Islamabad');
    });

    it('shows other failures as they are', async () => {
      open([], [JOB]);
      await text();
      buttonNamed(fixture, 'Today').click();
      backend.expectOne('/api/v1/shipments/42/accept').flush({ message: 'expectedDeliveryDate: can’t be in the past' }, { status: 400, statusText: 'Bad' });
      answer([], [JOB]);
      expect(await text()).toContain('can’t be in the past');
    });
  });

  describe('parcels on the route', () => {
    it('lists the parcel on the road first', async () => {
      open([shipmentOf({ id: 1, status: 'CREATED', trackingCode: 'ST-2026-000001', ...MINE }), shipmentOf({ id: 2, status: 'IN_TRANSIT', trackingCode: 'ST-2026-000002', ...MINE })], []);
      const shown = await text();
      expect(shown.indexOf('ST-2026-000002')).toBeLessThan(shown.indexOf('ST-2026-000001'));
    });

    it('before pickup, offers the sender and the first step', async () => {
      open([shipmentOf(MINE)], []);
      await text();
      expect(hasButton(fixture, 'Mark picked up')).toBe(true);
      expect(links()).toContain(callLink('+92 300 1234567'));
      expect(links()).toContain(directionsLink('House 12'));
      expect(links()).not.toContain(callLink('+92 321 7654321'));
    });

    it('after pickup, offers the receiver instead', async () => {
      open([shipmentOf({ status: 'PICKED_UP', ...MINE })], []);
      await text();
      expect(hasButton(fixture, 'Start delivery')).toBe(true);
      expect(links()).toContain(callLink('+92 321 7654321'));
      expect(links()).toContain(directionsLink('Flat 3B'));
      expect(links()).not.toContain(callLink('+92 300 1234567'));
    });

    it('shows the promised day', async () => {
      open([shipmentOf({ expectedDeliveryDate: '2026-11-12', ...MINE })], []);
      expect(await text()).toContain('Deliver by Thu 12 Nov');
    });

    it('can give back a parcel before pickup, after confirming', async () => {
      open([shipmentOf(MINE)], []);
      await text();
      const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
      buttonNamed(fixture, 'Give back').click();
      backend.expectNone('/api/v1/shipments/1/release');

      confirm.mockReturnValue(true);
      buttonNamed(fixture, 'Give back').click();
      backend.expectOne('/api/v1/shipments/1/release').flush(shipmentOf());
      answer([], [JOB]);
      expect(await text()).toContain('ST-2026-000001 is back in open parcels.');
    });

    it('does not offer giving back once collected', async () => {
      open([shipmentOf({ status: 'PICKED_UP', ...MINE })], []);
      await text();
      expect(hasButton(fixture, 'Give back')).toBe(false);
    });

    it('shows why giving back failed', async () => {
      open([shipmentOf(MINE)], []);
      await text();
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      buttonNamed(fixture, 'Give back').click();
      backend.expectOne('/api/v1/shipments/1/release').flush({ message: 'Already picked up' }, { status: 409, statusText: 'Conflict' });
      answer([shipmentOf(MINE)], []);
      expect(await text()).toContain('Already picked up');
    });

    it('moves to the next step with the version it saw', async () => {
      open([shipmentOf({ version: 6, ...MINE })], []);
      await text();
      buttonNamed(fixture, 'Mark picked up').click();
      const call = backend.expectOne('/api/v1/shipments/1/status');
      expect(call.request.body).toEqual({ status: 'PICKED_UP', note: null, expectedVersion: 6 });
      call.flush(shipmentOf({ status: 'PICKED_UP', ...MINE }));
      answer([shipmentOf({ status: 'PICKED_UP', ...MINE })], []);
      const shown = await text();
      expect(shown).toContain('ST-2026-000001: picked up.');
      expect(hasButton(fixture, 'Start delivery')).toBe(true);
    });

    it('asks before marking delivered, because that bills the customer', async () => {
      open([shipmentOf({ status: 'IN_TRANSIT', ...MINE })], []);
      await text();
      const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
      buttonNamed(fixture, 'Mark delivered').click();
      expect(confirm).toHaveBeenCalled();
      backend.expectNone('/api/v1/shipments/1/status');

      confirm.mockReturnValue(true);
      vi.spyOn(window, 'prompt').mockReturnValue('1234');
      buttonNamed(fixture, 'Mark delivered').click();
      const delivery = backend.expectOne('/api/v1/shipments/1/status');
      expect(delivery.request.body.deliveryCode).toBe('1234');
      delivery.flush(shipmentOf({ status: 'DELIVERED', ...MINE }));
      answer([], []);
      expect(await text()).toContain('delivered');
    });

    it('shows when a step fails and refreshes the list', async () => {
      open([shipmentOf(MINE)], []);
      await text();
      buttonNamed(fixture, 'Mark picked up').click();
      backend.expectOne('/api/v1/shipments/1/status').flush({ message: 'Shipment changed' }, { status: 409, statusText: 'Conflict' });
      answer([shipmentOf(MINE)], []);
      expect(await text()).toContain('Shipment changed');
    });
  });

  describe('staying up to date', () => {
    it('refreshes every 30 seconds', () => {
      vi.useFakeTimers();
      open([], []);
      vi.advanceTimersByTime(30_000);
      answer([], [JOB]);
      fixture.detectChanges();
      expect(textOf(fixture)).toContain('F-7/2, Islamabad');
    });

    it('waits while the phone screen is hidden', () => {
      vi.useFakeTimers();
      open([], []);
      vi.spyOn(document, 'hidden', 'get').mockReturnValue(true);
      vi.advanceTimersByTime(60_000);
      backend.expectNone('/api/v1/shipments/available?page=0&size=50');
    });

    it('stops when the page is closed', () => {
      vi.useFakeTimers();
      open([], []);
      fixture.destroy();
      vi.advanceTimersByTime(60_000);
      backend.expectNone('/api/v1/shipments/available?page=0&size=50');
    });

    it('offers a retry if the first load fails', async () => {
      fixture = render(DriverPage);
      backend.expectOne(statusUrl('IN_TRANSIT')).flush({}, { status: 500, statusText: 'Error' });
      backend.match(() => true); // the other calls were cancelled along with this one
      expect(await text()).toContain('ShipTrack hit a problem');
      buttonNamed(fixture, 'Try again').click();
      answer([], []);
      expect(await text()).toContain('Nothing on your route yet');
    });
  });
});

describe('driver links', () => {
  it('strips spaces and dashes from phone numbers', () => {
    expect(callLink('+92 300-1234567')).toBe('tel:+923001234567');
  });

  it('escapes addresses for Google Maps', () => {
    expect(directionsLink('House 12, F-7/2 & more')).toBe('https://www.google.com/maps/dir/?api=1&destination=House%2012%2C%20F-7%2F2%20%26%20more');
  });

  it('works out days in the person’s own calendar, across a month end', () => {
    expect(dayFromNow(0, new Date(2026, 10, 30, 23, 30))).toBe('2026-11-30');
    expect(dayFromNow(1, new Date(2026, 10, 30, 23, 30))).toBe('2026-12-01');
    expect(dayFromNow(3, new Date(2026, 11, 30))).toBe('2027-01-02');
  });
});
