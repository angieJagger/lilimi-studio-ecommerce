import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { finalize } from 'rxjs';
import { CustomerProfileApiService } from '../../core/customer/customer-profile-api.service';
import type { CustomerProfile as ProfileData } from '../../core/customer/customer-profile.model';

const profileValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = control.getRawValue();
  const name = [value.firstName.trim(), value.lastName.trim()].filter(Boolean).join(' ');
  const errors: ValidationErrors = {};
  if (name.length > 150) errors['fullNameLength'] = true;
  if (
    value.saveAddress &&
    (!value.addressLine1.trim() ||
      !value.city.trim() ||
      !/^[0-9]{2}-[0-9]{3}$/.test(value.postalCode.trim()))
  )
    errors['addressIncomplete'] = true;
  return Object.keys(errors).length ? errors : null;
};

@Component({
  selector: 'app-customer-profile',
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe],
  templateUrl: './customer-profile.html',
  styleUrl: './customer-profile.scss',
})
export class CustomerProfile {
  private readonly api = inject(CustomerProfileApiService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private version: number | null = null;

  protected readonly transloco = inject(TranslocoService);
  protected readonly isLoading = signal(false);
  protected readonly isSaving = signal(false);
  protected readonly loaded = signal(false);
  protected readonly profile = signal<ProfileData | null>(null);
  protected readonly editing = signal(false);
  protected readonly confirmingDelete = signal(false);
  protected readonly isDeleting = signal(false);
  protected readonly noticeKey = signal<string | null>(null);
  protected readonly busy = computed(
    () => this.isLoading() || this.isSaving() || this.isDeleting(),
  );
  protected readonly hasDetails = computed(() => {
    const profile = this.profile();
    return Boolean(profile && (
      profile.firstName || profile.lastName || profile.phone ||
      profile.addressLine1 || profile.addressLine2 ||
      profile.postalCode || profile.city || profile.countryCode
    ));
  });
  protected readonly conflict = signal(false);
  protected readonly accessBlocked = signal(false);
  protected readonly email = signal('');
  protected readonly errorKey = signal<string | null>(null);

  protected readonly contactFields = [
    { name: 'firstName', autocomplete: 'given-name', max: 100, type: 'text' },
    { name: 'lastName', autocomplete: 'family-name', max: 100, type: 'text' },
    { name: 'phone', autocomplete: 'tel', max: 30, type: 'tel' },
  ] as const;

  protected readonly addressFields = [
    { name: 'addressLine1', autocomplete: 'address-line1', max: 200 },
    { name: 'addressLine2', autocomplete: 'address-line2', max: 200 },
    { name: 'postalCode', autocomplete: 'postal-code', max: 6 },
    { name: 'city', autocomplete: 'address-level2', max: 100 },
  ] as const;

  protected readonly form = this.fb.nonNullable.group(
    {
      firstName: ['', Validators.maxLength(100)],
      lastName: ['', Validators.maxLength(100)],
      phone: ['', Validators.maxLength(30)],
      saveAddress: false,
      addressLine1: ['', Validators.maxLength(200)],
      addressLine2: ['', Validators.maxLength(200)],
      postalCode: ['', Validators.maxLength(6)],
      city: ['', Validators.maxLength(100)],
    },
    { validators: profileValidator },
  );

  constructor() {
    this.form.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.noticeKey.set(null));
    this.load();
  }

  protected load(): void {
    if (this.busy() || this.accessBlocked()) return;
    this.isLoading.set(true);
    this.errorKey.set(null);
    this.noticeKey.set(null);
    this.form.disable({ emitEvent: false });

    this.api
      .getProfile()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isLoading.set(false);
          if (!this.accessBlocked()) this.form.enable({ emitEvent: false });
        }),
      )
      .subscribe({
        next: (profile) => {
          this.applyProfile(profile);
          this.loaded.set(true);
          this.conflict.set(false);
          this.editing.set(false);
          this.confirmingDelete.set(false);
        },
        error: (error: unknown) => this.handleError(error, 'loadFailed'),
      });
  }

  protected submit(): void {
    if (
      !this.editing() ||
      !this.loaded() ||
      this.isDeleting() ||
      this.isSaving() ||
      this.isLoading() ||
      this.conflict() ||
      this.accessBlocked()
    )
      return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;

    const value = this.form.getRawValue();
    const nullable = (text: string): string | null => text.trim() || null;
    const request = {
      firstName: nullable(value.firstName),
      lastName: nullable(value.lastName),
      phone: nullable(value.phone),
      addressLine1: value.saveAddress ? nullable(value.addressLine1) : null,
      addressLine2: value.saveAddress ? nullable(value.addressLine2) : null,
      postalCode: value.saveAddress ? nullable(value.postalCode) : null,
      city: value.saveAddress ? nullable(value.city) : null,
      countryCode: value.saveAddress ? 'PL' : null,
      version: this.version,
    };

    this.noticeKey.set(null);
    this.errorKey.set(null);
    this.isSaving.set(true);
    this.form.disable({ emitEvent: false });
    this.api
      .updateProfile(request)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isSaving.set(false);
          if (!this.accessBlocked()) this.form.enable({ emitEvent: false });
        }),
      )
      .subscribe({
        next: (profile) => {
          this.applyProfile(profile);
          this.editing.set(false);
          this.noticeKey.set('customerProfile.saved');
        },
        error: (error: unknown) => this.handleError(error, 'saveFailed'),
      });
  }

  protected startEditing(): void {
    const profile = this.profile();
    if (!profile || this.busy() || this.accessBlocked() || this.conflict()) return;
    this.applyProfile(profile);
    this.noticeKey.set(null);
    this.errorKey.set(null);
    this.confirmingDelete.set(false);
    this.editing.set(true);
  }

  protected cancelEditing(): void {
    const profile = this.profile();
    if (!profile || this.busy()) return;
    this.applyProfile(profile);
    this.errorKey.set(this.conflict() ? 'customerProfile.errors.conflict' : null);
    this.editing.set(false);
  }

  protected requestDelete(): void {
    if (this.busy() || this.conflict() || !this.hasDetails()) return;
    this.noticeKey.set(null);
    this.errorKey.set(null);
    this.confirmingDelete.set(true);
  }

  protected cancelDelete(): void {
    if (this.busy()) return;
    this.confirmingDelete.set(false);
    this.errorKey.set(null);
  }

  protected deleteDetails(): void {
    if (!this.confirmingDelete() || this.busy() || this.conflict() ||
      this.accessBlocked() || !this.hasDetails()) return;

    this.isDeleting.set(true);
    this.errorKey.set(null);
    this.api.updateProfile({
      firstName: null,
      lastName: null,
      phone: null,
      addressLine1: null,
      addressLine2: null,
      postalCode: null,
      city: null,
      countryCode: null,
      version: this.version,
    }).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.isDeleting.set(false)),
    ).subscribe({
      next: (profile) => {
        this.applyProfile(profile);
        this.confirmingDelete.set(false);
        this.noticeKey.set('customerProfile.deleted');
      },
      error: (error: unknown) => this.handleError(error, 'deleteFailed'),
    });
  }

  private applyProfile(profile: ProfileData): void {
    this.profile.set(profile);
    this.version = profile.version;
    this.email.set(profile.email);
    this.form.reset(
      {
        firstName: profile.firstName ?? '',
        lastName: profile.lastName ?? '',
        phone: profile.phone ?? '',
        saveAddress: profile.addressLine1 !== null,
        addressLine1: profile.addressLine1 ?? '',
        addressLine2: profile.addressLine2 ?? '',
        postalCode: profile.postalCode ?? '',
        city: profile.city ?? '',
      },
      { emitEvent: false },
    );
  }

  private handleError(error: unknown, fallback: string): void {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 401 || status === 403) {
      this.accessBlocked.set(true);
      this.loaded.set(false);
      this.email.set('');
      this.profile.set(null);
      this.editing.set(false);
      this.confirmingDelete.set(false);
      this.form.reset({}, { emitEvent: false });
      this.errorKey.set(
        'customerProfile.errors.' + (status === 401 ? 'sessionExpired' : 'accessDenied'),
      );
    } else if (status === 409) {
      this.conflict.set(true);
      this.errorKey.set('customerProfile.errors.conflict');
    } else {
      this.errorKey.set('customerProfile.errors.' + (status === 400 ? 'invalid' : fallback));
    }
  }
}
