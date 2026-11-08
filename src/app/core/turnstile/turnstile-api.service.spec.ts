import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { TurnstileApiService } from './turnstile-api.service';

describe('TurnstileApiService', () => {
  let service: TurnstileApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(TurnstileApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should read disabled protection', async () => {
    const result = firstValueFrom(service.getConfiguration());

    http.expectOne('/api/security/turnstile').flush({
      enabled: false,
      siteKey: null,
    });

    expect(await result).toEqual({
      enabled: false,
      siteKey: null,
    });
  });

  it('should share the configuration request', async () => {
    const first = firstValueFrom(service.getConfiguration());
    const second = firstValueFrom(service.getConfiguration());

    http.expectOne('/api/security/turnstile').flush({
      enabled: true,
      siteKey: 'test-only-site-key',
    });

    const expected = {
      enabled: true,
      siteKey: 'test-only-site-key',
    };

    expect(await Promise.all([first, second])).toEqual([expected, expected]);
  });

  it.each([
    null,
    {},
    { enabled: 'false' },
    { enabled: true, siteKey: null },
    { enabled: true, siteKey: ' ' },
  ])('should reject invalid configuration: %j', async (response) => {
    const result = firstValueFrom(service.getConfiguration());
    const rejection = expect(result).rejects.toThrow();

    http.expectOne('/api/security/turnstile').flush(response);

    await rejection;
  });

  it('should allow retry after an HTTP failure', async () => {
    const first = firstValueFrom(service.getConfiguration());
    const rejection = expect(first).rejects.toMatchObject({
      status: 503,
    });

    http
      .expectOne('/api/security/turnstile')
      .flush({}, { status: 503, statusText: 'Service Unavailable' });

    await rejection;

    const retry = firstValueFrom(service.getConfiguration());

    http.expectOne('/api/security/turnstile').flush({
      enabled: false,
      siteKey: null,
    });

    expect(await retry).toEqual({
      enabled: false,
      siteKey: null,
    });
  });
});
