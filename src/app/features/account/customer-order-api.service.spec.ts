import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../core/api/api-base-url';
import { CustomerOrderApiService } from './customer-order-api.service';
import type { CustomerOrderPage } from './customer-order.model';

describe('CustomerOrderApiService', () => {
  let service: CustomerOrderApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });

    service = TestBed.inject(CustomerOrderApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should request the first page with the default size', () => {
    const response: CustomerOrderPage = {
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    };

    let result: CustomerOrderPage | undefined;

    service.getOrders().subscribe((page) => {
      result = page;
    });

    const pending = http.expectOne('/api/account/orders?page=0&size=20');

    expect(pending.request.method).toBe('GET');
    pending.flush(response);

    expect(result).toEqual(response);
  });

  it('should request the selected page and size', () => {
    service.getOrders(2, 10).subscribe();

    const pending = http.expectOne('/api/account/orders?page=2&size=10');

    expect(pending.request.method).toBe('GET');
    pending.flush({
      items: [],
      page: 2,
      size: 10,
      totalElements: 0,
      totalPages: 0,
    });
  });

  it('should encode the order identifier in the details URL', () => {
    let result: unknown;

    service.getOrder('order/id?test').subscribe((order) => {
      result = order;
    });

    const pending = http.expectOne('/api/account/orders/order%2Fid%3Ftest');

    expect(pending.request.method).toBe('GET');
    pending.flush(null);

    expect(result).toBeNull();
  });

  it('should propagate an expired session error', () => {
    let receivedError: HttpErrorResponse | undefined;

    service.getOrders().subscribe({
      next: () => {
        throw new Error('Unauthorized request should not return orders');
      },
      error: (error: HttpErrorResponse) => {
        receivedError = error;
      },
    });

    http.expectOne('/api/account/orders?page=0&size=20').flush(null, {
      status: 401,
      statusText: 'Unauthorized',
    });

    expect(receivedError?.status).toBe(401);
  });

  it('should propagate a missing or inaccessible order error', () => {
    let receivedError: HttpErrorResponse | undefined;

    service.getOrder('missing-order').subscribe({
      next: () => {
        throw new Error('Missing order should not return details');
      },
      error: (error: HttpErrorResponse) => {
        receivedError = error;
      },
    });

    http.expectOne('/api/account/orders/missing-order').flush(null, {
      status: 404,
      statusText: 'Not Found',
    });

    expect(receivedError?.status).toBe(404);
  });
});
