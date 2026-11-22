import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, switchMap, type Observable } from 'rxjs';
import { API_BASE_URL } from '../api/api-base-url';
import { CsrfService } from './csrf.service';

export interface AdminMfaStatus {
  readonly enabled: boolean;
  readonly verified: boolean;
}

export interface AdminMfaSetup {
  readonly secret: string;
}

export interface AdminMfaActivation {
  readonly recoveryCodes: readonly string[];
}

@Injectable({
  providedIn: 'root',
})
export class AdminMfaApiService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);
  private readonly baseUrl = inject(API_BASE_URL);

  getStatus(): Observable<AdminMfaStatus> {
    return this.http.get<AdminMfaStatus>(
      `${this.baseUrl}/auth/mfa/status`,
      { transferCache: false },
    );
  }

  beginSetup(): Observable<AdminMfaSetup> {
    return this.csrf.initialize().pipe(
      switchMap(() =>
        this.http.post<AdminMfaSetup>(
          `${this.baseUrl}/auth/mfa/setup`,
          null,
        ),
      ),
    );
  }

  activate(code: string): Observable<AdminMfaActivation> {
    return this.csrf.initialize().pipe(
      switchMap(() =>
        this.http.post<AdminMfaActivation>(
          `${this.baseUrl}/auth/mfa/activate`,
          { code },
        ),
      ),
      switchMap((result) =>
        this.csrf.initialize().pipe(map(() => result)),
      ),
    );
  }

  verify(code: string): Observable<void> {
    return this.csrf.initialize().pipe(
      switchMap(() =>
        this.http.post<void>(
          `${this.baseUrl}/auth/mfa/verify`,
          { code },
        ),
      ),
      switchMap(() => this.csrf.initialize()),
    );
  }

  recover(code: string): Observable<void> {
    return this.csrf.initialize().pipe(
      switchMap(() =>
        this.http.post<void>(
          `${this.baseUrl}/auth/mfa/recovery`,
          { code },
        ),
      ),
      switchMap(() => this.csrf.initialize()),
    );
  }
}
