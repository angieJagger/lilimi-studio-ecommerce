import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { ProductDetail } from './product-detail';

describe('ProductDetail', () => {
  let harness: RouterTestingHarness;

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
      ],
    }).compileComponents();

    harness = await RouterTestingHarness.create();
  });

  it('should display the product matching the slug', async () => {
    await harness.navigateByUrl('/pl/products/forest-dragon', ProductDetail);

    const element = harness.routeNativeElement!;

    expect(element.querySelector('h1')?.textContent).toContain('Wzór haftu „Leśny smok”');
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

    expect(details.textContent).toContain('DST, PES, JEF');
    expect(details.textContent).not.toContain('Możliwość personalizacji');

    const links = Array.from(
      element.querySelectorAll<HTMLAnchorElement>('.product-detail__related-link'),
    );

    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/pl/products/floral-monogram',
      '/pl/products/butterfly-pattern',
      '/pl/products/forest-leaves-pattern',
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

    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/pl/products/embroidered-sweatshirt',
      '/pl/products/embroidered-tote-bag',
    ]);
  });
});
