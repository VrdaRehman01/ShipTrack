import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/guards';

export const routes: Routes = [
  { path: '', pathMatch: 'full', loadComponent: () => import('./pages/track.page').then((m) => m.TrackPage), title: 'ShipTrack' },
  { path: 'track', loadComponent: () => import('./pages/track.page').then((m) => m.TrackPage), title: 'Track a parcel - ShipTrack' },
  { path: 'track/:code', loadComponent: () => import('./pages/track.page').then((m) => m.TrackPage), title: 'Track a parcel - ShipTrack' },
  { path: 'login', canActivate: [guestGuard], loadComponent: () => import('./pages/login.page').then((m) => m.LoginPage), title: 'Log in - ShipTrack' },
  { path: 'register', canActivate: [guestGuard], loadComponent: () => import('./pages/register.page').then((m) => m.RegisterPage), title: 'Create account - ShipTrack' },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      {
        path: 'driver', canActivate: [roleGuard('DRIVER')],
        loadComponent: () => import('./pages/driver.page').then((m) => m.DriverPage), title: 'Your jobs - ShipTrack',
      },
      { path: 'shipments', loadComponent: () => import('./pages/shipments.page').then((m) => m.ShipmentsPage), title: 'Shipments - ShipTrack' },
      {
        path: 'shipments/new', canActivate: [roleGuard('CUSTOMER')],
        loadComponent: () => import('./pages/new-shipment.page').then((m) => m.NewShipmentPage), title: 'Send a parcel - ShipTrack',
      },
      { path: 'shipments/:id', loadComponent: () => import('./pages/shipment.page').then((m) => m.ShipmentPage), title: 'Shipment - ShipTrack' },
      {
        path: 'invoices', canActivate: [roleGuard('CUSTOMER', 'ADMIN')],
        loadComponent: () => import('./pages/invoices.page').then((m) => m.InvoicesPage), title: 'Invoices - ShipTrack',
      },
      {
        path: 'admin/users', canActivate: [roleGuard('ADMIN')],
        loadComponent: () => import('./pages/users.page').then((m) => m.UsersPage), title: 'People - ShipTrack',
      },
    ],
  },
  { path: '**', loadComponent: () => import('./pages/not-found.page').then((m) => m.NotFoundPage), title: 'Not found - ShipTrack' },
];
