import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../core/api/api-base-url';
import { DigitalOrderDownloadApiService } from './digital-order-download-api.service';
import type { DigitalOrderDownload } from './digital-order-download.model';

describe('DigitalOrderDownloadApiService', () => {
  let service: DigitalOrderDownloadApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });

    service = TestBed.inject(DigitalOrderDownloadApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should load purchased file metadata', () => {
    const downloads: readonly DigitalOrderDownload[] = [
      {
        itemId: 'item-1',
        productName: 'Leśny smok',
        filename: 'dragon.zip',
        packageVersion: 2,
        byteSize: 1024,
      },
    ];

    let result: readonly DigitalOrderDownload[] | undefined;

    service.getDownloads('order-1').subscribe((response) => {
      result = response;
    });

    const pending = http.expectOne('/api/orders/order-1/downloads');

    expect(pending.request.method).toBe('GET');
    http.expectNone('/api/auth/csrf');

    pending.flush(downloads);

    expect(result).toEqual(downloads);
  });

  it('should encode the order identifier when loading metadata', () => {
    service.getDownloads('order/name').subscribe();

    http.expectOne('/api/orders/order%2Fname/downloads').flush([]);
  });

  it('should build an encoded download URL without fetching the file', () => {
    expect(service.downloadUrl('order/name', 'item/name')).toBe(
      '/api/orders/order%2Fname/items/item%2Fname/download',
    );

    http.expectNone(() => true);
  });

  it('should preserve access errors from the backend', () => {
    let receivedError: HttpErrorResponse | undefined;

    service.getDownloads('order-1').subscribe({
      error: (error: HttpErrorResponse) => {
        receivedError = error;
      },
    });

    http.expectOne('/api/orders/order-1/downloads').flush(null, {
      status: 404,
      statusText: 'Not Found',
    });

    expect(receivedError?.status).toBe(404);
  });
});
