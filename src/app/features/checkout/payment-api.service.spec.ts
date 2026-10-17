import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_BASE_URL } from '../../core/api/api-base-url';
import { PaymentApiService, type CheckoutSessionResult } from './payment-api.service';

describe('PaymentApiService', () => {
  let service: PaymentApiService;
  let http: HttpTestingController;

  const orderId = '21b640dc-a04a-40ba-99d8-3c0b25bbc258';
  const key = '4c0cba07-bd01-4295-95c4-e67d4b876f30';
  const url = `/api/orders/${orderId}/payment-session`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });

    service = TestBed.inject(PaymentApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should initialize CSRF before requesting a payment session', () => {
    const response: CheckoutSessionResult = {
      paymentAttemptId: '38e96ac8-89e7-40f1-a856-086992226bdf',
      status: 'pending',
      checkoutUrl: 'https://checkout.stripe.com/c/pay/test',
    };

    let received: CheckoutSessionResult | undefined;

    service.startCheckout(orderId, key).subscribe((result) => {
      received = result;
    });

    http.expectNone(url);

    const csrf = http.expectOne('/api/auth/csrf');
    expect(csrf.request.method).toBe('GET');

    csrf.flush(null, {
      status: 204,
      statusText: 'No Content',
    });

    const request = http.expectOne(url);

    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBe(key);
    expect(request.request.body).toBeNull();

    request.flush(response);

    expect(received).toEqual(response);
  });

  it('should pass a payment provider failure to the caller', () => {
    let receivedStatus: number | undefined;

    service.startCheckout(orderId, key).subscribe({
      next: () => {
        throw new Error('Expected payment request to fail');
      },
      error: (error) => {
        receivedStatus = error.status;
      },
    });

    http.expectOne('/api/auth/csrf').flush(null, {
      status: 204,
      statusText: 'No Content',
    });

    http.expectOne(url).flush(
      {},
      {
        status: 503,
        statusText: 'Service Unavailable',
      },
    );

    expect(receivedStatus).toBe(503);
  });

  it('should not request payment when CSRF initialization fails', () => {
    let receivedStatus: number | undefined;

    service.startCheckout(orderId, key).subscribe({
      next: () => {
        throw new Error('Expected CSRF initialization to fail');
      },
      error: (error) => {
        receivedStatus = error.status;
      },
    });

    http.expectOne('/api/auth/csrf').flush(
      {},
      {
        status: 503,
        statusText: 'Service Unavailable',
      },
    );

    expect(receivedStatus).toBe(503);
    http.expectNone(url);
  });

  it('should read payment status without initializing CSRF', () => {
    const attemptId = '38e96ac8-89e7-40f1-a856-086992226bdf';

    let receivedStatus: string | undefined;

    service.getStatus(attemptId).subscribe((result) => {
      receivedStatus = result.status;
    });

    http.expectNone('/api/auth/csrf');

    const request = http.expectOne(`/api/payments/attempts/${attemptId}`);

    expect(request.request.method).toBe('GET');

    request.flush({
      paymentAttemptId: attemptId,
      status: 'succeeded',
    });

    expect(receivedStatus).toBe('succeeded');
  });
});
