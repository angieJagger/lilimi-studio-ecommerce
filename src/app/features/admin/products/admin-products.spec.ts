import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { AdminProductApiService } from './admin-product-api.service';
import type {
  AdminProductPage,
  AdminProductSummary,
} from './admin-product.model';
import { AdminProducts } from './admin-products';

describe('AdminProducts', () => {
  let fixture: ComponentFixture<AdminProducts>;
  let element: HTMLElement;
  let result: Subject<AdminProductPage>;
  let visibilityResult: Subject<AdminProductSummary>;

  const api = {
  getProducts: vi.fn(),
  changeVisibility: vi.fn(),
};

  const page: AdminProductPage = {
    items: [
      {
        id: 'pattern-001',
        version: 0,
        slug: 'forest-dragon',
        productType: 'digital',
        active: false,
        madeToOrder: false,
        personalizationAvailable: false,
      },
    ],
    page: 0,
    size: 20,
    totalElements: 21,
    totalPages: 2,
  };

  beforeEach(async () => {
    result = new Subject<AdminProductPage>();
    visibilityResult = new Subject<AdminProductSummary>();

    api.getProducts.mockReset();
    api.getProducts.mockReturnValue(result.asObservable());
    api.changeVisibility.mockReset();
    api.changeVisibility.mockReturnValue(visibilityResult.asObservable());

    await TestBed.configureTestingModule({
      imports: [AdminProducts, getTranslocoTestingModule()],
      providers: [provideRouter([]), { provide: AdminProductApiService, useValue: api }],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminProducts);
    element = fixture.nativeElement;

    await fixture.whenStable();
  });

  async function respond(response: AdminProductPage): Promise<void> {
    result.next(response);
    result.complete();

    await fixture.whenStable();
  }

  function prepareNextResponse(): void {
    result = new Subject<AdminProductPage>();
    api.getProducts.mockReturnValue(result.asObservable());
  }

  it('should request the first page and show loading', () => {
    expect(api.getProducts).toHaveBeenCalledExactlyOnceWith(0);
    expect(element.querySelector('output')?.textContent).toContain('Ładowanie produktów');
    expect(element.querySelector('table')).toBeNull();
  });

  it('should display an inactive product and its type', async () => {
    await respond(page);

    const row = element.querySelector('tbody tr')!;

    expect(row.textContent).toContain('forest-dragon');
    expect(row.textContent).toContain('pattern-001');
    expect(row.textContent).toContain('Wzór cyfrowy');
    expect(row.textContent).toContain('Ukryty');
    expect(element.querySelector('output')).toBeNull();
  });

  it('should show an empty state without a table or pagination', async () => {
    await respond({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });

    expect(element.textContent).toContain('Brak produktów.');
    expect(element.querySelector('table')).toBeNull();
    expect(element.querySelector('nav')).toBeNull();
  });

  it('should allow retrying after a loading error', async () => {
    result.error(new HttpErrorResponse({ status: 503 }));

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się pobrać produktów.',
    );

    prepareNextResponse();

    element.querySelector<HTMLButtonElement>('button')!.click();

    await fixture.whenStable();

    expect(api.getProducts).toHaveBeenCalledTimes(2);
    expect(api.getProducts).toHaveBeenLastCalledWith(0);

    await respond(page);

    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(element.querySelector('table')).not.toBeNull();
  });

  it.each([401, 403])('should show a login link for HTTP %s', async (status) => {
    result.error(new HttpErrorResponse({ status }));

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')).not.toBeNull();
    expect(element.querySelector('a[href="/pl/admin/login"]')).not.toBeNull();
    expect(element.querySelector('button')).toBeNull();
    expect(element.querySelector('table')).toBeNull();
  });

  it('should retry the requested page and respect pagination boundaries', async () => {
    await respond(page);

    const firstButtons = element.querySelectorAll<HTMLButtonElement>('.products-pagination button');

    expect(firstButtons[0].disabled).toBe(true);
    expect(firstButtons[1].disabled).toBe(false);

    prepareNextResponse();
    firstButtons[1].click();

    await fixture.whenStable();

    expect(api.getProducts).toHaveBeenLastCalledWith(1);

    result.error(new HttpErrorResponse({ status: 503 }));

    await fixture.whenStable();

    prepareNextResponse();

    element.querySelector<HTMLButtonElement>('button')!.click();

    await fixture.whenStable();

    expect(api.getProducts).toHaveBeenLastCalledWith(1);

    await respond({
      ...page,
      page: 1,
    });

    const lastButtons = element.querySelectorAll<HTMLButtonElement>('.products-pagination button');

    expect(lastButtons[0].disabled).toBe(false);
    expect(lastButtons[1].disabled).toBe(true);

    prepareNextResponse();
    lastButtons[0].click();

    await fixture.whenStable();

    expect(api.getProducts).toHaveBeenLastCalledWith(0);

    await respond(page);

    expect(element.querySelector<HTMLButtonElement>('.products-pagination button')!.disabled).toBe(
      true,
    );
  });
    it('should allow cancelling without changing visibility', async () => {
      await respond(page);

      element.querySelector<HTMLButtonElement>('[data-product-id="pattern-001"]')!.click();

      await fixture.whenStable();

      expect(element.querySelector('[data-testid="confirm-visibility"]')).not.toBeNull();

      expect(api.changeVisibility).not.toHaveBeenCalled();

      element.querySelector<HTMLButtonElement>('[data-testid="cancel-visibility"]')!.click();

      await fixture.whenStable();

      expect(element.querySelector('[data-testid="confirm-visibility"]')).toBeNull();

      expect(
        element.querySelector<HTMLButtonElement>('[data-product-id="pattern-001"]')!.disabled,
      ).toBe(false);

      expect(api.changeVisibility).not.toHaveBeenCalled();
    });

    it('should update visibility and use the returned version for the next change', async () => {
      await respond(page);

      const product = page.items[0];

      element.querySelector<HTMLButtonElement>('[data-product-id="pattern-001"]')!.click();

      await fixture.whenStable();

      element.querySelector<HTMLButtonElement>('[data-testid="confirm-visibility"]')!.click();

      await fixture.whenStable();

      expect(api.changeVisibility).toHaveBeenCalledExactlyOnceWith(product.id, {
        active: true,
        expectedVersion: product.version,
      });

      visibilityResult.next({
        ...product,
        active: true,
        version: product.version + 1,
      });
      visibilityResult.complete();

      await fixture.whenStable();

      expect(element.querySelector('tbody tr')?.textContent).toContain('Widoczny');

      expect(element.querySelector('[data-testid="confirm-visibility"]')).toBeNull();

      visibilityResult = new Subject<AdminProductSummary>();
      api.changeVisibility.mockReturnValue(visibilityResult.asObservable());

      element.querySelector<HTMLButtonElement>('[data-product-id="pattern-001"]')!.click();

      await fixture.whenStable();

      element.querySelector<HTMLButtonElement>('[data-testid="confirm-visibility"]')!.click();

      await fixture.whenStable();

      expect(api.changeVisibility).toHaveBeenLastCalledWith(product.id, {
        active: false,
        expectedVersion: product.version + 1,
      });

      visibilityResult.next({
        ...product,
        active: false,
        version: product.version + 2,
      });
      visibilityResult.complete();

      await fixture.whenStable();

      expect(element.querySelector('tbody tr')?.textContent).toContain('Ukryty');
    });

    it('should prevent duplicate submissions while saving', async () => {
      await respond(page);

      element.querySelector<HTMLButtonElement>('[data-product-id="pattern-001"]')!.click();

      await fixture.whenStable();

      const confirmButton = element.querySelector<HTMLButtonElement>(
        '[data-testid="confirm-visibility"]',
      )!;

      confirmButton.click();
      confirmButton.click();

      await fixture.whenStable();

      expect(api.changeVisibility).toHaveBeenCalledTimes(1);
      expect(confirmButton.disabled).toBe(true);

      expect(
        element.querySelector<HTMLButtonElement>('[data-testid="cancel-visibility"]')!.disabled,
      ).toBe(true);

      expect(
        element.querySelector<HTMLButtonElement>('[data-product-id="pattern-001"]')!.disabled,
      ).toBe(true);

      visibilityResult.next({
        ...page.items[0],
        active: true,
        version: page.items[0].version + 1,
      });
      visibilityResult.complete();

      await fixture.whenStable();
    });

    it('should require reloading after a version conflict', async () => {
      await respond(page);

      element.querySelector<HTMLButtonElement>('[data-product-id="pattern-001"]')!.click();

      await fixture.whenStable();

      element.querySelector<HTMLButtonElement>('[data-testid="confirm-visibility"]')!.click();

      await fixture.whenStable();

      visibilityResult.error(
        new HttpErrorResponse({
          status: 409,
          error: { code: 'PRODUCT_VERSION_CONFLICT' },
        }),
      );

      await fixture.whenStable();

      expect(element.querySelector('[role="alert"]')?.textContent).toContain(
        'Produkt został zmieniony.',
      );

      expect(
        element.querySelector<HTMLButtonElement>('[data-product-id="pattern-001"]')!.disabled,
      ).toBe(true);

      prepareNextResponse();

      element.querySelector<HTMLButtonElement>('[data-testid="reload-products"]')!.click();

      await fixture.whenStable();

      expect(api.getProducts).toHaveBeenCalledTimes(2);
      expect(api.getProducts).toHaveBeenLastCalledWith(0);

      await respond({
        ...page,
        items: [
          {
            ...page.items[0],
            active: true,
            version: page.items[0].version + 1,
          },
        ],
      });

      expect(element.querySelector('[role="alert"]')).toBeNull();

      expect(
        element.querySelector<HTMLButtonElement>('[data-product-id="pattern-001"]')!.disabled,
      ).toBe(false);

      expect(element.querySelector('tbody tr')?.textContent).toContain('Widoczny');
    });
      it('should reload the actual visibility after an unconfirmed update', async () => {
        await respond(page);

        element.querySelector<HTMLButtonElement>('[data-product-id="pattern-001"]')!.click();

        await fixture.whenStable();

        element.querySelector<HTMLButtonElement>('[data-testid="confirm-visibility"]')!.click();

        await fixture.whenStable();

        visibilityResult.error(
          new HttpErrorResponse({
            status: 0,
            statusText: 'Unknown Error',
          }),
        );

        await fixture.whenStable();

        expect(element.querySelector('[role="alert"]')?.textContent).toContain(
          'Nie udało się potwierdzić zmiany widoczności.',
        );

        expect(element.querySelector('tbody tr')?.textContent).toContain('Ukryty');

        expect(
          element.querySelector<HTMLButtonElement>('[data-product-id="pattern-001"]')!.disabled,
        ).toBe(true);

        expect(api.changeVisibility).toHaveBeenCalledTimes(1);

        prepareNextResponse();

        element.querySelector<HTMLButtonElement>('[data-testid="reload-products"]')!.click();

        await fixture.whenStable();

        await respond({
          ...page,
          items: [
            {
              ...page.items[0],
              active: true,
              version: page.items[0].version + 1,
            },
          ],
        });

        expect(element.querySelector('[role="alert"]')).toBeNull();

        expect(element.querySelector('tbody tr')?.textContent).toContain('Widoczny');

        expect(
          element.querySelector<HTMLButtonElement>('[data-product-id="pattern-001"]')!.disabled,
        ).toBe(false);

        expect(api.changeVisibility).toHaveBeenCalledTimes(1);
      });
});
