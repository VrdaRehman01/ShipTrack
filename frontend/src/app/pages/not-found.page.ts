import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'st-not-found-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel box">
      <h1>This page doesn't exist</h1>
      <p class="muted">The link may be old or mistyped.</p>
      <a class="btn" routerLink="/">Go to tracking</a>
    </section>
  `,
  styles: `.box { max-width: 520px; margin: var(--s6) auto; }`,
})
export class NotFoundPage {}
