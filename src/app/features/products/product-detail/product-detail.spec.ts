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
});
