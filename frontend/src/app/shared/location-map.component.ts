import {
  AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, effect, input, viewChild,
} from '@angular/core';
import * as L from 'leaflet';

/** A small OpenStreetMap map with one marker at the driver's latest position. */
@Component({
  selector: 'st-location-map',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div #map class="map" role="img" [attr.aria-label]="'Map showing latest location ' + latitude() + ', ' + longitude()"></div>`,
  styles: `
    .map { height: 260px; border-radius: var(--radius-panel); border: 1px solid var(--line); z-index: 0; }
  `,
})
export class LocationMapComponent implements AfterViewInit, OnDestroy {
  readonly latitude = input.required<number>();
  readonly longitude = input.required<number>();

  private readonly el = viewChild.required<ElementRef<HTMLDivElement>>('map');
  private map?: L.Map;
  private marker?: L.CircleMarker;

  constructor() {
    effect(() => {
      const point: L.LatLngExpression = [this.latitude(), this.longitude()];
      if (this.map && this.marker) {
        this.marker.setLatLng(point);
        this.map.panTo(point);
      }
    });
  }

  ngAfterViewInit(): void {
    const point: L.LatLngExpression = [this.latitude(), this.longitude()];
    this.map = L.map(this.el().nativeElement, { scrollWheelZoom: false }).setView(point, 13);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors',
    }).addTo(this.map);
    // A circle marker avoids Leaflet's default icon images (which need extra asset setup).
    this.marker = L.circleMarker(point, { radius: 9, color: '#095c44', weight: 3, fillColor: '#0e7a5a', fillOpacity: 1 })
      .addTo(this.map);
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }
}
