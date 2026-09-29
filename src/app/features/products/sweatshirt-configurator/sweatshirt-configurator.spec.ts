import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SweatshirtConfigurator } from './sweatshirt-configurator';
import { provideRouter } from '@angular/router';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { provideSweatshirtVariantsTesting } from '../../../testing/sweatshirt-variants-testing';
import { provideProductCatalogTesting } from '../../../testing/product-catalog-testing';

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
});
