import { Component, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';
import { finalize } from 'rxjs';
import { DigitalOrderDownloadApiService } from './digital-order-download-api.service';
import type { DigitalOrderDownload } from './digital-order-download.model';

@Component({
  selector: 'app-digital-order-downloads',
  imports: [TranslocoPipe, DecimalPipe],
  templateUrl: './digital-order-downloads.html',
  styleUrl: './digital-order-downloads.scss',
})
export class DigitalOrderDownloads {
  readonly orderId = input.required<string>();

  protected readonly api = inject(DigitalOrderDownloadApiService);

  private readonly destroyRef = inject(DestroyRef);

  protected readonly downloads = signal<readonly DigitalOrderDownload[]>([]);

  protected readonly isLoading = signal(false);
  protected readonly errorKey = signal<string | null>(null);

  private requestNumber = 0;

  constructor() {
    effect(() => {
      const orderId = this.orderId();

      this.downloads.set([]);
      this.loadDownloads(orderId);
    });
  }

  protected retry(): void {
    if (this.isLoading()) {
      return;
    }

    this.loadDownloads(this.orderId());
  }

  private loadDownloads(orderId: string): void {
    const requestNumber = ++this.requestNumber;

    this.isLoading.set(true);
    this.errorKey.set(null);

    this.api
      .getDownloads(orderId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (requestNumber === this.requestNumber) {
            this.isLoading.set(false);
          }
        }),
      )
      .subscribe({
        next: (downloads) => {
          if (requestNumber !== this.requestNumber) {
            return;
          }

          this.downloads.set(downloads);
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
        case 403:
        case 404:
          return 'digitalDownloads.errors.accessDenied';
        case 409:
          return 'digitalDownloads.errors.unavailable';
      }
    }

    return 'digitalDownloads.errors.loadFailed';
  }
}
