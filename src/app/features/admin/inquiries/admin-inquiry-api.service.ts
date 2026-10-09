import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import type { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-base-url';
import type {
  AdminInquiryDetails,
  AdminInquiryPage,
} from './admin-inquiry.model';

@Injectable({
  providedIn: 'root',
})
export class AdminInquiryApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  getInquiries(page = 0, size = 20): Observable<AdminInquiryPage> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size);

    return this.http.get<AdminInquiryPage>(
      `${this.baseUrl}/admin/project-inquiries`,
      { params },
    );
  }

  getInquiry(id: string): Observable<AdminInquiryDetails> {
    return this.http.get<AdminInquiryDetails>(
      `${this.baseUrl}/admin/project-inquiries/${encodeURIComponent(id)}`,
    );
  }
}
