import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { AuthApiService } from './auth-api.service';
import type { AuthSessionState } from './auth-api.service';
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
    getSessionState: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
  };

  beforeEach(() => {
    api.getSessionState.mockReset();
    api.login.mockReset();
    api.logout.mockReset();

    TestBed.configureTestingModule({
      providers: [{ provide: AuthApiService, useValue: api }],
    });

    service = TestBed.inject(AuthSessionService);
  });

  function restoreAdminSession(): void {
    api.getSessionState.mockReturnValue(of({ user: admin, mfaRequired: false }));
    service.refresh().subscribe();
  }

  it('should update the current customer name and trim whitespace', () => {
    api.getSessionState.mockReturnValue(of({
  user: { ...customer, firstName: 'Anna' },
  mfaRequired: false,
}));
    service.refresh().subscribe();

    service.updateCustomerName(customer.email, '  Maria  ');

    expect(service.user()).toEqual({
      ...customer,
      firstName: 'Maria',
    });
  });

  it('should clear the customer name when it is empty', () => {
    api.getSessionState.mockReturnValue(of({
  user: { ...customer, firstName: 'Anna' },
  mfaRequired: false,
}));
    service.refresh().subscribe();

    service.updateCustomerName(customer.email, '   ');

    expect(service.user()?.firstName).toBeNull();
    expect(service.isAuthenticated()).toBe(true);
  });

  it('should ignore a profile belonging to another customer', () => {
    api.getSessionState.mockReturnValue(of({
  user: { ...customer, firstName: 'Anna' },
  mfaRequired: false,
}));
    service.refresh().subscribe();

    service.updateCustomerName('another@example.com', 'Maria');

    expect(service.user()?.firstName).toBe('Anna');
  });

  it('should not change an administrator name', () => {
    restoreAdminSession();

    service.updateCustomerName(admin.email, 'Anna');

    expect(service.user()).toEqual(admin);
  });

  it('should not recreate a session when updating a name after logout', () => {
    service.updateCustomerName(customer.email, 'Anna');

    expect(service.user()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('should start without an authenticated user', () => {
    expect(service.user()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
    expect(service.isAdmin()).toBe(false);
  });

  it('shares a pending refresh and requests a fresh session after completion', () => {
    const response = new Subject<AuthSessionState>();
    api.getSessionState.mockReturnValueOnce(response).mockReturnValueOnce(of({ user: null, mfaRequired: false }));

    const first = vi.fn();
    const second = vi.fn();
    service.refresh().subscribe(first);
    service.refresh().subscribe(second);

    expect(api.getSessionState).toHaveBeenCalledOnce();
    response.next({ user: customer, mfaRequired: false });
    response.complete();
    expect(first).toHaveBeenCalledWith(customer);
    expect(second).toHaveBeenCalledWith(customer);

    service.refresh().subscribe();
    expect(api.getSessionState).toHaveBeenCalledTimes(2);
    expect(service.user()).toBeNull();
  });

  it('ignores an earlier guest response after login and keeps the new refresh pending', () => {
    const earlier = new Subject<AuthSessionState>();
    const latest = new Subject<AuthSessionState>();
    api.getSessionState.mockReturnValueOnce(earlier).mockReturnValueOnce(latest);
    api.login.mockReturnValue(of(customer));

    const result = vi.fn();
    service.refresh().subscribe(result);
    service.login({ email: customer.email, password: 'test' }).subscribe();
    service.refresh().subscribe();

    earlier.next({ user: null, mfaRequired: false });
    earlier.complete();
    expect(service.user()).toEqual(customer);
    expect(result).toHaveBeenCalledWith(customer);

    service.refresh().subscribe();
    expect(api.getSessionState).toHaveBeenCalledTimes(2);
    latest.next({ user: customer, mfaRequired: false });
    latest.complete();
  });

  it('ignores an earlier authenticated response after logout', () => {
    const earlier = new Subject<AuthSessionState>();
    api.getSessionState.mockReturnValue(earlier);
    api.logout.mockReturnValue(of(undefined));

    const result = vi.fn();
    service.refresh().subscribe(result);
    service.logout().subscribe();
    earlier.next({ user: customer, mfaRequired: false });
    earlier.complete();

    expect(service.user()).toBeNull();
    expect(result).toHaveBeenCalledWith(null);
  });

  it('should restore an administrator session', () => {
    let result: CurrentUser | null | undefined;

    api.getSessionState.mockReturnValue(of({ user: admin, mfaRequired: false }));

    service.refresh().subscribe((user) => {
      result = user;
    });

    expect(api.getSessionState).toHaveBeenCalledOnce();
    expect(result).toEqual(admin);
    expect(service.user()).toEqual(admin);
    expect(service.isAuthenticated()).toBe(true);
    expect(service.isAdmin()).toBe(true);
  });

  it('should recognize a customer without administrator access', () => {
    api.getSessionState.mockReturnValue(of({ user: customer, mfaRequired: false }));

    service.refresh().subscribe();

    expect(service.user()).toEqual(customer);
    expect(service.isAuthenticated()).toBe(true);
    expect(service.isAdmin()).toBe(false);
  });

  it('should clear an expired session and return null', () => {
    restoreAdminSession();

    api.getSessionState.mockReturnValue(
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

    api.getSessionState.mockReturnValue(throwError(() => connectionError));

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

    let result: CurrentUser | null | undefined;

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

  it('should restore pending MFA without granting administrator access', () => {
    api.getSessionState.mockReturnValue(
      of({
        user: null,
        mfaRequired: true,
      }),
    );

    service.refresh().subscribe();

    expect(service.mfaRequired()).toBe(true);
    expect(service.user()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
    expect(service.isAdmin()).toBe(false);
  });

  it('should require MFA after a password-only administrator login', () => {
    api.login.mockReturnValue(of(null));

    service
      .login({
        email: admin.email,
        password: 'test',
      })
      .subscribe();

    expect(service.mfaRequired()).toBe(true);
    expect(service.isAdmin()).toBe(false);
  });

  it('should clear pending MFA after successful verification', () => {
    api.login.mockReturnValue(of(null));

    service
      .login({
        email: admin.email,
        password: 'test',
      })
      .subscribe();

    api.getSessionState.mockReturnValue(
      of({
        user: admin,
        mfaRequired: false,
      }),
    );

    service.refresh().subscribe();

    expect(service.mfaRequired()).toBe(false);
    expect(service.user()).toEqual(admin);
    expect(service.isAdmin()).toBe(true);
  });
});
