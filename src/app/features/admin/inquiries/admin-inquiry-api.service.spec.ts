import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../../core/api/api-base-url';
import { AdminInquiryApiService } from './admin-inquiry-api.service';
import type { AdminInquiryDetails, AdminInquiryPage } from './admin-inquiry.model';

describe('AdminInquiryApiService', () => {
  let service: AdminInquiryApiService;
  let http: HttpTestingController;

  const inquiry: AdminInquiryDetails = {
    id: '1548c928-f278-4cbb-b8d0-d57455c72f91',
    createdAt: '2026-10-09T08:00:00Z',
    status: 'new',
    language: 'pl',
    name: 'Anna Kowalska',
    email: 'anna@example.com',
    projectType: 'website',
    description: 'Potrzebuję strony dla swojej pracowni.',
    inspirationUrl: 'https://example.com/inspiration',
    productId: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });

    service = TestBed.inject(AdminInquiryApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should request the first page with the default size', () => {
    const response: AdminInquiryPage = {
      items: [
        {
          id: inquiry.id,
          createdAt: inquiry.createdAt,
          status: inquiry.status,
          name: inquiry.name,
          email: inquiry.email,
          projectType: inquiry.projectType,
          productId: inquiry.productId,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };

    let result: AdminInquiryPage | undefined;

    service.getInquiries().subscribe((page) => {
      result = page;
    });

    const pending = http.expectOne('/api/admin/project-inquiries?page=0&size=20');

    expect(pending.request.method).toBe('GET');

    pending.flush(response);

    expect(result).toEqual(response);
  });

  it('should request the selected page and size', () => {
    service.getInquiries(2, 10).subscribe();

    const pending = http.expectOne('/api/admin/project-inquiries?page=2&size=10');

    expect(pending.request.method).toBe('GET');

    pending.flush({
      items: [],
      page: 2,
      size: 10,
      totalElements: 0,
      totalPages: 0,
    });
  });

  it('should request inquiry details by id', () => {
    let result: AdminInquiryDetails | undefined;

    service.getInquiry(inquiry.id).subscribe((details) => {
      result = details;
    });

    const pending = http.expectOne(`/api/admin/project-inquiries/${inquiry.id}`);

    expect(pending.request.method).toBe('GET');

    pending.flush(inquiry);

    expect(result).toEqual(inquiry);
  });

  it('should propagate an expired session error', () => {
    let receivedError: HttpErrorResponse | undefined;

    service.getInquiries().subscribe({
      next: () => {
        throw new Error('Unauthorized request should not return inquiries');
      },
      error: (error: HttpErrorResponse) => {
        receivedError = error;
      },
    });

    http
      .expectOne('/api/admin/project-inquiries?page=0&size=20')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(receivedError?.status).toBe(401);
  });

  it('should propagate a missing inquiry error', () => {
    let receivedError: HttpErrorResponse | undefined;

    service.getInquiry(inquiry.id).subscribe({
      next: () => {
        throw new Error('Missing inquiry should not return details');
      },
      error: (error: HttpErrorResponse) => {
        receivedError = error;
      },
    });

    http
      .expectOne(`/api/admin/project-inquiries/${inquiry.id}`)
      .flush(null, { status: 404, statusText: 'Not Found' });

    expect(receivedError?.status).toBe(404);
  });
});
