import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import type { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/api/api-base-url';
import type { CreateOrderRequest, CreateOrderResponse } from './order.model';

import { switchMap } from 'rxjs';
import { CsrfService } from '../../core/auth/csrf.service';

@Injectable({
  providedIn: 'root',
})
export class OrderApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly csrf = inject(CsrfService);

  createOrder(
    request: CreateOrderRequest,
    idempotencyKey: string,
  ): Observable<CreateOrderResponse> {
    return this.csrf.initialize().pipe(
      switchMap(() =>
        this.http.post<CreateOrderResponse>(`${this.baseUrl}/orders`, request, {
          headers: {
            'Idempotency-Key': idempotencyKey,
          },
        }),
      ),
    );
  }
}
