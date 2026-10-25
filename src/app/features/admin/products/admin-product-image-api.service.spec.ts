import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../../core/api/api-base-url';
import { AdminProductImageApiService } from './admin-product-image-api.service';
import type { AdminProductImages } from './admin-product-image.model';

describe('AdminProductImageApiService', () => {
  let service: AdminProductImageApiService;
  let http: HttpTestingController;

  const productId = 'pattern-001';
  const imageId = '00000000-0000-0000-0000-000000000001';
  const imagesUrl = `/api/admin/products/${productId}/images`;

  const gallery: AdminProductImages = {
    productId,
    version: 3,
    images: [],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });

    service = TestBed.inject(AdminProductImageApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should load the gallery without initializing CSRF', () => {
    let result: AdminProductImages | undefined;

    service.getImages(productId).subscribe((response) => {
      result = response;
    });

    const pending = http.expectOne(imagesUrl);

    expect(pending.request.method).toBe('GET');
    http.expectNone('/api/auth/csrf');

    pending.flush(gallery);

    expect(result).toEqual(gallery);
  });

  it('should encode product and image identifiers in URLs', () => {
    service.getImageContent('product/name', 'image/name').subscribe();

    const pending = http.expectOne(
      '/api/admin/products/product%2Fname/images/image%2Fname/content',
    );

    expect(pending.request.method).toBe('GET');
    expect(pending.request.responseType).toBe('blob');

    pending.flush(new Blob(['image'], { type: 'image/png' }));
  });

  it('should return image content as a Blob', () => {
    const image = new Blob(['image content'], { type: 'image/png' });
    let result: Blob | undefined;

    service.getImageContent(productId, imageId).subscribe((response) => {
      result = response;
    });

    const pending = http.expectOne(`${imagesUrl}/${imageId}/content`);

    expect(pending.request.responseType).toBe('blob');
    http.expectNone('/api/auth/csrf');

    pending.flush(image);

    expect(result).toBe(image);
  });

  it.each(['upload', 'replace'] as const)(
    'should initialize CSRF before %s and send JSON metadata with the file',
    async (operation) => {
      const file = new File(['image content'], 'photo.png', {
        type: 'image/png',
      });

      const metadata =
        operation === 'upload'
          ? {
              altPl: 'Haft kwiatowy',
              altEn: 'Floral embroidery',
              expectedVersion: 2,
            }
          : { expectedVersion: 2 };

      const url = operation === 'upload' ? imagesUrl : `${imagesUrl}/${imageId}/file`;

      const request =
        operation === 'upload'
          ? service.upload(productId, file, {
              altPl: 'Haft kwiatowy',
              altEn: 'Floral embroidery',
              expectedVersion: 2,
            })
          : service.replaceFile(productId, imageId, file, {
              expectedVersion: 2,
            });

      let result: AdminProductImages | undefined;

      request.subscribe((response) => {
        result = response;
      });

      http.expectNone(url);
      completeCsrf();

      const pending = http.expectOne(url);

      expect(pending.request.method).toBe('POST');
      expect(pending.request.headers.has('Content-Type')).toBe(false);

      const body = pending.request.body;

      expect(body).toBeInstanceOf(FormData);

      const uploadedFile = body.get('file') as File;
      const metadataPart = body.get('metadata') as Blob;

      expect(uploadedFile.name).toBe('photo.png');
      expect(uploadedFile.type).toBe('image/png');
      expect(uploadedFile.size).toBe(file.size);
      expect(metadataPart.type).toBe('application/json');

      pending.flush(gallery);

      expect(result).toEqual(gallery);
      expect(JSON.parse(await readBlob(metadataPart))).toEqual(metadata);
    },
  );

  it('should not upload a file when CSRF initialization fails', () => {
    const file = new File(['image'], 'photo.png');
    let receivedError: HttpErrorResponse | undefined;

    service
      .upload(productId, file, {
        altPl: '',
        altEn: '',
        expectedVersion: 2,
      })
      .subscribe({
        error: (error: HttpErrorResponse) => {
          receivedError = error;
        },
      });

    http.expectOne('/api/auth/csrf').flush(null, {
      status: 403,
      statusText: 'Forbidden',
    });

    expect(receivedError?.status).toBe(403);
    http.expectNone(imagesUrl);
  });

    it('should initialize CSRF before updating alternative text', () => {
      const request = {
        altPl: 'Nowy opis',
        altEn: 'Updated description',
        expectedVersion: 2,
      };
      const url = `${imagesUrl}/${imageId}/text`;

      service.updateText(productId, imageId, request).subscribe();

      http.expectNone(url);
      completeCsrf();

      const pending = http.expectOne(url);

      expect(pending.request.method).toBe('PATCH');
      expect(pending.request.body).toEqual(request);

      pending.flush(gallery);
    });

    it('should initialize CSRF before changing the primary image', () => {
      const request = { expectedVersion: 2 };
      const url = `${imagesUrl}/${imageId}/primary`;

      service.changePrimaryImage(productId, imageId, request).subscribe();

      http.expectNone(url);
      completeCsrf();

      const pending = http.expectOne(url);

      expect(pending.request.method).toBe('PATCH');
      expect(pending.request.body).toEqual(request);

      pending.flush(gallery);
    });

    it('should send the complete requested image order', () => {
      const request = {
        imageIds: ['second-image', 'first-image'],
        expectedVersion: 2,
      };
      const url = `${imagesUrl}/order`;

      service.reorder(productId, request).subscribe();

      http.expectNone(url);
      completeCsrf();

      const pending = http.expectOne(url);

      expect(pending.request.method).toBe('PATCH');
      expect(pending.request.body).toEqual(request);

      pending.flush(gallery);
    });

    it('should initialize CSRF before deleting an image with its expected version', () => {
      const url = `${imagesUrl}/${imageId}?expectedVersion=2`;

      service.delete(productId, imageId, 2).subscribe();

      http.expectNone(url);
      completeCsrf();

      const pending = http.expectOne(url);

      expect(pending.request.method).toBe('DELETE');
      expect(pending.request.params.get('expectedVersion')).toBe('2');
      expect(pending.request.body).toBeNull();

      pending.flush(gallery);
    });

    it('should propagate a version conflict without retrying the change', () => {
      const url = `${imagesUrl}/${imageId}/primary`;
      let receivedError: HttpErrorResponse | undefined;

      service
        .changePrimaryImage(productId, imageId, {
          expectedVersion: 2,
        })
        .subscribe({
          error: (error: HttpErrorResponse) => {
            receivedError = error;
          },
        });

      completeCsrf();

      http
        .expectOne(url)
        .flush({ code: 'PRODUCT_VERSION_CONFLICT' }, { status: 409, statusText: 'Conflict' });

      expect(receivedError?.status).toBe(409);
      expect(receivedError?.error.code).toBe('PRODUCT_VERSION_CONFLICT');

      http.expectNone(url);
      http.expectNone('/api/auth/csrf');
    });

  function completeCsrf(): void {
    const pending = http.expectOne('/api/auth/csrf');

    expect(pending.request.method).toBe('GET');

    pending.flush(null, {
      status: 204,
      statusText: 'No Content',
    });
  }

  function readBlob(blob: Blob): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();

      reader.onload = () => resolve(String(reader.result));
      reader.onerror = () => reject(reader.error ?? new Error('Could not read Blob'));

      reader.readAsText(blob);
    });
  }
});
