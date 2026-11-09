import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { InvoicesPage } from './invoices.page';
import { ShipmentsPage } from './shipments.page';
import { UsersPage } from './users.page';
import { Role } from '../core/models';
import { buttonNamed, configureSpec, hasButton, invoiceOf, pageOf, render, shipmentOf, signIn, submitForm, textOf, typeInto, userOf } from '../testing/helpers';

const SERVER_ERROR = { status: 500, statusText: 'Server Error' };

async function refreshed(fixture: ComponentFixture<unknown>): Promise<string> {
  await fixture.whenStable();
  fixture.detectChanges();
  return textOf(fixture);
}

describe('list pages', () => {
  let backend: HttpTestingController;

  function as(role: Role): void {
    signIn(role);
    backend = configureSpec();
    TestBed.tick();
  }

  beforeEach(() => sessionStorage.clear());
  afterEach(() => backend.verify());

  describe('ShipmentsPage', () => {
    const FIRST = '/api/v1/shipments?page=0&size=20';

    it.each([
      ['CUSTOMER', 'My shipments'],
      ['DRIVER', 'My deliveries'],
      ['ADMIN', 'All shipments'],
    ] as const)('titles the list for a %s', async (role, heading) => {
      as(role);
      const fixture = render(ShipmentsPage);
      backend.expectOne(FIRST).flush(pageOf([shipmentOf()]));
      const text = await refreshed(fixture);
      expect(text).toContain(heading);
      expect(text).toContain('ST-2026-000001');
      expect(text).toContain('Booked');
    });

    it('invites a new customer to send a parcel', async () => {
      as('CUSTOMER');
      const fixture = render(ShipmentsPage);
      backend.expectOne(FIRST).flush(pageOf([]));
      expect(await refreshed(fixture)).toContain('You haven’t sent anything yet');
    });

    it('filters by status, starting again from the first page', async () => {
      as('ADMIN');
      const fixture = render(ShipmentsPage);
      backend.expectOne(FIRST).flush(pageOf([shipmentOf()], 2, 5));
      await refreshed(fixture);
      buttonNamed(fixture, 'On the way').click();
      backend.expectOne('/api/v1/shipments?status=IN_TRANSIT&page=0&size=20').flush(pageOf([]));
      expect(await refreshed(fixture)).toContain('No on the way shipments');
    });

    it('drops a slower earlier request when the filter changes', async () => {
      as('ADMIN');
      const fixture = render(ShipmentsPage);
      const slow = backend.expectOne(FIRST);
      await fixture.whenStable();
      buttonNamed(fixture, 'Delivered').click();
      expect(slow.cancelled).toBe(true);
      backend.expectOne('/api/v1/shipments?status=DELIVERED&page=0&size=20').flush(pageOf([]));
    });

    it('moves between pages', async () => {
      as('ADMIN');
      const fixture = render(ShipmentsPage);
      backend.expectOne(FIRST).flush(pageOf([shipmentOf()], 0, 3));
      await refreshed(fixture);
      expect(buttonNamed(fixture, 'Newer').disabled).toBe(true);
      buttonNamed(fixture, 'Older').click();
      backend.expectOne('/api/v1/shipments?page=1&size=20').flush(pageOf([shipmentOf()], 1, 3));
      expect(await refreshed(fixture)).toContain('Page 2 of 3');
    });

    it('offers a retry after a failure, and clears the error when it works', async () => {
      as('ADMIN');
      const fixture = render(ShipmentsPage);
      backend.expectOne(FIRST).flush({}, SERVER_ERROR);
      expect(await refreshed(fixture)).toContain('ShipTrack hit a problem');
      buttonNamed(fixture, 'Try again').click();
      backend.expectOne(FIRST).flush(pageOf([shipmentOf()]));
      expect(await refreshed(fixture)).not.toContain('ShipTrack hit a problem');
    });
  });

  describe('InvoicesPage', () => {
    const FIRST = '/api/v1/invoices?page=0&size=20';

    it('shows a customer their invoices, without the payment button', async () => {
      as('CUSTOMER');
      const fixture = render(InvoicesPage);
      backend.expectOne(FIRST).flush(pageOf([invoiceOf()]));
      const text = await refreshed(fixture);
      expect(text).toContain('INV-2026-000005');
      expect(text).toContain('PKR 550.00');
      expect(hasButton(fixture, 'Record payment')).toBe(false);
    });

    it('lets an admin record a payment and reloads the list', async () => {
      as('ADMIN');
      const fixture = render(InvoicesPage);
      backend.expectOne(FIRST).flush(pageOf([invoiceOf()]));
      await refreshed(fixture);
      buttonNamed(fixture, 'Record payment').click();
      backend.expectOne('/api/v1/invoices/5/payments').flush(invoiceOf({ status: 'PAID' }));
      backend.expectOne(FIRST).flush(pageOf([invoiceOf({ status: 'PAID', paidAt: '2026-11-05T09:30:00Z' })]));
      const text = await refreshed(fixture);
      expect(text).toContain('Payment recorded for INV-2026-000005');
      expect(hasButton(fixture, 'Record payment')).toBe(false);
    });

    it('filters by payment state', async () => {
      as('ADMIN');
      const fixture = render(InvoicesPage);
      backend.expectOne(FIRST).flush(pageOf([]));
      await refreshed(fixture);
      buttonNamed(fixture, 'Paid').click();
      backend.expectOne('/api/v1/invoices?status=PAID&page=0&size=20').flush(pageOf([]));
      expect(await refreshed(fixture)).toContain('No invoices paid yet');
    });

    it('clears an old error once loading works again', async () => {
      as('ADMIN');
      const fixture = render(InvoicesPage);
      backend.expectOne(FIRST).flush({}, SERVER_ERROR);
      expect(await refreshed(fixture)).toContain('ShipTrack hit a problem');
      buttonNamed(fixture, 'All').click();
      backend.expectOne(FIRST).flush(pageOf([]));
      expect(await refreshed(fixture)).not.toContain('ShipTrack hit a problem');
    });

    it('shows why a payment failed', async () => {
      as('ADMIN');
      const fixture = render(InvoicesPage);
      backend.expectOne(FIRST).flush(pageOf([invoiceOf()]));
      await refreshed(fixture);
      buttonNamed(fixture, 'Record payment').click();
      backend.expectOne('/api/v1/invoices/5/payments').flush({ message: 'already paid' }, { status: 409, statusText: 'Conflict' });
      expect(await refreshed(fixture)).toContain('already paid');
    });
  });

  describe('UsersPage', () => {
    const DRIVERS = '/api/v1/users?role=DRIVER&page=0&size=50';

    it('starts on drivers', async () => {
      as('ADMIN');
      const fixture = render(UsersPage);
      backend.expectOne(DRIVERS).flush(pageOf([userOf('DRIVER')]));
      const text = await refreshed(fixture);
      expect(text).toContain('Test User');
      expect(text).toContain('Driver');
    });

    it('can reach people past the first page', async () => {
      as('ADMIN');
      const fixture = render(UsersPage);
      backend.expectOne(DRIVERS).flush(pageOf([userOf('DRIVER')], 0, 2));
      await refreshed(fixture);
      buttonNamed(fixture, 'Older').click();
      backend.expectOne('/api/v1/users?role=DRIVER&page=1&size=50').flush(pageOf([userOf('DRIVER', 8)], 1, 2));
      expect(await refreshed(fixture)).toContain('Page 2 of 2');
    });

    it('filters by role', async () => {
      as('ADMIN');
      const fixture = render(UsersPage);
      backend.expectOne(DRIVERS).flush(pageOf([]));
      const empty = await refreshed(fixture);
      expect(empty).toContain('Nobody with this role yet');
      buttonNamed(fixture, 'Everyone').click();
      backend.expectOne('/api/v1/users?page=0&size=50').flush(pageOf([]));
    });

    async function addPerson(fixture: ComponentFixture<UsersPage>): Promise<void> {
      await typeInto(fixture, '#nu-name', 'New Driver');
      await typeInto(fixture, '#nu-email', 'driver@example.com');
      await typeInto(fixture, '#nu-password', 'Dr1ver-pass!');
      submitForm(fixture);
    }

    it('adds a person and reloads the list', async () => {
      as('ADMIN');
      const fixture = render(UsersPage);
      backend.expectOne(DRIVERS).flush(pageOf([]));
      await addPerson(fixture);
      const call = backend.expectOne('/api/v1/users');
      expect(call.request.body).toEqual({ email: 'driver@example.com', password: 'Dr1ver-pass!', fullName: 'New Driver', role: 'DRIVER' });
      call.flush(userOf('DRIVER'));
      backend.expectOne(DRIVERS).flush(pageOf([userOf('DRIVER')]));
      expect(await refreshed(fixture)).toContain('added as driver');
    });

    it('saves a driver’s phone number', async () => {
      as('ADMIN');
      const fixture = render(UsersPage);
      backend.expectOne(DRIVERS).flush(pageOf([]));
      await typeInto(fixture, '#nu-phone', ' +92 311 7654321 ');
      await addPerson(fixture);
      const call = backend.expectOne('/api/v1/users');
      expect(call.request.body.phone).toBe('+92 311 7654321');
      call.flush({ ...userOf('DRIVER'), phone: '+92 311 7654321' });
      backend.expectOne(DRIVERS).flush(pageOf([{ ...userOf('DRIVER'), phone: '+92 311 7654321' }]));
      expect(await refreshed(fixture)).toContain('+92 311 7654321');
    });

    describe('editing someone', () => {
      async function openEditor(user = { ...userOf('DRIVER', 9), phone: '+92 300 1111111' }): Promise<ComponentFixture<UsersPage>> {
        as('ADMIN');
        const fixture = render(UsersPage);
        backend.expectOne(DRIVERS).flush(pageOf([user]));
        await refreshed(fixture);
        buttonNamed(fixture, 'Edit').click();
        fixture.detectChanges();
        await refreshed(fixture);
        return fixture;
      }

      it('starts from their current details', async () => {
        const fixture = await openEditor();
        const form = fixture.nativeElement as HTMLElement;
        expect(textOf(fixture)).toContain('Edit Test User');
        expect((form.querySelector('#ed-name') as HTMLInputElement).value).toBe('Test User');
        expect((form.querySelector('#ed-phone') as HTMLInputElement).value).toBe('+92 300 1111111');
      });

      it('saves a new phone number and reloads the list', async () => {
        const fixture = await openEditor();
        await typeInto(fixture, '#ed-phone', '+92 311 7654321');
        submitForm(fixture);
        const call = backend.expectOne('/api/v1/users/9');
        expect(call.request.method).toBe('PATCH');
        expect(call.request.body).toEqual({ fullName: 'Test User', phone: '+92 311 7654321' });
        call.flush({ ...userOf('DRIVER', 9), phone: '+92 311 7654321' });
        backend.expectOne(DRIVERS).flush(pageOf([{ ...userOf('DRIVER', 9), phone: '+92 311 7654321' }]));
        const shown = await refreshed(fixture);
        expect(shown).toContain('Test User updated.');
        expect(shown).toContain('Add a person');
      });

      it('removes the number when the field is cleared', async () => {
        const fixture = await openEditor();
        await typeInto(fixture, '#ed-phone', '   ');
        (fixture.componentInstance as unknown as { saveEdit(): void }).saveEdit();
        const call = backend.expectOne('/api/v1/users/9');
        expect(call.request.body.phone).toBeUndefined();
        call.flush(userOf('DRIVER', 9));
        backend.expectOne(DRIVERS).flush(pageOf([userOf('DRIVER', 9)]));
      });

      it('keeps the form open if saving fails', async () => {
        const fixture = await openEditor();
        submitForm(fixture);
        backend.expectOne('/api/v1/users/9').flush({ message: 'phone: must be a phone number' }, { status: 400, statusText: 'Bad' });
        const shown = await refreshed(fixture);
        expect(shown).toContain('phone: must be a phone number');
        expect(shown).toContain('Edit Test User');
      });

      it('can be cancelled without saving', async () => {
        const fixture = await openEditor();
        buttonNamed(fixture, 'Cancel').click();
        expect(await refreshed(fixture)).toContain('Add a person');
      });
    });

    it('shows why adding a person failed', async () => {
      as('ADMIN');
      const fixture = render(UsersPage);
      backend.expectOne(DRIVERS).flush(pageOf([]));
      await addPerson(fixture);
      backend.expectOne('/api/v1/users').flush({ message: 'Email already registered' }, { status: 409, statusText: 'Conflict' });
      expect(await refreshed(fixture)).toContain('Email already registered');
    });
  });
});
