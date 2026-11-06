import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api, describeError, errorCode } from '../core/api';
import { AuthService } from '../core/auth.service';
import { PublicTracking } from '../core/models';
import { STATUS_LABEL } from '../core/status';
import { RouteLineComponent } from '../shared/route-line.component';
import { TrackingCodeComponent } from '../shared/tracking-code.component';

const CODE_PATTERN = /^[A-Za-z]{2}-\d{4}-\d{6,}$/;

/** Public page: anyone with a tracking code can follow a parcel. Also the home page. */
@Component({
  selector: 'st-track-page',
  imports: [FormsModule, RouterLink, DatePipe, RouteLineComponent, TrackingCodeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hero">
      <h1>Where is my parcel?</h1>
      <p class="lede">Enter the tracking code from your receipt or message. No account needed.</p>
      <form class="lookup" (ngSubmit)="submit()" role="search">
        <label for="code" class="visually-hidden">Tracking code</label>
        <input id="code" name="code" [(ngModel)]="entered" placeholder="ST-2026-000042"
               autocomplete="off" autocapitalize="characters" spellcheck="false" required
               [attr.aria-invalid]="formatProblem() ? true : null" aria-describedby="code-help" />
        <button class="btn" type="submit" [disabled]="loading()">{{ loading() ? 'Looking…' : 'Track' }}</button>
      </form>
      <p id="code-help" class="help" [class.problem]="formatProblem()">
        {{ formatProblem() ? 'Tracking codes look like ST-2026-000042.' : 'Format: ST-year-number, for example ST-2026-000042.' }}
      </p>
    </section>

    @if (error()) {
      <p class="notice notice-error" role="alert">{{ error() }}</p>
    }

    @if (result(); as r) {
      <section class="panel result" aria-live="polite">
        <div class="head">
          <st-tracking-code [code]="r.trackingCode" />
          <span class="status">{{ label(r.status) }}</span>
        </div>
        <st-route-line [status]="r.status" [timeline]="r.timeline" />
        <p class="muted updated">Last update {{ r.lastUpdated | date: 'EEEE d MMMM, HH:mm' }}</p>
      </section>
    } @else if (!auth.isLoggedIn()) {
      <section class="pitch">
        <div>
          <h2>Sending parcels?</h2>
          <p>Book a pickup, follow every step and get your invoice the moment it's delivered.</p>
        </div>
        <a class="btn" routerLink="/register">Create an account</a>
      </section>
    }
  `,
  styles: `
    .hero { padding: var(--s6) 0 var(--s5); max-width: 720px; }
    .hero h1 { font-size: clamp(var(--step-4), 7vw, var(--step-5)); margin-bottom: var(--s3); }
    .lede { font-size: var(--step-1); color: var(--ink-soft); margin-bottom: var(--s5); }
    .lookup { display: flex; gap: var(--s2); }
    .lookup input {
      flex: 1; min-width: 0; min-height: 56px; padding: 0 var(--s4);
      border: 2px solid var(--ink); border-radius: var(--radius-control); background: var(--paper);
      font: 700 var(--step-2) var(--font-sign); letter-spacing: 0.04em; text-transform: uppercase; color: var(--ink);
    }
    .lookup input::placeholder { color: #a3aeb8; }
    .lookup .btn { min-height: 56px; padding: 0 var(--s6); font-size: var(--step-1); }
    .help { margin-top: var(--s2); font-size: var(--step--1); color: var(--ink-soft); }
    .help.problem { color: var(--danger); }
    .result { max-width: 720px; display: grid; gap: var(--s5); }
    .head { display: flex; align-items: center; justify-content: space-between; gap: var(--s3); flex-wrap: wrap; }
    .status { font: 700 var(--step-2) var(--font-sign); }
    .updated { margin: 0; font-size: var(--step--1); }
    .pitch {
      max-width: 720px; margin-top: var(--s6); padding-top: var(--s5); border-top: 1px solid var(--line);
      display: flex; gap: var(--s5); align-items: center; justify-content: space-between; flex-wrap: wrap;
    }
    .pitch p { margin: 0; color: var(--ink-soft); }
    @media (max-width: 480px) {
      .lookup { flex-direction: column; }
    }
  `,
})
export class TrackPage {
  /** From /track/:code (router input binding). */
  readonly code = input<string>();

  protected readonly auth = inject(AuthService);
  private readonly api = inject(Api);
  private readonly router = inject(Router);

  protected entered = '';
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly formatProblem = signal(false);
  protected readonly result = signal<PublicTracking | null>(null);

  constructor() {
    effect(() => {
      const code = this.code();
      if (code) {
        this.entered = code.toUpperCase();
        this.lookUp(code);
      }
    });
  }

  protected label(status: PublicTracking['status']): string {
    return STATUS_LABEL[status];
  }

  protected submit(): void {
    const code = this.entered.trim().toUpperCase();
    if (!CODE_PATTERN.test(code)) {
      this.formatProblem.set(true);
      return;
    }
    this.formatProblem.set(false);
    void this.router.navigate(['/track', code]);
  }

  private lookUp(code: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.track(code).subscribe({
      next: (r) => { this.result.set(r); this.loading.set(false); },
      error: (err) => {
        this.result.set(null);
        this.loading.set(false);
        this.error.set(errorCode(err) === 'NOT_FOUND'
          ? `No parcel with code ${code.toUpperCase()}. Check the code and try again.`
          : describeError(err));
      },
    });
  }
}
