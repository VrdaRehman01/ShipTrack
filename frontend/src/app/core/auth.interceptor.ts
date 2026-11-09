import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService, BACKGROUND_CALL, RENEW_URL } from './auth.service';

const CREDENTIAL_CALLS = ['/api/v1/auth/login', '/api/v1/auth/register'];

/** Adds the JWT to API calls; a 401 on a protected call means the session ended, so go to login. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  // Read before token(), which clears a session that has just expired.
  const hadSession = auth.isLoggedIn();
  const token = auth.token();
  const isCredentialCall = CREDENTIAL_CALLS.includes(req.url);
  const isRenewal = req.url === RENEW_URL;
  const sendsToken = !!token && req.url.startsWith('/api/') && !isCredentialCall;
  const request = sendsToken ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  // Using the app is what keeps a session going, so a tab left idle still logs out.
  if (sendsToken && !isRenewal && !req.context.get(BACKGROUND_CALL)) auth.renewIfDue();

  return next(request).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401 && !isCredentialCall && !isRenewal && hadSession) {
        auth.logout('expired');
      }
      return throwError(() => err);
    }),
  );
};
