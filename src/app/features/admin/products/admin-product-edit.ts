import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { form, FormField, required, validate } from '@angular/forms/signals';
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
  skip,
  takeUntil,
} from 'rxjs';
import { AdminProductApiService } from './admin-product-api.service';
import type { AdminProductDetails } from './admin-product.model';

interface ProductTranslationsModel {
  plName: string;
  plDescription: string;
  enName: string;
  enDescription: string;
}

@Component({
  selector: 'app-admin-product-edit',
  imports: [TranslocoPipe, RouterLink, FormField],
  templateUrl: './admin-product-edit.html',
  styleUrl: './admin-product-edit.scss',
})
export class AdminProductEdit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(AdminProductApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reloadRequests = new Subject<void>();

  protected readonly transloco = inject(TranslocoService);

  protected readonly product = signal<AdminProductDetails | null>(null);
  protected readonly isLoading = signal(false);
  protected readonly errorKey = signal<string | null>(null);
  protected readonly isSaving = signal(false);
  protected readonly saveErrorKey = signal<string | null>(null);
  protected readonly saved = signal(false);

  protected readonly translationsModel = signal<ProductTranslationsModel>({
    plName: '',
    plDescription: '',
    enName: '',
    enDescription: '',
  });

  protected readonly translationsForm = form(this.translationsModel, (path) => {
    const nameFields = [path.plName, path.enName];
    const descriptionFields = [path.plDescription, path.enDescription];

    for (const field of nameFields) {
      required(field, {
        message: 'adminProducts.edit.errors.nameRequired',
      });

      validate(field, ({ value }) => {
        const input = value();

        if (input !== '' && input.trim() === '') {
          return {
            kind: 'blank',
            message: 'adminProducts.edit.errors.nameRequired',
          };
        }

        if (input.length > 200) {
          return {
            kind: 'maxLength',
            message: 'adminProducts.edit.errors.nameTooLong',
          };
        }

        return undefined;
      });
    }

    for (const field of descriptionFields) {
      required(field, {
        message: 'adminProducts.edit.errors.descriptionRequired',
      });

      validate(field, ({ value }) => {
        const input = value();

        if (input !== '' && input.trim() === '') {
          return {
            kind: 'blank',
            message: 'adminProducts.edit.errors.descriptionRequired',
          };
        }

        if (input.length > 5000) {
          return {
            kind: 'maxLength',
            message: 'adminProducts.edit.errors.descriptionTooLong',
          };
        }

        return undefined;
      });
    }
  });

  protected readonly translationGroups = [
    {
      language: 'pl',
      name: this.translationsForm.plName,
      description: this.translationsForm.plDescription,
    },
    {
      language: 'en',
      name: this.translationsForm.enName,
      description: this.translationsForm.enDescription,
    },
  ] as const;

  constructor() {
    combineLatest([this.route.paramMap, this.reloadRequests.pipe(startWith(undefined))])
      .pipe(
        switchMap(([params]) =>
          defer(() => {
            const id = params.get('id');

            this.product.set(null);
            this.errorKey.set(null);
            this.saveErrorKey.set(null);
            this.saved.set(false);

            if (!id) {
              this.errorKey.set('adminProducts.errors.notFound');
              return EMPTY;
            }

            this.isLoading.set(true);

            return this.api.getProduct(id).pipe(
              tap((product) => this.populateForm(product)),
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

  protected onSubmit(event: Event): void {
    event.preventDefault();

    const product = this.product();

    if (!product || this.isLoading() || this.isSaving() || this.saveErrorKey()) {
      return;
    }

    this.saved.set(false);
    this.translationsForm().markAsTouched();

    if (!this.translationsForm().valid()) {
      const firstError = this.translationsForm().errorSummary()[0];
      firstError?.fieldTree().focusBoundControl();
      return;
    }

    const model = this.translationsModel();

    this.isSaving.set(true);

    this.api
      .updateTranslations(product.id, {
        pl: {
          name: model.plName.trim(),
          description: model.plDescription.trim(),
        },
        en: {
          name: model.enName.trim(),
          description: model.enDescription.trim(),
        },
        expectedVersion: product.version,
      })
      .pipe(
        takeUntil(this.route.paramMap.pipe(skip(1))),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSaving.set(false)),
      )
      .subscribe({
        next: (updated) => {
          if (this.product()?.id !== product.id) {
            return;
          }

          this.populateForm(updated);

          if (!this.errorKey()) {
            this.saved.set(true);
          }
        },
        error: (error: unknown) => {
          if (this.product()?.id !== product.id) {
            return;
          }

          this.saveErrorKey.set(this.getSaveErrorKey(error));
        },
      });
  }

  protected retry(): void {
    if (this.isLoading() || this.isSaving()) {
      return;
    }

    this.reloadRequests.next();
  }

  private populateForm(product: AdminProductDetails): void {
    const polish = product.translations.find((translation) => translation.language === 'pl');

    const english = product.translations.find((translation) => translation.language === 'en');

    if (!polish || !english) {
      this.product.set(null);
      this.errorKey.set('adminProducts.errors.translationsMissing');
      return;
    }

    this.translationsForm().reset({
      plName: polish.name,
      plDescription: polish.description,
      enName: english.name,
      enDescription: english.description,
    });

    this.product.set(product);
  }

  private getSaveErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      switch (error.status) {
        case 400:
          return 'adminProducts.edit.errors.invalidRequest';
        case 401:
          return 'adminOrders.errors.sessionExpired';
        case 403:
          return 'adminOrders.errors.accessDenied';
        case 404:
          return 'adminProducts.errors.notFound';
        case 409:
          return error.error?.code === 'PRODUCT_VERSION_CONFLICT'
            ? 'adminProducts.errors.versionConflict'
            : 'adminProducts.edit.errors.conflict';
      }
    }

    return 'adminProducts.edit.errors.saveFailed';
  }

  private getErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      switch (error.status) {
        case 400:
        case 404:
          return 'adminProducts.errors.notFound';
        case 401:
          return 'adminOrders.errors.sessionExpired';
        case 403:
          return 'adminOrders.errors.accessDenied';
      }
    }

    return 'adminProducts.errors.detailsLoadFailed';
  }
}
