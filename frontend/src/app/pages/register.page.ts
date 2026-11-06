import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { describeError } from '../core/api';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'st-register-page',
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel auth">
      <h1>Create your account</h1>
      <p class="muted">Send parcels and follow them door to door.</p>
      @if (error()) {
        <p class="notice notice-error" role="alert">{{ error() }}</p>
      }
      <form (ngSubmit)="submit()" #f="ngForm">
        <div class="field">
          <label for="name">Full name</label>
          <input id="name" name="fullName" [(ngModel)]="fullName" required maxlength="100" autocomplete="name" />
        </div>
        <div class="field">
          <label for="email">Email</label>
          <input id="email" name="email" type="email" [(ngModel)]="email" required email autocomplete="email" />
        </div>
        <div class="field">
          <label for="password">Password</label>
          <input id="password" name="password" type="password" [(ngModel)]="password" required minlength="8" maxlength="72"
                 autocomplete="new-password" aria-describedby="pw-hint" />
          <span id="pw-hint" class="hint">At least 8 characters.</span>
        </div>
        <button class="btn btn-big" type="submit" [disabled]="loading() || f.invalid">{{ loading() ? 'Creating account…' : 'Create account' }}</button>
      </form>
      <p class="muted alt">Already have an account? <a routerLink="/login">Log in</a></p>
    </section>
  `,
  styles: `
    .auth { max-width: 420px; margin: var(--s6) auto; }
    .alt { margin: var(--s4) 0 0; }
  `,
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected fullName = '';
  protected email = '';
  protected password = '';
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected submit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.auth.register(this.email, this.password, this.fullName).subscribe({
      next: () => void this.router.navigate(['/login'], { queryParams: { registered: 1 } }),
      error: (err) => {
        this.loading.set(false);
        this.error.set(describeError(err));
      },
    });
  }
}
