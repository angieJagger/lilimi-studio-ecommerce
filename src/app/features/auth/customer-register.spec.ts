import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import { AuthApiService } from '../../core/auth/auth-api.service';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { CustomerRegister } from './customer-register';
import { provideRouter } from '@angular/router';

describe('CustomerRegister', () => {
  let fixture: ComponentFixture<CustomerRegister>;
  let element: HTMLElement;
  let registrationResult: Subject<void>;

  const api = {
    register: vi.fn(),
  };

  beforeEach(async () => {
    registrationResult = new Subject<void>();
    api.register.mockReset();
    api.register.mockReturnValue(registrationResult.asObservable());

    await TestBed.configureTestingModule({
      imports: [CustomerRegister, getTranslocoTestingModule()],
      providers: [{ provide: AuthApiService, useValue: api }, provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(CustomerRegister);
    element = fixture.nativeElement;
    await fixture.whenStable();
  });

  async function fillField(
    name: 'email' | 'password' | 'confirm-password',
    value: string,
  ): Promise<void> {
    const input = element.querySelector<HTMLInputElement>(`#registration-${name}`)!;

    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await fixture.whenStable();
  }

  async function fillValidForm(): Promise<void> {
    await fillField('email', 'customer@example.com');
    await fillField('password', 'Customer-password!');
    await fillField('confirm-password', 'Customer-password!');
  }

  async function submitForm(): Promise<void> {
    element
      .querySelector<HTMLFormElement>('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await fixture.whenStable();
  }

  it('should reject an empty form without sending a request', async () => {
    await submitForm();

    expect(api.register).not.toHaveBeenCalled();
    expect(element.querySelectorAll('[aria-invalid="true"]')).toHaveLength(3);
  });

  it('should reject mismatched passwords', async () => {
    await fillValidForm();
    await fillField('confirm-password', 'Different-password!');
    await submitForm();

    expect(api.register).not.toHaveBeenCalled();
    expect(
      element.querySelector('#registration-confirm-password')?.getAttribute('aria-invalid'),
    ).toBe('true');
  });

  it('should reject passwords exceeding 72 UTF-8 bytes', async () => {
    await fillField('email', 'customer@example.com');
    await fillField('password', 'ą'.repeat(37));
    await fillField('confirm-password', 'ą'.repeat(37));
    await submitForm();

    expect(api.register).not.toHaveBeenCalled();
    expect(element.querySelector('#registration-password')?.getAttribute('aria-invalid')).toBe(
      'true',
    );
  });

  it('should submit once and disable fields while waiting', async () => {
    await fillValidForm();
    await submitForm();
    await submitForm();

    expect(api.register).toHaveBeenCalledExactlyOnceWith({
      email: 'customer@example.com',
      password: 'Customer-password!',
      language: 'pl',
    });

    expect(element.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(true);

    expect(
      Array.from(element.querySelectorAll<HTMLInputElement>('input')).every(
        (input) => input.disabled,
      ),
    ).toBe(true);
  });

  it('should show confirmation and remove the form after success', async () => {
    await fillValidForm();
    await submitForm();

    registrationResult.next();
    registrationResult.complete();
    await fixture.whenStable();

    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('output')?.textContent).toContain('Konto zostało utworzone');
  });

  it('should preserve email and clear passwords after a conflict', async () => {
    await fillValidForm();
    await submitForm();

    registrationResult.error(new HttpErrorResponse({ status: 409 }));
    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie można utworzyć konta',
    );

    expect(element.querySelector<HTMLInputElement>('#registration-email')?.value).toBe(
      'customer@example.com',
    );

    expect(element.querySelector<HTMLInputElement>('#registration-password')?.value).toBe('');

    expect(element.querySelector<HTMLInputElement>('#registration-confirm-password')?.value).toBe(
      '',
    );

    expect(element.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(false);
  });
});
