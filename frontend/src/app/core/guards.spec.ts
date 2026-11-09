import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, RouterStateSnapshot, UrlTree } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { authGuard, guestGuard, roleGuard } from './guards';
import { Role } from './models';
import { configureSpec, signIn } from '../testing/helpers';

function run(guard: CanActivateFn, url = '/shipments/4'): boolean | string {
  const result = TestBed.runInInjectionContext(() =>
    guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot));
  return result instanceof UrlTree ? result.toString() : (result as boolean);
}

function as(role: Role | null): void {
  if (role) signIn(role);
  configureSpec();
}

describe('route guards', () => {
  beforeEach(() => sessionStorage.clear());

  it('authGuard lets a logged-in user through', () => {
    as('CUSTOMER');
    expect(run(authGuard)).toBe(true);
  });

  it('authGuard sends visitors to login and remembers where they were going', () => {
    as(null);
    expect(run(authGuard)).toBe('/login?next=%2Fshipments%2F4');
  });

  it('roleGuard allows the listed roles', () => {
    as('ADMIN');
    expect(run(roleGuard('CUSTOMER', 'ADMIN'))).toBe(true);
  });

  it('roleGuard sends other roles to their start page', () => {
    as('DRIVER');
    expect(run(roleGuard('ADMIN'))).toBe('/driver');
  });

  it('roleGuard refuses when nobody is logged in', () => {
    as(null);
    expect(run(roleGuard('ADMIN'))).toBe('/shipments');
  });

  it('guestGuard shows login to visitors', () => {
    as(null);
    expect(run(guestGuard)).toBe(true);
  });

  it('guestGuard skips login for someone already logged in', () => {
    as('CUSTOMER');
    expect(run(guestGuard)).toBe('/shipments');
  });
});
