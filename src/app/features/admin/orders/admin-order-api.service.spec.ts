import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../../core/api/api-base-url';
import { AdminOrderApiService } from './admin-order-api.service';
import type { AdminOrderPage } from './admin-order.model';

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
});
