import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { finalize } from 'rxjs';
import { DigitalDownloadLinkApiService } from './digital-download-link-api.service';

@Component({
  selector: 'app-resend-digital-download-link',
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe],
  templateUrl: './resend-digital-download-link.html',
  styleUrl: './resend-digital-download-link.scss',
})
export class ResendDigitalDownloadLink {
  private readonly api = inject(DigitalDownloadLinkApiService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly transloco = inject(TranslocoService);
  protected readonly isSubmitting = signal(false);
  protected readonly submitted = signal(false);
  protected readonly errorKey = signal<string | null>(null);

  protected readonly form = this.formBuilder.nonNullable.group({
    orderId: [
      '',
      [
        Validators.required,
        Validators.pattern(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i),
      ],
    ],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
  });

  protected submit(): void {
    if (this.isSubmitting() || this.submitted()) {
      return;
    }

    const values = this.form.getRawValue();

    this.form.patchValue({
      orderId: values.orderId.trim(),
      email: values.email.trim(),
    });

    this.form.markAllAsTouched();

    if (this.form.invalid) {
      return;
    }

    const { orderId, email } = this.form.getRawValue();

    this.errorKey.set(null);
    this.isSubmitting.set(true);
    this.form.disable({ emitEvent: false });

    this.api
      .resend(orderId, email)
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
        error: (error: unknown) => {
          this.errorKey.set(this.getErrorKey(error));
        },
      });
  }

  private getErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 429) {
        return 'resendDownloadLink.errors.rateLimited';
      }

      if (error.status === 400) {
        return 'resendDownloadLink.errors.invalidData';
      }

      if (error.status === 403) {
        return 'resendDownloadLink.errors.session';
      }
    }

    return 'resendDownloadLink.errors.failed';
  }
}
