import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { switchMap, type Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-base-url';
import { CsrfService } from '../../../core/auth/csrf.service';
import type {
  AdminInquiryDetails,
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

  getInquiries(page = 0, size = 20): Observable<AdminInquiryPage> {
    const params = new HttpParams().set('page', page).set('size', size);

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
