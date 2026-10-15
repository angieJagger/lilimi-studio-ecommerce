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
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { finalize } from 'rxjs';
import { AuthApiService } from '../../core/auth/auth-api.service';

const passwordByteLimit: ValidatorFn = (
  control: AbstractControl,
): ValidationErrors | null => {
  const value = control.value;

  return typeof value === 'string' &&
    new TextEncoder().encode(value).length > 72
    ? { passwordByteLimit: true }
    : null;
};

const passwordsMatch: ValidatorFn = (
  control: AbstractControl,
): ValidationErrors | null =>
  control.get('password')?.value === control.get('confirmPassword')?.value
    ? null
    : { passwordsMismatch: true };

@Component({
  selector: 'app-customer-reset-password',
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe],
  templateUrl: './customer-reset-password.html',
  styleUrl: './customer-reset-password.scss',
})
export class CustomerResetPassword {
  private readonly api = inject(AuthApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private token: string | null = null;

  protected readonly transloco = inject(TranslocoService);
  protected readonly isSubmitting = signal(false);
  protected readonly completed = signal(false);
  protected readonly hasToken = signal(false);
  protected readonly errorKey = signal<string | null>(null);

  protected readonly form = this.formBuilder.nonNullable.group(
    {
      password: ['', [
        Validators.required,
        Validators.minLength(12),
        Validators.maxLength(72),
        Validators.pattern(/\S/),
        passwordByteLimit,
      ]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );

  constructor() {
    this.route.fragment
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((fragment) => {
        this.token = new URLSearchParams(fragment ?? '').get('token');
        this.hasToken.set(
          this.token !== null && /^[A-Za-z0-9_-]{43}$/.test(this.token),
        );
        this.completed.set(false);
        this.form.reset();
        this.errorKey.set(
          this.hasToken() ? null : 'resetPassword.errors.invalidLink',
        );
      });
  }

  protected submit(): void {
    if (
      this.isSubmitting() ||
      this.completed() ||
      !this.hasToken() ||
      !this.token
    ) {
      return;
    }

    this.form.markAllAsTouched();

    if (this.form.invalid) {
      return;
    }

    const password = this.form.getRawValue().password;

    this.errorKey.set(null);
    this.isSubmitting.set(true);
    this.form.disable({ emitEvent: false });

    this.api.resetPassword(this.token, password).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => {
        this.isSubmitting.set(false);
        this.form.enable({ emitEvent: false });
      }),
    ).subscribe({
      next: () => {
        this.form.reset();
        this.token = null;
        this.hasToken.set(false);
        this.completed.set(true);
      },
      error: (error: unknown) => {
        this.form.reset();

        if (error instanceof HttpErrorResponse && error.status === 400) {
          // Both an invalid password and an invalid token can return 400.
          // Do not claim that every 400 means an expired link.
          this.errorKey.set('resetPassword.errors.invalidRequest');
          return;
        }

        this.errorKey.set('resetPassword.errors.failed');
      },
    });
  }
}
