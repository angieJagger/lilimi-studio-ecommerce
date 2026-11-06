import { HttpErrorResponse } from '@angular/common/http';
import { DOCUMENT } from '@angular/common';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  catchError,
  combineLatest,
  defer,
  EMPTY,
  finalize,
  forkJoin,
  of,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';
import { AdminInquiryApiService } from './admin-inquiry-api.service';
import type { AdminInquiryDetails as InquiryDetails, InquiryStatus,  AdminInquiryAttachment } from './admin-inquiry.model';

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
  private readonly document = inject(DOCUMENT);
  private readonly reloadRequests = new Subject<void>();

  protected readonly transloco = inject(TranslocoService);

  protected readonly inquiry = signal<InquiryDetails | null>(null);
  protected readonly isLoading = signal(false);
  protected readonly errorKey = signal<string | null>(null);

  protected readonly isSaving = signal(false);
  protected readonly pendingStatus = signal<InquiryStatus | null>(null);
  protected readonly statusErrorKey = signal<string | null>(null);

  protected readonly availableStatuses = computed<readonly InquiryStatus[]>(() => {
    const inquiry = this.inquiry();

    if (!inquiry) {
      return [];
    }

    switch (inquiry.status) {
      case 'new':
        return ['in_progress', 'closed'];
      case 'in_progress':
        return ['answered', 'closed'];
      case 'answered':
        return ['in_progress', 'closed'];
      case 'closed':
        return [];
    }
  });

  protected readonly attachments = signal<readonly AdminInquiryAttachment[]>([]);
  protected readonly areAttachmentsLoading = signal(false);
  protected readonly attachmentsErrorKey = signal<string | null>(null);
  protected readonly downloadingAttachmentId = signal<string | null>(null);
  protected readonly downloadErrorKey = signal<string | null>(null);

  constructor() {
    combineLatest([this.route.paramMap, this.reloadRequests.pipe(startWith(undefined))])
      .pipe(
        switchMap(([params]) =>
          defer(() => {
            const id = params.get('id');

            this.inquiry.set(null);
            this.attachments.set([]);
            this.attachmentsErrorKey.set(null);
            this.areAttachmentsLoading.set(false);
            this.downloadErrorKey.set(null);
            this.errorKey.set(null);
            this.pendingStatus.set(null);
            this.statusErrorKey.set(null);

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
              switchMap((inquiry) => {
                this.areAttachmentsLoading.set(true);

                return forkJoin({
                  read: this.api.markRead(inquiry.id).pipe(catchError(() => of(undefined))),
                  attachments: this.api.getAttachments(inquiry.id).pipe(
                    tap((attachments) => this.attachments.set(attachments)),
                    catchError((error: unknown) => {
                      this.attachmentsErrorKey.set(this.getAttachmentErrorKey(error));
                      return of([]);
                    }),
                    finalize(() => this.areAttachmentsLoading.set(false)),
                  ),
                });
              }),
            );
          }),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  protected downloadAttachment(attachment: AdminInquiryAttachment): void {
    const inquiry = this.inquiry();

    if (
      !inquiry ||
      this.downloadingAttachmentId() ||
      !this.attachments().some((item) => item.id === attachment.id)
    ) {
      return;
    }

    this.downloadingAttachmentId.set(attachment.id);
    this.downloadErrorKey.set(null);

    this.api
      .downloadAttachment(inquiry.id, attachment.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.downloadingAttachmentId.set(null)),
      )
      .subscribe({
        next: (blob) => {
          if (this.inquiry()?.id !== inquiry.id) {
            return;
          }

          const url = URL.createObjectURL(blob);
          const link = this.document.createElement('a');

          link.href = url;
          link.download = attachment.originalFilename;
          this.document.body.appendChild(link);

          try {
            link.click();
          } finally {
            link.remove();

            setTimeout(() => URL.revokeObjectURL(url), 60_000);
          }
        },
        error: (error: unknown) => {
          if (this.inquiry()?.id !== inquiry.id) {
            return;
          }

          let message = 'adminInquiries.attachments.errors.downloadFailed';

          if (error instanceof HttpErrorResponse) {
            switch (error.status) {
              case 401:
                message = 'adminOrders.errors.sessionExpired';
                break;
              case 403:
                message = 'adminOrders.errors.accessDenied';
                break;
            }
          }

          this.downloadErrorKey.set(message);
        },
      });
  }

  protected requestStatusChange(status: InquiryStatus): void {
    if (
      this.isLoading() ||
      this.isSaving() ||
      this.statusErrorKey() ||
      !this.availableStatuses().includes(status)
    ) {
      return;
    }

    this.pendingStatus.set(status);
  }

  protected cancelStatusChange(): void {
    if (this.isSaving()) {
      return;
    }

    this.pendingStatus.set(null);
  }

  protected confirmStatusChange(): void {
    const inquiry = this.inquiry();
    const status = this.pendingStatus();

    if (
      !inquiry ||
      !status ||
      this.isLoading() ||
      this.isSaving() ||
      this.statusErrorKey() ||
      !this.availableStatuses().includes(status)
    ) {
      return;
    }

    this.isSaving.set(true);

    this.api
      .changeStatus(inquiry.id, {
        status,
        expectedVersion: inquiry.version,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSaving.set(false)),
      )
      .subscribe({
        next: (updated) => {
          if (this.inquiry()?.id !== inquiry.id) {
            return;
          }

          this.inquiry.set(updated);
          this.pendingStatus.set(null);
          this.statusErrorKey.set(null);
        },
        error: (error: unknown) => {
          if (this.inquiry()?.id !== inquiry.id) {
            return;
          }

          this.pendingStatus.set(null);
          this.statusErrorKey.set(this.getStatusErrorKey(error));
        },
      });
  }

  protected retry(): void {
    if (this.isLoading() || this.isSaving()) {
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

  private getStatusErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        return 'adminOrders.errors.sessionExpired';
      }

      if (error.status === 403) {
        return 'adminOrders.errors.accessDenied';
      }

      if (error.status === 404) {
        return 'adminInquiries.errors.notFound';
      }

      if (error.status === 409) {
        switch (error.error?.code) {
          case 'INQUIRY_VERSION_CONFLICT':
            return 'adminInquiries.errors.versionConflict';
          case 'INQUIRY_STATUS_TRANSITION_INVALID':
            return 'adminInquiries.errors.statusTransitionInvalid';
        }
      }
    }

    return 'adminInquiries.errors.statusSaveFailed';
  }

  private getAttachmentErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        return 'adminOrders.errors.sessionExpired';
      }

      if (error.status === 403) {
        return 'adminOrders.errors.accessDenied';
      }
    }

    return 'adminInquiries.attachments.errors.loadFailed';
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
