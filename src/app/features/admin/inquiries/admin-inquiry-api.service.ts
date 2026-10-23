import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { switchMap, type Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-base-url';
import { CsrfService } from '../../../core/auth/csrf.service';
import type {
  AdminInquiryDetails,
  AdminInquiryFilters,
  AdminInquiryPage,
  ChangeInquiryStatusRequest,
} from './admin-inquiry.model';

@Injectable({
  providedIn: 'root',
})
export class AdminInquiryApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly csrf = inject(CsrfService);

  getInquiries(
    page = 0,
    size = 20,
    filters: AdminInquiryFilters = {},
  ): Observable<AdminInquiryPage> {
    let params = new HttpParams().set('page', page).set('size', size);

    if (filters.view) {
      params = params.set('view', filters.view);
    }

    if (filters.status) {
      params = params.set('status', filters.status);
    }

    if (filters.projectType) {
      params = params.set('projectType', filters.projectType);
    }

    if (filters.dateFrom) {
      params = params.set('dateFrom', filters.dateFrom);
    }

    if (filters.dateTo) {
      params = params.set('dateTo', filters.dateTo);
    }

    if (filters.search?.trim()) {
      params = params.set('search', filters.search.trim());
    }

    return this.http.get<AdminInquiryPage>(`${this.baseUrl}/admin/project-inquiries`, { params });
  }

  getInquiry(id: string): Observable<AdminInquiryDetails> {
    return this.http.get<AdminInquiryDetails>(
      `${this.baseUrl}/admin/project-inquiries/${encodeURIComponent(id)}`,
    );
  }

  changeStatus(id: string, request: ChangeInquiryStatusRequest): Observable<AdminInquiryDetails> {
    return this.csrf
      .initialize()
      .pipe(
        switchMap(() =>
          this.http.patch<AdminInquiryDetails>(
            `${this.baseUrl}/admin/project-inquiries/${encodeURIComponent(id)}/status`,
            request,
          ),
        ),
      );
  }
}
