import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../app';
import { NotFoundPage } from '../pages/not-found.page';
import { LocationMapComponent } from './location-map.component';
import { TrackingCodeComponent } from './tracking-code.component';
import { Role } from '../core/models';
import { configureSpec, render, signIn, textOf } from '../testing/helpers';

describe('App header', () => {
  beforeEach(() => sessionStorage.clear());

  function navFor(role: Role | null): string {
    if (role) signIn(role);
    configureSpec();
    return textOf(render(App));
  }

  it('offers visitors log in and sign up', () => {
    const text = navFor(null);
    expect(text).toContain('Log in');
    expect(text).toContain('Create account');
  });

  it('gives a customer their menu', () => {
    const text = navFor('CUSTOMER');
    expect(text).toContain('Send a parcel');
    expect(text).toContain('Invoices');
    expect(text).not.toContain('People');
  });

  it('gives a driver only deliveries and tracking', () => {
    const text = navFor('DRIVER');
    expect(text).toContain('Jobs');
    expect(text).toContain('All deliveries');
    expect(text).not.toContain('Invoices');
  });

  it('gives an admin the whole menu', () => {
    const text = navFor('ADMIN');
    expect(text).toContain('Invoices');
    expect(text).toContain('People');
    expect(text).toContain('Log out');
  });
});

describe('NotFoundPage', () => {
  it('renders', () => {
    configureSpec();
    expect(textOf(render(NotFoundPage)).trim().length).toBeGreaterThan(0);
  });
});

describe('TrackingCodeComponent', () => {
  const clipboard = (writeText: (text: string) => Promise<void>) =>
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });

  afterEach(() => Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true }));

  it('copies the code and says so', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    clipboard(writeText);
    const fixture = render(TrackingCodeComponent, { code: 'ST-2026-000001' });
    await fixture.componentInstance.copy();
    expect(writeText).toHaveBeenCalledWith('ST-2026-000001');
    expect(fixture.componentInstance.copied()).toBe(true);
  });

  it('selects the code instead when the clipboard is unavailable', async () => {
    Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true });
    const fixture = render(TrackingCodeComponent, { code: 'ST-2026-000001' });
    await fixture.componentInstance.copy();
    expect(fixture.componentInstance.copied()).toBe(false);
    expect(window.getSelection()?.toString()).toBe('ST-2026-000001');
  });
});

describe('LocationMapComponent', () => {
  it('draws a map and follows a new position, then cleans up', async () => {
    TestBed.configureTestingModule({});
    const fixture = render(LocationMapComponent, { latitude: 24.86, longitude: 67.01 });
    const map = fixture.nativeElement.querySelector('.map') as HTMLElement;
    expect(map.getAttribute('aria-label')).toContain('24.86');
    expect(map.classList.contains('leaflet-container')).toBe(true);

    fixture.componentRef.setInput('latitude', 25);
    fixture.detectChanges();
    expect(map.getAttribute('aria-label')).toContain('25');

    fixture.destroy();
    expect(map.querySelector('.leaflet-pane')).toBeNull();
  });
});
