import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { ProductGallery } from './product-gallery';
import type { ProductImage } from '../product.model';

describe('ProductGallery', () => {
  let fixture: ComponentFixture<ProductGallery>;
  const secondary: ProductImage = {
    id: 'secondary',
    width: 400,
    height: 300,
    primaryImage: false,
    alt: { pl: 'Drugie', en: 'Second' },
    version: 1,
  };
  const primary: ProductImage = {
    ...secondary,
    id: 'primary',
    primaryImage: true,
    alt: { pl: 'Główne', en: 'Main' },
  };
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductGallery, getTranslocoTestingModule()],
    }).compileComponents();
    fixture = TestBed.createComponent(ProductGallery);
    fixture.componentRef.setInput('slug', 'first-product');
    fixture.componentRef.setInput('images', [secondary, primary]);
    fixture.componentRef.setInput('locale', 'pl');
    await fixture.whenStable();
  });
  it('should open the selected image and close it using the close button', async () => {
    const dialog = fixture.nativeElement.querySelector('dialog') as HTMLDialogElement;
    dialog.showModal = () => dialog.setAttribute('open', '');
    dialog.close = () => dialog.removeAttribute('open');
    fixture.nativeElement.querySelector('.gallery__main').click();
    await fixture.whenStable();
    expect(dialog.open).toBe(true);
    expect(dialog.querySelector('img')?.getAttribute('src')).toContain('/primary/');
    (dialog.querySelector('.gallery__zoom-close') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(dialog.open).toBe(false);
  });

  it('should close only when the dialog background is clicked', async () => {
    const dialog = fixture.nativeElement.querySelector('dialog') as HTMLDialogElement;
    dialog.showModal = () => dialog.setAttribute('open', '');
    dialog.close = () => dialog.removeAttribute('open');
    fixture.nativeElement.querySelector('.gallery__main').click();
    await fixture.whenStable();
    (dialog.querySelector('img') as HTMLImageElement).click();
    expect(dialog.open).toBe(true);
    dialog.click();
    expect(dialog.open).toBe(false);
  });
  it('should start with the primary image and accessible thumbnail buttons', () => {
    const main = fixture.nativeElement.querySelector('.gallery__main img');
    expect(main.getAttribute('src')).toContain('/primary/');
    expect(main.alt).toBe('Główne');
    const buttons = fixture.nativeElement.querySelectorAll('.gallery__thumbnails button');
    expect(buttons).toHaveLength(2);
    expect(buttons[0].getAttribute('aria-pressed')).toBe('true');
    expect(buttons[1].getAttribute('aria-label')).toBe('Pokaż zdjęcie 2');
  });
  it('should select another image using its thumbnail', async () => {
    fixture.nativeElement.querySelectorAll('.gallery__thumbnails button')[1].click();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.gallery__main img').getAttribute('src')).toContain(
      '/secondary/',
    );
    expect(fixture.nativeElement.querySelectorAll('.gallery__thumbnails button')[1].getAttribute('aria-pressed')).toBe(
      'true',
    );
  });
  it('should reset selection when navigating to another product', async () => {
    fixture.nativeElement.querySelectorAll('.gallery__thumbnails button')[1].click();
    await fixture.whenStable();
    fixture.componentRef.setInput('slug', 'second-product');
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.gallery__main img').getAttribute('src')).toContain(
      '/second-product/images/primary/',
    );
  });
  it('should hide thumbnail controls for one image and show a placeholder for none', async () => {
    fixture.componentRef.setInput('images', [primary]);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.gallery__thumbnails button')).toBeNull();
    fixture.componentRef.setInput('images', []);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    expect(fixture.nativeElement.querySelector('.placeholder').getAttribute('aria-label')).toBe('Zdjęcie produktu niedostępne');
  });
});
