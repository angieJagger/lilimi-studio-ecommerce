import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
  type AbstractControl,
  type ValidationErrors,
  type ValidatorFn,
} from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { finalize } from 'rxjs';
import { AuthApiService } from '../../core/auth/auth-api.service';
import { RouterLink } from '@angular/router';

const passwordByteLimit: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value;

  if (typeof value !== 'string') {
    return null;
  }

  return new TextEncoder().encode(value).length > 72 ? { passwordByteLimit: true } : null;
};

const passwordsMatch: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const password = control.get('password')?.value;
  const confirmation = control.get('confirmPassword')?.value;

  return password === confirmation ? null : { passwordsMismatch: true };
};

@Component({
  selector: 'app-customer-register',
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe],
  templateUrl: './customer-register.html',
  styleUrl: './customer-register.scss',
})
export class CustomerRegister {
  private readonly formBuilder = inject(FormBuilder);
  private readonly api = inject(AuthApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly transloco = inject(TranslocoService);

  protected readonly isSubmitting = signal(false);
  protected readonly registered = signal(false);
  protected readonly errorKey = signal<string | null>(null);

  protected readonly registrationForm = this.formBuilder.nonNullable.group(
    {
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      password: [
        '',
        [
          Validators.required,
          Validators.minLength(12),
          Validators.maxLength(72),
          Validators.pattern(/\S/),
          passwordByteLimit,
        ],
      ],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );

  protected submit(): void {
    if (this.isSubmitting() || this.registered()) {
      return;
    }

    this.errorKey.set(null);
    this.registrationForm.markAllAsTouched();

    if (this.registrationForm.invalid) {
      return;
    }

    const { email, password } = this.registrationForm.getRawValue();

    this.isSubmitting.set(true);
    this.registrationForm.disable({ emitEvent: false });

    this.api
      .register({
        email,
        password,
        language: this.transloco.getActiveLang() === 'pl' ? 'pl' : 'en',
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isSubmitting.set(false);
          this.registrationForm.enable({ emitEvent: false });
        }),
      )
      .subscribe({
        next: () => {
          this.registrationForm.reset();
          this.registered.set(true);
        },
        error: (error: unknown) => {
          this.errorKey.set(this.getErrorKey(error));

          this.registrationForm.controls.password.reset();
          this.registrationForm.controls.confirmPassword.reset();
        },
      });
  }

  private getErrorKey(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) {
      return 'customerRegister.errors.generic';
    }

    switch (error.status) {
      case 409:
        return 'customerRegister.errors.emailTaken';
      case 400:
        return 'customerRegister.errors.invalidData';
      case 403:
        return 'customerRegister.errors.session';
      case 429:
        return 'customerRegister.errors.tooManyRequests';
      default:
        return 'customerRegister.errors.generic';
    }
  }
}
