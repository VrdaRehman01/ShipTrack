import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { Subscription } from 'rxjs';
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
            <thead><tr><th>Name</th><th>Role</th><th>Joined</th><th><span class="visually-hidden">Action</span></th></tr></thead>
            <tbody>
              @for (u of p.content; track u.id) {
                <tr>
                  <td><strong>{{ u.fullName }}</strong><span class="muted sub">{{ u.email }}{{ u.phone ? ', ' + u.phone : '' }}</span></td>
                  <td>{{ roleName(u.role) }}</td>
                  <td class="muted">{{ u.createdAt | date: 'd MMM y' }}</td>
                  <td><button type="button" class="btn btn-quiet" [attr.aria-label]="'Edit ' + u.fullName" (click)="edit(u)">Edit</button></td>
                </tr>
              } @empty {
                <tr><td colspan="4" class="muted">Nobody with this role yet.</td></tr>
              }
            </tbody>
          </table>
          @if (p.totalPages > 1) {
            <nav class="pager" aria-label="Pages">
              <button class="btn btn-quiet" [disabled]="p.page === 0" (click)="load(p.page - 1)">Newer</button>
              <span class="muted">Page {{ p.page + 1 }} of {{ p.totalPages }}</span>
              <button class="btn btn-quiet" [disabled]="p.page + 1 >= p.totalPages" (click)="load(p.page + 1)">Older</button>
            </nav>
          }
        }
      </section>

      <section class="panel add" aria-labelledby="add-title">
        <h2 id="add-title">{{ editing() ? 'Edit ' + editing()?.fullName : 'Add a person' }}</h2>
        @if (message()) {
          <p class="notice" [class.notice-error]="isError()" role="status">{{ message() }}</p>
        }
        @if (editing()) {
          <form #e="ngForm" (ngSubmit)="saveEdit()">
            <div class="field">
              <label for="ed-name">Full name</label>
              <input id="ed-name" name="editName" [(ngModel)]="editName" required maxlength="100" />
            </div>
            <div class="field">
              <label for="ed-phone">Phone (optional)</label>
              <input id="ed-phone" name="editPhone" type="tel" [(ngModel)]="editPhone" [pattern]="phonePattern"
                     placeholder="+92 300 1234567" />
            </div>
            <div class="edit-actions">
              <button class="btn" type="submit" [disabled]="saving() || e.invalid">{{ saving() ? 'Saving…' : 'Save changes' }}</button>
              <button class="btn btn-quiet" type="button" (click)="stopEditing()">Cancel</button>
            </div>
          </form>
        } @else {
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
            <label for="nu-phone">Phone (optional)</label>
            <input id="nu-phone" name="phone" type="tel" [(ngModel)]="phone" [pattern]="phonePattern"
                   placeholder="+92 300 1234567" aria-describedby="nu-phone-hint" />
            <span id="nu-phone-hint" class="hint">Customers see a driver's number once the driver has their parcel.</span>
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
        }
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
    .pager { display: flex; align-items: center; justify-content: center; gap: var(--s4); margin-top: var(--s4); }
    .edit-actions { display: flex; gap: var(--s2); }
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
  protected readonly editing = signal<User | null>(null);
  protected editName = '';
  protected editPhone = '';
  private loading?: Subscription;

  protected fullName = '';
  protected email = '';
  protected password = '';
  protected phone = '';
  protected readonly phonePattern = '^\\+?[0-9][0-9 \\-]{6,18}$';
  protected newRole: Role = 'DRIVER';

  constructor() { this.load(); }

  protected roleName(r: Role): string { return { ADMIN: 'Admin', DRIVER: 'Driver', CUSTOMER: 'Customer' }[r]; }

  protected filter(r: Role | ''): void { this.role.set(r); this.load(); }

  protected edit(user: User): void {
    this.editName = user.fullName;
    this.editPhone = user.phone ?? '';
    this.message.set(null);
    this.editing.set(user);
  }

  protected stopEditing(): void { this.editing.set(null); }

  protected saveEdit(): void {
    const user = this.editing();
    if (!user) return;
    this.saving.set(true);
    this.api.updateUser(user.id, this.editName, this.editPhone?.trim() || undefined).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.editing.set(null);
        this.message.set(`${saved.fullName} updated.`);
        this.isError.set(false);
        this.load(this.page()?.page ?? 0);
      },
      error: (err) => { this.saving.set(false); this.message.set(describeError(err)); this.isError.set(true); },
    });
  }

  protected create(form: NgForm): void {
    this.saving.set(true);
    this.api.createUser(this.email, this.password, this.fullName, this.newRole, this.phone?.trim() || undefined).subscribe({
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

  protected load(page = 0): void {
    this.loading?.unsubscribe(); // a slow earlier filter must not overwrite this one
    this.loading = this.api.users(this.role(), page).subscribe({
      next: (p) => {
        this.page.set(p);
        if (this.isError()) { this.message.set(null); this.isError.set(false); } // it worked, so the old error no longer applies
      },
      error: (err) => { this.message.set(describeError(err)); this.isError.set(true); },
    });
  }
}
