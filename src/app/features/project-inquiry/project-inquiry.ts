import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { email, form, FormField, required, validate } from '@angular/forms/signals';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import type {
  CreateInquiryRequest,
  CreateInquiryResponse,
  ProjectType,
} from './project-inquiry.model';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { ProjectInquiryApiService } from './project-inquiry-api.service';
import { ProductCatalogService } from '../products/product-catalog.service';

interface ProjectInquiryModel {
  name: string;
  email: string;
  projectType: ProjectType | '';
  description: string;
  inspirationUrl: string;
}
@Component({
  imports: [TranslocoPipe, FormField],
  selector: 'app-project-inquiry',
  styleUrl: './project-inquiry.scss',
  templateUrl: './project-inquiry.html',
})
export class ProjectInquiry {
  private readonly route = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  private readonly api = inject(ProjectInquiryApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly catalog = inject(ProductCatalogService);

  private readonly queryParams = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });

  private readonly activeLanguage = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });

  protected readonly language = computed<'pl' | 'en'>(() =>
    this.activeLanguage() === 'en' ? 'en' : 'pl',
  );

  protected readonly selectedProduct = computed(() => {
    const slug = this.queryParams().get('product');

    return this.catalog.products().find((product) => product.slug === slug);
  });

  protected readonly projectTypes: readonly ProjectType[] = [
    'embroideredProduct',
    'digitizing',
    'website',
    'other',
  ];

  protected readonly inquiryModel = signal<ProjectInquiryModel>({
    name: '',
    email: '',
    projectType: '',
    description: '',
    inspirationUrl: '',
  });

  protected readonly inquiryForm = form(this.inquiryModel, (path) => {
    required(path.name, {
      message: 'projectInquiry.errors.nameRequired',
    });

    required(path.email, {
      message: 'projectInquiry.errors.emailRequired',
    });

    email(path.email, {
      message: 'projectInquiry.errors.emailInvalid',
    });

    required(path.projectType, {
      message: 'projectInquiry.errors.projectTypeRequired',
    });

    required(path.description, {
      message: 'projectInquiry.errors.descriptionRequired',
    });

    for (const field of ['name', 'description'] as const) {
      validate(path[field], ({ value }) => {
        const text = value();

        return text.length > 0 && text.trim() === ''
          ? {
              kind: 'blank',
              message:
                field === 'name'
                  ? 'projectInquiry.errors.nameRequired'
                  : 'projectInquiry.errors.descriptionRequired',
            }
          : undefined;
      });
    }

    const limits = [
      { field: 'name', max: 150, message: 'projectInquiry.errors.nameTooLong' },
      { field: 'email', max: 254, message: 'projectInquiry.errors.emailTooLong' },
      {
        field: 'description',
        max: 5000,
        message: 'projectInquiry.errors.descriptionTooLong',
      },
      {
        field: 'inspirationUrl',
        max: 2048,
        message: 'projectInquiry.errors.inspirationUrlTooLong',
      },
    ] as const;

    for (const limit of limits) {
      validate(path[limit.field], ({ value }) =>
        value().length > limit.max
          ? {
              kind: 'maxLength',
              message: limit.message,
            }
          : undefined,
      );
    }

    validate(path.inspirationUrl, ({ value }) => {
      const input = value().trim();

      if (input === '') {
        return undefined;
      }

      const error = {
        kind: 'invalidUrl',
        message: 'projectInquiry.errors.inspirationUrlInvalid',
      };

      if (!/^https?:\/\//i.test(input) || /\s/.test(input)) {
        return error;
      }

      try {
        const url = new URL(input);

        return url.hostname && !url.username && !url.password ? undefined : error;
      } catch {
        return error;
      }
    });
  });

  protected readonly isSubmitting = signal(false);
  protected readonly createdInquiry = signal<CreateInquiryResponse | null>(null);
  protected readonly inquiryErrorKey = signal<string | null>(null);

  protected onSubmit(event: Event): void {
    event.preventDefault();

    if (this.isSubmitting() || this.createdInquiry()) {
      return;
    }

    this.inquiryErrorKey.set(null);
    if (this.queryParams().get('product') && !this.selectedProduct()) {
      const state = this.catalog.state();

      this.inquiryErrorKey.set(
        state.status === 'loading'
          ? 'projectInquiry.errors.catalogLoading'
          : state.status === 'error'
            ? 'projectInquiry.errors.catalogUnavailable'
            : 'projectInquiry.errors.productUnavailable',
      );

      return;
    }
    this.inquiryForm().markAsTouched();

    if (!this.inquiryForm().valid()) {
      const firstError = this.inquiryForm().errorSummary()[0];
      firstError?.fieldTree().focusBoundControl();
      return;
    }

    const model = this.inquiryModel();

    if (!model.projectType) {
      return;
    }

    const request: CreateInquiryRequest = {
      language: this.language(),
      name: model.name.trim(),
      email: model.email.trim(),
      projectType: model.projectType,
      description: model.description.trim(),
      inspirationUrl: model.inspirationUrl.trim() || null,
      productId: this.selectedProduct()?.id ?? null,
    };

    this.isSubmitting.set(true);

    this.api
      .createInquiry(request)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSubmitting.set(false)),
      )
      .subscribe({
        next: (confirmation) => {
          this.createdInquiry.set(confirmation);
        },
        error: (error: unknown) => {
          this.inquiryErrorKey.set(this.getInquiryErrorKey(error));
        },
      });
  }

  private getInquiryErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      switch (error.error?.code) {
        case 'INQUIRY_PRODUCT_UNAVAILABLE':
          return 'projectInquiry.errors.productUnavailable';

        case 'INQUIRY_VALIDATION_FAILED':
        case 'INQUIRY_REQUEST_INVALID':
          return 'projectInquiry.errors.requestInvalid';
      }
    }

    return 'projectInquiry.errors.submitFailed';
  }
}
