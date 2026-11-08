import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, shareReplay, type Observable } from 'rxjs';
import { API_BASE_URL } from '../api/api-base-url';

export interface TurnstileConfiguration {
  enabled: boolean;
  siteKey: string | null;
}

@Injectable({
  providedIn: 'root',
})
export class TurnstileApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  private readonly configuration$ = this.http
    .get<unknown>(`${this.baseUrl}/security/turnstile`)
    .pipe(
      map((response) => this.readConfiguration(response)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );

  getConfiguration(): Observable<TurnstileConfiguration> {
    return this.configuration$;
  }

  private readConfiguration(value: unknown): TurnstileConfiguration {
    if (
      typeof value !== 'object' ||
      value === null ||
      !('enabled' in value) ||
      typeof value.enabled !== 'boolean'
    ) {
      throw new Error('Invalid Turnstile configuration');
    }

    if (!value.enabled) {
      return { enabled: false, siteKey: null };
    }

    if (!('siteKey' in value) || typeof value.siteKey !== 'string' || value.siteKey.trim() === '') {
      throw new Error('Missing Turnstile site key');
    }

    return {
      enabled: true,
      siteKey: value.siteKey.trim(),
    };
  }
}
