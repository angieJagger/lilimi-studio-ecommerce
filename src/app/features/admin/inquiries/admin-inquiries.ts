import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
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
import type { ProjectType } from '../../project-inquiry/project-inquiry.model';
import { AdminInquiryApiService } from './admin-inquiry-api.service';
import type {
  AdminInquiryFilters,
  AdminInquiryPage,
  AdminInquiryView,
  InquiryStatus,
} from './admin-inquiry.model';

@Component({
  selector: 'app-admin-inquiries',
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './admin-inquiries.html',
  styleUrl: './admin-inquiries.scss',
})
export class AdminInquiries {
  private readonly api = inject(AdminInquiryApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reload = new Subject<void>();

  protected readonly transloco = inject(TranslocoService);

  protected readonly inquiries = signal<AdminInquiryPage | null>(null);
  protected readonly isLoading = signal(false);
  protected readonly errorKey = signal<string | null>(null);
  protected readonly filterErrorKey = signal<string | null>(null);
  protected readonly filters = signal<AdminInquiryFilters>({
    view: 'active',
  });

  protected readonly views: readonly AdminInquiryView[] = ['active', 'answered', 'archive', 'all'];

  protected readonly statuses: readonly InquiryStatus[] = [
    'new',
    'in_progress',
    'answered',
    'closed',
  ];

  protected readonly projectTypes: readonly ProjectType[] = [
    'embroideredProduct',
    'digitizing',
    'website',
    'uxUi',
    'wordpress',
    'graphicDesign',
    'other',
  ];

  constructor() {
    combineLatest([this.route.queryParamMap, this.reload.pipe(startWith(undefined))])
      .pipe(
        switchMap(([params]) =>
          defer(() => {
            const filters = this.readFilters(params);
            const page = this.readPage(params);

            this.filters.set(filters);
            this.inquiries.set(null);
            this.errorKey.set(null);
            this.filterErrorKey.set(null);
            this.isLoading.set(true);

            return this.api.getInquiries(page, 20, filters).pipe(
              tap((response) => this.inquiries.set(response)),
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

  protected selectView(view: AdminInquiryView): void {
    this.navigate({
      ...this.filters(),
      view,
      status: undefined,
    });
  }

  protected applyFilters(
    status: string,
    projectType: string,
    dateFrom: string,
    dateTo: string,
    search: string,
  ): void {
    const phrase = search.trim();

    if (phrase.length > 254) {
      this.filterErrorKey.set('adminInquiries.filters.searchTooLong');
      return;
    }

    if (phrase && phrase !== (this.filters().search ?? '')) {
      this.filterErrorKey.set(null);
      this.navigate({
        view: 'all',
        search: phrase,
      });
      return;
    }

    if (dateFrom && dateTo && dateFrom > dateTo) {
      this.filterErrorKey.set('adminInquiries.filters.invalidDateRange');
      return;
    }

    this.filterErrorKey.set(null);

    this.navigate({
      ...this.filters(),
      status: this.readStatus(status),
      projectType: this.readProjectType(projectType),
      dateFrom: dateFrom || undefined,
      dateTo: dateTo || undefined,
      search: phrase || undefined,
    });
  }

  protected resetFilters(): void {
    this.filterErrorKey.set(null);
    this.navigate({ view: this.filters().view ?? 'active' });
  }

  protected previousPage(): void {
    const current = this.inquiries();

    if (current && current.page > 0) {
      this.navigate(this.filters(), current.page - 1);
    }
  }

  protected nextPage(): void {
    const current = this.inquiries();

    if (current && current.page + 1 < current.totalPages) {
      this.navigate(this.filters(), current.page + 1);
    }
  }

  protected retry(): void {
    if (!this.isLoading()) {
      this.reload.next();
    }
  }

  protected formatDate(value: string): string {
    const locale = this.transloco.getActiveLang() === 'en' ? 'en-GB' : 'pl-PL';

    return new Intl.DateTimeFormat(locale, {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(value));
  }

  private navigate(filters: AdminInquiryFilters, page = 0): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        view: filters.view ?? 'active',
        status: filters.status ?? null,
        projectType: filters.projectType ?? null,
        dateFrom: filters.dateFrom ?? null,
        dateTo: filters.dateTo ?? null,
        search: filters.search ?? null,
        page: page > 0 ? page : null,
      },
    });
  }

  private readFilters(params: ParamMap): AdminInquiryFilters {
    const view = this.views.find((value) => value === params.get('view')) ?? 'active';

    return {
      view,
      status: this.readStatus(params.get('status')),
      projectType: this.readProjectType(params.get('projectType')),
      dateFrom: params.get('dateFrom') || undefined,
      dateTo: params.get('dateTo') || undefined,
      search: params.get('search')?.trim() || undefined,
    };
  }

  private readStatus(value: string | null): InquiryStatus | undefined {
    return this.statuses.find((status) => status === value);
  }

  private readProjectType(value: string | null): ProjectType | undefined {
    return this.projectTypes.find((projectType) => projectType === value);
  }

  private readPage(params: ParamMap): number {
    const value = Number(params.get('page') ?? '0');

    return Number.isSafeInteger(value) && value >= 0 ? value : 0;
  }

  private getErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 400) {
        return 'adminInquiries.filters.invalidParameters';
      }

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
