import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ROUTE, STATUS_LABEL, routeIndex } from '../core/status';
import { ShipmentStatus } from '../core/models';

/**
 * The shipment's journey drawn like a transit line: four stops, filled up to where the parcel is now.
 * This is the one bold element of the interface; it fills once when it appears.
 */
@Component({
  selector: 'st-route-line',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <figure class="route" [class.cancelled]="cancelled()" [attr.aria-label]="summary()">
      <div class="track" aria-hidden="true">
        <div class="fill" [style.--progress]="progress()"></div>
      </div>
      <ol class="stops">
        @for (stop of stops(); track stop.status) {
          <li class="stop" [class.done]="stop.done" [class.here]="stop.here">
            <span class="dot" aria-hidden="true"></span>
            <span class="name">{{ stop.label }}</span>
            @if (stop.at) {
              <time class="at" [attr.datetime]="stop.at">{{ stop.at | date: 'd MMM, HH:mm' }}</time>
            }
          </li>
        }
      </ol>
      @if (cancelled()) {
        <figcaption class="exit">Cancelled before pickup</figcaption>
      }
    </figure>
  `,
  styles: `
    :host { display: block; }
    .route { margin: 0; position: relative; padding-top: 6px; }
    .track {
      position: absolute; left: 12.5%; right: 12.5%; top: 14px;
      height: 6px; border-radius: 3px; background: var(--line);
    }
    .fill {
      height: 100%; border-radius: inherit; background: var(--route);
      width: calc(var(--progress) * 100%);
      animation: fill 900ms cubic-bezier(.2,.7,.2,1) both;
    }
    @keyframes fill { from { width: 0; } }
    .stops { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(4, 1fr); }
    .stop { display: grid; justify-items: center; align-content: start; text-align: center; gap: 6px; position: relative; }
    .dot {
      width: 22px; height: 22px; border-radius: 50%;
      background: var(--paper); border: 4px solid var(--line); box-sizing: border-box;
    }
    .done .dot { border-color: var(--route); background: var(--route); }
    .here .dot { background: var(--paper); border-width: 6px; box-shadow: 0 0 0 4px var(--route-wash); }
    .name { font-family: var(--font-sign); font-weight: 600; font-size: var(--step-0); line-height: 1.1; }
    .stop:not(.done) .name { color: var(--ink-soft); }
    .at { font-size: var(--step--1); color: var(--ink-soft); }
    .cancelled .fill { background: var(--ink-soft); }
    .cancelled .done .dot { border-color: var(--ink-soft); background: var(--ink-soft); }
    .exit { margin-top: var(--s3); color: var(--danger); font-weight: 600; text-align: center; }
    @media (max-width: 420px) { .name { font-size: var(--step--1); } }
  `,
})
export class RouteLineComponent {
  readonly status = input.required<ShipmentStatus>();
  /** When each stop was reached, if known. */
  readonly timeline = input<{ status: ShipmentStatus; at: string }[]>([]);

  readonly cancelled = computed(() => this.status() === 'CANCELLED');
  readonly progress = computed(() => routeIndex(this.status()) / (ROUTE.length - 1));

  readonly stops = computed(() => {
    const reached = routeIndex(this.status());
    const times = new Map(this.timeline().map((t) => [t.status, t.at]));
    return ROUTE.map((status, i) => ({
      status,
      label: STATUS_LABEL[status],
      done: i <= reached,
      here: i === reached && !this.cancelled() && status !== 'DELIVERED',
      at: times.get(status) ?? null,
    }));
  });

  readonly summary = computed(() => `Shipment status: ${STATUS_LABEL[this.status()]}`);
}
