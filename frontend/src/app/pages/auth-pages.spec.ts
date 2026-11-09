import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LoginPage } from './login.page';
import { RegisterPage } from './register.page';
import { configureSpec, render, submitForm, textOf, typeInto, userOf } from '../testing/helpers';

const LOGGED_IN = { accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 900, user: userOf('CUSTOMER') };
const WRONG_PASSWORD = { status: 401, statusText: 'Unauthorized' };

describe('auth pages', () => {
  let backend: HttpTestingController;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    sessionStorage.clear();
    backend = configureSpec();
    const router = TestBed.inject(Router);
    navigateByUrl = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => backend.verify());

  describe('LoginPage', () => {
    async function logIn(inputs: Record<string, unknown> = {}) {
      const fixture = render(LoginPage, inputs);
      await typeInto(fixture, '#email', 'ali@example.com');
      await typeInto(fixture, '#password', 'S3cure-pass!');
      submitForm(fixture);
      return fixture;
    }

    it('sends the credentials and goes to the start page', async () => {
      await logIn();
      const call = backend.expectOne('/api/v1/auth/login');
      expect(call.request.body).toEqual({ email: 'ali@example.com', password: 'S3cure-pass!' });
      call.flush(LOGGED_IN);
      expect(navigateByUrl).toHaveBeenCalledWith('/shipments');
    });

    it('takes a driver straight to their job screen', async () => {
      await logIn();
      backend.expectOne('/api/v1/auth/login').flush({ ...LOGGED_IN, user: userOf('DRIVER') });
      expect(navigateByUrl).toHaveBeenCalledWith('/driver');
    });

    it('returns to the page they came from', async () => {
      await logIn({ next: '/shipments/4' });
      backend.expectOne('/api/v1/auth/login').flush(LOGGED_IN);
      expect(navigateByUrl).toHaveBeenCalledWith('/shipments/4');
    });

    it('ignores a next address that points at another site', async () => {
      await logIn({ next: '//evil.example.com' });
      backend.expectOne('/api/v1/auth/login').flush(LOGGED_IN);
      expect(navigateByUrl).toHaveBeenCalledWith('/shipments');
    });

    it('shows the API error and lets them try again', async () => {
      const fixture = await logIn();
      backend.expectOne('/api/v1/auth/login').flush({ message: 'Email or password is incorrect' }, WRONG_PASSWORD);
      await fixture.whenStable();
      fixture.detectChanges();
      expect(textOf(fixture)).toContain('Email or password is incorrect');
      expect(textOf(fixture)).toContain('Log in');
      expect(navigateByUrl).not.toHaveBeenCalled();
    });

    it('explains an expired session', () => {
      expect(textOf(render(LoginPage, { reason: 'expired' }))).toContain('your session has ended');
    });

    it('welcomes a newly registered user', () => {
      expect(textOf(render(LoginPage, { registered: '1' }))).toContain('Your account is ready');
    });
  });

  describe('RegisterPage', () => {
    async function register() {
      const fixture = render(RegisterPage);
      await typeInto(fixture, '#name', 'Ali Khan');
      await typeInto(fixture, '#email', 'ali@example.com');
      await typeInto(fixture, '#password', 'S3cure-pass!');
      submitForm(fixture);
      return fixture;
    }

    it('creates the account, logs in and goes to the start page', async () => {
      await register();
      const signUp = backend.expectOne('/api/v1/auth/register');
      expect(signUp.request.body).toEqual({ email: 'ali@example.com', password: 'S3cure-pass!', fullName: 'Ali Khan' });
      signUp.flush(userOf('CUSTOMER'));
      backend.expectOne('/api/v1/auth/login').flush(LOGGED_IN);
      expect(navigateByUrl).toHaveBeenCalledWith('/shipments');
    });

    it('falls back to the login page if the automatic login fails', async () => {
      await register();
      backend.expectOne('/api/v1/auth/register').flush(userOf('CUSTOMER'));
      backend.expectOne('/api/v1/auth/login').flush({}, WRONG_PASSWORD);
      expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { registered: 1 } });
    });

    it('shows why sign-up failed', async () => {
      const fixture = await register();
      backend.expectOne('/api/v1/auth/register').flush({ message: 'Email already registered' }, { status: 409, statusText: 'Conflict' });
      await fixture.whenStable();
      fixture.detectChanges();
      expect(textOf(fixture)).toContain('Email already registered');
    });
  });
});
