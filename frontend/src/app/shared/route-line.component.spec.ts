import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { RouteLineComponent } from './route-line.component';
import { ShipmentStatus } from '../core/models';

function render(status: ShipmentStatus, timeline: { status: ShipmentStatus; at: string }[] = []): HTMLElement {
  const fixture = TestBed.createComponent(RouteLineComponent);
  fixture.componentRef.setInput('status', status);
  fixture.componentRef.setInput('timeline', timeline);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('RouteLineComponent', () => {
  it('shows four stops and fills them up to the current status', () => {
    const el = render('IN_TRANSIT', [{ status: 'CREATED', at: '2026-11-04T09:30:00Z' }]);
    const stops = el.querySelectorAll('.stop');
    expect(Array.from(stops).map((s) => s.querySelector('.name')!.textContent!.trim()))
      .toEqual(['Booked', 'Picked up', 'On the way', 'Delivered']);
    expect(el.querySelectorAll('.stop.done').length).toBe(3);
    expect(el.querySelector('.stop.here .name')!.textContent).toContain('On the way');
    expect(el.querySelectorAll('time').length).toBe(1);
  });

  it('announces the status for screen readers', () => {
    expect(render('DELIVERED').querySelector('figure')!.getAttribute('aria-label')).toBe('Shipment status: Delivered');
  });

  it('marks a cancelled shipment as an exit, not a stop', () => {
    const el = render('CANCELLED');
    expect(el.querySelector('.exit')!.textContent).toContain('Cancelled');
    expect(el.querySelectorAll('.stop.here').length).toBe(0);
  });
});
