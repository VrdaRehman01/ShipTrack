import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TrackPage } from './track.page';
import { NOT_FOUND, configureSpec, render, submitForm, textOf, typeInto } from '../testing/helpers';

const URL = '/api/v1/public/shipments/ST-2026-000001';
const TRACKED = {
  trackingCode: 'ST-2026-000001', status: 'IN_TRANSIT', lastUpdated: '2026-11-04T12:10:00Z',
  timeline: [{ status: 'CREATED', at: '2026-11-04T09:30:00Z' }],
};

describe('TrackPage', () => {
  let backend: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    sessionStorage.clear();
    backend = configureSpec();
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => backend.verify());

  async function refreshed(fixture: ComponentFixture<TrackPage>): Promise<string> {
    await fixture.whenStable();
    fixture.detectChanges();
    return textOf(fixture);
  }

  it('looks up the code from the address and shows its status', async () => {
    const fixture = render(TrackPage, { code: 'ST-2026-000001' });
    backend.expectOne(URL).flush(TRACKED);
    expect(await refreshed(fixture)).toContain('On the way');
  });

  it('offers sign-up to visitors before they search', () => {
    expect(textOf(render(TrackPage))).toContain('Sending parcels?');
  });

  it('says so when a code does not exist', async () => {
    const fixture = render(TrackPage, { code: 'ST-2026-000001' });
    backend.expectOne(URL).flush({ error: 'NOT_FOUND', message: 'x' }, NOT_FOUND);
    expect(await refreshed(fixture)).toContain('No parcel with code ST-2026-000001');
  });

  it('shows other failures as they are', async () => {
    const fixture = render(TrackPage, { code: 'ST-2026-000001' });
    backend.expectOne(URL).flush({ message: 'Too many attempts. Wait a minute and try again.' }, { status: 429, statusText: 'Too Many' });
    expect(await refreshed(fixture)).toContain('Too many attempts');
  });

  it('rejects a badly formatted code without calling the API', async () => {
    const fixture = render(TrackPage);
    await typeInto(fixture, '#code', 'hello');
    submitForm(fixture);
    expect(await refreshed(fixture)).toContain('Tracking codes look like');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('opens the address for a new code, tidied up', async () => {
    const fixture = render(TrackPage);
    await typeInto(fixture, '#code', ' st-2026-000001 ');
    submitForm(fixture);
    expect(navigate).toHaveBeenCalledWith(['/track', 'ST-2026-000001']);
  });

  it('refreshes the result when the same code is searched again', async () => {
    const fixture = render(TrackPage, { code: 'ST-2026-000001' });
    backend.expectOne(URL).flush(TRACKED);
    await fixture.whenStable();
    submitForm(fixture);
    backend.expectOne(URL).flush({ ...TRACKED, status: 'DELIVERED' });
    expect(navigate).not.toHaveBeenCalled();
    expect(await refreshed(fixture)).toContain('Delivered');
  });
});
