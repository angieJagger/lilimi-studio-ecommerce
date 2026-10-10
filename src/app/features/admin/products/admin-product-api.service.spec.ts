import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../../core/api/api-base-url';
import { AdminProductApiService } from './admin-product-api.service';
import type { AdminProductPage, AdminProductSummary, AdminProductDetails,
UpdateProductTranslationsRequest, } from './admin-product.model';

describe('AdminProductApiService', () => {
  let service: AdminProductApiService;
  let http: HttpTestingController;

  const page: AdminProductPage = {
    items: [
      {
        id: 'pattern-001',
        version: 0,
        slug: 'forest-dragon',
        productType: 'digital',
        active: false,
        madeToOrder: false,
        personalizationAvailable: false,
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  };

  const details: AdminProductDetails = {
    ...page.items[0],
    translations: [
      {
        language: 'en',
        name: 'Forest dragon',
        description: 'A digital embroidery pattern.',
      },
      {
        language: 'pl',
        name: 'Leśny smok',
        description: 'Cyfrowy wzór haftu.',
      },
    ],
  };

  const translationRequest: UpdateProductTranslationsRequest = {
    pl: {
      name: 'Nowa polska nazwa',
      description: 'Nowy polski opis.',
    },
    en: {
      name: 'Updated English name',
      description: 'Updated English description.',
    },
    expectedVersion: details.version,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });

    service = TestBed.inject(AdminProductApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should request the default page and return inactive products', () => {
    let result: AdminProductPage | undefined;

    service.getProducts().subscribe((response) => {
      result = response;
    });

    const pending = http.expectOne('/api/admin/products?page=0&size=20');

    expect(pending.request.method).toBe('GET');

    pending.flush(page);

    expect(result).toEqual(page);
    expect(result?.items[0].active).toBe(false);
  });

  it('should request the selected page and size', () => {
    service.getProducts(2, 10).subscribe();

    const pending = http.expectOne('/api/admin/products?page=2&size=10');

    expect(pending.request.method).toBe('GET');

    pending.flush({
      ...page,
      page: 2,
      size: 10,
    });
  });

  it.each([401, 403, 503])('should propagate HTTP %s without retrying automatically', (status) => {
    let receivedError: HttpErrorResponse | undefined;

    service.getProducts().subscribe({
      error: (error: HttpErrorResponse) => {
        receivedError = error;
      },
    });

    const url = '/api/admin/products?page=0&size=20';

    http.expectOne(url).flush(null, {
      status,
      statusText: 'Request failed',
    });

    expect(receivedError?.status).toBe(status);

    http.expectNone(url);
  });

    it('should initialize CSRF before changing product visibility', () => {
      const product = page.items[0];
      const request = {
        active: true,
        expectedVersion: product.version,
      };

      const updated: AdminProductSummary = {
        ...product,
        active: true,
        version: product.version + 1,
      };

      let result: AdminProductSummary | undefined;

      service.changeVisibility(product.id, request).subscribe((response) => {
        result = response;
      });

      const url = `/api/admin/products/${product.id}/visibility`;
      const csrfRequest = http.expectOne('/api/auth/csrf');

      expect(csrfRequest.request.method).toBe('GET');
      http.expectNone(url);

      csrfRequest.flush(null, {
        status: 204,
        statusText: 'No Content',
      });

      const pending = http.expectOne(url);

      expect(pending.request.method).toBe('PATCH');
      expect(pending.request.body).toEqual(request);

      pending.flush(updated);

      expect(result).toEqual(updated);
    });

    it('should propagate a version conflict without retrying', () => {
      const product = page.items[0];
      let receivedError: HttpErrorResponse | undefined;

      service
        .changeVisibility(product.id, {
          active: true,
          expectedVersion: product.version,
        })
        .subscribe({
          error: (error: HttpErrorResponse) => {
            receivedError = error;
          },
        });

      http.expectOne('/api/auth/csrf').flush(null, {
        status: 204,
        statusText: 'No Content',
      });

      const url = `/api/admin/products/${product.id}/visibility`;

      http
        .expectOne(url)
        .flush({ code: 'PRODUCT_VERSION_CONFLICT' }, { status: 409, statusText: 'Conflict' });

      expect(receivedError?.status).toBe(409);
      expect(receivedError?.error.code).toBe('PRODUCT_VERSION_CONFLICT');

      http.expectNone(url);
      http.expectNone('/api/auth/csrf');
    });

    it('should not update visibility when CSRF initialization fails', () => {
      const product = page.items[0];
      let receivedError: HttpErrorResponse | undefined;

      service
        .changeVisibility(product.id, {
          active: true,
          expectedVersion: product.version,
        })
        .subscribe({
          error: (error: HttpErrorResponse) => {
            receivedError = error;
          },
        });

      http.expectOne('/api/auth/csrf').flush(null, {
        status: 503,
        statusText: 'Service Unavailable',
      });

      expect(receivedError?.status).toBe(503);

      http.expectNone(`/api/admin/products/${product.id}/visibility`);
    });
    it('should request product details with both translations', () => {
      let result: AdminProductDetails | undefined;

      service.getProduct(details.id).subscribe((response) => {
        result = response;
      });

      const pending = http.expectOne(`/api/admin/products/${details.id}`);

      expect(pending.request.method).toBe('GET');

      pending.flush(details);

      expect(result).toEqual(details);
    });

    it('should initialize CSRF before saving both translations', () => {
      const updated: AdminProductDetails = {
        ...details,
        version: details.version + 1,
        translations: [
          { language: 'en', ...translationRequest.en },
          { language: 'pl', ...translationRequest.pl },
        ],
      };

      let result: AdminProductDetails | undefined;

      service.updateTranslations(details.id, translationRequest).subscribe((response) => {
        result = response;
      });

      const url = `/api/admin/products/${details.id}/translations`;
      const csrfRequest = http.expectOne('/api/auth/csrf');

      expect(csrfRequest.request.method).toBe('GET');
      http.expectNone(url);

      csrfRequest.flush(null, {
        status: 204,
        statusText: 'No Content',
      });

      const pending = http.expectOne(url);

      expect(pending.request.method).toBe('PATCH');
      expect(pending.request.body).toEqual(translationRequest);

      pending.flush(updated);

      expect(result).toEqual(updated);
    });

    it('should propagate a translation version conflict without retrying', () => {
      let receivedError: HttpErrorResponse | undefined;

      service.updateTranslations(details.id, translationRequest).subscribe({
        error: (error: HttpErrorResponse) => {
          receivedError = error;
        },
      });

      http.expectOne('/api/auth/csrf').flush(null, {
        status: 204,
        statusText: 'No Content',
      });

      const url = `/api/admin/products/${details.id}/translations`;

      http
        .expectOne(url)
        .flush({ code: 'PRODUCT_VERSION_CONFLICT' }, { status: 409, statusText: 'Conflict' });

      expect(receivedError?.status).toBe(409);
      expect(receivedError?.error.code).toBe('PRODUCT_VERSION_CONFLICT');

      http.expectNone(url);
    });

    it('should not save translations when CSRF initialization fails', () => {
      let receivedError: HttpErrorResponse | undefined;

      service.updateTranslations(details.id, translationRequest).subscribe({
        error: (error: HttpErrorResponse) => {
          receivedError = error;
        },
      });

      http.expectOne('/api/auth/csrf').flush(null, {
        status: 503,
        statusText: 'Service Unavailable',
      });

      expect(receivedError?.status).toBe(503);

      http.expectNone(`/api/admin/products/${details.id}/translations`);
    });
});
