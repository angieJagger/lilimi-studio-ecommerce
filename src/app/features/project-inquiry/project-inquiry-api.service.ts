import { HttpClient } from '@angular/common/http';
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

  createInquiry(request: CreateInquiryRequest): Observable<CreateInquiryResponse> {
    return this.csrf
      .initialize()
      .pipe(
        switchMap(() =>
          this.http.post<CreateInquiryResponse>(`${this.baseUrl}/project-inquiries`, request),
        ),
      );
  }
}
