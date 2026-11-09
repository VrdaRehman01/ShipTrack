import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { Api, describeError } from '../core/api';
import { AuthService } from '../core/auth.service';
import { Page, Shipment, ShipmentStatus } from '../core/models';
import { ROUTE, STATUS_LABEL, routeIndex } from '../core/status';

const FILTERS: { value: ShipmentStatus | ''; label: string }[] = [
  { value: '', label: 'All' },
  { value: 'CREATED', label: 'Booked' },
  { value: 'PICKED_UP', label: 'Picked up' },
  { value: 'IN_TRANSIT', label: 'On the way' },
  { value: 'DELIVERED', label: 'Delivered' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

/** One list for every role: the API returns the right shipments for whoever is asking. */
@Component({
  selector: 'st-shipments-page',
  imports: [RouterLink, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="top">
      <div>
        <h1>{{ heading() }}</h1>
        <p class="muted">{{ subheading() }}</p>
      </div>
      @if (auth.role() === 'CUSTOMER') {
        <a class="btn" routerLink="/shipments/new">Send a parcel</a>
      }
    </header>

    <div class="filters" role="group" aria-label="Filter by status">
      @for (f of filters; track f.value) {
        <button type="button" class="chip" [class.on]="status() === f.value" [attr.aria-pressed]="status() === f.value"
                (click)="filter(f.value)">{{ f.label }}</button>
      }
    </div>

    @if (error()) {
      <p class="notice notice-error" role="alert">{{ error() }} <button class="btn btn-quiet" (click)="load()">Try again</button></p>
    }

    @if (page(); as p) {
      @if (p.content.length === 0) {
        <section class="panel empty">
          <h2>{{ emptyTitle() }}</h2>
          <p class="muted">{{ emptyText() }}</p>
          @if (auth.role() === 'CUSTOMER' && !status()) {
            <a class="btn" routerLink="/shipments/new">Send your first parcel</a>
          }
        </section>
      } @else {
        <table class="rows panel-table">
          <thead>
            <tr><th>Tracking code</th><th>To</th><th>Progress</th><th>Booked</th></tr>
          </thead>
          <tbody>
            @for (s of p.content; track s.id) {
              <tr>
                <td><a class="code" [routerLink]="['/shipments', s.id]">{{ s.trackingCode }}</a></td>
                <td>
                  <span class="who">{{ s.receiverName }}</span>
                  <span class="muted where">{{ s.deliveryAddress }}</span>
                </td>
                <td>
                  <span class="mini" [class.cancelled]="s.status === 'CANCELLED'" aria-hidden="true">
                    @for (stop of route; track stop; let i = $index) {
                      <span class="seg" [class.done]="i <= index(s.status)"></span>
                    }
                  </span>
                  <span class="state">{{ label(s.status) }}</span>
                </td>
                <td class="muted">{{ s.createdAt | date: 'd MMM y' }}</td>
              </tr>
            }
          </tbody>
        </table>

        @if (p.totalPages > 1) {
          <nav class="pager" aria-label="Pages">
            <button class="btn btn-quiet" [disabled]="p.page === 0" (click)="go(p.page - 1)">Newer</button>
            <span class="muted">Page {{ p.page + 1 }} of {{ p.totalPages }}</span>
            <button class="btn btn-quiet" [disabled]="p.page + 1 >= p.totalPages" (click)="go(p.page + 1)">Older</button>
          </nav>
        }
      }
    } @else if (!error()) {
      <p class="muted">Loading shipments…</p>
    }
  `,
  styles: `
    .top { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--s4); flex-wrap: wrap; margin-bottom: var(--s4); }
    .top p { margin: 0; }
    .filters { display: flex; gap: var(--s2); flex-wrap: wrap; margin-bottom: var(--s4); }
    .chip {
      min-height: 36px; padding: 0 var(--s4); border-radius: 18px; border: 1px solid var(--line);
      background: var(--paper); color: var(--ink-soft); font: 600 var(--step--1) var(--font); cursor: pointer;
    }
    .chip.on { background: var(--ink); border-color: var(--ink); color: #fff; }
    .panel-table { border: 1px solid var(--line); border-radius: var(--radius-panel); overflow: hidden; border-collapse: separate; border-spacing: 0; }
    .code { font: 700 var(--step-1) var(--font-sign); letter-spacing: 0.03em; color: var(--ink); text-decoration: none; }
    .code:hover { color: var(--route-deep); text-decoration: underline; }
    .who { display: block; font-weight: 600; }
    .where { display: block; font-size: var(--step--1); max-width: 34ch; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .mini { display: inline-flex; gap: 3px; vertical-align: middle; margin-right: var(--s2); }
    .seg { width: 18px; height: 6px; border-radius: 3px; background: var(--line); }
    .seg.done { background: var(--route); }
    .mini.cancelled .seg.done { background: var(--ink-soft); }
    .state { font-weight: 600; }
    .empty { text-align: left; }
    .pager { display: flex; align-items: center; justify-content: center; gap: var(--s4); margin-top: var(--s4); }
  `,
})
export class ShipmentsPage {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(Api);

  protected readonly filters = FILTERS;
  protected readonly route = ROUTE;
  protected readonly status = signal<ShipmentStatus | ''>('');
  protected readonly page = signal<Page<Shipment> | null>(null);
  protected readonly error = signal<string | null>(null);
  private loading?: Subscription;

  protected readonly heading = computed(() =>
    ({ CUSTOMER: 'My shipments', DRIVER: 'My deliveries', ADMIN: 'All shipments' } as const)[this.auth.role() ?? 'CUSTOMER']);
  protected readonly subheading = computed(() =>
    ({
      CUSTOMER: 'Everything you have sent, newest first.',
      DRIVER: 'Parcels assigned to you. Open one to update it on the road.',
      ADMIN: 'Every shipment in ShipTrack. Open one to assign a driver.',
    } as const)[this.auth.role() ?? 'CUSTOMER']);
  protected readonly emptyTitle = computed(() =>
    this.status() ? `No ${STATUS_LABEL[this.status() as ShipmentStatus].toLowerCase()} shipments`
      : ({ CUSTOMER: 'You haven’t sent anything yet', DRIVER: 'No deliveries assigned yet', ADMIN: 'No shipments yet' } as const)[this.auth.role() ?? 'CUSTOMER']);
  protected readonly emptyText = computed(() =>
    this.status() ? 'Try another filter.'
      : ({
        CUSTOMER: 'Book a pickup and we’ll show every step here.',
        DRIVER: 'When an admin assigns you a parcel, it appears here.',
        ADMIN: 'Shipments appear here as soon as customers book them.',
      } as const)[this.auth.role() ?? 'CUSTOMER']);

  constructor() {
    this.load();
  }

  protected label(s: ShipmentStatus): string { return STATUS_LABEL[s]; }
  protected index(s: ShipmentStatus): number { return routeIndex(s); }

  protected filter(value: ShipmentStatus | ''): void {
    this.status.set(value);
    this.load(0);
  }

  protected go(page: number): void { this.load(page); }

  protected load(page = this.page()?.page ?? 0): void {
    this.error.set(null);
    this.loading?.unsubscribe(); // a slow earlier filter must not overwrite this one
    this.loading = this.api.shipments(this.status(), page).subscribe({
      next: (p) => this.page.set(p),
      error: (err) => this.error.set(describeError(err)),
    });
  }
}
