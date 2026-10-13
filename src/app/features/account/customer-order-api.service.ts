import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import type { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/api/api-base-url';
import type { CustomerOrderDetails, CustomerOrderPage } from './customer-order.model';

@Injectable({
  providedIn: 'root',
})
export class CustomerOrderApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  getOrders(page = 0, size = 20): Observable<CustomerOrderPage> {
    const params = new HttpParams().set('page', page).set('size', size);

    return this.http.get<CustomerOrderPage>(`${this.baseUrl}/account/orders`, { params });
  }

  getOrder(id: string): Observable<CustomerOrderDetails> {
    return this.http.get<CustomerOrderDetails>(
      `${this.baseUrl}/account/orders/${encodeURIComponent(id)}`,
    );
  }
}
