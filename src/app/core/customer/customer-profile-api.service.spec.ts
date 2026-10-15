import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../api/api-base-url';
import { CustomerProfileApiService } from './customer-profile-api.service';
import type { CustomerProfile, UpdateCustomerProfileRequest } from './customer-profile.model';

describe('CustomerProfileApiService', () => {
  let service: CustomerProfileApiService;
  let http: HttpTestingController;
  const profile: CustomerProfile = {
    email: 'anna@example.com', firstName: 'Anna', lastName: null,
    phone: null, addressLine1: null, addressLine2: null,
    postalCode: null, city: null, countryCode: null, version: 0,
  };
  const request: UpdateCustomerProfileRequest = {
    firstName: 'Anna', lastName: null, phone: null,
    addressLine1: null, addressLine2: null, postalCode: null,
    city: null, countryCode: null, version: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    service = TestBed.inject(CustomerProfileApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the profile without an unnecessary CSRF request', () => {
    let result: CustomerProfile | undefined;
    service.getProfile().subscribe(value => result = value);
    const pending = http.expectOne('/api/account/profile');
    expect(pending.request.method).toBe('GET');
    http.expectNone('/api/auth/csrf');
    pending.flush(profile);
    expect(result).toEqual(profile);
  });

  it('initializes CSRF before updating and returns the saved profile', () => {
    let result: CustomerProfile | undefined;
    service.updateProfile(request).subscribe(value => result = value);
    http.expectNone('/api/account/profile');
    const csrf = http.expectOne('/api/auth/csrf');
    expect(csrf.request.method).toBe('GET');
    csrf.flush(null, { status: 204, statusText: 'No Content' });

    const pending = http.expectOne('/api/account/profile');
    expect(pending.request.method).toBe('PUT');
    expect(pending.request.body).toEqual(request);
    expect(pending.request.body).not.toHaveProperty('email');
    pending.flush(profile);
    expect(result).toEqual(profile);
  });

  it('does not update when CSRF initialization fails', () => {
    let received: HttpErrorResponse | undefined;
    service.updateProfile(request).subscribe({
      error: (error: HttpErrorResponse) => received = error,
    });
    http.expectOne('/api/auth/csrf').flush(null, {
      status: 503, statusText: 'Service Unavailable',
    });
    http.expectNone('/api/account/profile');
    expect(received?.status).toBe(503);
  });

  it('passes a version conflict back to the caller', () => {
    let received: HttpErrorResponse | undefined;
    service.updateProfile(request).subscribe({
      error: (error: HttpErrorResponse) => received = error,
    });
    http.expectOne('/api/auth/csrf').flush(null, {
      status: 204, statusText: 'No Content',
    });
    http.expectOne('/api/account/profile').flush(null, {
      status: 409, statusText: 'Conflict',
    });
    expect(received?.status).toBe(409);
  });
});
