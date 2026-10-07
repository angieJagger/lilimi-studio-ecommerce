import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_BASE_URL } from '../../core/api/api-base-url';
import { OrderApiService } from './order-api.service';
import type { CreateOrderRequest, CreateOrderResponse } from './order.model';

describe('OrderApiService', () => {
  let service: OrderApiService;
  let http: HttpTestingController;

  const idempotencyKey = '7c82dd0f-9289-4a9e-bf58-05b415d6da48';
  const request: CreateOrderRequest = {
    language: 'pl',
    contact: {
      fullName: 'Test Customer',
      email: 'customer@example.com',
    },
    delivery: {
      kind: 'digital',
    },
    items: [
      {
        kind: 'digital',
        productId: 'pattern-001',
        quantity: 1,
      },
    ],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });

    service = TestBed.inject(OrderApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should post the order and return its confirmation', () => {
    const response: CreateOrderResponse = {
      id: '7c82dd0f-9289-4a9e-bf58-05b415d6da48',
      createdAt: '2026-10-05T12:00:00Z',
      status: 'new',
      currency: 'PLN',
      subtotalInGrosz: 2900,
      deliveryPriceInGrosz: 0,
      totalInGrosz: 2900,
    };

    let received: CreateOrderResponse | undefined;

    service.createOrder(request, idempotencyKey).subscribe((value) => {
      received = value;
    });

    const csrfRequest = http.expectOne('/api/auth/csrf');

    expect(csrfRequest.request.method).toBe('GET');
    http.expectNone('/api/orders');

    csrfRequest.flush(null, {
      status: 204,
      statusText: 'No Content',
    });
    const pending = http.expectOne('/api/orders');

    expect(pending.request.method).toBe('POST');
    expect(pending.request.headers.get('Idempotency-Key')).toBe(idempotencyKey);
    expect(pending.request.body).toEqual(request);

    pending.flush(response, {
      status: 201,
      statusText: 'Created',
    });

    expect(received).toEqual(response);
  });

  it('should pass an unavailable product error to the caller', () => {
    let receivedStatus: number | undefined;
    let receivedCode: string | undefined;

    service.createOrder(request, idempotencyKey).subscribe({
      next: () => {
        throw new Error('Expected the request to fail');
      },
      error: (error) => {
        receivedStatus = error.status;
        receivedCode = error.error.code;
      },
    });

    const csrfRequest = http.expectOne('/api/auth/csrf');

    expect(csrfRequest.request.method).toBe('GET');
    http.expectNone('/api/orders');

    csrfRequest.flush(null, {
      status: 204,
      statusText: 'No Content',
    });
    const pending = http.expectOne('/api/orders');

    pending.flush(
      {
        status: 409,
        code: 'ORDER_PRODUCT_UNAVAILABLE',
        productId: 'pattern-001',
      },
      {
        status: 409,
        statusText: 'Conflict',
      },
    );

    expect(receivedStatus).toBe(409);
    expect(receivedCode).toBe('ORDER_PRODUCT_UNAVAILABLE');
  });

  it('should not submit an order when CSRF initialization fails', () => {
    let receivedStatus: number | undefined;

    service.createOrder(request, idempotencyKey).subscribe({
      next: () => {
        throw new Error('Expected CSRF initialization to fail');
      },
      error: (error) => {
        receivedStatus = error.status;
      },
    });

    const csrfRequest = http.expectOne('/api/auth/csrf');

    csrfRequest.flush(
      {},
      {
        status: 503,
        statusText: 'Service Unavailable',
      },
    );

    expect(receivedStatus).toBe(503);
    http.expectNone('/api/orders');
  });

  
});
