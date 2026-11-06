import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { describeError } from '../core/api';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'st-login-page',
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel auth">
      <h1>Log in</h1>
      @if (reason() === 'expired') {
        <p class="notice">For your security you were logged out after 15 minutes. Log in to continue.</p>
      }
      @if (registered()) {
        <p class="notice">Your account is ready. Log in to send your first parcel.</p>
      }
      @if (error()) {
        <p class="notice notice-error" role="alert">{{ error() }}</p>
      }
      <form (ngSubmit)="submit()" #f="ngForm">
        <div class="field">
          <label for="email">Email</label>
          <input id="email" name="email" type="email" [(ngModel)]="email" required email autocomplete="email" />
        </div>
        <div class="field">
          <label for="password">Password</label>
          <input id="password" name="password" type="password" [(ngModel)]="password" required autocomplete="current-password" />
        </div>
        <button class="btn btn-big" type="submit" [disabled]="loading() || f.invalid">{{ loading() ? 'Logging in…' : 'Log in' }}</button>
      </form>
      <p class="muted alt">New to ShipTrack? <a routerLink="/register">Create an account</a></p>
    </section>
  `,
  styles: `
    .auth { max-width: 420px; margin: var(--s6) auto; }
    .alt { margin: var(--s4) 0 0; }
  `,
})
export class LoginPage {
  readonly reason = input<string>();
  readonly next = input<string>();
  readonly registered = input<string>();

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected email = '';
  protected password = '';
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected submit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.email, this.password).subscribe({
      next: (res) => {
        const next = this.next();
        // Only follow in-app paths, never an outside URL.
        const target = next && next.startsWith('/') && !next.startsWith('//') ? next : this.auth.homeFor(res.user.role);
        void this.router.navigateByUrl(target);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(describeError(err));
      },
    });
  }
}
