import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import type { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-base-url';
import type { AdminOrderDetails, AdminOrderPage } from './admin-order.model';

@Injectable({
  providedIn: 'root',
})
export class AdminOrderApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  getOrders(page = 0, size = 20): Observable<AdminOrderPage> {
    const params = new HttpParams().set('page', page).set('size', size);

    return this.http.get<AdminOrderPage>(`${this.baseUrl}/admin/orders`, { params });
  }
  getOrder(id: string): Observable<AdminOrderDetails> {
    return this.http.get<AdminOrderDetails>(
      `${this.baseUrl}/admin/orders/${encodeURIComponent(id)}`,
    );
  }
}
