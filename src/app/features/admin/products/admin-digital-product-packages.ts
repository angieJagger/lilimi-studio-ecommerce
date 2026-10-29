import { Component, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe, DecimalPipe } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';
import { finalize } from 'rxjs';
import { AdminDigitalProductPackageApiService } from './admin-digital-product-package-api.service';
import type { AdminDigitalProductPackage } from './admin-digital-product-package.model';

@Component({
  selector: 'app-admin-digital-product-packages',
  imports: [TranslocoPipe, DatePipe, DecimalPipe],
  templateUrl: './admin-digital-product-packages.html',
  styleUrl: './admin-digital-product-packages.scss',
})
export class AdminDigitalProductPackages {
  readonly productId = input.required<string>();

  private readonly api = inject(AdminDigitalProductPackageApiService);
  private readonly destroyRef = inject(DestroyRef);

  private requestNumber = 0;

  protected readonly packages = signal<readonly AdminDigitalProductPackage[]>([]);
  protected readonly isLoading = signal(false);
  protected readonly errorKey = signal<string | null>(null);
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly fileErrorKey = signal<string | null>(null);
  protected readonly isUploading = signal(false);
  protected readonly requiresHistoryRefresh = signal(false);
  protected readonly uploadErrorKey = signal<string | null>(null);
  protected readonly uploadSucceeded = signal(false);

  constructor() {
    effect(() => {
      const productId = this.productId();

      this.selectedFile.set(null);
      this.fileErrorKey.set(null);
      this.uploadErrorKey.set(null);
      this.requiresHistoryRefresh.set(false);
      this.uploadSucceeded.set(false);
      this.packages.set([]);
      this.loadPackages(productId);
    });
  }

  protected uploadPackage(fileInput: HTMLInputElement): void {
    const file = this.selectedFile();

    if (!file || this.isUploading() || this.isLoading() || this.requiresHistoryRefresh()) {
      return;
    }

    const productId = this.productId();

    this.isUploading.set(true);
    this.uploadErrorKey.set(null);
    this.uploadSucceeded.set(false);

    this.api
      .upload(productId, file)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isUploading.set(false)),
      )
      .subscribe({
        next: () => {
          if (this.productId() !== productId) {
            return;
          }

          this.selectedFile.set(null);
          fileInput.value = '';
          this.uploadSucceeded.set(true);
          this.loadPackages(productId);
        },
        error: (error: unknown) => {
          if (this.productId() !== productId) {
            return;
          }

          const status = error instanceof HttpErrorResponse ? error.status : null;

          this.requiresHistoryRefresh.set(status === null || status === 0 || status >= 500);

          this.uploadErrorKey.set(this.getUploadErrorKey(error));
        },
      });
  }

  private getUploadErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      switch (error.status) {
        case 400:
          return 'adminDigitalPackages.errors.invalidPackage';
        case 401:
          return 'adminOrders.errors.sessionExpired';
        case 403:
          return 'adminOrders.errors.accessDenied';
        case 404:
          return 'adminProducts.errors.notFound';
        case 409:
          return 'adminDigitalPackages.errors.uploadConflict';
        case 413:
          return 'adminDigitalPackages.errors.tooLarge';
      }
    }

    return 'adminDigitalPackages.errors.uploadFailed';
  }

  protected selectFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;

    this.selectedFile.set(null);
    this.fileErrorKey.set(null);
    this.uploadErrorKey.set(null);
    this.uploadSucceeded.set(false);

    if (!file) {
      return;
    }

    if (!file.name.toLowerCase().endsWith('.zip')) {
      this.fileErrorKey.set('adminDigitalPackages.errors.zipRequired');
      input.value = '';
      return;
    }

    if (file.size === 0) {
      this.fileErrorKey.set('adminDigitalPackages.errors.emptyFile');
      input.value = '';
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      this.fileErrorKey.set('adminDigitalPackages.errors.tooLarge');
      input.value = '';
      return;
    }

    this.selectedFile.set(file);
  }

  protected retry(): void {
    if (this.isLoading()) {
      return;
    }

    this.loadPackages(this.productId());
  }

  private loadPackages(productId: string): void {
    const requestNumber = ++this.requestNumber;

    this.isLoading.set(true);
    this.errorKey.set(null);

    this.api
      .getPackages(productId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (requestNumber === this.requestNumber) {
            this.isLoading.set(false);
          }
        }),
      )
      .subscribe({
        next: (packages) => {
          if (requestNumber !== this.requestNumber) {
            return;
          }

          this.packages.set(packages);
          this.requiresHistoryRefresh.set(false);
        },
        error: (error: unknown) => {
          if (requestNumber !== this.requestNumber) {
            return;
          }

          this.errorKey.set(this.getErrorKey(error));
        },
      });
  }

  private getErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      switch (error.status) {
        case 401:
          return 'adminOrders.errors.sessionExpired';
        case 403:
          return 'adminOrders.errors.accessDenied';
        case 404:
          return 'adminProducts.errors.notFound';
      }
    }

    return 'adminDigitalPackages.errors.loadFailed';
  }
}
