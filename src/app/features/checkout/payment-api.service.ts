import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { switchMap, type Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/api/api-base-url';
import { CsrfService } from '../../core/auth/csrf.service';

export type PaymentStatus =
  | 'created'
  | 'pending'
  | 'succeeded'
  | 'failed'
  | 'expired';

export interface PaymentStatusResult {
  paymentAttemptId: string;
  status: PaymentStatus;
}

export interface CheckoutSessionResult extends PaymentStatusResult {
  checkoutUrl: string | null;
}

@Injectable({
  providedIn: 'root',
})
export class PaymentApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly csrf = inject(CsrfService);

  startCheckout(orderId: string, idempotencyKey: string): Observable<CheckoutSessionResult> {
    return this.csrf.initialize().pipe(
      switchMap(() =>
        this.http.post<CheckoutSessionResult>(
          `${this.baseUrl}/orders/${encodeURIComponent(orderId)}/payment-session`,
          null,
          {
            headers: {
              'Idempotency-Key': idempotencyKey,
            },
          },
        ),
      ),
    );
  }

  resumeCheckout(orderId: string, idempotencyKey: string): Observable<CheckoutSessionResult> {
    return this.csrf.initialize().pipe(
      switchMap(() =>
        this.http.post<CheckoutSessionResult>(
          `${this.baseUrl}/orders/${encodeURIComponent(orderId)}/payment-session/resume`,
          null,
          {
            headers: {
              'Idempotency-Key': idempotencyKey,
            },
          },
        ),
      ),
    );
  }

  getStatus(attemptId: string): Observable<PaymentStatusResult> {
    return this.http.get<PaymentStatusResult>(
      `${this.baseUrl}/payments/attempts/${encodeURIComponent(attemptId)}`,
    );
  }
}
