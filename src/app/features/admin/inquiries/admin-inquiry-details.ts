import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  catchError,
  combineLatest,
  defer,
  EMPTY,
  finalize,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';
import { AdminInquiryApiService } from './admin-inquiry-api.service';
import { AdminInquiryDetails as InquiryDetails } from './admin-inquiry.model';

@Component({
  selector: 'app-admin-inquiry-details',
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './admin-inquiry-details.html',
  styleUrl: './admin-inquiry-details.scss',
})
export class AdminInquiryDetails {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(AdminInquiryApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reloadRequests = new Subject<void>();

  protected readonly transloco = inject(TranslocoService);

  protected readonly inquiry = signal<InquiryDetails | null>(null);
  protected readonly isLoading = signal(false);
  protected readonly errorKey = signal<string | null>(null);

  constructor() {
    combineLatest([this.route.paramMap, this.reloadRequests.pipe(startWith(undefined))])
      .pipe(
        switchMap(([params]) =>
          defer(() => {
            const id = params.get('id');

            this.inquiry.set(null);
            this.errorKey.set(null);

            if (!id) {
              this.errorKey.set('adminInquiries.errors.notFound');
              return EMPTY;
            }

            this.isLoading.set(true);

            return this.api.getInquiry(id).pipe(
              tap((inquiry) => this.inquiry.set(inquiry)),
              catchError((error: unknown) => {
                this.errorKey.set(this.getErrorKey(error));
                return EMPTY;
              }),
              finalize(() => this.isLoading.set(false)),
            );
          }),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  protected retry(): void {
    if (this.isLoading()) {
      return;
    }

    this.reloadRequests.next();
  }

  protected formatDate(value: string): string {
    const locale = this.transloco.getActiveLang() === 'en' ? 'en-GB' : 'pl-PL';

    return new Intl.DateTimeFormat(locale, {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(value));
  }

  private getErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      switch (error.status) {
        case 400:
        case 404:
          return 'adminInquiries.errors.notFound';
        case 401:
          return 'adminOrders.errors.sessionExpired';
        case 403:
          return 'adminOrders.errors.accessDenied';
      }
    }

    return 'adminInquiries.errors.detailsLoadFailed';
  }
}
