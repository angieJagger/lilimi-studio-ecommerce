import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../../core/api/api-base-url';
import { AdminDigitalProductPackageApiService } from './admin-digital-product-package-api.service';
import type { AdminDigitalProductPackage } from './admin-digital-product-package.model';

describe('AdminDigitalProductPackageApiService', () => {
  let service: AdminDigitalProductPackageApiService;
  let http: HttpTestingController;

  const productId = 'pattern-001';
  const url = `/api/admin/products/${productId}/digital-packages`;

  const savedPackage: AdminDigitalProductPackage = {
    id: '00000000-0000-0000-0000-000000000001',
    productId,
    packageVersion: 1,
    originalFilename: 'forest-dragon.zip',
    byteSize: 1024,
    sha256: 'a'.repeat(64),
    currentPackage: true,
    createdAt: '2026-10-29T10:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });

    service = TestBed.inject(AdminDigitalProductPackageApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should load package history without initializing CSRF', () => {
    let result: readonly AdminDigitalProductPackage[] | undefined;

    service.getPackages(productId).subscribe((response) => {
      result = response;
    });

    const pending = http.expectOne(url);

    expect(pending.request.method).toBe('GET');
    http.expectNone('/api/auth/csrf');

    pending.flush([savedPackage]);

    expect(result).toEqual([savedPackage]);
  });

  it('should encode the product identifier', () => {
    service.getPackages('product/name').subscribe();

    http.expectOne('/api/admin/products/product%2Fname/digital-packages').flush([]);
  });

  it('should initialize CSRF before uploading the ZIP file', () => {
    const file = new File(['test ZIP content'], 'forest-dragon.zip', {
      type: 'application/zip',
    });
    let result: AdminDigitalProductPackage | undefined;

    service.upload(productId, file).subscribe((response) => {
      result = response;
    });

    http.expectNone(url);

    const csrf = http.expectOne('/api/auth/csrf');
    expect(csrf.request.method).toBe('GET');
    csrf.flush(null);

    const pending = http.expectOne(url);

    expect(pending.request.method).toBe('POST');
    expect(pending.request.headers.has('Content-Type')).toBe(false);
    expect(pending.request.body).toBeInstanceOf(FormData);

    const body = pending.request.body as FormData;

    expect(body.get('file')).toBe(file);
    expect(Array.from(body.keys())).toEqual(['file']);

    pending.flush(savedPackage);

    expect(result).toEqual(savedPackage);
  });

  it('should not upload when CSRF initialization fails', () => {
    const file = new File(['content'], 'package.zip');
    let receivedError: HttpErrorResponse | undefined;

    service.upload(productId, file).subscribe({
      error: (error: HttpErrorResponse) => {
        receivedError = error;
      },
    });

    http.expectOne('/api/auth/csrf').flush(null, {
      status: 403,
      statusText: 'Forbidden',
    });

    http.expectNone(url);
    expect(receivedError?.status).toBe(403);
  });

  it('should preserve package validation errors from the backend', () => {
    const file = new File(['invalid content'], 'package.zip');
    let receivedError: HttpErrorResponse | undefined;

    service.upload(productId, file).subscribe({
      error: (error: HttpErrorResponse) => {
        receivedError = error;
      },
    });

    http.expectOne('/api/auth/csrf').flush(null);

    http
      .expectOne(url)
      .flush({ code: 'DIGITAL_PACKAGE_INVALID' }, { status: 400, statusText: 'Bad Request' });

    expect(receivedError?.status).toBe(400);
    expect(receivedError?.error.code).toBe('DIGITAL_PACKAGE_INVALID');
  });
});
