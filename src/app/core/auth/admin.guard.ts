import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthSessionService } from './auth-session.service';

export const adminGuard: CanActivateFn = (_route, state) => {
  const session = inject(AuthSessionService);
  const router = inject(Router);

  const language = state.url.split('/')[1] === 'en' ? 'en' : 'pl';
  const loginUrl = router.createUrlTree(['/', language, 'admin', 'login']);

  return session.refresh().pipe(
    map((user) => (user?.roles.includes('ADMIN') ? true : loginUrl)),
    catchError(() => of(loginUrl)),
  );
};
