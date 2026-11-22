import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthSessionService } from './auth-session.service';

export const adminGuard: CanActivateFn = (_route, state) => {
  const session = inject(AuthSessionService);
  const router = inject(Router);

  const language = state.url.split('/')[1] === 'en' ? 'en' : 'pl';
  const loginUrl = router.createUrlTree(['/', language, 'account', 'login']);
  const mfaUrl = router.createUrlTree(['/', language, 'account', 'mfa']);

  return session.refresh().pipe(
    map((user) => {
      if (session.mfaRequired()) {
        return mfaUrl;
      }

      return user?.roles.includes('ADMIN') ? true : loginUrl;
    }),
    catchError(() => of(loginUrl)),
  );
};
