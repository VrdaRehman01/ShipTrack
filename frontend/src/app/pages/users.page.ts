import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { Api, describeError } from '../core/api';
import { Page, Role, User } from '../core/models';

/** Admin: see who uses ShipTrack and add drivers (public sign-up only creates customers). */
@Component({
  selector: 'st-users-page',
  imports: [DatePipe, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>People</h1>
    <p class="muted">Customers sign up themselves. Add drivers and other admins here.</p>

    <div class="layout">
      <section class="list">
        <div class="filters" role="group" aria-label="Filter by role">
          @for (f of filters; track f.value) {
            <button type="button" class="chip" [class.on]="role() === f.value" [attr.aria-pressed]="role() === f.value"
                    (click)="filter(f.value)">{{ f.label }}</button>
          }
        </div>
        @if (page(); as p) {
          <table class="rows panel-table">
            <thead><tr><th>Name</th><th>Role</th><th>Joined</th></tr></thead>
            <tbody>
              @for (u of p.content; track u.id) {
                <tr>
                  <td><strong>{{ u.fullName }}</strong><span class="muted sub">{{ u.email }}</span></td>
                  <td>{{ roleName(u.role) }}</td>
                  <td class="muted">{{ u.createdAt | date: 'd MMM y' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="3" class="muted">Nobody with this role yet.</td></tr>
              }
            </tbody>
          </table>
        }
      </section>

      <section class="panel add" aria-labelledby="add-title">
        <h2 id="add-title">Add a person</h2>
        @if (message()) {
          <p class="notice" [class.notice-error]="isError()" role="status">{{ message() }}</p>
        }
        <form #f="ngForm" (ngSubmit)="create(f)">
          <div class="field">
            <label for="nu-name">Full name</label>
            <input id="nu-name" name="fullName" [(ngModel)]="fullName" required maxlength="100" />
          </div>
          <div class="field">
            <label for="nu-email">Email</label>
            <input id="nu-email" name="email" type="email" [(ngModel)]="email" required email />
          </div>
          <div class="field">
            <label for="nu-password">Temporary password</label>
            <input id="nu-password" name="password" type="text" [(ngModel)]="password" required minlength="8" maxlength="72"
                   autocomplete="off" aria-describedby="nu-pw-hint" />
            <span id="nu-pw-hint" class="hint">At least 8 characters. Share it with them privately.</span>
          </div>
          <div class="field">
            <label for="nu-role">Role</label>
            <select id="nu-role" name="role" [(ngModel)]="newRole" required>
              <option value="DRIVER">Driver</option>
              <option value="ADMIN">Admin</option>
              <option value="CUSTOMER">Customer</option>
            </select>
          </div>
          <button class="btn" type="submit" [disabled]="saving() || f.invalid">{{ saving() ? 'Adding…' : 'Add person' }}</button>
        </form>
      </section>
    </div>
  `,
  styles: `
    .layout { display: grid; gap: var(--s5); grid-template-columns: 1fr 340px; align-items: start; margin-top: var(--s4); }
    @media (max-width: 860px) { .layout { grid-template-columns: 1fr; } }
    .filters { display: flex; gap: var(--s2); flex-wrap: wrap; margin-bottom: var(--s4); }
    .chip { min-height: 36px; padding: 0 var(--s4); border-radius: 18px; border: 1px solid var(--line); background: var(--paper);
            color: var(--ink-soft); font: 600 var(--step--1) var(--font); cursor: pointer; }
    .chip.on { background: var(--ink); border-color: var(--ink); color: #fff; }
    .panel-table { border: 1px solid var(--line); border-radius: var(--radius-panel); overflow: hidden; border-collapse: separate; border-spacing: 0; }
    .sub { display: block; font-size: var(--step--1); }
  `,
})
export class UsersPage {
  private readonly api = inject(Api);

  protected readonly filters: { value: Role | ''; label: string }[] = [
    { value: '', label: 'Everyone' }, { value: 'DRIVER', label: 'Drivers' },
    { value: 'CUSTOMER', label: 'Customers' }, { value: 'ADMIN', label: 'Admins' },
  ];
  protected readonly role = signal<Role | ''>('DRIVER');
  protected readonly page = signal<Page<User> | null>(null);
  protected readonly saving = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly isError = signal(false);

  protected fullName = '';
  protected email = '';
  protected password = '';
  protected newRole: Role = 'DRIVER';

  constructor() { this.load(); }

  protected roleName(r: Role): string { return { ADMIN: 'Admin', DRIVER: 'Driver', CUSTOMER: 'Customer' }[r]; }

  protected filter(r: Role | ''): void { this.role.set(r); this.load(); }

  protected create(form: NgForm): void {
    this.saving.set(true);
    this.api.createUser(this.email, this.password, this.fullName, this.newRole).subscribe({
      next: (u) => {
        this.saving.set(false);
        this.message.set(`${u.fullName} added as ${this.roleName(u.role).toLowerCase()}.`);
        this.isError.set(false);
        form.resetForm({ role: this.newRole });
        this.load();
      },
      error: (err) => { this.saving.set(false); this.message.set(describeError(err)); this.isError.set(true); },
    });
  }

  private load(): void {
    this.api.users(this.role()).subscribe({
      next: (p) => this.page.set(p),
      error: (err) => { this.message.set(describeError(err)); this.isError.set(true); },
    });
  }
}
