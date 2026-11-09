import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Subscription, forkJoin, timer } from 'rxjs';
import { Api, describeError, errorCode } from '../core/api';
import { callLink, dayFromNow, directionsLink } from '../core/links';
import { AvailableJob, Shipment, ShipmentStatus } from '../core/models';
import { MOVE_LABEL, STATUS_LABEL, movesFor } from '../core/status';
import { TrackingCodeComponent } from '../shared/tracking-code.component';

const REFRESH_MS = 30_000;
/** Furthest along first, so the parcel on the road is at the top. */
const IN_PROGRESS: ShipmentStatus[] = ['IN_TRANSIT', 'PICKED_UP', 'CREATED'];
const DELIVER_BY = [
  { label: 'Today', days: 0 },
  { label: 'Tomorrow', days: 1 },
  { label: 'In 3 days', days: 3 },
];

/** The driver's phone screen: the parcels they are carrying, and open parcels they can take. */
@Component({
  selector: 'st-driver-page',
  imports: [DatePipe, RouterLink, TrackingCodeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Your jobs</h1>

    @if (message()) {
      <p class="notice" [class.notice-error]="isError()" role="status">{{ message() }}</p>
    }

    @if (loaded()) {
      <section aria-labelledby="active-title">
        <h2 id="active-title">On your route</h2>
        @for (s of active(); track s.id) {
          <article class="ticket">
            <header class="ticket-head">
              <st-tracking-code [code]="s.trackingCode" />
              <span class="state">{{ statusLabel(s.status) }}</span>
            </header>

            <ol class="stops">
              <li class="stop" [class.next]="s.status === 'CREATED'" [class.done]="s.status !== 'CREATED'">
                <span class="verb">Pick up from {{ s.senderName }}</span>
                <span class="address">{{ s.pickupAddress }}</span>
                @if (s.status === 'CREATED') {
                  <span class="reach">
                    <a class="btn btn-quiet" [href]="call(s.senderPhone)">Call</a>
                    <a class="btn btn-quiet" [href]="directions(s.pickupAddress)" target="_blank" rel="noopener">Navigate</a>
                  </span>
                }
              </li>
              <li class="stop" [class.next]="s.status !== 'CREATED'">
                <span class="verb">Deliver to {{ s.receiverName }}</span>
                <span class="address">{{ s.deliveryAddress }}</span>
                @if (s.status !== 'CREATED') {
                  <span class="reach">
                    <a class="btn btn-quiet" [href]="call(s.receiverPhone)">Call</a>
                    <a class="btn btn-quiet" [href]="directions(s.deliveryAddress)" target="_blank" rel="noopener">Navigate</a>
                  </span>
                }
              </li>
            </ol>

            <p class="muted small">
              {{ kg(s.weightKg) }} kg{{ s.description ? ', ' + s.description : '' }}@if (s.expectedDeliveryDate) {. Deliver by {{ s.expectedDeliveryDate | date: 'EEE d MMM' }}}
            </p>

            <button class="btn btn-big" [disabled]="busy()" (click)="advance(s)">{{ moveLabel(s) }}</button>
            @if (s.status === 'CREATED') {
              <button class="btn btn-danger" [disabled]="busy()" (click)="giveBack(s)">Give back</button>
            }
            <a class="details" [routerLink]="['/shipments', s.id]">Details and location sharing</a>
          </article>
        } @empty {
          <p class="muted">Nothing on your route yet. Accept a parcel below to start.</p>
        }
      </section>

      <section aria-labelledby="open-title">
        <h2 id="open-title">Open parcels</h2>
        @for (job of open(); track job.id) {
          <article class="job">
            <p class="leg"><span class="muted">From</span> {{ job.pickupAddress }}</p>
            <p class="leg"><span class="muted">To</span> {{ job.deliveryAddress }}</p>
            <p class="muted small">{{ kg(job.weightKg) }} kg{{ job.description ? ', ' + job.description : '' }}</p>
            <p class="ask" [id]="'deliver-by-' + job.id">Accept and deliver</p>
            <div class="when" role="group" [attr.aria-labelledby]="'deliver-by-' + job.id">
              @for (option of deliverBy; track option.days) {
                <button class="btn" [disabled]="busy()" (click)="accept(job, option.days)">{{ option.label }}</button>
              }
            </div>
          </article>
        } @empty {
          <p class="muted">No open parcels right now. New ones appear here automatically.</p>
        }
      </section>
    } @else if (!isError()) {
      <p class="muted">Loading your jobs…</p>
    } @else {
      <button class="btn btn-quiet" (click)="load()">Try again</button>
    }
  `,
  styles: `
    :host { display: grid; gap: var(--s5); max-width: 560px; margin: 0 auto; }
    h1 { margin: 0; }
    section { display: grid; gap: var(--s4); }
    h2 { margin: 0; }
    .small { font-size: var(--step--1); margin: 0; }
    .ticket, .job {
      display: grid; gap: var(--s4); padding: var(--s4);
      background: var(--paper); border: 1px solid var(--line); border-radius: var(--radius-panel);
    }
    .ticket { border-top: 4px solid var(--route); }
    .ticket-head { display: flex; align-items: center; justify-content: space-between; gap: var(--s3); flex-wrap: wrap; }
    .state { font: 700 var(--step-1) var(--font-sign); }

    /* The route: a line from pickup to delivery, with the next stop picked out. */
    .stops { list-style: none; margin: 0; padding: 0; display: grid; }
    .stop { position: relative; display: grid; gap: var(--s1); padding: 0 0 var(--s5) var(--s6); }
    .stop:last-child { padding-bottom: 0; }
    .stop::before {
      content: ''; position: absolute; left: 0; top: 3px; width: 18px; height: 18px; border-radius: 50%;
      background: var(--paper); border: 4px solid var(--route); z-index: 1;
    }
    .stop:not(:last-child)::after {
      content: ''; position: absolute; left: 7px; top: 12px; bottom: -6px; width: 4px; background: var(--route);
    }
    .stop.done::before { background: var(--route); }
    .stop.done .verb, .stop.done .address { color: var(--ink-soft); }
    .verb { font-weight: 600; font-size: var(--step--1); color: var(--ink-soft); }
    .address { overflow-wrap: anywhere; }
    .stop.next .verb { color: var(--route-deep); }
    .stop.next .address { font: 700 var(--step-2) / 1.15 var(--font-sign); }
    .reach { display: grid; grid-template-columns: 1fr 1fr; gap: var(--s2); margin-top: var(--s2); }

    .details { justify-self: center; padding: var(--s2); }
    .leg { margin: 0; overflow-wrap: anywhere; }
    .leg .muted { display: inline-block; min-width: 3em; }
    .ask { margin: 0; font-weight: 600; }
    .when { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--s2); }
    .when .btn { padding: 0 var(--s2); }
  `,
})
export class DriverPage {
  private readonly api = inject(Api);

  protected readonly deliverBy = DELIVER_BY;
  protected readonly active = signal<Shipment[]>([]);
  protected readonly open = signal<AvailableJob[]>([]);
  protected readonly loaded = signal(false);
  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly isError = signal(false);
  private loading?: Subscription;

  constructor() {
    this.load();
    // Keeping this screen open means being on duty, so these refreshes do keep the session going.
    timer(REFRESH_MS, REFRESH_MS).pipe(takeUntilDestroyed()).subscribe(() => {
      if (!document.hidden && !this.busy()) this.load();
    });
  }

  protected statusLabel(status: ShipmentStatus): string { return STATUS_LABEL[status]; }
  protected kg(weight: string): string { return String(Number(weight)); }
  protected call(phone: string): string { return callLink(phone); }
  protected directions(address: string): string { return directionsLink(address); }
  protected moveLabel(s: Shipment): string { return MOVE_LABEL[this.nextMove(s)]; }

  private nextMove(s: Shipment): ShipmentStatus {
    return movesFor('DRIVER', s.status, true, false)[0];
  }

  protected load(): void {
    this.loading?.unsubscribe(); // a slow earlier refresh must not overwrite this one
    this.loading = forkJoin({
      route: forkJoin(IN_PROGRESS.map((status) => this.api.shipments(status, 0, 50))),
      open: this.api.availableJobs(),
    }).subscribe({
      next: ({ route, open }) => {
        this.active.set(route.flatMap((page) => page.content));
        this.open.set(open.content);
        this.loaded.set(true);
      },
      error: (err) => this.say(describeError(err), true),
    });
  }

  protected accept(job: AvailableJob, days: number): void {
    this.busy.set(true);
    this.api.acceptJob(job.id, dayFromNow(days)).subscribe({
      next: () => this.finish('Parcel accepted. It’s on your route.'),
      error: (err) => this.finish(this.isTaken(err) ? 'Another driver took that parcel first.' : describeError(err), true),
    });
  }

  protected advance(s: Shipment): void {
    const next = this.nextMove(s);
    if (next === 'DELIVERED' && !confirm('Mark this parcel as delivered? The customer will be billed.')) return;
    this.busy.set(true);
    this.api.moveShipment(s.id, next, s.version).subscribe({
      next: () => this.finish(`${s.trackingCode}: ${STATUS_LABEL[next].toLowerCase()}.`),
      error: (err) => this.finish(describeError(err), true),
    });
  }

  protected giveBack(s: Shipment): void {
    if (!confirm('Give this parcel back so another driver can take it?')) return;
    this.busy.set(true);
    this.api.releaseJob(s.id).subscribe({
      next: () => this.finish(`${s.trackingCode} is back in open parcels.`),
      error: (err) => this.finish(describeError(err), true),
    });
  }

  private isTaken(err: unknown): boolean {
    return errorCode(err) === 'JOB_TAKEN' || errorCode(err) === 'CONCURRENT_MODIFICATION';
  }

  private finish(text: string, isError = false): void {
    this.busy.set(false);
    this.say(text, isError);
    this.load();
  }

  private say(text: string, isError = false): void {
    this.message.set(text);
    this.isError.set(isError);
  }
}
