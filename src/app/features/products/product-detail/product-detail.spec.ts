import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { ProductDetail } from './product-detail';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { ProductApiService } from '../product-api.service';
import type { ProductApiResponse } from '../product-api.model';
import { vi } from 'vitest';
import { CartService } from '../../cart/cart.service';
import { provideProductCatalogTesting } from '../../../testing/product-catalog-testing';
import { testSweatshirtVariants } from '../../../testing/sweatshirt-variants-testing';

describe('ProductDetail', () => {
  let harness: RouterTestingHarness;

    const apiProducts: readonly ProductApiResponse[] = [
      {
        id: 'pattern-001',
        slug: 'forest-dragon',
        productType: 'digital',
        name: {
          pl: 'Wzór haftu „Leśny smok”',
          en: 'Forest dragon embroidery pattern',
        },
        description: {
          pl: 'Cyfrowy wzór haftu.',
          en: 'Digital embroidery pattern.',
        },
        priceInGrosz: 2900,
        priceType: 'fixed',
        currency: 'PLN',
        fileFormats: ['DST', 'PES', 'JEF'],
        madeToOrder: false,
        personalizationAvailable: false,
      },
      {
        id: 'embroidered-001',
        slug: 'embroidered-shirt',
        productType: 'tshirt',
        name: {
          pl: 'Koszulka z haftem',
          en: 'Embroidered T-shirt',
        },
        description: {
          pl: 'Koszulka z gotowym haftem.',
          en: 'T-shirt with an embroidery design.',
        },
        priceInGrosz: 10000,
        priceType: 'from',
        currency: 'PLN',
        fileFormats: [],
        madeToOrder: true,
        personalizationAvailable: true,
      },
    ];

      const relatedPattern: ProductApiResponse = {
        ...apiProducts[0]!,
        id: 'pattern-api-only',
        slug: 'api-only-pattern',
        name: {
          pl: 'Wzór dostępny z API',
          en: 'Pattern available from API',
        },
      };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductDetail, getTranslocoTestingModule()],
      providers: [
        provideRouter([
          {
            path: 'pl/products/:slug',
            component: ProductDetail,
          },
        ]),
        {
          provide: ProductApiService,
          useValue: {
            getProducts: () => of([...apiProducts, relatedPattern]),
            getVariants: vi.fn(() => of(testSweatshirtVariants)),
            checkQuantity: vi.fn(() => of(null)),
            getProduct: (slug: string) => {
              const product = apiProducts.find((item) => item.slug === slug);

              return product
                ? of(product)
                : throwError(
                    () =>
                      new HttpErrorResponse({
                        status: 404,
                        statusText: 'Not Found',
                      }),
                  );
            },
          },
        },
        provideProductCatalogTesting(),
      ],
    }).compileComponents();

    harness = await RouterTestingHarness.create();
  });

  it('should display the product matching the slug', async () => {
    await harness.navigateByUrl('/pl/products/forest-dragon', ProductDetail);

    const element = harness.routeNativeElement!;

    expect(element.querySelector('h1')?.textContent).toContain('Wzór haftu „Leśny smok”');
  });

  it('should load the configurator for a sweatshirt with a different slug', async () => {
    const api = TestBed.inject(ProductApiService);
    const sweatshirt: ProductApiResponse = {
      ...apiProducts[1]!,
      id: 'embroidered-004',
      slug: 'another-sweatshirt',
      productType: 'sweatshirt',
    };

    vi.spyOn(api, 'getProduct').mockReturnValueOnce(of(sweatshirt));

    await harness.navigateByUrl('/pl/products/another-sweatshirt', ProductDetail);
    await harness.fixture.whenStable();

    expect(harness.routeNativeElement?.querySelector('app-sweatshirt-configurator')).not.toBeNull();
    expect(api.getVariants).toHaveBeenCalledExactlyOnceWith(sweatshirt.slug);
  });

  it('should enforce shared stock when adding different options to the cart', async () => {
    const api = TestBed.inject(ProductApiService);
    const cart = TestBed.inject(CartService);
    const keyring: ProductApiResponse = {
      ...apiProducts[1]!,
      id: 'stock-test-keyring',
      slug: 'stock-test-keyring',
      productType: 'physical',
      priceType: 'fixed',
      variantAttributes: [{ name: 'Color', values: ['Green', 'Black'] }],
    };
    vi.spyOn(api, 'getProduct').mockReturnValueOnce(of(keyring));
    const checkQuantity = vi.mocked(api.checkQuantity)
      .mockReturnValueOnce(of(null))
      .mockReturnValueOnce(of({ maxQuantity: 1 }));

    await harness.navigateByUrl('/pl/products/stock-test-keyring', ProductDetail);
    await harness.fixture.whenStable();

    const form = harness.routeNativeElement!.querySelector('form')!;
    const select = form.querySelector('select')!;
    select.value = 'Green';
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    select.value = 'Black';
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    harness.detectChanges();

    expect(checkQuantity.mock.calls).toEqual([[keyring.slug, 1], [keyring.slug, 2]]);
    const items = cart.physicalItems().filter((item) => item.productId === keyring.id);
    expect(items).toMatchObject([{ options: { Color: 'Green' }, quantity: 1 }]);
    expect(form.querySelector('output')?.textContent).toContain('maksymalnie 1');
    cart.clear();
  });

  it('should update the product when the slug changes', async () => {
    await harness.navigateByUrl('/pl/products/forest-dragon', ProductDetail);

    await harness.navigateByUrl('/pl/products/embroidered-shirt', ProductDetail);

    const element = harness.routeNativeElement!;

    expect(element.querySelector('h1')?.textContent).toContain('Koszulka z haftem');

    expect(element.querySelector('.product-detail__price')?.textContent).toContain('od');
  });

  it('should show a not-found message for an unknown product', async () => {
    await harness.navigateByUrl('/pl/products/unknown-product', ProductDetail);

    const element = harness.routeNativeElement!;

    expect(element.querySelector('h1')?.textContent).toContain('Nie znaleziono produktu');

    expect(element.querySelector('.product-detail__overview')).toBeNull();

    expect(element.querySelector('a')?.getAttribute('href')).toBe('/pl/products');
  });

  it('should show file formats and other embroidery patterns', async () => {
    await harness.navigateByUrl('/pl/products/forest-dragon', ProductDetail);

    const element = harness.routeNativeElement!;
    const details = element.querySelector('.product-detail__details')!;

    expect(details.textContent).toContain('DST · PES · JEF');
    expect(details.textContent).not.toContain('Możliwość personalizacji');

    const links = Array.from(
      element.querySelectorAll<HTMLAnchorElement>('.product-detail__related-link'),
    );

    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/pl/products/api-only-pattern',
    ]);
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/pl/products/api-only-pattern',
    ]);
  });

  it('should show physical product details and update related products', async () => {
    await harness.navigateByUrl('/pl/products/forest-dragon', ProductDetail);

    await harness.navigateByUrl('/pl/products/embroidered-shirt', ProductDetail);

    const element = harness.routeNativeElement!;
    const details = element.querySelector('.product-detail__details')!;

    expect(details.textContent).toContain('Wykonywany na zamówienie');
    expect(details.textContent).toContain('Możliwość personalizacji');
    expect(details.textContent).not.toContain('Formaty plików');

    const values = Array.from(details.querySelectorAll('dd')).map((value) =>
      value.textContent?.trim(),
    );

    expect(values).toEqual(['Tak', 'Tak']);

    const links = Array.from(
      element.querySelectorAll<HTMLAnchorElement>('.product-detail__related-link'),
    );

    expect(links).toHaveLength(0);
    expect(element.querySelector('.product-detail__related')).toBeNull();
    expect(links).toHaveLength(0);
    expect(element.querySelector('.product-detail__related')).toBeNull();
  });

    it('should retry loading the product after a connection error', async () => {
      const productApi = TestBed.inject(ProductApiService);

      const getProduct = vi
        .spyOn(productApi, 'getProduct')
        .mockReturnValueOnce(
          throwError(
            () =>
              new HttpErrorResponse({
                status: 0,
                statusText: 'Unknown Error',
              }),
          ),
        )
        .mockReturnValueOnce(of(apiProducts[0]!));

      await harness.navigateByUrl('/pl/products/forest-dragon', ProductDetail);

      const element = harness.routeNativeElement!;

      expect(element.querySelector('[role="alert"]')?.textContent).toContain(
        'Nie udało się pobrać produktu',
      );

      expect(element.querySelector('.product-detail__overview')).toBeNull();

      const retryButton = element.querySelector<HTMLButtonElement>('button');

      expect(retryButton?.textContent).toContain('Spróbuj ponownie');

      retryButton!.click();
      harness.detectChanges();

      expect(getProduct).toHaveBeenCalledTimes(2);
      expect(getProduct).toHaveBeenLastCalledWith('forest-dragon');

      expect(element.querySelector('[role="alert"]')).toBeNull();
      expect(element.querySelector('h1')?.textContent).toContain('Wzór haftu „Leśny smok”');
    });

      it('should display the product when related products cannot load', async () => {
        const productApi = TestBed.inject(ProductApiService);

        vi.spyOn(productApi, 'getProducts').mockReturnValueOnce(
          throwError(
            () =>
              new HttpErrorResponse({
                status: 500,
                statusText: 'Internal Server Error',
              }),
          ),
        );

        await harness.navigateByUrl('/pl/products/forest-dragon', ProductDetail);

        const element = harness.routeNativeElement!;

        expect(element.querySelector('h1')?.textContent).toContain('Wzór haftu „Leśny smok”');

        expect(element.querySelector('.product-detail__overview')).not.toBeNull();

        expect(element.querySelector('.product-detail__related')).toBeNull();
        expect(element.querySelector('[role="alert"]')).toBeNull();
      });
});
