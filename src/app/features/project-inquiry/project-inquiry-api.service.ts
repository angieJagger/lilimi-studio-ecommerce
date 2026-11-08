import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { switchMap, type Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/api/api-base-url';
import { CsrfService } from '../../core/auth/csrf.service';
import type { CreateInquiryRequest, CreateInquiryResponse } from './project-inquiry.model';

@Injectable({
  providedIn: 'root',
})
export class ProjectInquiryApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly csrf = inject(CsrfService);

  createInquiry(
    request: CreateInquiryRequest,
    files: readonly File[] = [],
    turnstileToken: string | null = null,
  ): Observable<CreateInquiryResponse> {
    let body: CreateInquiryRequest | FormData = request;

    if (files.length > 0) {
      const multipart = new FormData();

      multipart.append(
        'request',
        new Blob([JSON.stringify(request)], { type: 'application/json' }),
      );

      for (const file of files) {
        multipart.append('files', file, file.name);
      }

      body = multipart;
    }

    const headers = turnstileToken
    ? new HttpHeaders().set('X-Turnstile-Token', turnstileToken)
    : new HttpHeaders();

    return this.csrf
      .initialize()
      .pipe(
        switchMap(() =>
          this.http.post<CreateInquiryResponse>(`${this.baseUrl}/project-inquiries`, body, {
            headers,
          }),
        ),
      );
  }
}
