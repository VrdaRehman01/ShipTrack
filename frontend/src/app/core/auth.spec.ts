import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';
import { AuthResponse } from './models';

const RESPONSE: AuthResponse = {
  accessToken: 'jwt-123',
  tokenType: 'Bearer',
  expiresIn: 900,
  user: { id: 7, email: 'ali@example.com', fullName: 'Ali Khan', role: 'CUSTOMER', createdAt: '2026-11-04T09:30:00Z' },
};

describe('AuthService + authInterceptor', () => {
  let auth: AuthService;
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
  });

  function logIn(): void {
    auth.login('ali@example.com', 'pw').subscribe();
    backend.expectOne('/api/v1/auth/login').flush(RESPONSE);
  }

  it('stores the session and exposes the user and role', () => {
    logIn();
    expect(auth.isLoggedIn()).toBe(true);
    expect(auth.role()).toBe('CUSTOMER');
    expect(auth.token()).toBe('jwt-123');
    expect(JSON.parse(sessionStorage.getItem('shiptrack.session')!).user.id).toBe(7);
  });

  it('adds the bearer token to API calls but not to login', () => {
    logIn();
    http.get('/api/v1/shipments').subscribe();
    expect(backend.expectOne('/api/v1/shipments').request.headers.get('Authorization')).toBe('Bearer jwt-123');

    auth.login('x@example.com', 'pw').subscribe();
    expect(backend.expectOne('/api/v1/auth/login').request.headers.has('Authorization')).toBe(false);
  });

  it('forgets the token once it has expired (15 minutes)', () => {
    vi.useFakeTimers();
    logIn();
    vi.advanceTimersByTime(901_000);
    expect(auth.token()).toBeNull();
    expect(auth.isLoggedIn()).toBe(false);
  });

  it('a 401 on a protected call logs out and goes to login', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    logIn();
    http.get('/api/v1/shipments').subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/shipments').flush({ error: 'UNAUTHORIZED' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isLoggedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'expired' } });
  });
});
