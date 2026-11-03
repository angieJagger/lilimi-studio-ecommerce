import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../core/api/api-base-url';
import { DigitalDownloadLinkApiService } from './digital-download-link-api.service';
import type { DigitalOrderDownload } from './digital-order-download.model';

describe('DigitalDownloadLinkApiService', () => {
  let service: DigitalDownloadLinkApiService;
  let http: HttpTestingController;

  const token = 'a'.repeat(43);
  const itemId = '97c40491-b22a-4362-a347-87389785bfd5';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });

    service = TestBed.inject(DigitalDownloadLinkApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should request files with the token in a header', () => {
    const files: readonly DigitalOrderDownload[] = [
      {
        itemId,
        productName: 'Leśny smok',
        filename: 'dragon.zip',
        packageVersion: 1,
        byteSize: 2048,
      },
    ];

    let received: readonly DigitalOrderDownload[] | undefined;

    service.getDownloads(token).subscribe((result) => {
      received = result;
    });

    const request = http.expectOne('/api/digital-downloads');

    expect(request.request.method).toBe('GET');
    expect(request.request.headers.get('X-Digital-Download-Token')).toBe(token);
    expect(request.request.urlWithParams).not.toContain(token);

    http.expectNone('/api/auth/csrf');

    request.flush(files);

    expect(received).toEqual(files);
  });

  it('should download a ZIP as a blob and preserve response headers', () => {
    const zip = new Blob(['test ZIP content'], { type: 'application/zip' });

    let receivedBody: Blob | null | undefined;
    let disposition: string | null | undefined;

    service.download(token, itemId).subscribe((response) => {
      receivedBody = response.body;
      disposition = response.headers.get('Content-Disposition');
    });

    const request = http.expectOne(`/api/digital-downloads/items/${itemId}/download`);

    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');
    expect(request.request.headers.get('X-Digital-Download-Token')).toBe(token);
    expect(request.request.urlWithParams).not.toContain(token);

    request.flush(zip, {
      headers: {
        'Content-Disposition': 'attachment; filename="dragon.zip"',
      },
    });

    expect(receivedBody).toBe(zip);
    expect(disposition).toBe('attachment; filename="dragon.zip"');
  });

  it('should encode the item identifier as one path segment', () => {
    service.download(token, 'item/with space').subscribe();

    const request = http.expectOne('/api/digital-downloads/items/item%2Fwith%20space/download');

    request.flush(new Blob());
  });

  it('should pass an invalid-link response to the caller', () => {
    let receivedStatus: number | undefined;

    service.getDownloads(token).subscribe({
      next: () => {
        throw new Error('Expected the request to fail');
      },
      error: (error) => {
        receivedStatus = error.status;
      },
    });

    http.expectOne('/api/digital-downloads').flush(
      {},
      {
        status: 404,
        statusText: 'Not Found',
      },
    );

    expect(receivedStatus).toBe(404);
  });

  it('should initialize CSRF before requesting a new download link', () => {
    const orderId = '93af42f1-d557-4b3d-b452-24976e049713';
    const email = 'customer@example.com';
    const resendUrl = '/api/digital-downloads/resend';

    let completed = false;

    service.resend(orderId, email).subscribe({
      complete: () => {
        completed = true;
      },
    });

    http.expectNone(resendUrl);

    const csrf = http.expectOne('/api/auth/csrf');

    expect(csrf.request.method).toBe('GET');

    csrf.flush(null, {
      status: 204,
      statusText: 'No Content',
    });

    const request = http.expectOne(resendUrl);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ orderId, email });
    expect(request.request.headers.has('X-Digital-Download-Token')).toBe(false);

    request.flush(null, {
      status: 202,
      statusText: 'Accepted',
    });

    expect(completed).toBe(true);
  });

  it('should not request a new link when CSRF initialization fails', () => {
    let receivedStatus: number | undefined;

    service.resend('93af42f1-d557-4b3d-b452-24976e049713', 'customer@example.com').subscribe({
      next: () => {
        throw new Error('Expected CSRF initialization to fail');
      },
      error: (error) => {
        receivedStatus = error.status;
      },
    });

    http.expectOne('/api/auth/csrf').flush(
      {},
      {
        status: 503,
        statusText: 'Service Unavailable',
      },
    );

    http.expectNone('/api/digital-downloads/resend');
    expect(receivedStatus).toBe(503);
  });
});
