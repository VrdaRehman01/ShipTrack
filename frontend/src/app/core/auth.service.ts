import { HttpClient, HttpContextToken, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { AuthResponse, Role, User } from './models';

interface Session {
  token: string;
  expiresAt: number; // epoch ms
  renewAt: number; // epoch ms: from here on, the next API call also fetches a fresh token
  user: User;
}

const STORAGE_KEY = 'shiptrack.session';
export const RENEW_URL = '/api/v1/auth/refresh';

/** Marks automatic refreshes of a page nobody is touching, so they don't count as using the app. */
export const BACKGROUND_CALL = new HttpContextToken<boolean>(() => false);

function toSession(res: AuthResponse): Session {
  const now = Date.now();
  const lifetime = res.expiresIn * 1000;
  return { token: res.accessToken, expiresAt: now + lifetime, renewAt: now + lifetime / 3, user: res.user };
}

/**
 * Holds the logged-in user. The JWT lives in sessionStorage (gone when the tab closes) and lasts 15 minutes.
 * While the app is being used it is swapped for a fresh one, for up to 8 hours after logging in (the server
 * enforces that limit). The backend checks the token on every request, so the expiry here is only about UX.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(AuthService.restore());
  private renewing = false;
  private renewalRefused = false;

  readonly user = computed(() => this.session()?.user ?? null);
  readonly role = computed<Role | null>(() => this.session()?.user.role ?? null);
  readonly isLoggedIn = computed(() => this.session() !== null);

  token(): string | null {
    const s = this.session();
    if (!s) return null;
    if (Date.now() >= s.expiresAt) {
      this.clear();
      return null;
    }
    return s.token;
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/v1/auth/login', { email, password }).pipe(
      tap((res) => {
        this.renewalRefused = false;
        this.store(toSession(res));
      }),
    );
  }

  /** Fetches a fresh token in the background once the current one is due. The current one stays valid meanwhile. */
  renewIfDue(): void {
    const current = this.session();
    if (!current || this.renewing || this.renewalRefused || Date.now() < current.renewAt) return;
    this.renewing = true;
    this.http.post<AuthResponse>(RENEW_URL, {}).subscribe({
      next: (res) => {
        this.renewing = false;
        // They may have logged out while this was on its way; don't log them back in.
        if (this.session()?.user.id === res.user.id) this.store(toSession(res));
      },
      error: (err: unknown) => {
        this.renewing = false;
        // 401 means the session limit was reached: stop asking. Anything else can be retried on the next call.
        if (err instanceof HttpErrorResponse && err.status === 401) this.renewalRefused = true;
      },
    });
  }

  register(email: string, password: string, fullName: string): Observable<User> {
    return this.http.post<User>('/api/v1/auth/register', { email, password, fullName });
  }

  logout(reason?: 'expired'): void {
    this.clear();
    // After an expired session, come back to the same page once logged in again.
    const next = this.router.url.startsWith('/login') ? undefined : this.router.url;
    void this.router.navigate(['/login'], { queryParams: reason ? { reason, next } : {} });
  }

  /** Where someone starts after logging in: drivers get their job screen, everyone else the shipments list. */
  home(): string {
    return this.role() === 'DRIVER' ? '/driver' : '/shipments';
  }

  private store(s: Session): void {
    this.session.set(s);
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(s));
    } catch {
      // storage unavailable (private mode): stay logged in for this page only
    }
  }

  private clear(): void {
    this.session.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // ignore
    }
  }

  private static restore(): Session | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const s = JSON.parse(raw) as Session;
      return s.expiresAt > Date.now() ? s : null;
    } catch {
      return null;
    }
  }
}
