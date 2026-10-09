import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { finalize } from 'rxjs';
import { AdminInquiryApiService } from './admin-inquiry-api.service';
import type { AdminInquiryPage } from './admin-inquiry.model';

@Component({
  selector: 'app-admin-inquiries',
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './admin-inquiries.html',
  styleUrl: './admin-inquiries.scss',
})
export class AdminInquiries {
  private readonly api = inject(AdminInquiryApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly transloco = inject(TranslocoService);

  protected readonly inquiries = signal<AdminInquiryPage | null>(null);
  protected readonly isLoading = signal(false);
  protected readonly errorKey = signal<string | null>(null);

  constructor() {
    this.loadPage(0);
  }

  protected loadPage(page: number): void {
    if (this.isLoading() || page < 0) {
      return;
    }

    this.isLoading.set(true);
    this.errorKey.set(null);

    this.api
      .getInquiries(page)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isLoading.set(false)),
      )
      .subscribe({
        next: (response) => {
          this.inquiries.set(response);
        },
        error: (error: unknown) => {
          this.errorKey.set(this.getErrorKey(error));
        },
      });
  }

  protected previousPage(): void {
    const current = this.inquiries();

    if (current && current.page > 0) {
      this.loadPage(current.page - 1);
    }
  }

  protected nextPage(): void {
    const current = this.inquiries();

    if (current && current.page + 1 < current.totalPages) {
      this.loadPage(current.page + 1);
    }
  }

  protected retry(): void {
    this.loadPage(this.inquiries()?.page ?? 0);
  }

  protected formatDate(value: string): string {
    const locale = this.transloco.getActiveLang() === 'en' ? 'en-GB' : 'pl-PL';

    return new Intl.DateTimeFormat(locale, {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(value));
  }

  private getErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        return 'adminOrders.errors.sessionExpired';
      }

      if (error.status === 403) {
        return 'adminOrders.errors.accessDenied';
      }
    }

    return 'adminInquiries.errors.loadFailed';
  }
}
