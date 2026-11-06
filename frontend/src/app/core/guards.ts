import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './models';

/** Logged-in users only; remembers where they were going. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (auth.token()) return true;
  return inject(Router).createUrlTree(['/login'], { queryParams: { next: state.url } });
};

/** Only the given roles; others go to their own start page. */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const role = auth.role();
    if (role && roles.includes(role)) return true;
    return inject(Router).parseUrl(auth.homeFor(role));
  };
}

/** Login/register pages: skip them when already logged in. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.token() ? inject(Router).parseUrl(auth.homeFor(auth.role())) : true;
};
