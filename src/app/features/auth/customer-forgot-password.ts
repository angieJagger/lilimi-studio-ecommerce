import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { finalize } from 'rxjs';
import { AuthApiService } from '../../core/auth/auth-api.service';

@Component({
  selector: 'app-customer-forgot-password',
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe],
  templateUrl: './customer-forgot-password.html',
  styleUrl: './customer-forgot-password.scss',
})
export class CustomerForgotPassword {
  private readonly api = inject(AuthApiService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly transloco = inject(TranslocoService);
  protected readonly isSubmitting = signal(false);
  protected readonly submitted = signal(false);
  protected readonly failed = signal(false);

  protected readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
  });

  protected submit(): void {
    if (this.isSubmitting() || this.submitted()) {
      return;
    }

    this.form.markAllAsTouched();

    if (this.form.invalid) {
      return;
    }

    const email = this.form.getRawValue().email;
    const language = this.transloco.getActiveLang() === 'pl' ? 'pl' : 'en';

    this.failed.set(false);
    this.isSubmitting.set(true);
    this.form.disable({ emitEvent: false });

    this.api
      .requestPasswordReset(email, language)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isSubmitting.set(false);
          this.form.enable({ emitEvent: false });
        }),
      )
      .subscribe({
        next: () => {
          this.form.reset();
          this.submitted.set(true);
        },
        error: () => {
          this.failed.set(true);
        },
      });
  }
}
