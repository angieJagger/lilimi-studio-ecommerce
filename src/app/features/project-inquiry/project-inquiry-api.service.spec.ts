import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../core/api/api-base-url';
import { ProjectInquiryApiService } from './project-inquiry-api.service';
import type { CreateInquiryRequest, CreateInquiryResponse } from './project-inquiry.model';

describe('ProjectInquiryApiService', () => {
  let service: ProjectInquiryApiService;
  let http: HttpTestingController;

  const request: CreateInquiryRequest = {
    language: 'pl',
    name: 'Anna Kowalska',
    email: 'anna@example.com',
    projectType: 'website',
    description: 'Potrzebuję strony dla swojej pracowni.',
    inspirationUrl: null,
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

    service = TestBed.inject(ProjectInquiryApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  function completeCsrfRequest(): void {
    const pending = http.expectOne('/api/auth/csrf');

    expect(pending.request.method).toBe('GET');

    pending.flush(null, {
      status: 204,
      statusText: 'No Content',
    });
  }

  it('should initialize CSRF before submitting and return confirmation', () => {
    const response: CreateInquiryResponse = {
      id: '1548c928-f278-4cbb-b8d0-d57455c72f91',
      createdAt: '2026-10-09T08:00:00Z',
      status: 'new',
    };

    let result: CreateInquiryResponse | undefined;

    service.createInquiry(request).subscribe((confirmation) => {
      result = confirmation;
    });

    http.expectNone('/api/project-inquiries');

    completeCsrfRequest();

    const pending = http.expectOne('/api/project-inquiries');

    expect(pending.request.method).toBe('POST');
    expect(pending.request.body).toEqual(request);

    pending.flush(response, {
      status: 201,
      statusText: 'Created',
    });

    expect(result).toEqual(response);
  });

  it('should propagate an unavailable product error without retrying', () => {
    let receivedError: HttpErrorResponse | undefined;

    service.createInquiry(request).subscribe({
      next: () => {
        throw new Error('Rejected inquiry should not return confirmation');
      },
      error: (error: HttpErrorResponse) => {
        receivedError = error;
      },
    });

    completeCsrfRequest();

    http
      .expectOne('/api/project-inquiries')
      .flush({ code: 'INQUIRY_PRODUCT_UNAVAILABLE' }, { status: 409, statusText: 'Conflict' });

    expect(receivedError?.status).toBe(409);
    expect(receivedError?.error.code).toBe('INQUIRY_PRODUCT_UNAVAILABLE');

    http.expectNone('/api/project-inquiries');
  });

  it('should not submit the inquiry when CSRF initialization fails', () => {
    let receivedError: HttpErrorResponse | undefined;

    service.createInquiry(request).subscribe({
      next: () => {
        throw new Error('Failed CSRF initialization should prevent submission');
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
    http.expectNone('/api/project-inquiries');
  });
});
