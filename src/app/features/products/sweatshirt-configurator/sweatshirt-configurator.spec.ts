import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SweatshirtConfigurator } from './sweatshirt-configurator';
import { provideRouter } from '@angular/router';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import {
  createSweatshirtVariantsMock,
  provideSweatshirtVariantsTesting,
  testSweatshirtVariants,
} from '../../../testing/sweatshirt-variants-testing';
import { provideProductCatalogTesting } from '../../../testing/product-catalog-testing';
import { SweatshirtVariantsService } from '../sweatshirt-variants.service';
import { CartService } from '../../cart/cart.service';
import { vi } from 'vitest';

describe('SweatshirtConfigurator', () => {
  let component: SweatshirtConfigurator;
  let fixture: ComponentFixture<SweatshirtConfigurator>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SweatshirtConfigurator, getTranslocoTestingModule()],
      providers: [
        provideSweatshirtVariantsTesting(),
        provideRouter([]),
        provideProductCatalogTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SweatshirtConfigurator);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

    it('should require a size before allowing the product to be added', async () => {
      const element: HTMLElement = fixture.nativeElement;
      const selects = element.querySelectorAll('select');
      const sizeSelect = selects[1]!;
      const addButton = element.querySelector<HTMLButtonElement>('.configurator__actions button')!;

      expect(addButton.disabled).toBe(true);

      const availableSizes = Array.from(sizeSelect.options)
        .map((option) => option.value)
        .filter((value) => value !== '');

      expect(availableSizes).toEqual(['M', 'L']);

      sizeSelect.value = 'M';
      sizeSelect.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      await fixture.whenStable();

      expect(addButton.disabled).toBe(false);
      expect(element.querySelector('.configurator__price')?.textContent).toContain('149');
    });

    it('should reset the size and update available options when the fit changes', async () => {
      const element: HTMLElement = fixture.nativeElement;
      const selects = element.querySelectorAll('select');
      const fitSelect = selects[0]!;
      const sizeSelect = selects[1]!;
      const embroiderySelect = selects[3]!;
      const addButton = element.querySelector<HTMLButtonElement>('.configurator__actions button')!;

      sizeSelect.value = 'M';
      sizeSelect.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      await fixture.whenStable();

      expect(addButton.disabled).toBe(false);

      fitSelect.value = 'children';
      fitSelect.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      await fixture.whenStable();

      expect(sizeSelect.value).toBe('');
      expect(addButton.disabled).toBe(true);

      const availableSizes = Array.from(sizeSelect.options)
        .map((option) => option.value)
        .filter((value) => value !== '');

      expect(availableSizes).toEqual(['92']);
      expect(embroiderySelect.value).toBe('large-back');

      sizeSelect.value = '92';
      sizeSelect.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      await fixture.whenStable();

      expect(addButton.disabled).toBe(false);
    });

      it('should preserve size when changing embroidery and colour and add the selected variant', async () => {
        const variants = TestBed.inject(SweatshirtVariantsService) as unknown as ReturnType<
          typeof createSweatshirtVariantsMock
        >;

        const largeVariant = {
          ...testSweatshirtVariants[0]!,
          id: 'test-women-m-black-large',
          embroideryOptionId: 'large-back' as const,
          widthMm: 200,
          heightMm: 200,
          placement: 'back' as const,
          priceInGrosz: 16900,
        };

        variants.state.set({
          status: 'ready',
          variants: [
            largeVariant,
            ...testSweatshirtVariants,
            {
              ...largeVariant,
              id: 'test-women-m-white-large',
              color: 'white',
            },
          ],
        });

        fixture.detectChanges();
        await fixture.whenStable();

        const element: HTMLElement = fixture.nativeElement;
        const selects = element.querySelectorAll('select');
        const sizeSelect = selects[1]!;
        const colorSelect = selects[2]!;
        const embroiderySelect = selects[3]!;
        const addButton = element.querySelector<HTMLButtonElement>(
          '.configurator__actions button',
        )!;

        async function select(control: HTMLSelectElement, value: string): Promise<void> {
          control.value = value;
          control.dispatchEvent(new Event('change'));
          fixture.detectChanges();
          await fixture.whenStable();
        }

        // The displayed default must match the selected small embroidery,
        // even when the API returns the large option first.
        expect(embroiderySelect.value).toBe('small-front');

        await select(sizeSelect, 'M');

        expect(element.querySelector('.configurator__price')?.textContent).toContain('149');

        await select(embroiderySelect, 'large-back');

        expect(sizeSelect.value).toBe('M');
        expect(addButton.disabled).toBe(false);
        expect(element.querySelector('.configurator__price')?.textContent).toContain('169');

        await select(colorSelect, 'white');

        expect(sizeSelect.value).toBe('M');
        expect(embroiderySelect.value).toBe('large-back');
        expect(addButton.disabled).toBe(false);
        expect(element.querySelector('.configurator__price')?.textContent).toContain('169');

        const cart = TestBed.inject(CartService);
        const addSweatshirt = vi.spyOn(cart, 'addSweatshirt');

        addButton.click();

        expect(addSweatshirt).toHaveBeenCalledExactlyOnceWith({
          fit: 'women',
          size: 'M',
          color: 'white',
          embroideryOptionId: 'large-back',
        });
      });
});
