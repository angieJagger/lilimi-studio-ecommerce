import { TranslocoService } from '@jsverse/transloco';
import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthApiService } from '../../core/auth/auth-api.service';
import type { CurrentUser } from '../../core/auth/auth.model';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { CustomerLogin } from './customer-login';

describe('CustomerLogin', () => {
  let fixture: ComponentFixture<CustomerLogin>;
  let element: HTMLElement;
  let loginResult: Subject<CurrentUser>;
  let logoutResult: Subject<void>;

  const customer: CurrentUser = {
    email: 'customer@example.com',
    roles: ['CUSTOMER'],
  };

  const api = {
    getCurrentUser: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
  };

  beforeEach(async () => {
    loginResult = new Subject<CurrentUser>();
    logoutResult = new Subject<void>();

    api.getCurrentUser.mockReset();
    api.getCurrentUser.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));

    api.login.mockReset();
    api.login.mockReturnValue(loginResult.asObservable());

    api.logout.mockReset();
    api.logout.mockReturnValue(logoutResult.asObservable());

    await TestBed.configureTestingModule({
      imports: [CustomerLogin, getTranslocoTestingModule()],
      providers: [{ provide: AuthApiService, useValue: api }, provideRouter([])],
    }).compileComponents();

    await openPage();
  });

  async function openPage(): Promise<void> {
    fixture = TestBed.createComponent(CustomerLogin);
    element = fixture.nativeElement;
    await fixture.whenStable();
  }

  async function fillField(name: 'email' | 'password', value: string): Promise<void> {
    const input = element.querySelector<HTMLInputElement>(`#customer-login-${name}`)!;

    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await fixture.whenStable();
  }

  async function submitForm(): Promise<void> {
    element
      .querySelector<HTMLFormElement>('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await fixture.whenStable();
  }

  async function fillValidForm(): Promise<void> {
    await fillField('email', customer.email);
    await fillField('password', 'Customer-password!');
  }

  it('should distinguish rate limiting from invalid credentials and clear the password', async () => {
    TestBed.inject(TranslocoService).setActiveLang('en');
    await fixture.whenStable();
    await fillValidForm();
    await submitForm();
    loginResult.error(new HttpErrorResponse({ status: 429 }));
    await fixture.whenStable();
    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Too many sign-in attempts');
    expect(element.querySelector<HTMLInputElement>('input[type="password"]')?.value).toBe('');
  });

  it('should reject an empty form without calling login', async () => {
    await submitForm();

    expect(api.login).not.toHaveBeenCalled();
    expect(element.querySelectorAll('[aria-invalid="true"]')).toHaveLength(2);
  });

  it('should submit once and show the authenticated customer', async () => {
    await fillValidForm();
    await submitForm();
    await submitForm();

    expect(api.login).toHaveBeenCalledExactlyOnceWith({
      email: customer.email,
      password: 'Customer-password!',
    });

    expect(element.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(true);

    loginResult.next(customer);
    loginResult.complete();
    await fixture.whenStable();

    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('output')?.textContent).toContain(customer.email);
    expect(element.querySelector('a[href="/pl/admin/orders"]')).toBeNull();
  });

  it('should preserve email and clear the password after rejected login', async () => {
    await fillValidForm();
    await submitForm();

    loginResult.error(new HttpErrorResponse({ status: 401 }));
    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Niepoprawny adres e-mail lub hasło',
    );

    expect(element.querySelector<HTMLInputElement>('#customer-login-email')?.value).toBe(
      customer.email,
    );

    expect(element.querySelector<HTMLInputElement>('#customer-login-password')?.value).toBe('');

    expect(element.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(false);
  });

  it('should restore an existing customer session', async () => {
    fixture.destroy();
    api.getCurrentUser.mockReturnValue(of(customer));

    await openPage();

    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('output')?.textContent).toContain(customer.email);
  });

  it('should show the administrator panel link for an administrator', async () => {
    fixture.destroy();
    api.getCurrentUser.mockReturnValue(
      of({
        email: 'admin@example.com',
        roles: ['ADMIN'],
      }),
    );

    await openPage();

    expect(element.querySelector('a[href="/pl/admin/orders"]')).not.toBeNull();
  });

  it('should show a session check failure', async () => {
    fixture.destroy();
    api.getCurrentUser.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));

    await openPage();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się sprawdzić sesji',
    );
  });

  it('should clear the session after logout', async () => {
    fixture.destroy();
    api.getCurrentUser.mockReturnValue(of(customer));

    await openPage();

    element.querySelector<HTMLButtonElement>('button')!.click();
    await fixture.whenStable();

    expect(api.logout).toHaveBeenCalledOnce();

    logoutResult.next();
    logoutResult.complete();
    await fixture.whenStable();

    expect(element.querySelector('form')).not.toBeNull();
    expect(element.querySelector<HTMLInputElement>('#customer-login-email')?.value).toBe('');
  });

  it('should preserve the session when logout fails', async () => {
    fixture.destroy();
    api.getCurrentUser.mockReturnValue(of(customer));

    await openPage();

    element.querySelector<HTMLButtonElement>('button')!.click();
    await fixture.whenStable();

    logoutResult.error(new HttpErrorResponse({ status: 503 }));
    await fixture.whenStable();

    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('output')?.textContent).toContain(customer.email);
    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się wylogować',
    );
  });
});
