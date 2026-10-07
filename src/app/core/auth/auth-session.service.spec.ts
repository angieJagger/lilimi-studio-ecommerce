import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AuthApiService } from './auth-api.service';
import type { CurrentUser, LoginRequest } from './auth.model';
import { AuthSessionService } from './auth-session.service';

describe('AuthSessionService', () => {
  let service: AuthSessionService;

  const admin: CurrentUser = {
    email: 'admin@example.com',
    roles: ['ADMIN'],
  };

  const customer: CurrentUser = {
    email: 'customer@example.com',
    roles: ['CUSTOMER'],
  };

  const api = {
    getCurrentUser: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
  };

  beforeEach(() => {
    api.getCurrentUser.mockReset();
    api.login.mockReset();
    api.logout.mockReset();

    TestBed.configureTestingModule({
      providers: [{ provide: AuthApiService, useValue: api }],
    });

    service = TestBed.inject(AuthSessionService);
  });

  function restoreAdminSession(): void {
    api.getCurrentUser.mockReturnValue(of(admin));
    service.refresh().subscribe();
  }

  it('should start without an authenticated user', () => {
    expect(service.user()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
    expect(service.isAdmin()).toBe(false);
  });

  it('should restore an administrator session', () => {
    let result: CurrentUser | null | undefined;

    api.getCurrentUser.mockReturnValue(of(admin));

    service.refresh().subscribe((user) => {
      result = user;
    });

    expect(api.getCurrentUser).toHaveBeenCalledOnce();
    expect(result).toEqual(admin);
    expect(service.user()).toEqual(admin);
    expect(service.isAuthenticated()).toBe(true);
    expect(service.isAdmin()).toBe(true);
  });

  it('should recognize a customer without administrator access', () => {
    api.getCurrentUser.mockReturnValue(of(customer));

    service.refresh().subscribe();

    expect(service.user()).toEqual(customer);
    expect(service.isAuthenticated()).toBe(true);
    expect(service.isAdmin()).toBe(false);
  });

  it('should clear an expired session and return null', () => {
    restoreAdminSession();

    api.getCurrentUser.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            statusText: 'Unauthorized',
          }),
      ),
    );

    let result: CurrentUser | null | undefined;

    service.refresh().subscribe((user) => {
      result = user;
    });

    expect(result).toBeNull();
    expect(service.user()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
    expect(service.isAdmin()).toBe(false);
  });

  it('should clear stale user data and propagate a connection error', () => {
    restoreAdminSession();

    const connectionError = new HttpErrorResponse({
      status: 0,
      statusText: 'Unknown Error',
    });

    api.getCurrentUser.mockReturnValue(throwError(() => connectionError));

    let receivedError: unknown;

    service.refresh().subscribe({
      next: () => {
        throw new Error('Failed session refresh should not return a user');
      },
      error: (error: unknown) => {
        receivedError = error;
      },
    });

    expect(receivedError).toBe(connectionError);
    expect(service.user()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
    expect(service.isAdmin()).toBe(false);
  });

  it('should store the user after successful login', () => {
    const request: LoginRequest = {
      email: 'admin@example.com',
      password: 'Test-only-password!',
    };

    api.login.mockReturnValue(of(admin));

    let result: CurrentUser | undefined;

    service.login(request).subscribe((user) => {
      result = user;
    });

    expect(api.login).toHaveBeenCalledExactlyOnceWith(request);
    expect(result).toEqual(admin);
    expect(service.user()).toEqual(admin);
    expect(service.isAuthenticated()).toBe(true);
    expect(service.isAdmin()).toBe(true);
  });

  it('should propagate rejected login without authenticating the user', () => {
    const loginError = new HttpErrorResponse({
      status: 401,
      statusText: 'Unauthorized',
    });

    api.login.mockReturnValue(throwError(() => loginError));

    let receivedError: unknown;

    service
      .login({
        email: 'admin@example.com',
        password: 'incorrect-password',
      })
      .subscribe({
        next: () => {
          throw new Error('Rejected login should not return a user');
        },
        error: (error: unknown) => {
          receivedError = error;
        },
      });

    expect(receivedError).toBe(loginError);
    expect(service.user()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('should clear the user after successful logout', () => {
    restoreAdminSession();
    api.logout.mockReturnValue(of(undefined));

    let completed = false;

    service.logout().subscribe({
      complete: () => {
        completed = true;
      },
    });

    expect(api.logout).toHaveBeenCalledOnce();
    expect(completed).toBe(true);
    expect(service.user()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
    expect(service.isAdmin()).toBe(false);
  });
});
