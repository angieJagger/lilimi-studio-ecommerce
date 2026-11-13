import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, finalize, map, of, shareReplay, tap, throwError, type Observable } from 'rxjs';
import { AuthApiService } from './auth-api.service';
import type { CurrentUser, LoginRequest } from './auth.model';

@Injectable({
  providedIn: 'root',
})
export class AuthSessionService {
  private readonly api = inject(AuthApiService);
  private readonly currentUser = signal<CurrentUser | null>(null);

  private pendingRefresh?: Observable<CurrentUser | null>;

  readonly user = this.currentUser.asReadonly();

  readonly isAuthenticated = computed(() => this.user() !== null);

  readonly isAdmin = computed(() => this.user()?.roles.includes('ADMIN') ?? false);

  refresh(): Observable<CurrentUser | null> {
    if (this.pendingRefresh) {
      return this.pendingRefresh;
    }

    const refresh: Observable<CurrentUser | null> = this.api.getSession().pipe(
      map((user) => {
        if (this.pendingRefresh !== refresh) {
          return this.currentUser();
        }

        this.currentUser.set(user);
        return user;
      }),
      catchError((error: unknown) => {
        if (this.pendingRefresh !== refresh) {
          return of(this.currentUser());
        }

        this.currentUser.set(null);

        if (error instanceof HttpErrorResponse && error.status === 401) {
          return of(null);
        }

        return throwError(() => error);
      }),
      finalize(() => {
        if (this.pendingRefresh === refresh) {
          this.pendingRefresh = undefined;
        }
      }),
      shareReplay({ bufferSize: 1, refCount: true }),
    );

    this.pendingRefresh = refresh;
    return refresh;
  }

  login(request: LoginRequest): Observable<CurrentUser> {
    return this.api.login(request).pipe(tap((user) => this.replaceSession(user)));
  }

  logout(): Observable<void> {
    return this.api.logout().pipe(tap(() => this.replaceSession(null)));
  }

  private replaceSession(user: CurrentUser | null): void {
    this.pendingRefresh = undefined;
    this.currentUser.set(user);
  }

  updateCustomerName(email: string, firstName: string | null): void {
    this.currentUser.update((user) => {
      if (
        !user ||
        user.email !== email ||
        !user.roles.includes('CUSTOMER') ||
        user.roles.includes('ADMIN')
      ) {
        return user;
      }

      return {
        ...user,
        firstName: firstName?.trim() || null,
      };
    });
  }
}
