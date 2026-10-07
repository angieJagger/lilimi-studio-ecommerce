import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthApiService } from '../../../core/auth/auth-api.service';
import type { CurrentUser } from '../../../core/auth/auth.model';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { AdminLogin } from './admin-login';

describe('AdminLogin', () => {
  let fixture: ComponentFixture<AdminLogin>;
  let element: HTMLElement;
  let loginResult: Subject<CurrentUser>;

  const api = {
    login: vi.fn(),
    getCurrentUser: vi.fn(),
    logout: vi.fn(),
  };

  beforeEach(async () => {
    loginResult = new Subject<CurrentUser>();

    api.login.mockReset();
    api.login.mockReturnValue(loginResult.asObservable());

    api.getCurrentUser.mockReset();
    api.getCurrentUser.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({
          status: 401,
          statusText: 'Unauthorized',
        }),
      ),
    );
    api.logout.mockReset();

    await TestBed.configureTestingModule({
      imports: [AdminLogin, getTranslocoTestingModule()],
      providers: [{ provide: AuthApiService, useValue: api }],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminLogin);
    element = fixture.nativeElement;

    await fixture.whenStable();
  });

  async function fillField(name: 'email' | 'password', value: string): Promise<void> {
    const input = element.querySelector<HTMLInputElement>(`#login-${name}`)!;

    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));

    await fixture.whenStable();
  }

  async function fillValidCredentials(): Promise<void> {
    await fillField('email', 'admin@example.com');
    await fillField('password', 'Test-only-password!');
  }

  async function submitForm(): Promise<void> {
    element
      .querySelector<HTMLFormElement>('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await fixture.whenStable();
  }

  async function reopenPage(): Promise<void> {
    fixture.destroy();

    fixture = TestBed.createComponent(AdminLogin);
    element = fixture.nativeElement;

    await fixture.whenStable();
  }

  async function openAdminSession(): Promise<void> {
    api.getCurrentUser.mockReturnValue(
      of({
        email: 'admin@example.com',
        roles: ['ADMIN'],
      }),
    );

    await reopenPage();
  }

  async function clickLogout(): Promise<void> {
    element.querySelector<HTMLButtonElement>('.logout-button')!.click();

    await fixture.whenStable();
  }

  it('should show required field errors without sending a request', async () => {
    await submitForm();

    expect(api.login).not.toHaveBeenCalled();
    expect(element.querySelector('#login-email-error')).not.toBeNull();
    expect(element.querySelector('#login-password-error')).not.toBeNull();
  });

  it('should reject an invalid email address', async () => {
    await fillField('email', 'incorrect-email');
    await fillField('password', 'Test-only-password!');

    await submitForm();

    expect(api.login).not.toHaveBeenCalled();
    expect(element.querySelector('#login-email-error')).not.toBeNull();
    expect(element.querySelector('#login-password-error')).toBeNull();
  });

  it('should prevent duplicate requests while login is pending', async () => {
    await fillValidCredentials();

    await submitForm();
    await submitForm();

    expect(api.login).toHaveBeenCalledExactlyOnceWith({
      email: 'admin@example.com',
      password: 'Test-only-password!',
    });

    const fieldset = element.querySelector<HTMLFieldSetElement>('fieldset')!;
    const button = element.querySelector<HTMLButtonElement>('button[type="submit"]')!;

    expect(fieldset.disabled).toBe(true);
    expect(button.getAttribute('aria-busy')).toBe('true');
  });

  it('should show a login error and clear the password after rejection', async () => {
    await fillValidCredentials();
    await submitForm();

    loginResult.error(
      new HttpErrorResponse({
        status: 401,
        statusText: 'Unauthorized',
      }),
    );

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nieprawidłowy adres e-mail lub hasło.',
    );

    expect(element.querySelector<HTMLInputElement>('#login-password')!.value).toBe('');

    expect(element.querySelector<HTMLInputElement>('#login-email')!.value).toBe(
      'admin@example.com',
    );

    expect(element.querySelector<HTMLFieldSetElement>('fieldset')!.disabled).toBe(false);
  });

  it('should show confirmation after administrator login', async () => {
    await fillValidCredentials();
    await submitForm();

    loginResult.next({
      email: 'admin@example.com',
      roles: ['ADMIN'],
    });
    loginResult.complete();

    await fixture.whenStable();

    expect(element.querySelector('output')?.textContent).toContain(
      'Zalogowano do konta administratora.',
    );
    expect(element.querySelector('form')).toBeNull();
  });

  it('should restore an existing administrator session when opening the page', async () => {
    api.getCurrentUser.mockReturnValue(
      of({
        email: 'admin@example.com',
        roles: ['ADMIN'],
      }),
    );

    await reopenPage();

    expect(element.querySelector('output')?.textContent).toContain(
      'Zalogowano do konta administratora.',
    );
    expect(element.querySelector('form')).toBeNull();
    expect(api.login).not.toHaveBeenCalled();
  });

  it('should show an error when the session check fails', async () => {
    api.getCurrentUser.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 503,
            statusText: 'Service Unavailable',
          }),
      ),
    );

    await reopenPage();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się sprawdzić sesji. Spróbuj odświeżyć stronę.',
    );
    expect(element.querySelector('form')).not.toBeNull();
    expect(api.login).not.toHaveBeenCalled();
  });

  it('should return to the login form after successful logout', async () => {
    const logoutResult = new Subject<void>();

    api.logout.mockReturnValue(logoutResult.asObservable());

    await openAdminSession();
    await clickLogout();

    expect(api.logout).toHaveBeenCalledOnce();
    expect(element.querySelector<HTMLButtonElement>('.logout-button')!.disabled).toBe(true);

    logoutResult.next(undefined);
    logoutResult.complete();

    await fixture.whenStable();

    expect(element.querySelector('form')).not.toBeNull();
    expect(element.querySelector('output')).toBeNull();
    expect(element.querySelector('.logout-button')).toBeNull();
  });

  it('should prevent duplicate logout requests while one is pending', async () => {
    const logoutResult = new Subject<void>();

    api.logout.mockReturnValue(logoutResult.asObservable());

    await openAdminSession();
    await clickLogout();
    await clickLogout();

    expect(api.logout).toHaveBeenCalledOnce();

    logoutResult.next(undefined);
    logoutResult.complete();

    await fixture.whenStable();
  });

  it('should show a logout error and allow another attempt', async () => {
    const logoutResult = new Subject<void>();

    api.logout.mockReturnValue(logoutResult.asObservable());

    await openAdminSession();
    await clickLogout();

    logoutResult.error(
      new HttpErrorResponse({
        status: 503,
        statusText: 'Service Unavailable',
      }),
    );

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się wylogować. Spróbuj ponownie.',
    );

    expect(element.querySelector<HTMLButtonElement>('.logout-button')!.disabled).toBe(false);

    api.logout.mockReturnValue(of(undefined));

    await clickLogout();

    expect(api.logout).toHaveBeenCalledTimes(2);
    expect(element.querySelector('form')).not.toBeNull();
  });
});
