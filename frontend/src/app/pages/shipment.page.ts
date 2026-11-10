import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin, of, catchError, retry, throwError, timer } from 'rxjs';
import { Api, describeError, errorCode } from '../core/api';
import { isSecureContextAvailable } from '../core/ids';
import { callLink } from '../core/links';
import { AuthService } from '../core/auth.service';
import { Invoice, Location, Shipment, ShipmentStatus, StatusChange, User } from '../core/models';
import { MOVE_LABEL, STATUS_LABEL, movesFor } from '../core/status';
import { LocationMapComponent } from '../shared/location-map.component';
import { RouteLineComponent } from '../shared/route-line.component';
import { TrackingCodeComponent } from '../shared/tracking-code.component';

const AUTO_SHARE_MS = 60_000;
// Billing creates the invoice just after delivery, so keep checking for a minute rather than making people reload.
const INVOICE_POLL_MS = 3_000;
const INVOICE_POLLS = 20;
const LIVE_UPDATE_MS = 30_000;

@Component({
  selector: 'st-shipment-page',
  imports: [DatePipe, FormsModule, RouterLink, RouteLineComponent, TrackingCodeComponent, LocationMapComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="back" [routerLink]="auth.home()">Back to {{ auth.role() === 'DRIVER' ? 'your jobs' : 'shipments' }}</a>

    @if (loadError()) {
      <p class="notice notice-error" role="alert">{{ loadError() }}</p>
    }

    @if (shipment(); as s) {
      @if (booked()) {
        <p class="notice">Booked. Share the tracking code and the receiver code below with the receiver.</p>
      }

      <header class="head">
        <st-tracking-code [code]="s.trackingCode" [large]="true" />
        <h1>{{ statusLabel(s.status) }}</h1>
      </header>

      <section class="panel" aria-label="Journey">
        <st-route-line [status]="s.status" [timeline]="timeline()" />
      </section>

      @if (message()) {
        <p class="notice" [class.notice-error]="messageIsError()" role="status">{{ message() }}</p>
      }

      <!-- What this person can do right now -->
      @if (moves().length || canShareLocation() || canAssign()) {
        <section class="panel act" aria-label="Actions">
          @for (m of moves(); track m) {
            <button class="btn" [class.btn-big]="auth.role() === 'DRIVER'" [class.btn-danger]="m === 'CANCELLED'"
                    [disabled]="busy()" (click)="move(m)">{{ moveLabel(m) }}</button>
          }

          @if (canShareLocation()) {
            <div class="share">
              <button class="btn btn-quiet" [class.btn-big]="auth.role() === 'DRIVER'" [disabled]="busy()" (click)="shareLocation()">
                Share my location
              </button>
              <label class="auto">
                <input type="checkbox" [checked]="autoShare()" (change)="toggleAutoShare($any($event.target).checked)" />
                Keep sharing every minute while this page is open
              </label>
            </div>
          }

          @if (canAssign()) {
            <form class="assign" (ngSubmit)="assign()">
              <div class="field">
                <label for="driver">{{ s.driverId ? 'Change driver' : 'Assign a driver' }}</label>
                <select id="driver" name="driver" [(ngModel)]="chosenDriver" required>
                  <option [ngValue]="null" disabled>Choose a driver</option>
                  @for (d of drivers(); track d.id) {
                    <option [ngValue]="d.id">{{ d.fullName }} ({{ d.email }})</option>
                  }
                </select>
                @if (drivers().length === 0) {
                  <span class="hint">No drivers yet. Add one under <a routerLink="/admin/users">People</a>.</span>
                }
              </div>
              <div class="field">
                <label for="expected">Expected delivery (optional)</label>
                <input id="expected" name="expected" type="date" [(ngModel)]="expectedDate" />
              </div>
              <button class="btn" type="submit" [disabled]="busy() || chosenDriver === null">{{ s.driverId ? 'Change driver' : 'Assign driver' }}</button>
            </form>
          }
        </section>
      }

      @if (canIssueDeliveryCode()) {
        <section class="panel proof" aria-label="Proof of delivery">
          <h2>Proof of delivery</h2>
          @if (deliveryCode()) {
            <p class="code">Receiver's code: <strong>{{ deliveryCode() }}</strong></p>
          } @else {
            <p class="muted">Generate a four-digit code and share it privately with the receiver. The rider must enter it to confirm delivery.</p>
          }
          @if (deliveryCode()) {
            <p class="muted small">Keep this code private. Replacing it invalidates the previous code.</p>
          }
          <button class="btn btn-quiet" [disabled]="busy()" (click)="issueDeliveryCode()">
            {{ deliveryCode() ? 'Replace receiver code' : 'Generate receiver code' }}
          </button>
        </section>
      }

      <div class="grid">
        <section class="panel" aria-label="Latest location">
          <h2>Latest location</h2>
          @if (location(); as l) {
            <st-location-map [latitude]="l.latitude" [longitude]="l.longitude" />
            <p class="muted small">Shared by the driver {{ l.recordedAt | date: 'd MMM, HH:mm' }}</p>
          } @else {
            <p class="muted">{{ s.status === 'CREATED' ? 'The driver shares their location once the parcel is picked up.' : 'No location shared yet.' }}</p>
          }
        </section>

        <section class="panel" aria-label="Details">
          <h2>Details</h2>
          <dl class="facts">
            <dt>From</dt><dd>{{ s.senderName }}, {{ s.senderPhone }}<br />{{ s.pickupAddress }}</dd>
            <dt>To</dt><dd>{{ s.receiverName }}, {{ s.receiverPhone }}<br />{{ s.deliveryAddress }}</dd>
            <dt>Parcel</dt><dd>{{ kg(s.weightKg) }} kg{{ s.description ? ', ' + s.description : '' }}</dd>
            <dt>Driver</dt>
            <dd>
              {{ driverName(s) }}
              @if (s.driverPhone && auth.role() !== 'DRIVER') {
                <br /><a [href]="call(s.driverPhone)">Call {{ s.driverPhone }}</a>
              }
            </dd>
            @if (s.expectedDeliveryDate && s.status !== 'DELIVERED' && s.status !== 'CANCELLED') {
              <dt>Expected</dt><dd>{{ s.expectedDeliveryDate | date: 'EEEE d MMMM' }}</dd>
            }
          </dl>
        </section>
      </div>

      @if (showInvoice()) {
        <section class="panel" aria-label="Invoice">
          <h2>Invoice</h2>
          @if (invoice(); as inv) {
            <div class="invoice">
              <div>
                <p class="amount">{{ inv.currency }} {{ inv.amount }}</p>
                <p class="muted small">Invoice {{ inv.invoiceNumber }}: base fee {{ inv.baseFee }} plus {{ inv.weightCharge }} for {{ kg(inv.weightKg) }} kg</p>
              </div>
              <span class="paid" [class.yes]="inv.status === 'PAID'">{{ inv.status === 'PAID' ? 'Paid ' + (inv.paidAt | date: 'd MMM') : 'Awaiting payment' }}</span>
              @if (auth.role() === 'ADMIN' && inv.status === 'ISSUED') {
                <button class="btn" [disabled]="busy()" (click)="recordPayment(inv)">Record payment</button>
              }
            </div>
          } @else {
            <p class="muted">The invoice is being prepared. It usually appears within a minute of delivery.</p>
          }
        </section>
      }

      <section class="panel" aria-label="History">
        <h2>History</h2>
        <ol class="history">
          @for (h of history(); track $index) {
            <li>
              <strong>{{ statusLabel(h.toStatus) }}</strong>
              <span class="muted"> by {{ roleName(h.changedByRole) }}, {{ h.changedAt | date: 'd MMM y, HH:mm' }}</span>
              @if (h.note) { <q>{{ h.note }}</q> }
            </li>
          }
        </ol>
      </section>
    } @else if (!loadError()) {
      <p class="muted">Loading shipment…</p>
    }
  `,
  styles: `
    :host { display: grid; gap: var(--s4); }
    .back { justify-self: start; }
    .head { display: flex; align-items: center; gap: var(--s4); flex-wrap: wrap; }
    .head h1 { margin: 0; }
    .act { display: flex; flex-wrap: wrap; gap: var(--s3); align-items: flex-end; }
    .share { display: grid; gap: var(--s2); }
    .proof { display: grid; gap: var(--s2); }
    .proof h2, .proof p { margin: 0; }
    .proof .code { font: 700 var(--step-3) var(--font-sign); letter-spacing: 0.08em; }
    .auto { display: flex; gap: var(--s2); align-items: center; font-size: var(--step--1); color: var(--ink-soft); }
    .auto input { width: 18px; height: 18px; }
    .assign { display: flex; gap: var(--s3); align-items: flex-end; flex-wrap: wrap; }
    .assign .field { margin: 0; min-width: 260px; }
    .grid { display: grid; gap: var(--s4); grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); }
    .facts { display: grid; grid-template-columns: max-content 1fr; gap: var(--s2) var(--s4); margin: 0; }
    .facts dt { font-weight: 600; color: var(--ink-soft); }
    .facts dd { margin: 0; }
    .small { font-size: var(--step--1); margin: var(--s2) 0 0; }
    .invoice { display: flex; align-items: center; gap: var(--s5); flex-wrap: wrap; }
    .amount { font: 700 var(--step-3) var(--font-sign); margin: 0; }
    .paid { font-weight: 600; color: var(--ink-soft); }
    .paid.yes { color: var(--route-deep); }
    .history { margin: 0; padding-left: var(--s5); display: grid; gap: var(--s2); }
    .history q { display: block; color: var(--ink-soft); }
    @media (max-width: 720px) { .act > .btn, .share { width: 100%; } }
  `,
})
export class ShipmentPage {
  /** Route params (router input binding). */
  readonly id = input.required<string>();
  readonly booked = input<string>();

  protected readonly auth = inject(AuthService);
  private readonly api = inject(Api);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly shipment = signal<Shipment | null>(null);
  protected readonly history = signal<StatusChange[]>([]);
  protected readonly location = signal<Location | null>(null);
  protected readonly invoice = signal<Invoice | null>(null);
  protected readonly drivers = signal<User[]>([]);
  protected readonly loadError = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly messageIsError = signal(false);
  protected readonly busy = signal(false);
  protected readonly autoShare = signal(false);
  protected readonly deliveryCode = signal<string | null>(null);
  protected chosenDriver: number | null = null;
  protected expectedDate = '';
  private autoTimer: ReturnType<typeof setInterval> | null = null;

  protected readonly timeline = computed(() => this.history().map((h) => ({ status: h.toStatus, at: h.changedAt })));
  private readonly isOwner = computed(() => this.shipment()?.customerId === this.auth.user()?.id);
  private readonly isAssigned = computed(() => this.shipment()?.driverId === this.auth.user()?.id);

  protected readonly moves = computed(() => {
    const s = this.shipment();
    const role = this.auth.role();
    if (!s || !role) return [];
    // Pickup needs an assigned driver; don't offer a move the server would refuse.
    return movesFor(role, s.status, this.isAssigned(), this.isOwner()).filter((m) => m !== 'PICKED_UP' || s.driverId !== null);
  });
  protected readonly canShareLocation = computed(() => {
    const s = this.shipment();
    return !!s && this.auth.role() === 'DRIVER' && this.isAssigned() && (s.status === 'PICKED_UP' || s.status === 'IN_TRANSIT');
  });
  protected readonly canAssign = computed(() => {
    const s = this.shipment();
    return !!s && this.auth.role() === 'ADMIN' && s.status !== 'DELIVERED' && s.status !== 'CANCELLED';
  });
  protected readonly showInvoice = computed(() =>
    this.shipment()?.status === 'DELIVERED' && (this.auth.role() === 'ADMIN' || this.isOwner()));
  protected readonly canIssueDeliveryCode = computed(() => {
    const s = this.shipment();
    return this.auth.role() === 'CUSTOMER' && this.isOwner() && !!s
      && s.status !== 'DELIVERED' && s.status !== 'CANCELLED';
  });

  constructor() {
    // untracked: only a new id should reload, not the signals the HTTP call happens to read (logging out, say).
    effect(() => {
      const id = Number(this.id());
      untracked(() => this.load(id));
    });
    if (this.auth.role() === 'ADMIN') {
      this.api.users('DRIVER', 0, 100).subscribe({ next: (p) => this.drivers.set(p.content) });
    }
    this.destroyRef.onDestroy(() => this.stopAutoShare());
    // The driver is the one making the changes, so only the people following along need live updates.
    if (this.auth.role() !== 'DRIVER') {
      timer(LIVE_UPDATE_MS, LIVE_UPDATE_MS).pipe(takeUntilDestroyed()).subscribe(() => this.liveUpdate());
    }
  }

  protected call(phone: string): string { return callLink(phone); }

  /** Skips hidden tabs and finished parcels. Marked as background, so watching a page doesn't keep a session alive. */
  private liveUpdate(): void {
    const current = this.shipment();
    if (!current || current.status === 'DELIVERED' || current.status === 'CANCELLED' || document.hidden || this.busy()) return;
    forkJoin({
      shipment: this.api.shipment(current.id, true),
      history: this.api.history(current.id, true),
      location: this.api.latestLocation(current.id, true).pipe(catchError(() => of(null))),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ shipment, history, location }) => {
        this.shipment.set(shipment);
        this.history.set(history);
        this.location.set(location);
        if (shipment.status === 'DELIVERED') this.refreshSideData(shipment); // starts looking for the invoice
      },
      error: () => undefined, // try again on the next round
    });
  }

  protected statusLabel(s: ShipmentStatus): string { return STATUS_LABEL[s]; }
  /** "2.500" -> "2.5" */
  protected kg(weight: string): string { return String(Number(weight)); }
  protected moveLabel(s: ShipmentStatus): string { return MOVE_LABEL[s]; }
  protected roleName(r: string): string { return ({ ADMIN: 'an admin', DRIVER: 'the driver', CUSTOMER: 'the customer' } as Record<string, string>)[r] ?? r; }

  protected driverName(s: Shipment): string {
    if (!s.driverId) return 'Not assigned yet';
    if (s.driverId === this.auth.user()?.id) return 'You';
    return s.driverName ?? this.drivers().find((d) => d.id === s.driverId)?.fullName ?? 'Assigned';
  }

  protected move(next: ShipmentStatus): void {
    const s = this.shipment();
    if (!s) return;
    if (next === 'CANCELLED' && !confirm('Cancel this shipment? This can’t be undone.')) return;
    let deliveryCode: string | undefined;
    if (next === 'DELIVERED') {
      if (!confirm('Mark this parcel as delivered? The customer will be billed.')) return;
      deliveryCode = prompt('Enter the four-digit code given by the receiver:')?.trim();
      if (!/^\d{4}$/.test(deliveryCode ?? '')) {
        this.say('Enter the four-digit code from the receiver to confirm delivery.', true);
        return;
      }
    }
    this.run(this.api.moveShipment(s.id, next, s.version, undefined, deliveryCode), (updated) => {
      this.shipment.set(updated);
      this.say(next === 'DELIVERED' ? 'Marked delivered. The invoice is on its way to the customer.' : `${STATUS_LABEL[next]}.`);
      this.refreshSideData(updated);
      if (!this.canShareLocation()) this.stopAutoShare();
    });
  }

  protected issueDeliveryCode(): void {
    const s = this.shipment();
    if (!s || !this.canIssueDeliveryCode()) return;
    if (this.deliveryCode() && !confirm('Replace the receiver code? The previous code will stop working.')) return;
    this.busy.set(true);
    this.api.issueDeliveryCode(s.id).subscribe({
      next: ({ deliveryCode }) => {
        this.busy.set(false);
        this.deliveryCode.set(deliveryCode);
        this.say('Receiver code is ready. Share it privately with the receiver.');
      },
      error: (err) => {
        this.busy.set(false);
        this.say(describeError(err), true);
      },
    });
  }

  protected assign(): void {
    const s = this.shipment();
    if (!s || this.chosenDriver === null) return;
    this.run(this.api.assignDriver(s.id, this.chosenDriver, this.expectedDate || undefined), (updated) => {
      this.shipment.set(updated);
      this.say(`Driver ${this.driverName(updated)} assigned.`);
    });
  }

  protected recordPayment(inv: Invoice): void {
    this.run(this.api.recordPayment(inv.id), (paid) => {
      this.invoice.set(paid);
      this.say(`Payment recorded for ${paid.invoiceNumber}.`);
    });
  }

  protected shareLocation(quiet = false): void {
    const s = this.shipment();
    if (!s) return;
    if (!isSecureContextAvailable()) {
      // Browsers only allow GPS on https sites (or localhost).
      this.say('Location sharing needs a secure connection. Open ShipTrack with https:// to share your location.', true);
      this.stopAutoShare();
      return;
    }
    if (!('geolocation' in navigator)) {
      this.say('This browser can’t share location.', true);
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => this.api.postLocation(s.id, pos.coords.latitude, pos.coords.longitude).subscribe({
        next: (l) => { this.location.set(l); if (!quiet) this.say('Location shared.'); },
        error: (err) => this.say(describeError(err), true),
      }),
      (failure) => {
        if (failure.code === failure.PERMISSION_DENIED) {
          this.stopAutoShare(); // asking again every minute can't succeed until they change the setting
          this.say('Location is blocked. Allow location for this site in your browser settings, then try again.', true);
        } else {
          this.say('Couldn’t get a GPS fix. Move somewhere with better signal and try again.', true);
        }
      },
      { enableHighAccuracy: true, timeout: 15_000, maximumAge: 30_000 },
    );
  }

  protected toggleAutoShare(on: boolean): void {
    this.autoShare.set(on);
    if (on) {
      this.shareLocation();
      // shareLocation turns this off when sharing is impossible; don't start a timer that can only fail.
      if (this.autoShare()) {
        this.autoTimer = setInterval(() => this.shareLocation(true), AUTO_SHARE_MS);
      }
    } else {
      this.stopAutoShare();
    }
  }

  private stopAutoShare(): void {
    if (this.autoTimer) clearInterval(this.autoTimer);
    this.autoTimer = null;
    this.autoShare.set(false);
  }

  private load(id: number): void {
    this.loadError.set(null);
    const navigationState = window.history.state;
    this.deliveryCode.set(Number(navigationState?.deliveryCodeShipmentId) === id ? navigationState.deliveryCode : null);
    if (Number(navigationState?.deliveryCodeShipmentId) === id && navigationState?.deliveryCodeUnavailable) {
      this.say('Shipment booked, but its receiver code could not be generated. Try Generate receiver code below.');
    }
    forkJoin({ shipment: this.api.shipment(id), history: this.api.history(id) }).subscribe({
      next: ({ shipment, history }) => {
        this.shipment.set(shipment);
        this.history.set(history);
        this.chosenDriver = shipment.driverId;
        this.refreshSideData(shipment);
      },
      error: (err) => this.loadError.set(errorCode(err) === 'FORBIDDEN'
        ? 'This shipment belongs to someone else.'
        : errorCode(err) === 'NOT_FOUND' ? 'No shipment with this number.' : describeError(err)),
    });
  }

  /** Location, invoice and history may not exist yet; missing ones simply stay empty. */
  private refreshSideData(s: Shipment): void {
    this.api.history(s.id).pipe(catchError(() => of(this.history()))).subscribe((h) => this.history.set(h));
    if (s.status !== 'CREATED') {
      this.api.latestLocation(s.id).pipe(catchError(() => of(null))).subscribe((l) => this.location.set(l));
    }
    if (s.status === 'DELIVERED' && (this.auth.role() === 'ADMIN' || this.isOwner())) {
      this.api.invoiceForShipment(s.id).pipe(
        retry({ count: INVOICE_POLLS, delay: (err) => (errorCode(err) === 'NOT_FOUND' ? timer(INVOICE_POLL_MS) : throwError(() => err)) }),
        catchError(() => of(null)),
        takeUntilDestroyed(this.destroyRef),
      ).subscribe((i) => this.invoice.set(i));
    }
  }

  private run<T>(call: import('rxjs').Observable<T>, done: (value: T) => void): void {
    this.busy.set(true);
    call.subscribe({
      next: (v) => { this.busy.set(false); done(v); },
      error: (err) => {
        this.busy.set(false);
        if (errorCode(err) === 'VERSION_MISMATCH' || errorCode(err) === 'CONCURRENT_MODIFICATION') {
          this.say('Someone else updated this shipment a moment ago. It has been reloaded; check it and try again.', true);
          this.load(Number(this.id()));
        } else {
          this.say(describeError(err), true);
        }
      },
    });
  }

  private say(text: string, isError = false): void {
    this.message.set(text);
    this.messageIsError.set(isError);
  }
}
