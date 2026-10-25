import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { vi } from 'vitest';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { AdminProductImageApiService } from './admin-product-image-api.service';
import { AdminProductImages } from './admin-product-images';
import type { AdminProductImages as Gallery } from './admin-product-image.model';

describe('AdminProductImages draft', () => {
  let fixture: ComponentFixture<AdminProductImages>;
  let element: HTMLElement;
  const api = { getImages: vi.fn(), getImageContent: vi.fn(), upload: vi.fn(), delete: vi.fn() };
  const gallery: Gallery = {
    productId: 'pattern-001',
    version: 2,
    images: [
      {
        id: 'first',
        contentType: 'image/png',
        byteSize: 3,
        width: 2,
        height: 2,
        sortOrder: 0,
        primaryImage: true,
        altPl: 'Pierwsze',
        altEn: 'First',
      },
      {
        id: 'second',
        contentType: 'image/png',
        byteSize: 3,
        width: 2,
        height: 2,
        sortOrder: 1,
        primaryImage: false,
        altPl: '',
        altEn: '',
      },
    ],
  };
  let counter: number;

  beforeEach(async () => {
    counter = 0;
    vi.stubGlobal(
      'URL',
      class extends URL {
        static override createObjectURL = vi.fn(() => `blob:preview-${++counter}`);
        static override revokeObjectURL = vi.fn();
      },
    );
    api.getImages.mockReset();
    api.getImageContent.mockReset();
    api.upload.mockReset();
    api.delete.mockReset();
    api.getImages.mockReturnValue(of(gallery));
    api.getImageContent.mockReturnValue(of(new Blob(['image'])));
    await TestBed.configureTestingModule({
      imports: [AdminProductImages, getTranslocoTestingModule()],
      providers: [{ provide: AdminProductImageApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(AdminProductImages);
    fixture.componentRef.setInput('productId', gallery.productId);
    fixture.componentRef.setInput('productVersion', gallery.version);
    element = fixture.nativeElement;
    await fixture.whenStable();
  });

  afterEach(() => {
    fixture.destroy();
    vi.unstubAllGlobals();
  });

  function click(label: string, card = 0): void {
    const cards = element.querySelectorAll('.product-images__card');
    const button = Array.from(cards[card].querySelectorAll('button')).find(
      (item) => item.textContent?.trim() === label,
    )!;
    button.click();
  }

  it('should stage removal and undo it without sending a write request', async () => {
    click('Usuń');
    await fixture.whenStable();
    const changes = fixture.componentInstance.buildChanges()!;
    expect(changes.images.map((image) => image.id)).toEqual(['second']);
    expect(changes.images[0].primaryImage).toBe(true);
    expect(element.textContent).toContain('Oznaczone do usunięcia');
    click('Cofnij usunięcie');
    await fixture.whenStable();
    expect(fixture.componentInstance.buildChanges()!.images).toHaveLength(2);
    expect(api.delete).not.toHaveBeenCalled();
    expect(api.upload).not.toHaveBeenCalled();
  });

  it('should reset removal, order and descriptions on cancel', async () => {
    click('Usuń');
    await fixture.whenStable();
    fixture.componentInstance.resetDraft();
    await fixture.whenStable();
    expect(fixture.componentInstance.buildChanges()!.images.map((image) => image.id)).toEqual([
      'first',
      'second',
    ]);
    expect(fixture.componentInstance.buildChanges()!.images[0].primaryImage).toBe(true);
    expect(element.querySelector('.product-images__card--removed')).toBeNull();
  });

  it('should stage a primary image and order change without saving', async () => {
    click('Ustaw jako główne', 1);
    click('Przesuń wcześniej', 1);
    await fixture.whenStable();
    expect(fixture.componentInstance.buildChanges()!.images.map((image) => image.id)).toEqual([
      'second',
      'first',
    ]);
    expect(fixture.componentInstance.buildChanges()!.images[0].primaryImage).toBe(true);
    expect(api.delete).not.toHaveBeenCalled();
    expect(api.upload).not.toHaveBeenCalled();
  });

  it('should stage new files and release their previews on cancel', async () => {
    const input = element.querySelector<HTMLInputElement>('#product-image-files')!;
    const file = new File(['image'], 'new.png', { type: 'image/png' });
    Object.defineProperty(input, 'files', { configurable: true, value: [file] });
    input.dispatchEvent(new Event('change', { bubbles: true }));
    await fixture.whenStable();
    const changes = fixture.componentInstance.buildChanges()!;
    expect(changes.files).toEqual([file]);
    expect(changes.images[2]).toEqual(expect.objectContaining({ id: null, fileIndex: 0 }));
    expect(api.upload).not.toHaveBeenCalled();
    fixture.componentInstance.resetDraft();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview-3');
    expect(fixture.componentInstance.buildChanges()!.files).toEqual([]);
  });

  it('should not overwrite a local replacement when the old preview arrives late', async () => {
    const pending = new Subject<Blob>();
    api.getImageContent.mockReturnValue(pending.asObservable());
    fixture.componentRef.setInput('productVersion', 3);
    api.getImages.mockReturnValue(of({ ...gallery, version: 3 }));
    await fixture.whenStable();
    const input = element.querySelector<HTMLInputElement>('input[id^="image-replacement-"]')!;
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: [new File(['new'], 'new.png', { type: 'image/png' })],
    });
    input.dispatchEvent(new Event('change', { bubbles: true }));
    await fixture.whenStable();
    const localUrl = element.querySelector('img')!.getAttribute('src');
    pending.next(new Blob(['old']));
    await fixture.whenStable();
    expect(element.querySelector('img')!.getAttribute('src')).toBe(localUrl);
  });

  it('should block saving a gallery from a different product version', async () => {
    fixture.componentRef.setInput('productVersion', 3);
    await fixture.whenStable();
    expect(fixture.componentInstance.buildChanges()).toBeNull();
  });
});
