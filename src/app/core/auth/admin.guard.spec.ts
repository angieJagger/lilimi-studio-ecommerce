import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
  type GuardResult,
} from '@angular/router';
import { firstValueFrom, isObservable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { adminGuard } from './admin.guard';
import { AuthSessionService } from './auth-session.service';

describe('adminGuard', () => {
  let router: Router;

  const session = {
    refresh: vi.fn(),
  };

  beforeEach(() => {
    session.refresh.mockReset();

    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthSessionService, useValue: session }],
    });

    router = TestBed.inject(Router);
  });

  async function checkAccess(url: string): Promise<GuardResult> {
    const result = TestBed.runInInjectionContext(() =>
      adminGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );

    return isObservable(result) ? firstValueFrom(result) : result;
  }

  function expectLoginRedirect(result: GuardResult, expectedUrl: string): void {
    expect(result).toBeInstanceOf(UrlTree);

    if (!(result instanceof UrlTree)) {
      throw new Error('Expected a redirect to login');
    }

    expect(router.serializeUrl(result)).toBe(expectedUrl);
  }

  it('should allow an administrator to access the panel', async () => {
    session.refresh.mockReturnValue(
      of({
        email: 'admin@example.com',
        roles: ['ADMIN'],
      }),
    );

    const result = await checkAccess('/pl/admin/orders');

    expect(result).toBe(true);
    expect(session.refresh).toHaveBeenCalledOnce();
  });

  it('should redirect an anonymous user to Polish login', async () => {
    session.refresh.mockReturnValue(of(null));

    const result = await checkAccess('/pl/admin/orders');

    expectLoginRedirect(result, '/pl/admin/login');
  });

  it('should redirect an anonymous user to English login', async () => {
    session.refresh.mockReturnValue(of(null));

    const result = await checkAccess('/en/admin/orders');

    expectLoginRedirect(result, '/en/admin/login');
  });

  it('should redirect a customer without administrator access', async () => {
    session.refresh.mockReturnValue(
      of({
        email: 'customer@example.com',
        roles: ['CUSTOMER'],
      }),
    );

    const result = await checkAccess('/pl/admin/orders');

    expectLoginRedirect(result, '/pl/admin/login');
  });

  it('should redirect to login when the session check fails', async () => {
    session.refresh.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 503,
            statusText: 'Service Unavailable',
          }),
      ),
    );

    const result = await checkAccess('/en/admin/orders');

    expectLoginRedirect(result, '/en/admin/login');
  });
});
