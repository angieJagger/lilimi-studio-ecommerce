import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { vi } from 'vitest';
import { AuthApiService } from '../../core/auth/auth-api.service';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { CustomerResetPassword } from './customer-reset-password';

describe('CustomerResetPassword', () => {
  let fixture: ComponentFixture<CustomerResetPassword>;
  let element: HTMLElement;
  let fragment: BehaviorSubject<string | null>;
  let result: Subject<void>;

  const token = 'a'.repeat(43);
  const password = 'Changed-password-2026!';
  const api = { resetPassword: vi.fn() };

  beforeEach(async () => {
    fragment = new BehaviorSubject<string | null>(`token=${token}`);
    result = new Subject<void>();
    api.resetPassword.mockReset();
    api.resetPassword.mockReturnValue(result.asObservable());

    await TestBed.configureTestingModule({
      imports: [CustomerResetPassword, getTranslocoTestingModule()],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { fragment: fragment.asObservable() } },
        { provide: AuthApiService, useValue: api },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CustomerResetPassword);
    element = fixture.nativeElement;
    await fixture.whenStable();
  });

  async function fillPasswords(first: string, second = first): Promise<void> {
    const firstInput = element.querySelector<HTMLInputElement>('#reset-password')!;
    const secondInput = element.querySelector<HTMLInputElement>('#reset-password-confirm')!;
    firstInput.value = first;
    secondInput.value = second;
    firstInput.dispatchEvent(new Event('input', { bubbles: true }));
    secondInput.dispatchEvent(new Event('input', { bubbles: true }));
    await fixture.whenStable();
  }

  async function submit(): Promise<void> {
    element
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await fixture.whenStable();
  }

  it('should not reset the password when the page opens', () => {
    expect(api.resetPassword).not.toHaveBeenCalled();
    expect(element.querySelector('form')).not.toBeNull();
  });

  it('should reject an empty form', async () => {
    await submit();
    expect(api.resetPassword).not.toHaveBeenCalled();
    expect(element.querySelectorAll('[aria-invalid="true"]')).toHaveLength(2);
  });

  it('should reject mismatched passwords', async () => {
    await fillPasswords(password, 'Different-password-2026!');
    await submit();
    expect(api.resetPassword).not.toHaveBeenCalled();
    expect(element.textContent).toContain('Wpisz takie samo hasło w obu polach.');
  });

  it.each(['short', ' '.repeat(12), 'ą'.repeat(37)])(
    'should reject a password that fails the password requirements: %s',
    async (invalidPassword) => {
      await fillPasswords(invalidPassword);
      await submit();
      expect(api.resetPassword).not.toHaveBeenCalled();
      expect(element.querySelector('#reset-password')?.getAttribute('aria-invalid')).toBe('true');
    },
  );

  it('should submit once and preserve spaces in the password', async () => {
    const spacedPassword = ` ${password} `;
    await fillPasswords(spacedPassword);
    await submit();
    await submit();
    expect(api.resetPassword).toHaveBeenCalledExactlyOnceWith(token, spacedPassword);
    expect(element.querySelector<HTMLButtonElement>('button')?.disabled).toBe(true);
    expect(
      Array.from(element.querySelectorAll<HTMLInputElement>('input')).every(
        (input) => input.disabled,
      ),
    ).toBe(true);
  });

  it('should show success and a sign-in link', async () => {
    await fillPasswords(password);
    await submit();
    result.next();
    result.complete();
    await fixture.whenStable();
    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('output')?.textContent).toContain('Hasło zostało zmienione.');
    expect(element.querySelector('a')?.getAttribute('href')).toBe('/pl/account/login');
  });

  it.each([null, 'token=invalid'])('should reject an invalid fragment: %s', async (value) => {
    fragment.next(value);
    await fixture.whenStable();
    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Link jest nieprawidłowy',
    );
    expect(api.resetPassword).not.toHaveBeenCalled();
  });

  it('should clear passwords after a rejected request and offer a new link', async () => {
    await fillPasswords(password);
    await submit();
    result.error(new HttpErrorResponse({ status: 400 }));
    await fixture.whenStable();
    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie można zmienić hasła.',
    );
    expect(
      Array.from(element.querySelectorAll<HTMLInputElement>('input')).every(
        (input) => input.value === '',
      ),
    ).toBe(true);
    expect(element.querySelector('a')?.getAttribute('href')).toBe('/pl/account/forgot-password');
  });

  it('should allow retry after a temporary failure', async () => {
    await fillPasswords(password);
    await submit();
    result.error(new HttpErrorResponse({ status: 503 }));
    await fixture.whenStable();
    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się zmienić hasła.',
    );
    expect(element.querySelector<HTMLButtonElement>('button')?.disabled).toBe(false);

    result = new Subject<void>();
    api.resetPassword.mockReturnValue(result.asObservable());
    await fillPasswords(password);
    await submit();
    expect(api.resetPassword).toHaveBeenCalledTimes(2);
    result.next();
    result.complete();
    await fixture.whenStable();
    expect(element.querySelector('output')).not.toBeNull();
  });
});
