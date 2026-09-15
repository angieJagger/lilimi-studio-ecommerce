import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { ProductList } from './product-list';

describe('ProductList', () => {
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductList, getTranslocoTestingModule()],
      providers: [provideRouter([{ path: 'pl/products', component: ProductList }])],
    }).compileComponents();

    harness = await RouterTestingHarness.create();
  });

  it('should show six products and reveal the remaining product', async () => {
    await harness.navigateByUrl('/pl/products', ProductList);

    const element = harness.routeNativeElement!;

    expect(element.querySelectorAll('app-product-card').length).toBe(6);

    element.querySelector<HTMLButtonElement>('.product-list__pagination button')!.click();

    await harness.fixture.whenStable();

    expect(element.querySelectorAll('app-product-card').length).toBe(7);
    expect(element.querySelector('.product-list__pagination button')).toBeNull();
  });

  it('should filter products and reset the visible limit after category changes', async () => {
    await harness.navigateByUrl('/pl/products', ProductList);

    harness
      .routeNativeElement!.querySelector<HTMLButtonElement>('.product-list__pagination button')!
      .click();

    await harness.fixture.whenStable();

    await harness.navigateByUrl('/pl/products?category=embroidery-patterns', ProductList);

    let element = harness.routeNativeElement!;

    expect(element.querySelectorAll('app-product-card').length).toBe(4);
    expect(element.querySelector('h1')?.textContent).toContain('Wzory haftów');

    for (const card of element.querySelectorAll('app-product-card')) {
      expect(card.textContent).toContain('Plik cyfrowy');
    }

    await harness.navigateByUrl('/pl/products', ProductList);

    element = harness.routeNativeElement!;

    expect(element.querySelectorAll('app-product-card').length).toBe(6);
    expect(element.querySelector('.product-list__pagination button')).not.toBeNull();
  });

  it('should sort by price through the select and preserve the category', async () => {
    await harness.navigateByUrl('/pl/products?category=embroidered-products', ProductList);

    const element = harness.routeNativeElement!;
    const select = element.querySelector<HTMLSelectElement>('#product-sort')!;
    const router = TestBed.inject(Router);

    select.value = 'price-desc';
    select.dispatchEvent(new Event('change', { bubbles: true }));

    await harness.fixture.whenStable();

    expect(element.querySelector('app-product-card')?.textContent).toContain('Bluza z haftem');

    expect(router.parseUrl(router.url).queryParams['category']).toBe('embroidered-products');

    expect(router.parseUrl(router.url).queryParams['sort']).toBe('price-desc');

    select.value = 'price-asc';
    select.dispatchEvent(new Event('change', { bubbles: true }));

    await harness.fixture.whenStable();

    expect(element.querySelector('app-product-card')?.textContent).toContain('Torba z haftem');
  });
});
