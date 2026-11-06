import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Api, describeError } from '../core/api';
import { AuthService } from '../core/auth.service';
import { Invoice, Page } from '../core/models';

@Component({
  selector: 'st-invoices-page',
  imports: [DatePipe, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Invoices</h1>
    <p class="muted">{{ isAdmin() ? 'Every invoice. Record payments as they arrive.' : 'Created automatically when a parcel is delivered.' }}</p>

    <div class="filters" role="group" aria-label="Filter by payment">
      @for (f of filters; track f.value) {
        <button type="button" class="chip" [class.on]="status() === f.value" [attr.aria-pressed]="status() === f.value"
                (click)="filter(f.value)">{{ f.label }}</button>
      }
    </div>

    @if (message()) {
      <p class="notice" [class.notice-error]="isError()" role="status">{{ message() }}</p>
    }

    @if (page(); as p) {
      @if (p.content.length === 0) {
        <section class="panel">
          <h2>No invoices {{ status() === 'ISSUED' ? 'awaiting payment' : status() === 'PAID' ? 'paid yet' : 'yet' }}</h2>
          <p class="muted">Invoices appear here once a shipment is delivered.</p>
        </section>
      } @else {
        <table class="rows panel-table">
          <thead><tr><th>Invoice</th><th>Parcel</th><th>Amount</th><th>Status</th>@if (isAdmin()) {<th><span class="visually-hidden">Action</span></th>}</tr></thead>
          <tbody>
            @for (inv of p.content; track inv.id) {
              <tr>
                <td><strong>{{ inv.invoiceNumber }}</strong><span class="muted sub">{{ inv.issuedAt | date: 'd MMM y' }}</span></td>
                <td><a [routerLink]="['/shipments', inv.shipmentId]">{{ inv.trackingCode }}</a><span class="muted sub">{{ +inv.weightKg }} kg</span></td>
                <td class="money">{{ inv.currency }} {{ inv.amount }}</td>
                <td><span class="paid" [class.yes]="inv.status === 'PAID'">{{ inv.status === 'PAID' ? 'Paid' : 'Awaiting payment' }}</span></td>
                @if (isAdmin()) {
                  <td>
                    @if (inv.status === 'ISSUED') {
                      <button class="btn btn-quiet" [disabled]="busy() === inv.id" (click)="pay(inv)">Record payment</button>
                    }
                  </td>
                }
              </tr>
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
    } @else if (!message()) {
      <p class="muted">Loading invoices…</p>
    }
  `,
  styles: `
    .filters { display: flex; gap: var(--s2); flex-wrap: wrap; margin: var(--s4) 0; }
    .chip { min-height: 36px; padding: 0 var(--s4); border-radius: 18px; border: 1px solid var(--line); background: var(--paper);
            color: var(--ink-soft); font: 600 var(--step--1) var(--font); cursor: pointer; }
    .chip.on { background: var(--ink); border-color: var(--ink); color: #fff; }
    .panel-table { border: 1px solid var(--line); border-radius: var(--radius-panel); overflow: hidden; border-collapse: separate; border-spacing: 0; }
    .sub { display: block; font-size: var(--step--1); }
    .money { font: 700 var(--step-1) var(--font-sign); white-space: nowrap; }
    .paid { font-weight: 600; color: var(--ink-soft); }
    .paid.yes { color: var(--route-deep); }
    .pager { display: flex; align-items: center; justify-content: center; gap: var(--s4); margin-top: var(--s4); }
  `,
})
export class InvoicesPage {
  private readonly api = inject(Api);
  private readonly auth = inject(AuthService);

  protected readonly filters = [
    { value: '' as const, label: 'All' },
    { value: 'ISSUED' as const, label: 'Awaiting payment' },
    { value: 'PAID' as const, label: 'Paid' },
  ];
  protected readonly isAdmin = computed(() => this.auth.role() === 'ADMIN');
  protected readonly status = signal<'' | 'ISSUED' | 'PAID'>('');
  protected readonly page = signal<Page<Invoice> | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly isError = signal(false);
  protected readonly busy = signal<number | null>(null);

  constructor() { this.load(0); }

  protected filter(v: '' | 'ISSUED' | 'PAID'): void { this.status.set(v); this.load(0); }

  protected load(page: number): void {
    this.api.invoices(this.status(), page).subscribe({
      next: (p) => this.page.set(p),
      error: (err) => { this.message.set(describeError(err)); this.isError.set(true); },
    });
  }

  protected pay(inv: Invoice): void {
    this.busy.set(inv.id);
    this.api.recordPayment(inv.id).subscribe({
      next: (paid) => {
        this.busy.set(null);
        this.message.set(`Payment recorded for ${paid.invoiceNumber}.`);
        this.isError.set(false);
        this.load(this.page()?.page ?? 0);
      },
      error: (err) => { this.busy.set(null); this.message.set(describeError(err)); this.isError.set(true); },
    });
  }
}
