import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { switchMap, type Observable } from 'rxjs';
import { API_BASE_URL } from '../api/api-base-url';
import type { CurrentUser, LoginRequest } from './auth.model';
import { CsrfService } from './csrf.service';

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

  login(request: LoginRequest): Observable<CurrentUser> {
    const body = new HttpParams()
      .set('email', request.email.trim())
      .set('password', request.password);

    return this.csrf.initialize().pipe(
      switchMap(() => this.http.post<void>(`${this.baseUrl}/auth/login`, body)),
      switchMap(() => this.csrf.initialize()),
      switchMap(() => this.getCurrentUser()),
    );
  }

  logout(): Observable<void> {
    return this.csrf.initialize().pipe(
      switchMap(() => this.http.post<void>(`${this.baseUrl}/auth/logout`, null)),
      switchMap(() => this.csrf.initialize()),
    );
  }
}
