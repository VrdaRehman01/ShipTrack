// Shapes returned by the ShipTrack API (see Swagger). Kept in one place so every screen agrees.

export type Role = 'CUSTOMER' | 'DRIVER' | 'ADMIN';
export type ShipmentStatus = 'CREATED' | 'PICKED_UP' | 'IN_TRANSIT' | 'DELIVERED' | 'CANCELLED';
export type InvoiceStatus = 'ISSUED' | 'PAID';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  correlationId?: string;
}

export interface User {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  phone?: string | null;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface Shipment {
  id: number;
  trackingCode: string;
  status: ShipmentStatus;
  customerId: number;
  driverId: number | null;
  senderName: string;
  senderPhone: string;
  pickupAddress: string;
  receiverName: string;
  receiverPhone: string;
  deliveryAddress: string;
  weightKg: string;
  description: string | null;
  invoiced: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
  deliveredAt: string | null;
  driverName: string | null;
  driverPhone: string | null;
  expectedDeliveryDate: string | null; // yyyy-MM-dd
}

/** A parcel waiting for a driver: no names or phone numbers until it has been accepted. */
export interface AvailableJob {
  id: number;
  pickupAddress: string;
  deliveryAddress: string;
  weightKg: string;
  description: string | null;
  createdAt: string;
}

export interface NewShipment {
  senderName: string;
  senderPhone: string;
  pickupAddress: string;
  receiverName: string;
  receiverPhone: string;
  deliveryAddress: string;
  weightKg: number;
  description?: string;
}

export interface StatusChange {
  fromStatus: ShipmentStatus | null;
  toStatus: ShipmentStatus;
  changedBy: number;
  changedByRole: Role;
  note: string | null;
  changedAt: string;
}

export interface PublicTracking {
  trackingCode: string;
  status: ShipmentStatus;
  lastUpdated: string;
  timeline: { status: ShipmentStatus; at: string }[];
}

export interface Location {
  id: number;
  shipmentId: number;
  driverId: number;
  latitude: number;
  longitude: number;
  recordedAt: string;
}

export interface Invoice {
  id: number;
  invoiceNumber: string;
  shipmentId: number;
  trackingCode: string;
  customerId: number;
  weightKg: string;
  baseFee: string;
  weightCharge: string;
  amount: string;
  currency: string;
  status: InvoiceStatus;
  issuedAt: string;
  paidAt: string | null;
}
