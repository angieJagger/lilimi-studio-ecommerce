import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { AdminProductApiService } from './admin-product-api.service';
import { AdminProductVariants } from './admin-product-variants';
import type { AdminGarmentVariant, AdminGarmentVariantPage } from './admin-product.model';

describe('AdminProductVariants', () => {
  let fixture: ComponentFixture<AdminProductVariants>;
  let element: HTMLElement;
  let result: Subject<AdminGarmentVariantPage>;
  let saveResult: Subject<AdminGarmentVariant>;

  const api = {
    getVariants: vi.fn(),
    updateVariant: vi.fn(),
  };

  const variant: AdminGarmentVariant = {
    id: '00000000-0000-0000-0000-000000000001',
    version: 2,
    productId: 'embroidered-002',
    patternId: 'pattern-001',
    embroideryOptionId: 'small-front',
    fit: 'women',
    size: 'M',
    color: 'navy',
    priceInGrosz: 14900,
    currency: 'PLN',
    active: true,
  };

  const page: AdminGarmentVariantPage = {
    items: [variant],
    page: 0,
    size: 20,
    totalElements: 21,
    totalPages: 2,
  };

  beforeEach(async () => {
    result = new Subject<AdminGarmentVariantPage>();
    saveResult = new Subject<AdminGarmentVariant>();

    api.getVariants.mockReset();
    api.updateVariant.mockReset();
    api.getVariants.mockReturnValue(result.asObservable());
    api.updateVariant.mockReturnValue(saveResult.asObservable());

    await TestBed.configureTestingModule({
      imports: [AdminProductVariants, getTranslocoTestingModule()],
      providers: [{ provide: AdminProductApiService, useValue: api }],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminProductVariants);
    fixture.componentRef.setInput('productId', variant.productId);
    element = fixture.nativeElement;

    await fixture.whenStable();
  });

  async function respond(response: AdminGarmentVariantPage = page): Promise<void> {
    result.next(response);
    result.complete();

    await fixture.whenStable();
  }

  async function openEditor(): Promise<void> {
    element.querySelector<HTMLButtonElement>('tbody button')!.click();

    await fixture.whenStable();
  }

  async function fillPrice(value: string): Promise<void> {
    const input = element.querySelector<HTMLInputElement>('#variant-price')!;

    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));

    await fixture.whenStable();
  }

  async function submit(): Promise<void> {
    element.querySelector('#variant-edit-form')!.dispatchEvent(
      new Event('submit', {
        bubbles: true,
        cancelable: true,
      }),
    );

    await fixture.whenStable();
  }

  it('should request the first page and show loading', () => {
    expect(api.getVariants).toHaveBeenCalledExactlyOnceWith(variant.productId, 0);

    expect(element.querySelector('output')).not.toBeNull();
    expect(element.querySelector('table')).toBeNull();
  });

  it('should display variant configuration and pagination', async () => {
    await respond();

    expect(element.querySelector('tbody')?.textContent).toContain('pattern-001');
    expect(element.querySelector('tbody')?.textContent).toContain('small-front');

    const buttons = element.querySelectorAll<HTMLButtonElement>('.variants__pagination button');

    expect(buttons[0].disabled).toBe(true);
    expect(buttons[1].disabled).toBe(false);
  });

  it('should display an empty state', async () => {
    await respond({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });

    expect(element.textContent).toContain('Ten produkt nie ma wariantów.');
    expect(element.querySelector('table')).toBeNull();
  });

  it('should open the editor with the current price and activity', async () => {
    await respond();
    await openEditor();

    expect(element.querySelector<HTMLInputElement>('#variant-price')!.value).toBe('149.00');

    expect(
      element.querySelector<HTMLInputElement>('#variant-edit-form input[type="checkbox"]')!.checked,
    ).toBe(true);

    const buttons = element.querySelectorAll<HTMLButtonElement>('.variants__pagination button');

    expect(buttons[0].disabled).toBe(true);
    expect(buttons[1].disabled).toBe(true);
  });

  it('should save the price and activity with the current version', async () => {
    await respond();
    await openEditor();
    await fillPrice('189,90');

    const checkbox = element.querySelector<HTMLInputElement>(
      '#variant-edit-form input[type="checkbox"]',
    )!;

    checkbox.click();

    await fixture.whenStable();
    await submit();

    expect(api.updateVariant).toHaveBeenCalledExactlyOnceWith(variant.productId, variant.id, {
      priceInGrosz: 18990,
      active: false,
      expectedVersion: variant.version,
    });

    const updated: AdminGarmentVariant = {
      ...variant,
      priceInGrosz: 18990,
      active: false,
      version: variant.version + 1,
    };

    saveResult.next(updated);
    saveResult.complete();

    await fixture.whenStable();

    expect(element.querySelector('#variant-edit-form')).toBeNull();
    expect(element.querySelector('tbody')?.textContent).toContain('Nieaktywny');

    await openEditor();

    expect(element.querySelector<HTMLInputElement>('#variant-price')!.value).toBe('189.90');
  });

  it.each(['', '0', '-1', '12.345', 'abc', '21474836.48'])(
    'should reject an invalid price: %s',
    async (amount) => {
      await respond();
      await openEditor();
      await fillPrice(amount);
      await submit();

      expect(api.updateVariant).not.toHaveBeenCalled();
      expect(element.querySelector('#variant-price-errors')).not.toBeNull();
    },
  );
    it('should request the next page and then return to the previous page', async () => {
      await respond();

      result = new Subject<AdminGarmentVariantPage>();
      api.getVariants.mockReturnValue(result.asObservable());

      element.querySelectorAll<HTMLButtonElement>('.variants__pagination button')[1].click();

      await fixture.whenStable();

      expect(api.getVariants).toHaveBeenLastCalledWith(variant.productId, 1);

      await respond({
        ...page,
        page: 1,
      });

      result = new Subject<AdminGarmentVariantPage>();
      api.getVariants.mockReturnValue(result.asObservable());

      element.querySelectorAll<HTMLButtonElement>('.variants__pagination button')[0].click();

      await fixture.whenStable();

      expect(api.getVariants).toHaveBeenLastCalledWith(variant.productId, 0);

      await respond();
    });

    it('should prevent duplicate saves while one save is pending', async () => {
      await respond();
      await openEditor();
      await fillPrice('189,90');

      await submit();
      await submit();

      expect(api.updateVariant).toHaveBeenCalledTimes(1);

      expect(
        element.querySelector<HTMLFieldSetElement>('#variant-edit-form fieldset')!.disabled,
      ).toBe(true);

      saveResult.next({
        ...variant,
        priceInGrosz: 18990,
        version: variant.version + 1,
      });
      saveResult.complete();

      await fixture.whenStable();

      expect(element.querySelector('#variant-edit-form')).toBeNull();
    });

    it.each([409, 0])(
      'should preserve the draft after HTTP %s and require a reload',
      async (status) => {
        await respond();
        await openEditor();
        await fillPrice('189,90');
        await submit();

        saveResult.error(
          new HttpErrorResponse({
            status,
            error: status === 409 ? { code: 'GARMENT_VARIANT_VERSION_CONFLICT' } : null,
          }),
        );

        await fixture.whenStable();

        expect(element.querySelector('[role="alert"]')).not.toBeNull();

        expect(element.querySelector<HTMLInputElement>('#variant-price')!.value).toBe('189,90');

        expect(
          element.querySelector<HTMLFieldSetElement>('#variant-edit-form fieldset')!.disabled,
        ).toBe(true);

        await submit();

        expect(api.updateVariant).toHaveBeenCalledTimes(1);

        result = new Subject<AdminGarmentVariantPage>();
        api.getVariants.mockReturnValue(result.asObservable());

        element.querySelector<HTMLButtonElement>('.variants__feedback button')!.click();

        await fixture.whenStable();

        expect(api.getVariants).toHaveBeenLastCalledWith(variant.productId, 0);

        const refreshed: AdminGarmentVariant = {
          ...variant,
          version: variant.version + 1,
          priceInGrosz: 19900,
        };

        await respond({
          ...page,
          items: [refreshed],
        });

        expect(element.querySelector('[role="alert"]')).toBeNull();
        expect(element.querySelector('#variant-edit-form')).toBeNull();

        await openEditor();

        expect(element.querySelector<HTMLInputElement>('#variant-price')!.value).toBe('199.00');

        saveResult = new Subject<AdminGarmentVariant>();
        api.updateVariant.mockReturnValue(saveResult.asObservable());

        await fillPrice('209');
        await submit();

        expect(api.updateVariant).toHaveBeenLastCalledWith(variant.productId, variant.id, {
          priceInGrosz: 20900,
          active: refreshed.active,
          expectedVersion: refreshed.version,
        });

        saveResult.next({
          ...refreshed,
          version: refreshed.version + 1,
          priceInGrosz: 20900,
        });
        saveResult.complete();

        await fixture.whenStable();
      },
    );
});
