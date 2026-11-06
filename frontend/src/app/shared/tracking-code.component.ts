import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';

/** The tracking code as a parcel label: the only place the high-visibility amber is used. */
@Component({
  selector: 'st-tracking-code',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="label" [class.large]="large()">
      <span class="code">{{ code() }}</span>
      <button type="button" class="copy" (click)="copy()" [attr.aria-label]="'Copy tracking code ' + code()">
        {{ copied() ? 'Copied' : 'Copy' }}
      </button>
    </span>
  `,
  styles: `
    .label {
      display: inline-flex; align-items: stretch; border-radius: 4px; overflow: hidden;
      background: var(--signal); color: var(--signal-ink);
    }
    .code {
      font-family: var(--font-sign); font-weight: 700; letter-spacing: 0.04em;
      font-size: var(--step-1); padding: 2px 10px;
    }
    .large .code { font-size: var(--step-3); padding: 4px 14px; }
    .copy {
      border: 0; border-left: 1px solid rgb(46 33 0 / 0.25); background: transparent; color: inherit;
      font: 600 var(--step--1) var(--font); padding: 0 10px; cursor: pointer;
    }
    .copy:hover { background: rgb(46 33 0 / 0.08); }
  `,
})
export class TrackingCodeComponent {
  readonly code = input.required<string>();
  readonly large = input(false);
  readonly copied = signal(false);

  async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.code());
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1800);
    } catch {
      // clipboard blocked: the code is still visible to copy by hand
    }
  }
}
