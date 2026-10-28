import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { ProductImage } from './product-image';
import type { ProductImage as ImageModel } from '../../../features/products/product.model';

describe('ProductImage', () => {
  let fixture: ComponentFixture<ProductImage>;
  const image: ImageModel = {
    id: 'photo/id',
    width: 640,
    height: 480,
    primaryImage: true,
    alt: { pl: 'Haft kwiatowy', en: 'Floral embroidery' },
    version: 4,
  };
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ProductImage] }).compileComponents();
    fixture = TestBed.createComponent(ProductImage);
    fixture.componentRef.setInput('slug', 'product/name');
    fixture.componentRef.setInput('fallbackAlt', 'Product name');
    fixture.componentRef.setInput('fallbackText', 'Image unavailable');
  });

  it('should use a browser-facing encoded URL and the saved dimensions', async () => {
    fixture.componentRef.setInput('image', image);
    await fixture.whenStable();
    const img = fixture.nativeElement.querySelector('img') as HTMLImageElement;
    expect(img.getAttribute('src')).toBe(
      '/api/products/product%2Fname/images/photo%2Fid/content?v=4',
    );
    expect(img.alt).toBe('Floral embroidery');
    expect(img.width).toBe(640);
    expect(img.height).toBe(480);
    expect(img.getAttribute('loading')).toBe('lazy');
  });

  it('should update localized alternative text when language changes', async () => {
    fixture.componentRef.setInput('image', image);
    fixture.componentRef.setInput('locale', 'pl');
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('img').alt).toBe('Haft kwiatowy');
    fixture.componentRef.setInput('locale', 'en');
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('img').alt).toBe('Floral embroidery');
  });

  it('should fall back to the other translation and then product name', async () => {
    fixture.componentRef.setInput('locale', 'pl');
    fixture.componentRef.setInput('image', { ...image, alt: { pl: '', en: 'English alt' } });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('img').alt).toBe('English alt');
    fixture.componentRef.setInput('image', { ...image, alt: { pl: '', en: '' } });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('img').alt).toBe('Product name');
  });

  it('should show a placeholder when no image is configured', async () => {
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    expect(fixture.nativeElement.querySelector('.placeholder').getAttribute('aria-label')).toBe('Image unavailable');
    expect(fixture.nativeElement.querySelector('.placeholder svg')).not.toBeNull();
  });

  it('should recover from a failed image when its version changes', async () => {
    fixture.componentRef.setInput('image', image);
    await fixture.whenStable();
    fixture.nativeElement.querySelector('img').dispatchEvent(new Event('error'));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    fixture.componentRef.setInput('image', { ...image, version: 5 });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('img').getAttribute('src')).toContain('?v=5');
  });
});
