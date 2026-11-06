import { describe, expect, it } from 'vitest';
import { movesFor, nextStatuses, routeIndex } from './status';

describe('shipment lifecycle (mirrors backend FR-5)', () => {
  it('moves one step at a time, cancel only before pickup', () => {
    expect(nextStatuses('CREATED')).toEqual(['PICKED_UP', 'CANCELLED']);
    expect(nextStatuses('PICKED_UP')).toEqual(['IN_TRANSIT']);
    expect(nextStatuses('IN_TRANSIT')).toEqual(['DELIVERED']);
    expect(nextStatuses('DELIVERED')).toEqual([]);
    expect(nextStatuses('CANCELLED')).toEqual([]);
  });

  it('admins can make any valid move', () => {
    expect(movesFor('ADMIN', 'CREATED', false, false)).toEqual(['PICKED_UP', 'CANCELLED']);
  });

  it('only the assigned driver moves the parcel, and drivers never cancel', () => {
    expect(movesFor('DRIVER', 'CREATED', true, false)).toEqual(['PICKED_UP']);
    expect(movesFor('DRIVER', 'IN_TRANSIT', true, false)).toEqual(['DELIVERED']);
    expect(movesFor('DRIVER', 'CREATED', false, false)).toEqual([]);
  });

  it('customers can only cancel their own shipment before pickup', () => {
    expect(movesFor('CUSTOMER', 'CREATED', false, true)).toEqual(['CANCELLED']);
    expect(movesFor('CUSTOMER', 'CREATED', false, false)).toEqual([]);
    expect(movesFor('CUSTOMER', 'PICKED_UP', false, true)).toEqual([]);
  });

  it('places each status on the route line', () => {
    expect(routeIndex('CREATED')).toBe(0);
    expect(routeIndex('IN_TRANSIT')).toBe(2);
    expect(routeIndex('DELIVERED')).toBe(3);
    expect(routeIndex('CANCELLED')).toBe(0);
  });
});
