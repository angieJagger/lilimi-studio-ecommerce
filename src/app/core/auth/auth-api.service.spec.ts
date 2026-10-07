import { HttpErrorResponse, HttpParams, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../api/api-base-url';
import { AuthApiService } from './auth-api.service';
import type { CurrentUser } from './auth.model';

describe('AuthApiService', () => {
  let service: AuthApiService;
  let http: HttpTestingController;

  const currentUser: CurrentUser = {
    email: 'admin@example.com',
    roles: ['ADMIN'],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });

    service = TestBed.inject(AuthApiService);
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

  it('should read the current user', () => {
    let result: CurrentUser | undefined;

    service.getCurrentUser().subscribe((user) => {
      result = user;
    });

    const pending = http.expectOne('/api/auth/me');

    expect(pending.request.method).toBe('GET');

    pending.flush(currentUser);

    expect(result).toEqual(currentUser);
  });

  it('should initialize CSRF, log in, renew CSRF and read the current user', () => {
    let result: CurrentUser | undefined;

    service
      .login({
        email: '  admin@example.com  ',
        password: ' Test-only-password! ',
      })
      .subscribe((user) => {
        result = user;
      });

    http.expectNone('/api/auth/login');

    completeCsrfRequest();

    const login = http.expectOne('/api/auth/login');

    expect(login.request.method).toBe('POST');
    expect(login.request.body).toBeInstanceOf(HttpParams);

    const body = login.request.body as HttpParams;

    expect(body.get('email')).toBe('admin@example.com');
    expect(body.get('password')).toBe(' Test-only-password! ');

    http.expectNone('/api/auth/me');

    login.flush(null, {
      status: 204,
      statusText: 'No Content',
    });

    http.expectNone('/api/auth/me');

    completeCsrfRequest();

    const session = http.expectOne('/api/auth/me');

    expect(session.request.method).toBe('GET');

    session.flush(currentUser);

    expect(result).toEqual(currentUser);
  });

  it('should stop after rejected login and return the error', () => {
    let receivedError: HttpErrorResponse | undefined;

    service
      .login({
        email: 'admin@example.com',
        password: 'incorrect-password',
      })
      .subscribe({
        next: () => {
          throw new Error('Rejected login should not return a user');
        },
        error: (error: HttpErrorResponse) => {
          receivedError = error;
        },
      });

    completeCsrfRequest();

    const login = http.expectOne('/api/auth/login');

    login.flush(null, {
      status: 401,
      statusText: 'Unauthorized',
    });

    expect(receivedError?.status).toBe(401);

    http.expectNone('/api/auth/csrf');
    http.expectNone('/api/auth/me');
  });

  it('should initialize CSRF, log out and renew CSRF before completing', () => {
    let completed = false;

    service.logout().subscribe({
      complete: () => {
        completed = true;
      },
    });

    http.expectNone('/api/auth/logout');

    completeCsrfRequest();

    const logout = http.expectOne('/api/auth/logout');

    expect(logout.request.method).toBe('POST');
    expect(logout.request.body).toBeNull();

    logout.flush(null, {
      status: 204,
      statusText: 'No Content',
    });

    expect(completed).toBe(false);

    completeCsrfRequest();

    expect(completed).toBe(true);
  });

  it('should not send login data when CSRF initialization fails', () => {
    let receivedError: HttpErrorResponse | undefined;

    service
      .login({
        email: 'admin@example.com',
        password: 'Test-only-password!',
      })
      .subscribe({
        next: () => {
          throw new Error('Failed CSRF initialization should not return a user');
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

    http.expectNone('/api/auth/login');
    http.expectNone('/api/auth/me');
  });
});
