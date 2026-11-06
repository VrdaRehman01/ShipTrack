import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { AuthResponse, Role, User } from './models';

interface Session {
  token: string;
  expiresAt: number; // epoch ms
  user: User;
}

const STORAGE_KEY = 'shiptrack.session';

/**
 * Holds the logged-in user. The JWT lives in sessionStorage (gone when the tab closes) and expires
 * after 15 minutes; the backend checks it on every request, so this is only about UX.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(AuthService.restore());

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
      tap((res) => this.store({ token: res.accessToken, expiresAt: Date.now() + res.expiresIn * 1000, user: res.user })),
    );
  }

  register(email: string, password: string, fullName: string): Observable<User> {
    return this.http.post<User>('/api/v1/auth/register', { email, password, fullName });
  }

  logout(reason?: 'expired'): void {
    this.clear();
    void this.router.navigate(['/login'], { queryParams: reason ? { reason } : {} });
  }

  /** Where each role starts after logging in (the shipments list adapts to the role). */
  homeFor(role: Role | null): string {
    return '/shipments';
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
