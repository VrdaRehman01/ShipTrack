import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api, describeError } from '../core/api';
import { newIdempotencyKey } from '../core/ids';
import { NewShipment } from '../core/models';

/**
 * Book a parcel. One Idempotency-Key per filled-in form, so a double click or a retry after a
 * dropped connection never creates two shipments (FR-10).
 */
@Component({
  selector: 'st-new-shipment-page',
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Send a parcel</h1>
    <p class="muted">We'll collect it from the sender and deliver it to the receiver.</p>

    @if (error()) {
      <p class="notice notice-error" role="alert">{{ error() }}</p>
    }

    <form (ngSubmit)="submit()" #f="ngForm" class="sheet">
      <fieldset class="panel">
        <legend>Pick up from</legend>
        <div class="field">
          <label for="senderName">Sender's name</label>
          <input id="senderName" name="senderName" [(ngModel)]="form.senderName" required maxlength="100" autocomplete="name" />
        </div>
        <div class="field">
          <label for="senderPhone">Sender's phone</label>
          <input id="senderPhone" name="senderPhone" type="tel" [(ngModel)]="form.senderPhone" required [pattern]="phone"
                 placeholder="+92 300 1234567" autocomplete="tel" />
        </div>
        <div class="field">
          <label for="pickupAddress">Pickup address</label>
          <textarea id="pickupAddress" name="pickupAddress" rows="2" [(ngModel)]="form.pickupAddress" required maxlength="255"></textarea>
        </div>
      </fieldset>

      <fieldset class="panel">
        <legend>Deliver to</legend>
        <div class="field">
          <label for="receiverName">Receiver's name</label>
          <input id="receiverName" name="receiverName" [(ngModel)]="form.receiverName" required maxlength="100" />
        </div>
        <div class="field">
          <label for="receiverPhone">Receiver's phone</label>
          <input id="receiverPhone" name="receiverPhone" type="tel" [(ngModel)]="form.receiverPhone" required [pattern]="phone"
                 placeholder="+92 321 7654321" />
        </div>
        <div class="field">
          <label for="deliveryAddress">Delivery address</label>
          <textarea id="deliveryAddress" name="deliveryAddress" rows="2" [(ngModel)]="form.deliveryAddress" required maxlength="255"></textarea>
        </div>
      </fieldset>

      <fieldset class="panel">
        <legend>The parcel</legend>
        <div class="field">
          <label for="weightKg">Weight (kg)</label>
          <input id="weightKg" name="weightKg" type="number" inputmode="decimal" step="0.001" min="0.001" max="1000"
                 [(ngModel)]="form.weightKg" required aria-describedby="weight-hint" />
          <span id="weight-hint" class="hint">Price: PKR 250 plus PKR 120 per kg, charged when it's delivered.</span>
        </div>
        <div class="field">
          <label for="description">What's inside (optional)</label>
          <input id="description" name="description" [(ngModel)]="form.description" maxlength="255" placeholder="Books, fragile" />
        </div>
      </fieldset>

      <div class="actions">
        <a class="btn btn-quiet" routerLink="/shipments">Cancel</a>
        <button class="btn" type="submit" [disabled]="saving() || f.invalid">{{ saving() ? 'Booking…' : 'Book pickup' }}</button>
      </div>
    </form>
  `,
  styles: `
    .sheet { display: grid; gap: var(--s4); max-width: 640px; margin-top: var(--s5); }
    fieldset { margin: 0; }
    legend { font: 700 var(--step-1) var(--font-sign); padding: 0 var(--s1); }
    textarea { resize: vertical; }
    .actions { display: flex; justify-content: flex-end; gap: var(--s3); }
  `,
})
export class NewShipmentPage {
  private readonly api = inject(Api);
  private readonly router = inject(Router);

  protected readonly phone = '^\\+?[0-9][0-9 \\-]{6,19}$';
  protected form: NewShipment = {
    senderName: '', senderPhone: '', pickupAddress: '',
    receiverName: '', receiverPhone: '', deliveryAddress: '',
    weightKg: 1, description: '',
  };
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Same key for every retry of this form; a new form gets a new key. */
  private readonly idempotencyKey = newIdempotencyKey();

  protected submit(): void {
    this.saving.set(true);
    this.error.set(null);
    const body: NewShipment = { ...this.form, description: this.form.description?.trim() || undefined };
    this.api.createShipment(body, this.idempotencyKey).subscribe({
      next: (s) => void this.router.navigate(['/shipments', s.id], { queryParams: { booked: 1 } }),
      error: (err) => {
        this.saving.set(false);
        this.error.set(describeError(err));
      },
    });
  }
}
