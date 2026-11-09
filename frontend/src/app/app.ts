import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';

interface NavItem { label: string; link: string; }

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="skip" href="#main">Skip to content</a>
    <header class="bar">
      <a class="brand" routerLink="/" aria-label="ShipTrack home">
        <svg viewBox="0 0 32 32" width="28" height="28" aria-hidden="true">
          <line x1="4" y1="16" x2="28" y2="16" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
          <circle cx="6" cy="16" r="4" fill="currentColor"/>
          <circle cx="26" cy="16" r="5" fill="var(--paper)" stroke="currentColor" stroke-width="3"/>
        </svg>
        ShipTrack
      </a>
      @if (auth.isLoggedIn()) {
        <nav class="nav" aria-label="Main">
          @for (item of nav(); track item.link) {
            <a [routerLink]="item.link" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: false }">{{ item.label }}</a>
          }
        </nav>
        <div class="me">
          <span class="who">{{ auth.user()?.fullName }}<span class="role">{{ roleName() }}</span></span>
          <button type="button" class="btn btn-quiet" (click)="auth.logout()">Log out</button>
        </div>
      } @else {
        <div class="me">
          <a class="btn btn-quiet" routerLink="/login">Log in</a>
          <a class="btn signup" routerLink="/register">Create account</a>
        </div>
      }
    </header>
    <main id="main" tabindex="-1">
      <router-outlet />
    </main>
  `,
  styles: `
    :host { display: block; min-height: 100vh; }
    .skip { position: absolute; left: -999px; top: 0; background: var(--paper); padding: var(--s2) var(--s4); z-index: 10; }
    .skip:focus { left: var(--s4); }
    .bar {
      display: flex; align-items: center; gap: var(--s5); flex-wrap: wrap;
      padding: var(--s3) var(--s5); background: var(--paper); border-bottom: 1px solid var(--line);
    }
    .brand {
      display: inline-flex; align-items: center; gap: var(--s2); color: var(--route-deep); text-decoration: none;
      font: 700 var(--step-2) / 1 var(--font-sign);
    }
    .nav { display: flex; gap: var(--s1); flex: 1; overflow-x: auto; }
    .nav a {
      padding: var(--s2) var(--s3); border-radius: var(--radius-control); text-decoration: none;
      color: var(--ink-soft); font-weight: 600; white-space: nowrap;
    }
    .nav a:hover { color: var(--ink); background: var(--ground); }
    .nav a.active { color: var(--route-deep); background: var(--route-wash); }
    .me { display: flex; align-items: center; gap: var(--s3); margin-left: auto; }
    .who { display: grid; text-align: right; font-weight: 600; line-height: 1.2; }
    .role { font-weight: 400; font-size: var(--step--1); color: var(--ink-soft); }
    main { padding: var(--s6) var(--s5) var(--s7); max-width: 1120px; margin: 0 auto; outline: none; }
    @media (max-width: 480px) { .signup { display: none; } }
    @media (max-width: 720px) {
      .bar { padding: var(--s3) var(--s4); gap: var(--s3); }
      .nav { order: 3; flex-basis: 100%; }
      .who { display: none; }
      .me .btn { min-height: 40px; padding: 0 var(--s3); }
      main { padding: var(--s5) var(--s4) var(--s7); }
    }
  `,
})
export class App {
  protected readonly auth = inject(AuthService);

  protected readonly nav = computed<NavItem[]>(() => {
    switch (this.auth.role()) {
      case 'ADMIN': return [
        { label: 'Shipments', link: '/shipments' },
        { label: 'Invoices', link: '/invoices' },
        { label: 'People', link: '/admin/users' },
        { label: 'Track a parcel', link: '/track' },
      ];
      case 'DRIVER': return [
        { label: 'Jobs', link: '/driver' },
        { label: 'All deliveries', link: '/shipments' },
        { label: 'Track a parcel', link: '/track' },
      ];
      case 'CUSTOMER': return [
        { label: 'My shipments', link: '/shipments' },
        { label: 'Send a parcel', link: '/shipments/new' },
        { label: 'Invoices', link: '/invoices' },
      ];
      default: return [];
    }
  });

  protected readonly roleName = computed(() =>
    ({ ADMIN: 'Admin', DRIVER: 'Driver', CUSTOMER: 'Customer' } as const)[this.auth.role() ?? 'CUSTOMER']);
}
