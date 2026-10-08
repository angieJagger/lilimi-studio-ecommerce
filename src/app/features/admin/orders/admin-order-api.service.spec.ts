import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../../core/api/api-base-url';
import { AdminOrderApiService } from './admin-order-api.service';
import type { AdminOrderDetails, AdminOrderPage } from './admin-order.model';

describe('AdminOrderApiService', () => {
  let service: AdminOrderApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });

    service = TestBed.inject(AdminOrderApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  const updatedOrder: AdminOrderDetails = {
    id: 'e6c91c08-bf6b-4b4a-93a4-97b90a62b055',
    version: 1,
    createdAt: '2026-10-08T08:00:00Z',
    status: 'processing',
    language: 'pl',
    currency: 'PLN',
    contact: {
      fullName: 'Anna Kowalska',
      email: 'anna@example.com',
      phone: null,
    },
    delivery: {
      kind: 'digital',
      methodId: null,
      addressLine1: null,
      addressLine2: null,
      postalCode: null,
      city: null,
      countryCode: null,
    },
    items: [
      {
        kind: 'digital',
        productId: 'pattern-001',
        productName: 'Wzór testowy',
        patternId: null,
        patternName: null,
        fit: null,
        size: null,
        color: null,
        embroideryOptionId: null,
        quantity: 1,
        unitPriceInGrosz: 2900,
        lineTotalInGrosz: 2900,
      },
    ],
    subtotalInGrosz: 2900,
    deliveryPriceInGrosz: 0,
    totalInGrosz: 2900,
  };

  it('should request the first page with the default size', () => {
    const response: AdminOrderPage = {
      items: [
        {
          id: 'e6c91c08-bf6b-4b4a-93a4-97b90a62b055',
          createdAt: '2026-10-08T08:00:00Z',
          status: 'new',
          customerFullName: 'Anna Kowalska',
          customerEmail: 'anna@example.com',
          currency: 'PLN',
          totalInGrosz: 2900,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };

    let result: AdminOrderPage | undefined;

    service.getOrders().subscribe((page) => {
      result = page;
    });

    const pending = http.expectOne('/api/admin/orders?page=0&size=20');

    expect(pending.request.method).toBe('GET');

    pending.flush(response);

    expect(result).toEqual(response);
  });

  it('should request the selected page and size', () => {
    service.getOrders(2, 10).subscribe();

    const pending = http.expectOne('/api/admin/orders?page=2&size=10');

    expect(pending.request.method).toBe('GET');

    pending.flush({
      items: [],
      page: 2,
      size: 10,
      totalElements: 0,
      totalPages: 0,
    });
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

    http.expectOne('/api/admin/orders?page=0&size=20').flush(null, {
      status: 401,
      statusText: 'Unauthorized',
    });

    expect(receivedError?.status).toBe(401);
  });

  it('should propagate an access denied error', () => {
    let receivedError: HttpErrorResponse | undefined;

    service.getOrders().subscribe({
      next: () => {
        throw new Error('Forbidden request should not return orders');
      },
      error: (error: HttpErrorResponse) => {
        receivedError = error;
      },
    });

    http.expectOne('/api/admin/orders?page=0&size=20').flush(null, {
      status: 403,
      statusText: 'Forbidden',
    });

    expect(receivedError?.status).toBe(403);
  });

  it('should initialize CSRF before changing status and return the updated order', () => {
    const url = `/api/admin/orders/${updatedOrder.id}/status`;
    const request = {
      status: 'processing' as const,
      expectedVersion: 0,
    };

    let result: AdminOrderDetails | undefined;

    service.changeStatus(updatedOrder.id, request).subscribe((order) => {
      result = order;
    });

    http.expectNone(url);

    const csrf = http.expectOne('/api/auth/csrf');

    expect(csrf.request.method).toBe('GET');

    csrf.flush(null, {
      status: 204,
      statusText: 'No Content',
    });

    const pending = http.expectOne(url);

    expect(pending.request.method).toBe('PATCH');
    expect(pending.request.body).toEqual(request);

    pending.flush(updatedOrder);

    expect(result).toEqual(updatedOrder);
    expect(result?.version).toBe(1);
  });

  it('should propagate a version conflict without retrying the update', () => {
    const url = `/api/admin/orders/${updatedOrder.id}/status`;

    let receivedError: HttpErrorResponse | undefined;

    service
      .changeStatus(updatedOrder.id, {
        status: 'processing',
        expectedVersion: 0,
      })
      .subscribe({
        next: () => {
          throw new Error('Conflicting update should not return an order');
        },
        error: (error: HttpErrorResponse) => {
          receivedError = error;
        },
      });

    http.expectOne('/api/auth/csrf').flush(null, {
      status: 204,
      statusText: 'No Content',
    });

    http
      .expectOne(url)
      .flush({ code: 'ORDER_VERSION_CONFLICT' }, { status: 409, statusText: 'Conflict' });

    expect(receivedError?.status).toBe(409);
    expect(receivedError?.error.code).toBe('ORDER_VERSION_CONFLICT');

    http.expectNone(url);
    http.expectNone('/api/auth/csrf');
  });

  it('should not change status when CSRF initialization fails', () => {
    const url = `/api/admin/orders/${updatedOrder.id}/status`;

    let receivedError: HttpErrorResponse | undefined;

    service
      .changeStatus(updatedOrder.id, {
        status: 'processing',
        expectedVersion: 0,
      })
      .subscribe({
        next: () => {
          throw new Error('Failed CSRF initialization should prevent the update');
        },
        error: (error: HttpErrorResponse) => {
          receivedError = error;
        },
      });

    http.expectOne('/api/auth/csrf').flush(null, {
      status: 503,
      statusText: 'Service Unavailable',
    });

    expect(receivedError?.status).toBe(503);
    http.expectNone(url);
  });
});
