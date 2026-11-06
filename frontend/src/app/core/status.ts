import { Role, ShipmentStatus } from './models';

/** The four stops on a shipment's route, in order. CANCELLED is an exit, not a stop. */
export const ROUTE: readonly ShipmentStatus[] = ['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'DELIVERED'];

export const STATUS_LABEL: Record<ShipmentStatus, string> = {
  CREATED: 'Booked',
  PICKED_UP: 'Picked up',
  IN_TRANSIT: 'On the way',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

/** Mirrors the backend lifecycle (FR-5): one step forward, cancel only before pickup. */
export function nextStatuses(status: ShipmentStatus): ShipmentStatus[] {
  switch (status) {
    case 'CREATED': return ['PICKED_UP', 'CANCELLED'];
    case 'PICKED_UP': return ['IN_TRANSIT'];
    case 'IN_TRANSIT': return ['DELIVERED'];
    default: return [];
  }
}

/** Which of the next moves this role may make (the backend enforces the same rules). */
export function movesFor(role: Role, status: ShipmentStatus, isAssignedDriver: boolean, isOwner: boolean): ShipmentStatus[] {
  return nextStatuses(status).filter((next) => {
    if (role === 'ADMIN') return true;
    if (role === 'DRIVER') return isAssignedDriver && next !== 'CANCELLED';
    return isOwner && next === 'CANCELLED';
  });
}

/** Button text for a move, named for what the person is doing. */
export const MOVE_LABEL: Record<ShipmentStatus, string> = {
  CREATED: 'Book',
  PICKED_UP: 'Mark picked up',
  IN_TRANSIT: 'Start delivery',
  DELIVERED: 'Mark delivered',
  CANCELLED: 'Cancel shipment',
};

/** How far along the route a status is, 0..3 (cancelled shows where it stopped: at booking). */
export function routeIndex(status: ShipmentStatus): number {
  return status === 'CANCELLED' ? 0 : ROUTE.indexOf(status);
}
