import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import { CustomerProfileApiService } from '../../core/customer/customer-profile-api.service';
import type { CustomerProfile as ProfileData } from '../../core/customer/customer-profile.model';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { CustomerProfile } from './customer-profile';

describe('CustomerProfile', () => {
  let fixture: ComponentFixture<CustomerProfile>;
  let element: HTMLElement;
  let loading: Subject<ProfileData>;
  let saving: Subject<ProfileData>;
  const api = { getProfile: vi.fn(), updateProfile: vi.fn() };
  const profile: ProfileData = {
    email: 'anna@example.com', firstName: 'Anna', lastName: 'Kowalska',
    phone: null, addressLine1: null, addressLine2: null, postalCode: null,
    city: null, countryCode: null, version: 3,
  };
  const empty: ProfileData = {
    ...profile, firstName: null, lastName: null, version: null,
  };

  beforeEach(async () => {
    loading = new Subject<ProfileData>();
    saving = new Subject<ProfileData>();
    api.getProfile.mockReset().mockReturnValue(loading.asObservable());
    api.updateProfile.mockReset().mockReturnValue(saving.asObservable());
    await TestBed.configureTestingModule({
      imports: [CustomerProfile, getTranslocoTestingModule()],
      providers: [provideRouter([]), { provide: CustomerProfileApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(CustomerProfile);
    element = fixture.nativeElement;
    await fixture.whenStable();
  });

  async function loaded(value: ProfileData = profile): Promise<void> {
    loading.next(value);
    loading.complete();
    await fixture.whenStable();
  }

  function input(name: string): HTMLInputElement {
    return element.querySelector<HTMLInputElement>('#profile-' + name)!;
  }

  async function action(name: string): Promise<void> {
    element.querySelector<HTMLButtonElement>('[data-action="' + name + '"]')!.click();
    await fixture.whenStable();
  }

  async function fill(name: string, value: string): Promise<void> {
    input(name).value = value;
    input(name).dispatchEvent(new Event('input', { bubbles: true }));
    await fixture.whenStable();
  }

  async function submit(): Promise<void> {
    element.querySelector('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );
    await fixture.whenStable();
  }

  async function toggleAddress(): Promise<void> {
    element.querySelector<HTMLInputElement>('[formControlName="saveAddress"]')!.click();
    await fixture.whenStable();
  }

  async function cancelEditing(): Promise<void> {
    element.querySelector<HTMLButtonElement>('form button[type="button"]')!.click();
    await fixture.whenStable();
  }

  it('shows saved details as a read-only summary', async () => {
    await loaded();
    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('input')).toBeNull();
    expect(element.querySelector('.customer-profile__cards')?.textContent).toContain('Anna');
    expect(element.textContent).toContain(profile.email);
    expect(element.querySelector('[data-action="edit"]')).not.toBeNull();
    expect(element.querySelector('[data-action="delete"]')).not.toBeNull();
  });

  it('offers adding details for an empty profile', async () => {
    await loaded(empty);
    expect(element.querySelector('.customer-profile__empty')).not.toBeNull();
    expect(element.querySelector('[data-action="delete"]')).toBeNull();
    await action('edit');
    expect(element.querySelector('form')).not.toBeNull();
    expect(input('firstName').value).toBe('');
    await fill('firstName', 'Anna');
    await submit();
    expect(api.updateProfile.mock.calls[0][0]).toMatchObject({
      firstName: 'Anna', version: null,
    });
  });

  it('cancels editing without saving and restores the saved values', async () => {
    await loaded();
    await action('edit');
    await fill('firstName', 'Unsaved');
    await cancelEditing();
    expect(element.querySelector('form')).toBeNull();
    expect(api.updateProfile).not.toHaveBeenCalled();
    expect(element.querySelector('.customer-profile__cards')?.textContent).toContain('Anna');
    await action('edit');
    expect(input('firstName').value).toBe('Anna');
  });

  it('saves once then returns to the updated summary with confirmation', async () => {
    await loaded();
    await action('edit');
    await fill('firstName', '  Maria  ');
    await submit();
    await submit();
    expect(api.updateProfile).toHaveBeenCalledExactlyOnceWith({
      firstName: 'Maria', lastName: 'Kowalska', phone: null,
      addressLine1: null, addressLine2: null, postalCode: null,
      city: null, countryCode: null, version: 3,
    });
    expect(input('firstName').disabled).toBe(true);
    saving.next({ ...profile, firstName: 'Maria', version: 4 });
    saving.complete();
    await fixture.whenStable();
    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('.customer-profile__cards')?.textContent).toContain('Maria');
    expect(element.querySelector('output.customer-profile__success')).not.toBeNull();
    await action('edit');
    saving = new Subject<ProfileData>();
    api.updateProfile.mockReturnValue(saving.asObservable());
    await fill('lastName', 'Nowak');
    await submit();
    expect(api.updateProfile.mock.calls[1][0]).toMatchObject({ version: 4 });
  });

  it('keeps the editable form and entries after a failed save', async () => {
    await loaded();
    await action('edit');
    await fill('firstName', 'Maria');
    await submit();
    saving.error(new HttpErrorResponse({ status: 503 }));
    await fixture.whenStable();
    expect(input('firstName').value).toBe('Maria');
    expect(input('firstName').disabled).toBe(false);
    expect(element.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('rejects an incomplete address and invalid postal code', async () => {
    await loaded();
    await action('edit');
    await toggleAddress();
    await fill('addressLine1', 'Testowa 12');
    await submit();
    expect(api.updateProfile).not.toHaveBeenCalled();
    await fill('city', 'Warszawa');
    await fill('postalCode', '00001');
    await submit();
    expect(api.updateProfile).not.toHaveBeenCalled();
    await fill('postalCode', '00-001');
    await submit();
    expect(api.updateProfile.mock.calls[0][0]).toMatchObject({
      addressLine1: 'Testowa 12', city: 'Warszawa',
      postalCode: '00-001', countryCode: 'PL',
    });
  });

  it('rejects a full name exceeding the checkout limit', async () => {
    await loaded();
    await action('edit');
    await fill('firstName', 'a'.repeat(75));
    await fill('lastName', 'b'.repeat(75));
    await submit();
    expect(api.updateProfile).not.toHaveBeenCalled();
    expect(element.querySelector('#profile-validation')).not.toBeNull();
  });

  it('shows a saved address in the summary and allows clearing only the address', async () => {
    await loaded({
      ...profile, addressLine1: 'Testowa 12', city: 'Warszawa',
      postalCode: '00-001', countryCode: 'PL',
    });
    expect(element.querySelector('address')?.textContent).toContain('Testowa 12');
    await action('edit');
    await toggleAddress();
    await submit();
    expect(api.updateProfile.mock.calls[0][0]).toMatchObject({
      firstName: 'Anna', addressLine1: null, city: null,
      postalCode: null, countryCode: null,
    });
  });

  it('requires confirmation before removing details and allows cancellation', async () => {
    await loaded();
    await action('delete');
    expect(element.querySelector('.customer-profile__confirmation')).not.toBeNull();
    expect(api.updateProfile).not.toHaveBeenCalled();
    element.querySelector<HTMLButtonElement>('.customer-profile__confirmation button')!.click();
    await fixture.whenStable();
    expect(element.querySelector('.customer-profile__confirmation')).toBeNull();
    expect(api.updateProfile).not.toHaveBeenCalled();
  });

  it('removes saved fields with version protection and keeps the account email', async () => {
    await loaded();
    await action('delete');
    await action('confirm-delete');
    await action('confirm-delete');
    expect(api.updateProfile).toHaveBeenCalledExactlyOnceWith({
      firstName: null, lastName: null, phone: null,
      addressLine1: null, addressLine2: null, postalCode: null,
      city: null, countryCode: null, version: 3,
    });
    saving.next({ ...empty, version: 4 });
    saving.complete();
    await fixture.whenStable();
    expect(element.querySelector('.customer-profile__empty')).not.toBeNull();
    expect(element.querySelector('.customer-profile__confirmation')).toBeNull();
    expect(element.textContent).toContain(profile.email);
    expect(element.querySelector('output.customer-profile__success')).not.toBeNull();
    await action('edit');
    saving = new Subject<ProfileData>();
    api.updateProfile.mockReturnValue(saving.asObservable());
    await fill('firstName', 'Anna');
    await submit();
    expect(api.updateProfile.mock.calls[1][0]).toMatchObject({ version: 4 });
  });

  it('preserves the summary after a failed removal', async () => {
    await loaded();
    await action('delete');
    await action('confirm-delete');
    saving.error(new HttpErrorResponse({ status: 503 }));
    await fixture.whenStable();
    expect(element.querySelector('.customer-profile__cards')?.textContent).toContain('Anna');
    expect(element.querySelector('.customer-profile__confirmation')).not.toBeNull();
    expect(element.querySelector('[role="alert"]')).not.toBeNull();
    expect(element.querySelector<HTMLButtonElement>('[data-action="confirm-delete"]')!.disabled)
      .toBe(false);
  });

  it('preserves conflicting edits until the user reloads', async () => {
    await loaded();
    await action('edit');
    await fill('firstName', 'Maria');
    await submit();
    saving.error(new HttpErrorResponse({ status: 409 }));
    await fixture.whenStable();
    expect(input('firstName').value).toBe('Maria');
    await submit();
    expect(api.updateProfile).toHaveBeenCalledTimes(1);
    loading = new Subject<ProfileData>();
    api.getProfile.mockReturnValue(loading.asObservable());
    await action('reload');
    await loaded({ ...profile, firstName: 'Ewa', version: 5 });
    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('.customer-profile__cards')?.textContent).toContain('Ewa');
  });

  it('does not retry a conflicting removal without reloading', async () => {
    await loaded();
    await action('delete');
    await action('confirm-delete');
    saving.error(new HttpErrorResponse({ status: 409 }));
    await fixture.whenStable();
    expect(element.querySelector<HTMLButtonElement>('[data-action="confirm-delete"]')!.disabled)
      .toBe(true);
    expect(element.querySelector('.customer-profile__cards')?.textContent).toContain('Anna');
    expect(element.querySelector('[data-action="reload"]')).not.toBeNull();
  });

  it('removes personal details from the page when the session expires', async () => {
    await loaded();
    await action('edit');
    await submit();
    saving.error(new HttpErrorResponse({ status: 401 }));
    await fixture.whenStable();
    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('.customer-profile__cards')).toBeNull();
    expect(element.textContent).not.toContain(profile.email);
    expect(element.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('does not offer editing after access is denied', async () => {
    loading.error(new HttpErrorResponse({ status: 403 }));
    await fixture.whenStable();
    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('[data-action="edit"]')).toBeNull();
    expect(element.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('allows retry after a failed initial load', async () => {
    loading.error(new HttpErrorResponse({ status: 503 }));
    await fixture.whenStable();
    loading = new Subject<ProfileData>();
    api.getProfile.mockReturnValue(loading.asObservable());
    await action('reload');
    await loaded();
    expect(api.getProfile).toHaveBeenCalledTimes(2);
    expect(element.querySelector('.customer-profile__cards')?.textContent).toContain('Anna');
  });
});
