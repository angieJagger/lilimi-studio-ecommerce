import { DOCUMENT, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { afterNextRender, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { distinctUntilChanged, finalize } from 'rxjs';
import { DigitalDownloadLinkApiService } from './digital-download-link-api.service';
import type { DigitalOrderDownload } from './digital-order-download.model';

@Component({
  selector: 'app-digital-download-link-page',
  imports: [TranslocoPipe, DecimalPipe, RouterLink],
  templateUrl: './digital-download-link-page.html',
  styleUrl: './digital-download-link-page.scss',
})
export class DigitalDownloadLinkPage {
  private readonly api = inject(DigitalDownloadLinkApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);

  protected readonly files = signal<readonly DigitalOrderDownload[]>([]);
  protected readonly isLoading = signal(false);
  protected readonly errorKey = signal<string | null>(null);
  protected readonly downloadErrorKey = signal<string | null>(null);
  protected readonly downloadingItemId = signal<string | null>(null);
  protected readonly transloco = inject(TranslocoService);

  private rawToken: string | null = null;
  private requestNumber = 0;

  constructor() {
    afterNextRender(() => {
      this.route.fragment
        .pipe(distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
        .subscribe((fragment) => {
          this.rawToken = this.readToken(fragment);
          this.loadFiles();
        });
    });
  }

  protected retry(): void {
    if (!this.isLoading()) {
      this.loadFiles();
    }
  }

  protected download(file: DigitalOrderDownload): void {
    const token = this.rawToken;

    if (!token || this.isLoading() || this.downloadingItemId()) {
      return;
    }

    this.downloadErrorKey.set(null);
    this.downloadingItemId.set(file.itemId);

    this.api
      .download(token, file.itemId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.downloadingItemId.set(null)),
      )
      .subscribe({
        next: (response) => {
          if (token !== this.rawToken) {
            return;
          }

          if (!response.body) {
            this.downloadErrorKey.set('downloadLink.errors.downloadFailed');
            return;
          }

          this.saveFile(response.body, file.filename);
        },
        error: (error: unknown) => {
          if (token === this.rawToken) {
            this.downloadErrorKey.set(this.errorFor(error, true));
          }
        },
      });
  }

  private loadFiles(): void {
    const requestNumber = ++this.requestNumber;

    this.files.set([]);
    this.errorKey.set(null);
    this.downloadErrorKey.set(null);

    const token = this.rawToken;

    if (!token) {
      this.isLoading.set(false);
      this.errorKey.set('downloadLink.errors.invalidLink');
      return;
    }

    this.isLoading.set(true);

    this.api
      .getDownloads(token)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (requestNumber === this.requestNumber) {
            this.isLoading.set(false);
          }
        }),
      )
      .subscribe({
        next: (files) => {
          if (requestNumber === this.requestNumber) {
            this.files.set(files);
          }
        },
        error: (error: unknown) => {
          if (requestNumber === this.requestNumber) {
            this.errorKey.set(this.errorFor(error, false));
          }
        },
      });
  }

  private readToken(fragment: string | null): string | null {
    const values = new URLSearchParams(fragment ?? '').getAll('token');

    return values.length === 1 && /^[A-Za-z0-9_-]{43}$/.test(values[0]) ? values[0] : null;
  }

  private errorFor(error: unknown, downloading: boolean): string {
    if (error instanceof HttpErrorResponse) {
      if ([400, 401, 403, 404].includes(error.status)) {
        return 'downloadLink.errors.invalidLink';
      }

      if (error.status === 409) {
        return 'downloadLink.errors.unavailable';
      }
    }

    return downloading ? 'downloadLink.errors.downloadFailed' : 'downloadLink.errors.loadFailed';
  }

  private saveFile(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const anchor = this.document.createElement('a');

    anchor.href = url;
    anchor.download = filename.replace(/[/\\\u0000-\u001f\u007f]/g, '_');
    anchor.hidden = true;

    this.document.body.appendChild(anchor);

    try {
      anchor.click();
    } finally {
      anchor.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
  }
}
