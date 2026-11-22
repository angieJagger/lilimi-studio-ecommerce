import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, of, switchMap, type Observable } from 'rxjs';
import { API_BASE_URL } from '../api/api-base-url';
import type { CurrentUser, LoginRequest, RegisterCustomerRequest } from './auth.model';
import { CsrfService } from './csrf.service';

export interface AuthSessionState {
  readonly user: CurrentUser | null;
  readonly mfaRequired: boolean;
}
@Injectable({
  providedIn: 'root',
})
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly csrf = inject(CsrfService);

  getCurrentUser(): Observable<CurrentUser> {
    return this.http.get<CurrentUser>(`${this.baseUrl}/auth/me`);
  }

  getSession(): Observable<CurrentUser | null> {
    return this.getSessionState().pipe(map((state) => state.user));
  }

  getSessionState(): Observable<AuthSessionState> {
    return this.http
      .get<CurrentUser>(`${this.baseUrl}/auth/session`, {
        observe: 'response',
        transferCache: false,
      })
      .pipe(
        map((response) => ({
          user: response.body,
          mfaRequired: response.headers.get('X-MFA-Required') === 'true',
        })),
      );
  }

  register(
    request: RegisterCustomerRequest,
    turnstileToken: string | null = null,
  ): Observable<void> {
    const body: RegisterCustomerRequest = {
      email: request.email.trim(),
      password: request.password,
      ...(request.language ? { language: request.language } : {}),
    };

    const headers = turnstileToken
      ? new HttpHeaders().set('X-Turnstile-Token', turnstileToken)
      : new HttpHeaders();

    return this.csrf
      .initialize()
      .pipe(
        switchMap(() => this.http.post<void>(`${this.baseUrl}/auth/register`, body, { headers })),
      );
  }

  login(request: LoginRequest): Observable<CurrentUser | null> {
    const body = new HttpParams()
      .set('email', request.email.trim())
      .set('password', request.password);

    return this.csrf.initialize().pipe(
      switchMap(() =>
        this.http.post<void>(`${this.baseUrl}/auth/login`, body, { observe: 'response' }),
      ),
      switchMap((response) =>
        this.csrf
          .initialize()
          .pipe(switchMap(() => (response.status === 202 ? of(null) : this.getCurrentUser()))),
      ),
    );
  }

  logout(): Observable<void> {
    return this.csrf.initialize().pipe(
      switchMap(() => this.http.post<void>(`${this.baseUrl}/auth/logout`, null)),
      switchMap(() => this.csrf.initialize()),
    );
  }

  verifyEmail(token: string): Observable<void> {
    return this.csrf
      .initialize()
      .pipe(switchMap(() => this.http.post<void>(`${this.baseUrl}/auth/verify-email`, { token })));
  }

  resendVerification(email: string, language: 'pl' | 'en'): Observable<void> {
    return this.csrf.initialize().pipe(
      switchMap(() =>
        this.http.post<void>(`${this.baseUrl}/auth/resend-verification`, {
          email: email.trim(),
          language,
        }),
      ),
    );
  }

  requestPasswordReset(email: string, language: 'pl' | 'en'): Observable<void> {
    return this.csrf.initialize().pipe(
      switchMap(() =>
        this.http.post<void>(`${this.baseUrl}/auth/request-password-reset`, {
          email: email.trim(),
          language,
        }),
      ),
    );
  }

  resetPassword(token: string, password: string): Observable<void> {
    return this.csrf
      .initialize()
      .pipe(
        switchMap(() =>
          this.http.post<void>(`${this.baseUrl}/auth/reset-password`, { token, password }),
        ),
      );
  }
}
