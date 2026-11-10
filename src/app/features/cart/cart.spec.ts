import { CartService } from './cart.service';
import { ProductCatalogService } from '../products/product-catalog.service';
import { createProductCatalogMock } from '../../testing/product-catalog-testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Cart } from './cart';
import { provideRouter } from '@angular/router';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { provideSweatshirtVariantsTesting } from '../../testing/sweatshirt-variants-testing';
import { provideProductCatalogTesting } from '../../testing/product-catalog-testing';
import { MAX_CART_ITEM_QUANTITY } from './cart-item.model';
import { getCartItemKey } from './cart-item-key';
import { ProductApiService } from '../products/product-api.service';
import { of } from 'rxjs';
import { vi } from 'vitest';

describe('Cart', () => {
  let component: Cart;
  let fixture: ComponentFixture<Cart>;

  const productApi = { checkQuantity: vi.fn() };

  beforeEach(async () => {
    productApi.checkQuantity.mockReset();
    productApi.checkQuantity.mockReturnValue(of(null));
    await TestBed.configureTestingModule({
      imports: [Cart, getTranslocoTestingModule()],
      providers: [
        provideRouter([]),
        { provide: ProductApiService, useValue: productApi },
        provideSweatshirtVariantsTesting(),
        provideProductCatalogTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Cart);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should display the primary product image in the cart', async () => {
    const catalog = TestBed.inject(ProductCatalogService) as unknown as ReturnType<typeof createProductCatalogMock>;
    const product = catalog.products().find((item) => item.id === 'pattern-001');
    if (!product || product.category !== 'embroidery-patterns') throw new Error('Missing test pattern');
    const updated = {
      ...product,
      images: [
        { id: 'secondary', width: 400, height: 400, primaryImage: false, alt: { pl: 'Drugie', en: 'Second' }, version: 1 },
        { id: 'primary', width: 400, height: 400, primaryImage: true, alt: { pl: 'Smok', en: 'Dragon' }, version: 2 },
      ],
    };
    catalog.state.set({ status: 'ready', products: catalog.products().map((item) => item.id === product.id ? updated : item) });
    TestBed.inject(CartService).addPattern(updated);
    await fixture.whenStable();
    const image = fixture.nativeElement.querySelector('.cart__media img') as HTMLImageElement;
    expect(image.getAttribute('src')).toContain('/forest-dragon/images/primary/content?v=2');
  });
  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show the available maximum only after a quantity change exceeds stock', async () => {
    const cart = TestBed.inject(CartService);
    cart.addSweatshirt('embroidered-002', 'pattern-001', {
      fit: 'women',
      size: 'M',
      color: 'black',
      embroideryOptionId: 'small-front',
    });
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    const input = element.querySelector<HTMLInputElement>('.cart__quantity-controls input')!;
    expect(productApi.checkQuantity).not.toHaveBeenCalled();
    expect(element.querySelector('[role="alert"]')).toBeNull();

    productApi.checkQuantity.mockReturnValueOnce(of({ maxQuantity: 4 }));
    input.value = '6';
    input.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    expect(productApi.checkQuantity).toHaveBeenCalledWith('embroidered-sweatshirt', 7, 'test-women-m-black-small');
    expect(element.querySelector('[role="alert"]')?.textContent).toContain('maksymalnie 4 szt.');
    expect(cart.sweatshirts()[0]?.quantity).toBe(1);
    expect(input.value).toBe('1');

    productApi.checkQuantity.mockReturnValueOnce(of({ maxQuantity: 4 }));
    input.value = '4';
    input.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(cart.sweatshirts()[0]?.quantity).toBe(4);
    const increase = element.querySelectorAll<HTMLButtonElement>('.cart__quantity-controls button')[1]!;
    expect(increase.disabled).toBe(true);
  });

  it('should disable increasing quantity at the limit', async () => {
    const cart = TestBed.inject(CartService);

    cart.addSweatshirt('embroidered-002', 'pattern-001', {
      fit: 'women',
      size: 'M',
      color: 'black',
      embroideryOptionId: 'small-front',
    });

    const key = getCartItemKey(cart.sweatshirts()[0]!);

    cart.updateSweatshirtQuantity(key, MAX_CART_ITEM_QUANTITY);
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    const buttons = element.querySelectorAll<HTMLButtonElement>('.cart__quantity-controls button');

    expect(buttons).toHaveLength(2);
    expect(buttons[0]!.disabled).toBe(false);
    expect(buttons[1]!.disabled).toBe(true);

    buttons[0]!.click();
    await fixture.whenStable();

    expect(cart.sweatshirts()[0]?.quantity).toBe(MAX_CART_ITEM_QUANTITY - 1);
    expect(buttons[1]!.disabled).toBe(false);
  });
});
